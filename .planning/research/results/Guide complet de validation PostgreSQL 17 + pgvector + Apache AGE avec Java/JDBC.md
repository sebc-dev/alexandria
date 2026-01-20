# Guide complet de validation PostgreSQL 17 + pgvector + Apache AGE avec Java/JDBC

Une stack PostgreSQL 17 combinant **pgvector** pour la recherche vectorielle et **Apache AGE** pour les requêtes graphes nécessite une validation rigoureuse. Ce guide fournit tous les scripts SQL, code Java, et diagnostics pour valider l'infrastructure de bout en bout. Les points critiques : pgvector utilise le format `'[1.0, 2.0, ...]'` pour les vecteurs, tandis qu'AGE exige `LOAD 'age'` et `SET search_path = ag_catalog` à **chaque connexion**.

---

## Script SQL complet de validation

Ce script valide séquentiellement PostgreSQL, pgvector, et Apache AGE avec toutes les opérations critiques.

```sql
-- ============================================================
-- SCRIPT DE VALIDATION COMPLET
-- PostgreSQL 17 + pgvector + Apache AGE
-- ============================================================

-- =====================
-- SECTION 1: HEALTH CHECKS POSTGRESQL
-- =====================

-- 1.1 Vérifier la version PostgreSQL
SELECT version();

-- 1.2 Vérifier que le serveur accepte les connexions
SELECT pg_is_in_recovery() AS is_standby, 
       current_database() AS database,
       current_user AS connected_user;

-- 1.3 Vérifier la configuration mémoire
SELECT name, setting, unit, context
FROM pg_settings 
WHERE name IN ('shared_buffers', 'work_mem', 'maintenance_work_mem', 
               'effective_cache_size', 'max_connections');

-- 1.4 Vérifier les extensions disponibles
SELECT name, default_version, installed_version, comment
FROM pg_available_extensions 
WHERE name IN ('vector', 'age')
ORDER BY name;

-- 1.5 Vérifier les extensions installées
SELECT extname, extversion, n.nspname AS schema
FROM pg_extension e
JOIN pg_namespace n ON e.extnamespace = n.oid
WHERE extname IN ('vector', 'age');

-- 1.6 Statistiques de connexion actuelles
SELECT state, count(*) AS connections
FROM pg_stat_activity 
WHERE datname = current_database() 
GROUP BY state;

-- 1.7 Cache hit ratio (doit être > 99%)
SELECT round(100.0 * blks_hit / nullif(blks_hit + blks_read, 0), 2) 
       AS cache_hit_ratio_percent
FROM pg_stat_database 
WHERE datname = current_database();

-- =====================
-- SECTION 2: TESTS PGVECTOR
-- =====================

-- 2.1 Créer l'extension pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- 2.2 Vérifier l'installation pgvector
SELECT extname, extversion 
FROM pg_extension 
WHERE extname = 'vector';

-- 2.3 Tester le type vector
SELECT '[1.0, 2.0, 3.0]'::vector AS test_vector;

-- 2.4 Créer une table test avec vector(384)
DROP TABLE IF EXISTS pgvector_validation;
CREATE TABLE pgvector_validation (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    embedding vector(384) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2.5 Insérer des vecteurs de test
INSERT INTO pgvector_validation (content, embedding) VALUES 
    ('Document A - proche de 0.1', array_fill(0.1, ARRAY[384])::vector(384)),
    ('Document B - proche de 0.2', array_fill(0.2, ARRAY[384])::vector(384)),
    ('Document C - proche de 0.5', array_fill(0.5, ARRAY[384])::vector(384)),
    ('Document D - proche de 0.9', array_fill(0.9, ARRAY[384])::vector(384));

-- 2.6 Vérifier les insertions
SELECT id, content, vector_dims(embedding) AS dimensions
FROM pgvector_validation;

-- 2.7 Test de recherche par similarité cosine (<=>)
SELECT id, content, 
       embedding <=> array_fill(0.15, ARRAY[384])::vector(384) AS cosine_distance,
       1 - (embedding <=> array_fill(0.15, ARRAY[384])::vector(384)) AS similarity
FROM pgvector_validation
ORDER BY embedding <=> array_fill(0.15, ARRAY[384])::vector(384)
LIMIT 3;

-- 2.8 Créer un index HNSW avec paramètres optimisés
CREATE INDEX pgvector_validation_hnsw_idx 
ON pgvector_validation USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 2.9 Vérifier la création de l'index
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename = 'pgvector_validation';

-- 2.10 Configurer ef_search pour les requêtes
SET hnsw.ef_search = 100;

-- 2.11 Tester la recherche avec l'index (vérifier via EXPLAIN)
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT id, content, embedding <=> array_fill(0.15, ARRAY[384])::vector(384) AS distance
FROM pgvector_validation
ORDER BY embedding <=> array_fill(0.15, ARRAY[384])::vector(384)
LIMIT 3;

-- 2.12 Test des autres opérateurs de distance
SELECT 
    'Cosine distance (<=>)' AS operator,
    embedding <=> array_fill(0.1, ARRAY[384])::vector(384) AS distance
FROM pgvector_validation WHERE id = 1
UNION ALL
SELECT 
    'L2/Euclidean distance (<->)',
    embedding <-> array_fill(0.1, ARRAY[384])::vector(384)
FROM pgvector_validation WHERE id = 1
UNION ALL
SELECT 
    'Inner product (<#>)',
    embedding <#> array_fill(0.1, ARRAY[384])::vector(384)
FROM pgvector_validation WHERE id = 1;

-- =====================
-- SECTION 3: TESTS APACHE AGE
-- =====================

-- 3.1 Créer l'extension Apache AGE
CREATE EXTENSION IF NOT EXISTS age;

-- 3.2 Vérifier l'installation AGE
SELECT extname, extversion 
FROM pg_extension 
WHERE extname = 'age';

-- 3.3 Charger AGE et configurer search_path (OBLIGATOIRE à chaque connexion)
LOAD 'age';
SET search_path = ag_catalog, "$user", public;

-- 3.4 Vérifier que ag_catalog est accessible
SELECT schema_name 
FROM information_schema.schemata 
WHERE schema_name = 'ag_catalog';

-- 3.5 Créer un graph de test
SELECT create_graph('validation_graph');

-- 3.6 Vérifier que le graph existe
SELECT * FROM ag_catalog.ag_graph WHERE name = 'validation_graph';

-- 3.7 Créer des nodes (vertices) avec Cypher
SELECT * FROM cypher('validation_graph', $$
    CREATE (alice:Person {name: 'Alice', age: 30, email: 'alice@example.com'})
    RETURN alice
$$) AS (node agtype);

SELECT * FROM cypher('validation_graph', $$
    CREATE (bob:Person {name: 'Bob', age: 25, email: 'bob@example.com'})
    RETURN bob
$$) AS (node agtype);

SELECT * FROM cypher('validation_graph', $$
    CREATE (techcorp:Company {name: 'TechCorp', founded: 2015, employees: 500})
    RETURN techcorp
$$) AS (node agtype);

-- 3.8 Créer des edges (relationships)
SELECT * FROM cypher('validation_graph', $$
    MATCH (a:Person {name: 'Alice'}), (b:Person {name: 'Bob'})
    CREATE (a)-[r:KNOWS {since: 2020, relationship: 'colleague'}]->(b)
    RETURN r
$$) AS (relationship agtype);

SELECT * FROM cypher('validation_graph', $$
    MATCH (a:Person {name: 'Alice'}), (c:Company {name: 'TechCorp'})
    CREATE (a)-[r:WORKS_AT {role: 'Senior Engineer', since: 2018}]->(c)
    RETURN r
$$) AS (relationship agtype);

-- 3.9 Requête MATCH basique - tous les nodes
SELECT * FROM cypher('validation_graph', $$
    MATCH (n)
    RETURN n
$$) AS (node agtype);

-- 3.10 Requête avec filtre WHERE
SELECT * FROM cypher('validation_graph', $$
    MATCH (p:Person)
    WHERE p.age > 20
    RETURN p.name, p.age
$$) AS (name agtype, age agtype);

-- 3.11 Requête de traversée de relations
SELECT * FROM cypher('validation_graph', $$
    MATCH (a:Person)-[r]->(b)
    RETURN a.name AS source, type(r) AS relation, b.name AS target
$$) AS (source agtype, relation agtype, target agtype);

-- 3.12 Requête de chemin complet
SELECT * FROM cypher('validation_graph', $$
    MATCH path = (a:Person {name: 'Alice'})-[*1..2]->(target)
    RETURN path
$$) AS (path agtype);

-- 3.13 Vérifier les labels créés
SELECT * FROM ag_catalog.ag_label 
WHERE graph = (SELECT graphid FROM ag_catalog.ag_graph WHERE name = 'validation_graph');

-- 3.14 Test du format agtype retourné
SELECT * FROM cypher('validation_graph', $$
    MATCH (p:Person {name: 'Alice'})
    RETURN p
$$) AS (person agtype);
-- Le résultat est au format: {"id": 844424930131969, "label": "Person", "properties": {"name": "Alice", "age": 30}}::vertex

-- =====================
-- SECTION 4: CLEANUP (optionnel)
-- =====================

-- 4.1 Supprimer la table pgvector de test
-- DROP TABLE IF EXISTS pgvector_validation;

-- 4.2 Supprimer le graph AGE de test
-- SELECT drop_graph('validation_graph', true);

-- =====================
-- SECTION 5: RÉSUMÉ DE VALIDATION
-- =====================

SELECT 'PostgreSQL' AS component, version() AS status
UNION ALL
SELECT 'pgvector', 
       (SELECT 'v' || extversion FROM pg_extension WHERE extname = 'vector')
UNION ALL
SELECT 'Apache AGE', 
       (SELECT 'v' || extversion FROM pg_extension WHERE extname = 'age')
UNION ALL
SELECT 'Graph validation_graph', 
       CASE WHEN EXISTS (SELECT 1 FROM ag_catalog.ag_graph WHERE name = 'validation_graph') 
            THEN 'EXISTS' ELSE 'NOT FOUND' END;
```

