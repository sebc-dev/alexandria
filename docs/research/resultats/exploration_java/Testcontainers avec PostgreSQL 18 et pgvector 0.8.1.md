# Testcontainers avec PostgreSQL 18 et pgvector 0.8.1

**PostgreSQL 18 + pgvector 0.8.1 est pleinement supporté** via l'image officielle `pgvector/pgvector:0.8.1-pg18`. Le support halfvec (vecteurs demi-précision) est disponible depuis pgvector 0.7.0 et fonctionne parfaitement avec la version 0.8.1. Pour les tests d'intégration avec Langchain4j, l'extension peut être créée automatiquement via `createTable(true)`, éliminant le besoin de scripts d'initialisation manuels.

## Image Docker recommandée

L'image officielle **pgvector/pgvector** est maintenue par l'organisation pgvector et supporte PostgreSQL 13 à 18. Pour votre configuration spécifique :

| Tag | Description |
|-----|-------------|
| `pgvector/pgvector:pg18` | PostgreSQL 18 + dernière version pgvector (actuellement 0.8.1) |
| `pgvector/pgvector:0.8.1-pg18` | **Recommandé** - version explicite pour reproductibilité |
| `pgvector/pgvector:0.8.1-pg18-bookworm` | Version complète avec variante Debian |

L'image **ankane/pgvector** est un compte personnel legacy d'Andrew Kane, moins maintenu et sans support PostgreSQL 18. Utilisez exclusivement `pgvector/pgvector`.

**Point critique** : l'extension vector est **pré-installée mais pas pré-activée**. PostgreSQL exige l'activation explicite par base de données via `CREATE EXTENSION vector`.

## Configuration Testcontainers complète

Pour Spring Boot 3.4+ avec Java 25, la configuration optimale utilise `@ServiceConnection` pour l'auto-configuration du datasource :

```java
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EmbeddingStoreIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:0.8.1-pg18")
            .asCompatibleSubstituteFor("postgres")
    );
}
```

L'image `pgvector/pgvector` est nativement reconnue par Testcontainers. La méthode `asCompatibleSubstituteFor("postgres")` garantit la compatibilité avec `@ServiceConnection`, bien que certaines versions récentes de Testcontainers la reconnaissent directement.

## Configuration avec Langchain4j EmbeddingStore

Pour les tests d'intégration Langchain4j, **aucun script d'initialisation n'est nécessaire** car `PgVectorEmbeddingStore` gère automatiquement la création de l'extension :

```java
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;

@SpringBootTest
@Testcontainers
class PgVectorEmbeddingStoreTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:0.8.1-pg18")
            .asCompatibleSubstituteFor("postgres")
    );
    
    @Autowired
    private DataSource dataSource;
    
    private EmbeddingStore<TextSegment> embeddingStore;
    
    @BeforeEach
    void setUp() {
        embeddingStore = PgVectorEmbeddingStore.builder()
            .datasource(dataSource)
            .table("document_embeddings")
            .dimension(1024)  // Pour halfvec 1024D
            .createTable(true)  // Exécute CREATE EXTENSION IF NOT EXISTS vector
            .dropTableFirst(true)  // État propre entre tests
            .useIndex(true)
            .indexListSize(100)
            .build();
    }
    
    @Test
    void shouldStoreAndQueryEmbeddings() {
        // Tests d'embedding...
    }
}
```

Le paramètre `createTable(true)` exécute automatiquement `CREATE EXTENSION IF NOT EXISTS vector` et crée la table avec les colonnes appropriées.

## Support halfvec confirmé

Le type **halfvec** (vecteurs demi-précision 16 bits) a été introduit dans **pgvector 0.7.0** (29 avril 2024) et est pleinement supporté dans la version 0.8.1. Caractéristiques techniques :

- **Stockage** : 2 octets par dimension + 8 octets overhead (50% de réduction vs vector)
- **Dimensions maximales** : 16 000 (vs 2 000 pour vector standard)
- **Dimensions indexables** : jusqu'à 4 000
- **Opérateurs d'index** : `halfvec_l2_ops`, `halfvec_cosine_ops`, `halfvec_ip_ops`

Pour utiliser halfvec avec 1024 dimensions :

```sql
-- Création extension (automatique avec Langchain4j createTable=true)
CREATE EXTENSION IF NOT EXISTS vector;

-- Table avec halfvec
CREATE TABLE embeddings (
    id UUID PRIMARY KEY,
    content TEXT,
    embedding halfvec(1024)
);

-- Index HNSW optimisé pour recherche cosine
CREATE INDEX ON embeddings USING hnsw (embedding halfvec_cosine_ops);
```

## Script d'initialisation alternatif

Si vous n'utilisez pas Langchain4j ou préférez un contrôle explicite, créez `src/test/resources/init-pgvector.sql` :

```sql
CREATE EXTENSION IF NOT EXISTS vector;

-- Optionnel: table avec halfvec pour vos tests
CREATE TABLE IF NOT EXISTS document_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    metadata JSONB,
    embedding halfvec(1024)
);

CREATE INDEX IF NOT EXISTS idx_embedding_hnsw 
    ON document_embeddings USING hnsw (embedding halfvec_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

Configuration Testcontainers avec script :

```java
@Container
@ServiceConnection
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
    DockerImageName.parse("pgvector/pgvector:0.8.1-pg18")
        .asCompatibleSubstituteFor("postgres")
)
.withInitScript("init-pgvector.sql");
```

## Dépendances Maven requises

```xml
<dependencies>
    <!-- Testcontainers -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    
    <!-- Langchain4j pgvector -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-pgvector</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- PostgreSQL driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

## Vérification du support halfvec dans les tests

Pour valider que votre environnement supporte correctement halfvec :

```java
@Test
void shouldSupportHalfvecType() {
    try (Connection conn = dataSource.getConnection();
         Statement stmt = conn.createStatement()) {
        
        // Vérifier version pgvector >= 0.7.0
        ResultSet rs = stmt.executeQuery(
            "SELECT extversion FROM pg_extension WHERE extname = 'vector'"
        );
        assertTrue(rs.next());
        String version = rs.getString(1);
        assertTrue(version.compareTo("0.7.0") >= 0, 
            "halfvec requires pgvector >= 0.7.0, found: " + version);
        
        // Tester création table halfvec
        stmt.execute("CREATE TEMP TABLE test_halfvec (embedding halfvec(1024))");
        stmt.execute("INSERT INTO test_halfvec VALUES ('[" + 
            String.join(",", Collections.nCopies(1024, "0.5")) + "]')");
        
        rs = stmt.executeQuery("SELECT embedding FROM test_halfvec");
        assertTrue(rs.next());
    }
}
```

## Conclusion

La combinaison **pgvector/pgvector:0.8.1-pg18** avec Testcontainers et Langchain4j est production-ready. Points clés à retenir :

- **Image exacte** : `pgvector/pgvector:0.8.1-pg18`
- **halfvec** : supporté nativement, idéal pour embeddings 1024D avec réduction 50% du stockage
- **Langchain4j** : `createTable(true)` élimine le besoin de scripts d'initialisation
- **PostgreSQL 18** : support ajouté spécifiquement dans pgvector 0.8.1 (septembre 2025)
- **@ServiceConnection** : auto-configure le datasource Spring Boot sans `@DynamicPropertySource`