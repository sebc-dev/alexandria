# Configuration Maven complète pour un projet RAG Java avec LangChain4j

Le stack LangChain4j 1.10.0 offre une intégration native pour les embeddings ONNX all-MiniLM-L6-v2 et pgvector, simplifiant considérablement la configuration Maven. Le module `langchain4j-embeddings-all-minilm-l6-v2` **existe officiellement** et bundle ONNX Runtime automatiquement—aucune configuration manuelle n'est requise. Pour Apache AGE, le driver JDBC n'est pas sur Maven Central et nécessite une compilation depuis les sources GitHub.

## Les dépendances Maven essentielles identifiées

LangChain4j utilise un BOM (Bill of Materials) version **1.10.0** pour gérer les versions. Les modules d'embeddings ONNX et pgvector restent en **1.10.0-beta18** mais sont stables pour la production. Le driver PostgreSQL **42.7.9** est compatible PostgreSQL 17, et le client pgvector Java **0.1.6** fournit le mapping `PGvector` pour le type vector. Les virtual threads Java 21 sont GA et ne nécessitent pas `--enable-preview`.

Le tableau suivant résume les versions validées:

| Composant | GroupId | ArtifactId | Version |
|-----------|---------|------------|---------|
| LangChain4j BOM | dev.langchain4j | langchain4j-bom | 1.10.0 |
| Embeddings ONNX | dev.langchain4j | langchain4j-embeddings-all-minilm-l6-v2 | 1.10.0-beta18 |
| PgVector Store | dev.langchain4j | langchain4j-pgvector | 1.10.0-beta18 |
| PostgreSQL JDBC | org.postgresql | postgresql | 42.7.9 |
| pgvector Java | com.pgvector | pgvector | 0.1.6 |
| ONNX Runtime | com.microsoft.onnxruntime | onnxruntime | 1.20.0 (bundlé) |
| Testcontainers | org.testcontainers | testcontainers-bom | 1.21.4 |