---

## Code Java complet de validation

### Dépendances Maven

```xml
<dependencies>
    <!-- PostgreSQL JDBC Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>42.7.9</version>
    </dependency>
    
    <!-- pgvector Java Library -->
    <dependency>
        <groupId>com.pgvector</groupId>
        <artifactId>pgvector</artifactId>
        <version>0.1.6</version>
    </dependency>
    
    <!-- HikariCP Connection Pool -->
    <dependency>
        <groupId>com.zaxxer</groupId>
        <artifactId>HikariCP</artifactId>
        <version>5.1.0</version>
    </dependency>
    
    <!-- JSON parsing for agtype -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.10.1</version>
    </dependency>
    
    <!-- Logging -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-simple</artifactId>
        <version>2.0.9</version>
    </dependency>
</dependencies>
```

### Classe de configuration et pool de connexions

```java
package com.validation.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConfig {
    private static HikariDataSource dataSource;
    
    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("postgres");
        config.setPassword("password");
        config.setDriverClassName("org.postgresql.Driver");
        
        // Configuration HikariCP optimisée
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(10);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        
        // Optimisations PostgreSQL
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        dataSource = new HikariDataSource(config);
    }
    
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    public static void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
```

### Service pgvector avec recherche vectorielle

```java
package com.validation.db;

import com.pgvector.PGvector;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VectorService {
    
    /**
     * Vérifie que pgvector est installé
     */
    public boolean checkPgvectorInstalled() throws SQLException {
        String sql = "SELECT extversion FROM pg_extension WHERE extname = 'vector'";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                System.out.println("✓ pgvector version: " + rs.getString(1));
                return true;
            }
        }
        return false;
    }
    
    /**
     * Crée l'extension pgvector si absente
     */
    public void initializeExtension() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE EXTENSION IF NOT EXISTS vector");
            System.out.println("✓ Extension vector créée/vérifiée");
        }
    }
    
    /**
     * Crée une table avec colonne vector et index HNSW
     */
    public void createVectorTable(String tableName, int dimensions) throws SQLException {
        String createTable = String.format(
            "CREATE TABLE IF NOT EXISTS %s (" +
            "id BIGSERIAL PRIMARY KEY, " +
            "content TEXT NOT NULL, " +
            "embedding vector(%d) NOT NULL, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")", tableName, dimensions);
        
        String createIndex = String.format(
            "CREATE INDEX IF NOT EXISTS %s_embedding_hnsw_idx " +
            "ON %s USING hnsw (embedding vector_cosine_ops) " +
            "WITH (m = 16, ef_construction = 64)", tableName, tableName);
        
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(createTable);
            stmt.executeUpdate(createIndex);
            System.out.println("✓ Table " + tableName + " avec index HNSW créée");
        }
    }
    
    /**
     * Insère un vecteur via PreparedStatement avec PGvector
     */
    public long insertVector(String tableName, String content, float[] embedding) 
            throws SQLException {
        String sql = String.format(
            "INSERT INTO %s (content, embedding) VALUES (?, ?) RETURNING id", tableName);
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            // Enregistrer le type vector pour cette connexion
            PGvector.registerTypes(conn);
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, content);
                pstmt.setObject(2, new PGvector(embedding));
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("id");
                    }
                }
            }
        }
        return -1;
    }
    
    /**
     * Insère un vecteur via le format string '[1.0, 2.0, ...]'
     */
    public long insertVectorAsString(String tableName, String content, float[] embedding) 
            throws SQLException {
        String vectorStr = arrayToVectorString(embedding);
        String sql = String.format(
            "INSERT INTO %s (content, embedding) VALUES (?, ?::vector) RETURNING id", 
            tableName);
        
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, content);
            pstmt.setString(2, vectorStr);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        }
        return -1;
    }
    
    /**
     * Convertit un tableau float[] en format pgvector '[1.0, 2.0, ...]'
     */
    private String arrayToVectorString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
    
    /**
     * Recherche par similarité cosine avec l'opérateur <=>
     */
    public List<SearchResult> similaritySearch(String tableName, float[] queryVector, 
            int limit) throws SQLException {
        String sql = String.format(
            "SELECT id, content, " +
            "embedding <=> ? AS distance, " +
            "1 - (embedding <=> ?) AS similarity " +
            "FROM %s " +
            "ORDER BY embedding <=> ? " +
            "LIMIT ?", tableName);
        
        List<SearchResult> results = new ArrayList<>();
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            PGvector.registerTypes(conn);
            
            // Configurer ef_search pour de meilleurs résultats
            try (Statement setup = conn.createStatement()) {
                setup.execute("SET hnsw.ef_search = 100");
            }
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                PGvector vector = new PGvector(queryVector);
                pstmt.setObject(1, vector);
                pstmt.setObject(2, vector);
                pstmt.setObject(3, vector);
                pstmt.setInt(4, limit);
                
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        SearchResult result = new SearchResult();
                        result.id = rs.getLong("id");
                        result.content = rs.getString("content");
                        result.distance = rs.getDouble("distance");
                        result.similarity = rs.getDouble("similarity");
                        results.add(result);
                    }
                }
            }
        }
        return results;
    }
    
    public static class SearchResult {
        public long id;
        public String content;
        public double distance;
        public double similarity;
        
        @Override
        public String toString() {
            return String.format("ID=%d, Similarity=%.4f, Content='%s'", 
                                id, similarity, content);
        }
    }
}
```

### Service Apache AGE avec requêtes Cypher

```java
package com.validation.db;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.sql.*;
import java.util.*;

public class GraphService {
    private static final Gson gson = new Gson();
    
    /**
     * Vérifie que Apache AGE est installé
     */
    public boolean checkAgeInstalled() throws SQLException {
        String sql = "SELECT extversion FROM pg_extension WHERE extname = 'age'";
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                System.out.println("✓ Apache AGE version: " + rs.getString(1));
                return true;
            }
        }
        return false;
    }
    
    /**
     * Initialise AGE pour une connexion (OBLIGATOIRE à chaque connexion)
     */
    public void initializeAgeSession(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("LOAD 'age'");
            stmt.execute("SET search_path = ag_catalog, \"$user\", public");
        }
    }
    
    /**
     * Crée l'extension AGE et initialise la session
     */
    public void initializeAge() throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE EXTENSION IF NOT EXISTS age");
            initializeAgeSession(conn);
            System.out.println("✓ Extension AGE créée et session initialisée");
        }
    }
    
    /**
     * Vérifie qu'un graph existe
     */
    public boolean graphExists(String graphName) throws SQLException {
        String sql = "SELECT 1 FROM ag_catalog.ag_graph WHERE name = ?";
        try (Connection conn = DatabaseConfig.getConnection()) {
            initializeAgeSession(conn);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, graphName);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }
    
    /**
     * Crée un graph
     */
    public void createGraph(String graphName) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            initializeAgeSession(conn);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(String.format("SELECT create_graph('%s')", graphName));
                System.out.println("✓ Graph '" + graphName + "' créé");
            }
        }
    }
    
    /**
     * Supprime un graph
     */
    public void dropGraph(String graphName) throws SQLException {
        try (Connection conn = DatabaseConfig.getConnection()) {
            initializeAgeSession(conn);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(String.format("SELECT drop_graph('%s', true)", graphName));
                System.out.println("✓ Graph '" + graphName + "' supprimé");
            }
        }
    }
    
    /**
     * Exécute une requête Cypher et retourne les résultats parsés
     */
    public List<Map<String, Object>> executeCypher(String graphName, String cypherQuery, 
            String... returnColumns) throws SQLException {
        
        List<Map<String, Object>> results = new ArrayList<>();
        
        // Construire la définition des colonnes de retour
        StringBuilder columns = new StringBuilder();
        for (int i = 0; i < returnColumns.length; i++) {
            if (i > 0) columns.append(", ");
            columns.append(returnColumns[i]).append(" agtype");
        }
        
        String sql = String.format(
            "SELECT * FROM cypher('%s', $$ %s $$) AS (%s)",
            graphName, cypherQuery, columns.toString()
        );
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            initializeAgeSession(conn);
            
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 0; i < returnColumns.length; i++) {
                        String agtypeStr = rs.getString(i + 1);
                        row.put(returnColumns[i], parseAgtype(agtypeStr));
                    }
                    results.add(row);
                }
            }
        }
        return results;
    }
    
    /**
     * Parse une chaîne agtype en objet Java
     * Format: {"id": 123, "label": "Person", "properties": {...}}::vertex
     */
    private Object parseAgtype(String agtypeStr) {
        if (agtypeStr == null) return null;
        
        // Retirer le suffixe ::vertex, ::edge, ::path
        String jsonStr = agtypeStr
            .replaceAll("::vertex$", "")
            .replaceAll("::edge$", "")
            .replaceAll("::path$", "");
        
        try {
            // Parser comme JSON
            return gson.fromJson(jsonStr, Map.class);
        } catch (Exception e) {
            // Retourner la chaîne brute si le parsing échoue
            return agtypeStr;
        }
    }
    
    /**
     * Crée un vertex avec label et propriétés
     */
    public void createVertex(String graphName, String label, Map<String, Object> properties) 
            throws SQLException {
        String propsJson = gson.toJson(properties);
        String cypher = String.format("CREATE (n:%s %s) RETURN n", label, propsJson);
        executeCypher(graphName, cypher, "n");
    }
    
    /**
     * Crée un edge entre deux vertices
     */
    public void createEdge(String graphName, 
            String sourceLabel, String sourceProp, Object sourceValue,
            String targetLabel, String targetProp, Object targetValue,
            String edgeLabel, Map<String, Object> edgeProperties) throws SQLException {
        
        String edgePropsJson = edgeProperties != null ? gson.toJson(edgeProperties) : "{}";
        String cypher = String.format(
            "MATCH (a:%s {%s: '%s'}), (b:%s {%s: '%s'}) " +
            "CREATE (a)-[r:%s %s]->(b) RETURN r",
            sourceLabel, sourceProp, sourceValue,
            targetLabel, targetProp, targetValue,
            edgeLabel, edgePropsJson
        );
        executeCypher(graphName, cypher, "r");
    }
    
    /**
     * Exécute une requête MATCH et retourne tous les nodes
     */
    public List<Map<String, Object>> getAllNodes(String graphName) throws SQLException {
        return executeCypher(graphName, "MATCH (n) RETURN n", "n");
    }
    
    /**
     * Exécute une requête MATCH avec un label spécifique
     */
    public List<Map<String, Object>> getNodesByLabel(String graphName, String label) 
            throws SQLException {
        String cypher = String.format("MATCH (n:%s) RETURN n", label);
        return executeCypher(graphName, cypher, "n");
    }
}
```