## pom.xml complet et fonctionnel

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>rag-project</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>RAG Project with LangChain4j</name>
    <description>RAG application with ONNX embeddings, pgvector, and Apache AGE</description>

    <properties>
        <!-- Java 21 pour virtual threads (GA, pas de preview) -->
        <java.version>21</java.version>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- Versions des dépendances -->
        <langchain4j.version>1.10.0</langchain4j.version>
        <langchain4j-embeddings.version>1.10.0-beta18</langchain4j-embeddings.version>
        <postgresql.version>42.7.9</postgresql.version>
        <pgvector.version>0.1.6</pgvector.version>
        <testcontainers.version>1.21.4</testcontainers.version>
        <junit.version>5.10.2</junit.version>
        <slf4j.version>2.0.12</slf4j.version>
        <hikari.version>5.1.0</hikari.version>

        <!-- Apache AGE dependencies -->
        <antlr4.version>4.9.2</antlr4.version>
        <commons-lang3.version>3.14.0</commons-lang3.version>
        <commons-text.version>1.11.0</commons-text.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- LangChain4j BOM -->
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-bom</artifactId>
                <version>${langchain4j.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- Testcontainers BOM -->
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- =============================================== -->
        <!-- LANGCHAIN4J CORE                                -->
        <!-- =============================================== -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
        </dependency>

        <!-- =============================================== -->
        <!-- EMBEDDINGS ONNX (all-MiniLM-L6-v2)              -->
        <!-- Le modèle ONNX (~90MB) est bundlé dans le JAR   -->
        <!-- ONNX Runtime 1.20.0 est inclus automatiquement  -->
        <!-- =============================================== -->
        <!-- Option 1: Modèle complet FP32 (plus précis) -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
            <version>${langchain4j-embeddings.version}</version>
        </dependency>
        
        <!-- Option 2: Modèle quantifié INT8 (plus rapide, ~23MB) -->
        <!-- Décommenter si vous préférez la version quantifiée -->
        <!--
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-embeddings-all-minilm-l6-v2-q</artifactId>
            <version>${langchain4j-embeddings.version}</version>
        </dependency>
        -->

        <!-- Pour modèles ONNX personnalisés (optionnel) -->
        <!--
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-embeddings</artifactId>
            <version>${langchain4j-embeddings.version}</version>
        </dependency>
        -->

        <!-- =============================================== -->
        <!-- POSTGRESQL / PGVECTOR                           -->
        <!-- =============================================== -->
        <!-- LangChain4j PgVector EmbeddingStore -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-pgvector</artifactId>
            <version>${langchain4j-embeddings.version}</version>
        </dependency>

        <!-- Driver JDBC PostgreSQL (compatible PG 8.4 - 17) -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>${postgresql.version}</version>
        </dependency>

        <!-- Client pgvector Java (pour usage JDBC direct) -->
        <dependency>
            <groupId>com.pgvector</groupId>
            <artifactId>pgvector</artifactId>
            <version>${pgvector.version}</version>
        </dependency>

        <!-- HikariCP pour connection pooling -->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
            <version>${hikari.version}</version>
        </dependency>

        <!-- =============================================== -->
        <!-- APACHE AGE (Graph queries Cypher)               -->
        <!-- Le driver JDBC AGE n'est PAS sur Maven Central  -->
        <!-- Il doit être compilé depuis les sources GitHub  -->
        <!-- =============================================== -->
        <!-- Dépendances requises par AGE JDBC driver -->
        <dependency>
            <groupId>org.antlr</groupId>
            <artifactId>antlr4-runtime</artifactId>
            <version>${antlr4.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>${commons-lang3.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-text</artifactId>
            <version>${commons-text.version}</version>
        </dependency>

        <!-- AGE JDBC Driver - À installer manuellement -->
        <!-- Voir instructions ci-dessous pour builder le JAR -->
        <dependency>
            <groupId>org.apache.age</groupId>
            <artifactId>age-jdbc</artifactId>
            <version>1.6.0</version>
            <scope>system</scope>
            <systemPath>${project.basedir}/lib/age-jdbc-1.6.0.jar</systemPath>
        </dependency>

        <!-- =============================================== -->
        <!-- DOCUMENT PARSERS                                -->
        <!-- =============================================== -->
        <!-- Apache Tika pour Markdown et autres formats -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-document-parser-apache-tika</artifactId>
        </dependency>

        <!-- =============================================== -->
        <!-- LOGGING                                         -->
        <!-- =============================================== -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>${slf4j.version}</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>${slf4j.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- =============================================== -->
        <!-- TESTS                                           -->
        <!-- =============================================== -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
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
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.25.3</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Compiler Java 21 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.14.1</version>
                <configuration>
                    <release>21</release>
                    <!-- Virtual threads sont GA - pas de preview nécessaire -->
                </configuration>
            </plugin>

            <!-- Tests unitaires -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                    <!-- Requis pour JDK 21+ avec mocking libraries -->
                    <argLine>-XX:+EnableDynamicAgentLoading</argLine>
                </configuration>
            </plugin>

            <!-- Tests d'intégration -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                    <argLine>-XX:+EnableDynamicAgentLoading</argLine>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>

            <!-- Fat JAR avec dépendances -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.2</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.example.rag.RagApplication</mainClass>
                                </transformer>
                                <!-- Merger les fichiers META-INF/services -->
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                            </transformers>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

## Structure de répertoires recommandée

Pour un projet RAG, la structure **monolithique** suffit initialement, mais une organisation modulaire facilite l'évolution vers une architecture multi-modules:

```
rag-project/
├── pom.xml
├── lib/
│   └── age-jdbc-1.6.0.jar          # Driver AGE compilé manuellement
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/rag/
│   │   │       ├── RagApplication.java
│   │   │       ├── config/
│   │   │       │   ├── EmbeddingConfig.java
│   │   │       │   ├── VectorStoreConfig.java
│   │   │       │   └── GraphStoreConfig.java
│   │   │       ├── embedding/
│   │   │       │   └── EmbeddingService.java
│   │   │       ├── vectorstore/
│   │   │       │   └── DocumentRepository.java
│   │   │       ├── graphstore/
│   │   │       │   ├── GraphRepository.java
│   │   │       │   └── AgtypeParser.java
│   │   │       ├── ingest/
│   │   │       │   └── DocumentIngestor.java
│   │   │       └── query/
│   │   │           └── RagQueryService.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── logback.xml
│   └── test/
│       ├── java/
│       │   └── com/example/rag/
│       │       ├── EmbeddingIntegrationTest.java
│       │       ├── VectorStoreIntegrationTest.java
│       │       └── GraphStoreIntegrationTest.java
│       └── resources/
│           └── docker/
│               ├── Dockerfile                # Image custom pgvector + AGE
│               └── init-extensions.sql
└── docker/
    ├── Dockerfile
    ├── docker-compose.yml
    └── init-extensions.sql
```

## Configuration Apache AGE sans Maven Central

Le driver JDBC Apache AGE n'est **pas publié sur Maven Central**. Voici les étapes pour l'obtenir:

```bash
# 1. Cloner le repository Apache AGE
git clone https://github.com/apache/age.git
cd age/drivers/jdbc

# 2. Builder le JAR avec Gradle
./gradlew assemble

# 3. Copier le JAR dans votre projet
cp lib/build/libs/lib.jar /chemin/vers/rag-project/lib/age-jdbc-1.6.0.jar

# 4. Alternative: Installer dans le repository Maven local
mvn install:install-file \
    -Dfile=lib/build/libs/lib.jar \
    -DgroupId=org.apache.age \
    -DartifactId=age-jdbc \
    -Dversion=1.6.0 \
    -Dpackaging=jar
```

Après installation locale, remplacer la dépendance `system` par:

```xml
<dependency>
    <groupId>org.apache.age</groupId>
    <artifactId>age-jdbc</artifactId>
    <version>1.6.0</version>
</dependency>
```

## Dockerfile custom pour PostgreSQL 17 + pgvector + AGE

Aucune image Docker officielle ne combine pgvector et Apache AGE. Voici le Dockerfile à créer dans `docker/Dockerfile`:

```dockerfile
FROM postgres:17-bookworm

# Dépendances de build
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential git postgresql-server-dev-17 \
    libreadline-dev zlib1g-dev flex bison \
    && rm -rf /var/lib/apt/lists/*

# pgvector 0.8.1
RUN git clone --branch v0.8.1 --depth 1 \
    https://github.com/pgvector/pgvector.git /tmp/pgvector \
    && cd /tmp/pgvector \
    && make clean && make OPTFLAGS="" && make install \
    && rm -rf /tmp/pgvector

# Apache AGE 1.5.0 pour PostgreSQL 17
RUN git clone --branch release/PG17/1.5.0 --depth 1 \
    https://github.com/apache/age.git /tmp/age \
    && cd /tmp/age \
    && make PG_CONFIG=/usr/bin/pg_config \
    && make install PG_CONFIG=/usr/bin/pg_config \
    && rm -rf /tmp/age

# Nettoyage des outils de build
RUN apt-get remove -y build-essential git postgresql-server-dev-17 \
    && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

# Script d'initialisation
COPY init-extensions.sql /docker-entrypoint-initdb.d/

# Preload AGE au démarrage
CMD ["postgres", "-c", "shared_preload_libraries=age"]
```

Fichier `docker/init-extensions.sql`:

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS age;
LOAD 'age';
SET search_path = ag_catalog, "$user", public;
```

## Exemple de code Java complet pour validation

```java
package com.example.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.apache.age.jdbc.base.Agtype;
import org.postgresql.jdbc.PgConnection;

import java.nio.file.Path;
import java.sql.*;
import java.util.List;

public class RagApplication {

    public static void main(String[] args) throws Exception {
        // === 1. EMBEDDINGS ONNX (in-process, ~90MB bundlé) ===
        System.out.println("Initializing ONNX embedding model...");
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        
        // Test embedding
        Embedding testEmbedding = embeddingModel.embed("Hello world").content();
        System.out.printf("Embedding dimension: %d (expected: 384)%n", 
            testEmbedding.dimension());

        // === 2. PGVECTOR EMBEDDING STORE ===
        System.out.println("\nConnecting to pgvector store...");
        EmbeddingStore<TextSegment> embeddingStore = PgVectorEmbeddingStore.builder()
            .host("localhost")
            .port(5432)
            .database("ragdb")
            .user("raguser")
            .password("ragpassword")
            .table("document_embeddings")
            .dimension(384)  // AllMiniLmL6V2 = 384 dimensions
            .createTable(true)
            .useIndex(true)
            .build();

        // === 3. DOCUMENT INGESTION ===
        System.out.println("\nIngesting documents...");
        Document document = Document.from(
            "LangChain4j is a Java library for building AI-powered applications. " +
            "It supports embeddings, vector stores, and RAG patterns. " +
            "The library integrates with ONNX Runtime for in-process embeddings."
        );

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
            .documentSplitter(DocumentSplitters.recursive(100, 20))
            .embeddingModel(embeddingModel)
            .embeddingStore(embeddingStore)
            .build();
        
        ingestor.ingest(document);
        System.out.println("Documents ingested successfully!");

        // === 4. SIMILARITY SEARCH ===
        System.out.println("\nPerforming similarity search...");
        Embedding queryEmbedding = embeddingModel.embed("What is LangChain4j?").content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(
            queryEmbedding, 
            3,      // maxResults
            0.5     // minScore
        );

        for (EmbeddingMatch<TextSegment> match : matches) {
            System.out.printf("Score: %.4f | Text: %s%n", 
                match.score(), 
                match.embedded().text());
        }

        // === 5. APACHE AGE GRAPH QUERIES ===
        System.out.println("\nExecuting Apache AGE graph query...");
        executeGraphQuery();

        System.out.println("\n✅ All components working correctly!");
    }

    private static void executeGraphQuery() throws SQLException {
        String url = "jdbc:postgresql://localhost:5432/ragdb";
        
        try (Connection conn = DriverManager.getConnection(url, "raguser", "ragpassword")) {
            // Unwrap vers PgConnection pour enregistrer le type agtype
            PgConnection pgConn = conn.unwrap(PgConnection.class);
            pgConn.addDataType("agtype", Agtype.class);

            try (Statement stmt = conn.createStatement()) {
                // Configuration AGE
                stmt.execute("LOAD 'age'");
                stmt.execute("SET search_path = ag_catalog, \"$user\", public");
                
                // Créer un graphe de connaissances
                stmt.execute("SELECT create_graph('knowledge_graph')");

                // Créer des nœuds et relations
                stmt.execute("""
                    SELECT * FROM cypher('knowledge_graph', $$
                        CREATE (l:Library {name: 'LangChain4j', version: '1.10.0'})
                        CREATE (e:Feature {name: 'ONNX Embeddings'})
                        CREATE (v:Feature {name: 'PgVector Store'})
                        CREATE (l)-[:HAS_FEATURE]->(e)
                        CREATE (l)-[:HAS_FEATURE]->(v)
                        RETURN l
                    $$) AS (library agtype)
                """);

                // Query le graphe
                ResultSet rs = stmt.executeQuery("""
                    SELECT * FROM cypher('knowledge_graph', $$
                        MATCH (l:Library)-[:HAS_FEATURE]->(f:Feature)
                        RETURN l.name, f.name
                    $$) AS (library agtype, feature agtype)
                """);

                while (rs.next()) {
                    Agtype library = rs.getObject(1, Agtype.class);
                    Agtype feature = rs.getObject(2, Agtype.class);
                    System.out.printf("Library: %s | Feature: %s%n", 
                        library.getString(), feature.getString());
                }

                // Cleanup
                stmt.execute("SELECT drop_graph('knowledge_graph', true)");
            }
        }
    }
}
```

## Test d'intégration avec Testcontainers

```java
package com.example.rag;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

import java.nio.file.Path;
import java.sql.*;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RagIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        new ImageFromDockerfile("postgres-rag-test", false)
            .withDockerfile(Path.of("src/test/resources/docker/Dockerfile"))
    )
    .withDatabaseName("testdb")
    .withUsername("test")
    .withPassword("test")
    .withCommand("postgres", "-c", "shared_preload_libraries=age");

    @BeforeAll
    static void setupExtensions() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("CREATE EXTENSION IF NOT EXISTS vector");
            stmt.execute("CREATE EXTENSION IF NOT EXISTS age");
            stmt.execute("LOAD 'age'");
            stmt.execute("SET search_path = ag_catalog, \"$user\", public");
        }
    }

    @Test
    void embeddingModelReturns384Dimensions() {
        var model = new AllMiniLmL6V2EmbeddingModel();
        var embedding = model.embed("test").content();
        
        assertThat(embedding.dimension()).isEqualTo(384);
    }

    @Test
    void pgvectorStoreAcceptsEmbeddings() {
        var store = PgVectorEmbeddingStore.builder()
            .host(postgres.getHost())
            .port(postgres.getFirstMappedPort())
            .database("testdb")
            .user("test")
            .password("test")
            .table("test_embeddings")
            .dimension(384)
            .createTable(true)
            .build();

        var model = new AllMiniLmL6V2EmbeddingModel();
        var embedding = model.embed("test document").content();
        
        String id = store.add(embedding);
        assertThat(id).isNotNull();
    }
}
```

## Notes critiques sur les versions et compatibilités

**Modèle all-MiniLM-L6-v2**: Le fichier ONNX (~90MB pour FP32, ~23MB pour quantifié INT8) est **bundlé dans le JAR Maven**. Aucun téléchargement externe requis. Le modèle génère des vecteurs de **384 dimensions** avec une longueur maximale de **256 tokens**.

**Apache AGE et PostgreSQL 17**: La branche `release/PG17/1.5.0` supporte PostgreSQL 17. Vérifiez toujours les branches disponibles sur GitHub car de nouvelles versions sont publiées régulièrement.

**Virtual threads Java 21**: Les virtual threads sont en GA (General Availability) dans Java 21—l'option `--enable-preview` n'est plus nécessaire. Pour les activer dans Spring Boot 3.2+, ajoutez `spring.threads.virtual.enabled=true`.

**MCP Server Java SDK**: Pour une future intégration MCP, surveillez le projet `modelcontextprotocol/java-sdk` sur GitHub qui est en développement actif et devrait atteindre une version stable en 2026.

## Conclusion

Cette configuration Maven fournit une base solide pour un projet RAG Java 21 avec LangChain4j. Les points clés à retenir: le module **langchain4j-embeddings-all-minilm-l6-v2 existe officiellement** et bundle ONNX Runtime automatiquement, le module **langchain4j-pgvector simplifie l'intégration** vectorielle, et **Apache AGE nécessite une compilation manuelle** du driver JDBC. L'utilisation d'une image Docker custom combinant pgvector et AGE est indispensable pour les tests d'intégration avec Testcontainers.