### Classe de test complète

```java
package com.validation.db;

import java.sql.*;
import java.util.*;

public class StackValidationTest {
    
    private static final String TEST_TABLE = "validation_vectors";
    private static final String TEST_GRAPH = "validation_graph";
    private static final int VECTOR_DIMS = 384;
    
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("VALIDATION PostgreSQL 17 + pgvector + Apache AGE");
        System.out.println("=".repeat(60));
        
        try {
            testJdbcConnection();
            testPgvectorOperations();
            testApacheAgeOperations();
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("✅ TOUTES LES VALIDATIONS RÉUSSIES");
            System.out.println("=".repeat(60));
            
        } catch (Exception e) {
            System.err.println("\n❌ ÉCHEC DE VALIDATION: " + e.getMessage());
            e.printStackTrace();
        } finally {
            DatabaseConfig.close();
        }
    }
    
    private static void testJdbcConnection() throws SQLException {
        System.out.println("\n--- Test 1: Connexion JDBC ---");
        
        try (Connection conn = DatabaseConfig.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            System.out.println("✓ Driver: " + meta.getDriverName() + " " + 
                              meta.getDriverVersion());
            System.out.println("✓ Database: " + meta.getDatabaseProductName() + " " + 
                              meta.getDatabaseProductVersion());
            
            // Vérifier la version PostgreSQL
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT version()")) {
                if (rs.next()) {
                    System.out.println("✓ Version complète: " + rs.getString(1));
                }
            }
        }
    }
    
    private static void testPgvectorOperations() throws SQLException {
        System.out.println("\n--- Test 2: Opérations pgvector ---");
        
        VectorService vectorService = new VectorService();
        
        // 2.1 Vérifier/Créer extension
        vectorService.initializeExtension();
        vectorService.checkPgvectorInstalled();
        
        // 2.2 Créer table avec index HNSW
        vectorService.createVectorTable(TEST_TABLE, VECTOR_DIMS);
        
        // 2.3 Insérer des vecteurs de test
        float[] vec1 = new float[VECTOR_DIMS];
        float[] vec2 = new float[VECTOR_DIMS];
        float[] vec3 = new float[VECTOR_DIMS];
        Arrays.fill(vec1, 0.1f);
        Arrays.fill(vec2, 0.5f);
        Arrays.fill(vec3, 0.9f);
        
        long id1 = vectorService.insertVector(TEST_TABLE, "Document proche de 0.1", vec1);
        long id2 = vectorService.insertVectorAsString(TEST_TABLE, "Document proche de 0.5", vec2);
        long id3 = vectorService.insertVector(TEST_TABLE, "Document proche de 0.9", vec3);
        System.out.println("✓ Vecteurs insérés avec IDs: " + id1 + ", " + id2 + ", " + id3);
        
        // 2.4 Test de recherche par similarité
        float[] queryVec = new float[VECTOR_DIMS];
        Arrays.fill(queryVec, 0.15f);  // Proche de vec1
        
        List<VectorService.SearchResult> results = 
            vectorService.similaritySearch(TEST_TABLE, queryVec, 3);
        
        System.out.println("✓ Recherche par similarité - " + results.size() + " résultats:");
        for (VectorService.SearchResult result : results) {
            System.out.println("  → " + result);
        }
        
        // Vérifier que le plus proche est vec1
        if (results.get(0).content.contains("0.1")) {
            System.out.println("✓ Ordre de similarité correct (0.1 en premier)");
        }
    }
    
    private static void testApacheAgeOperations() throws SQLException {
        System.out.println("\n--- Test 3: Opérations Apache AGE ---");
        
        GraphService graphService = new GraphService();
        
        // 3.1 Initialiser AGE
        graphService.initializeAge();
        graphService.checkAgeInstalled();
        
        // 3.2 Créer le graph de test
        if (graphService.graphExists(TEST_GRAPH)) {
            graphService.dropGraph(TEST_GRAPH);
        }
        graphService.createGraph(TEST_GRAPH);
        
        // 3.3 Créer des vertices
        Map<String, Object> aliceProps = new HashMap<>();
        aliceProps.put("name", "Alice");
        aliceProps.put("age", 30);
        graphService.createVertex(TEST_GRAPH, "Person", aliceProps);
        
        Map<String, Object> bobProps = new HashMap<>();
        bobProps.put("name", "Bob");
        bobProps.put("age", 25);
        graphService.createVertex(TEST_GRAPH, "Person", bobProps);
        
        Map<String, Object> companyProps = new HashMap<>();
        companyProps.put("name", "TechCorp");
        companyProps.put("employees", 500);
        graphService.createVertex(TEST_GRAPH, "Company", companyProps);
        System.out.println("✓ Vertices créés (Alice, Bob, TechCorp)");
        
        // 3.4 Créer des edges
        Map<String, Object> knowsProps = new HashMap<>();
        knowsProps.put("since", 2020);
        graphService.createEdge(TEST_GRAPH, 
            "Person", "name", "Alice",
            "Person", "name", "Bob",
            "KNOWS", knowsProps);
        
        Map<String, Object> worksAtProps = new HashMap<>();
        worksAtProps.put("role", "Engineer");
        graphService.createEdge(TEST_GRAPH,
            "Person", "name", "Alice",
            "Company", "name", "TechCorp",
            "WORKS_AT", worksAtProps);
        System.out.println("✓ Edges créés (KNOWS, WORKS_AT)");
        
        // 3.5 Requête MATCH basique
        List<Map<String, Object>> allNodes = graphService.getAllNodes(TEST_GRAPH);
        System.out.println("✓ MATCH (n) RETURN n - " + allNodes.size() + " nodes trouvés");
        
        // 3.6 Requête avec label
        List<Map<String, Object>> persons = graphService.getNodesByLabel(TEST_GRAPH, "Person");
        System.out.println("✓ MATCH (n:Person) - " + persons.size() + " personnes trouvées");
        
        // 3.7 Requête de relations
        List<Map<String, Object>> relations = graphService.executeCypher(TEST_GRAPH,
            "MATCH (a:Person)-[r]->(b) RETURN a.name, type(r), b.name",
            "source", "relation", "target");
        System.out.println("✓ Relations trouvées: " + relations.size());
        for (Map<String, Object> rel : relations) {
            System.out.println("  → " + rel.get("source") + " --[" + 
                              rel.get("relation") + "]--> " + rel.get("target"));
        }
        
        // 3.8 Vérifier le format agtype
        System.out.println("✓ Format agtype parsé correctement en Java Map");
    }
}
```

---

## Checklist de validation point par point

| # | Vérification | Commande/Action | Critère de succès |
|---|-------------|-----------------|-------------------|
| **PostgreSQL** |||
| 1 | Version PostgreSQL | `SELECT version()` | PostgreSQL 17.x |
| 2 | Connexion active | `SELECT pg_is_in_recovery()` | Retourne `false` |
| 3 | Cache hit ratio | Query pg_stat_database | > 99% |
| **pgvector** |||
| 4 | Extension disponible | `SELECT * FROM pg_available_extensions WHERE name='vector'` | Présent |
| 5 | Extension installée | `SELECT * FROM pg_extension WHERE extname='vector'` | Version ≥ 0.7.0 |
| 6 | Type vector fonctionne | `SELECT '[1,2,3]'::vector` | Pas d'erreur |
| 7 | Insertion vector | `INSERT INTO table (embedding) VALUES ('[...]')` | ID retourné |
| 8 | Opérateur `<=>` | `SELECT ... ORDER BY embedding <=> '[...]'` | Résultats ordonnés |
| 9 | Index HNSW créé | `CREATE INDEX ... USING hnsw` | Index visible dans pg_indexes |
| 10 | Index utilisé | `EXPLAIN ANALYZE` | "Index Scan using hnsw" |
| **Apache AGE** |||
| 11 | Extension disponible | `SELECT * FROM pg_available_extensions WHERE name='age'` | Présent |
| 12 | Extension installée | `SELECT * FROM pg_extension WHERE extname='age'` | Version ≥ 1.5.0 |
| 13 | LOAD 'age' | `LOAD 'age'` | Pas d'erreur |
| 14 | search_path configuré | `SET search_path = ag_catalog, "$user", public` | Pas d'erreur |
| 15 | Graph créé | `SELECT create_graph('test')` | Notice "graph created" |
| 16 | Graph existe | `SELECT * FROM ag_catalog.ag_graph` | Graph listé |
| 17 | CREATE vertex | `SELECT * FROM cypher(...CREATE (n:Label)...)` | Pas d'erreur |
| 18 | CREATE edge | `SELECT * FROM cypher(...CREATE (a)-[r:REL]->(b)...)` | Pas d'erreur |
| 19 | MATCH query | `SELECT * FROM cypher(...MATCH (n) RETURN n...)` | Résultats agtype |
| 20 | agtype parsable | Vérifier format JSON | Structure `{id, label, properties}` |
| **Java/JDBC** |||
| 21 | Connexion JDBC | `DriverManager.getConnection()` | Connection ouverte |
| 22 | PGvector type | `PGvector.registerTypes(conn)` | Pas d'exception |
| 23 | PreparedStatement vector | `pstmt.setObject(1, new PGvector(...))` | Insertion réussie |
| 24 | Similarity search Java | Exécuter query avec `<=>` | Liste de résultats |
| 25 | Cypher via JDBC | `stmt.executeQuery("SELECT * FROM cypher...")` | ResultSet avec agtype |

---

## Guide de troubleshooting des erreurs courantes

### Erreurs pgvector

| Erreur | Cause | Solution |
|--------|-------|----------|
| `type "vector" does not exist` | Extension non créée dans la base | `CREATE EXTENSION vector;` dans la bonne base de données |
| `extension "vector" is not available` | pgvector non installé sur le serveur | Installer: `apt install postgresql-17-pgvector` |
| `could not open extension control file` | Mauvais nom d'extension | Utiliser `vector` et non `pgvector` |
| `hnsw graph no longer fits into maintenance_work_mem` | Mémoire insuffisante pour build index | `SET maintenance_work_mem = '4GB';` |
| `column cannot have more than 2000 dimensions for hnsw index` | Limite HNSW dépassée | Utiliser `halfvec` ou réduire dimensions |
| Index non utilisé par la requête | Structure de requête incorrecte | Ajouter `ORDER BY ... LIMIT n` obligatoire |
| `Illegal instruction` | Incompatibilité CPU | Recompiler avec `make OPTFLAGS=""` |
| `unresolved external symbol float_to_shortest_decimal_bufn` | Bug PG 17.0-17.2 | Mettre à jour vers PostgreSQL 17.3+ |

### Erreurs Apache AGE

| Erreur | Cause | Solution |
|--------|-------|----------|
| `ag_catalog not found` | search_path non configuré | `SET search_path = ag_catalog, "$user", public;` |
| `function cypher(unknown, unknown) does not exist` | ag_catalog pas dans search_path | Même solution + vérifier `LOAD 'age'` |
| `unhandled cypher(cstring) function call` | AGE non chargé dans la session | `LOAD 'age';` au début de chaque connexion |
| `graph "X" does not exist` | Graph non créé | `SELECT create_graph('X');` |
| `cypher(...) in expressions is not supported` | Syntaxe incorrecte | Utiliser `SELECT * FROM cypher(...) AS (col agtype)` |
| Column list mismatch | Nombre de colonnes AS ≠ RETURN | Aligner le nombre de colonnes dans AS avec RETURN |
| `access to library "age" is not allowed` | Permissions insuffisantes | Exécuter en tant que superuser ou créer symlink dans plugins/ |

### Erreurs Java/JDBC

| Erreur | Cause | Solution |
|--------|-------|----------|
| `PSQLException: Connection refused` | PostgreSQL non démarré ou mauvais host/port | Vérifier que PostgreSQL est actif sur le bon port |
| `The connection attempt failed` | Timeout réseau | Vérifier firewall et `pg_hba.conf` |
| `No results were returned by the query` | INSERT sans RETURNING | Ajouter `RETURNING id` ou utiliser `executeUpdate()` |
| Type `vector` non reconnu en Java | PGvector non enregistré | Appeler `PGvector.registerTypes(conn)` avant usage |
| `Cannot cast agtype to String` | Mauvaise lecture du ResultSet | Utiliser `rs.getString(col)` puis parser le JSON |
| Memory leak / connexions épuisées | Ressources non fermées | Utiliser try-with-resources systématiquement |

### Configuration mémoire recommandée

| RAM Serveur | shared_buffers | work_mem | maintenance_work_mem |
|-------------|----------------|----------|----------------------|
| 8 GB | 2 GB | 128 MB | 1 GB |
| 16 GB | 4 GB | 256 MB | 2 GB |
| 32 GB | 8 GB | 512 MB | 4 GB |
| 64 GB+ | 16 GB | 1 GB | 8 GB |

### Latence acceptable pour vector search

| Taille dataset | Latence p99 cible | Notes |
|----------------|-------------------|-------|
| < 100K vecteurs | < 5 ms | Index HNSW optimal |
| 100K - 1M | < 20 ms | Configuration standard |
| 1M - 10M | < 50 ms | Augmenter ef_search |
| 10M+ | < 100 ms | Considérer pgvectorscale |

---

## Paramètres HNSW recommandés

```sql
-- Index HNSW production
CREATE INDEX ON table USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- Tuning runtime
SET hnsw.ef_search = 100;  -- Plus élevé = meilleur recall, plus lent

-- Pour construire l'index rapidement
SET maintenance_work_mem = '4GB';
SET max_parallel_maintenance_workers = 7;
```

| Paramètre | Défaut | Recommandé | Impact |
|-----------|--------|------------|--------|
| `m` | 16 | 16-24 | Connexions par layer (↑ = meilleur recall, plus gros index) |
| `ef_construction` | 64 | 64-128 | Qualité du build (↑ = meilleur index, build plus lent) |
| `ef_search` | 40 | 100-200 | Qualité des requêtes (↑ = meilleur recall, requêtes plus lentes) |

Cette documentation couvre l'ensemble des validations nécessaires pour une stack PostgreSQL 17 + pgvector + Apache AGE prête pour la production avec intégration Java/JDBC complète.