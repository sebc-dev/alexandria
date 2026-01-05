# Langchain4j PgVectorEmbeddingStore uses pure JDBC, not JPA

**PgVectorEmbeddingStore is implemented with raw JDBC** via the `com.pgvector:pgvector` library and PostgreSQL JDBC driver—JPA/Hibernate is neither required nor used. For Spring Boot projects using only vector storage without other JPA entities, `spring-boot-starter-jdbc` is sufficient, and `spring.jpa.hibernate.ddl-auto` is completely irrelevant. A critical caveat: as of January 2026, no official `langchain4j-pgvector-spring-boot-starter` exists (GitHub Issue #2102 remains open), requiring manual bean configuration.

## Implementation architecture confirms pure JDBC

The `langchain4j-pgvector` module's published POM on Maven Central definitively shows the dependency structure. Version **1.10.0-beta18** includes these compile-scope dependencies:

| Dependency | Version | Purpose |
|------------|---------|---------|
| `dev.langchain4j:langchain4j-core` | 1.10.0 | Core abstractions |
| `com.pgvector:pgvector` | 0.1.6 | pgvector-java JDBC library |
| `org.postgresql:postgresql` | **42.7.7** | PostgreSQL JDBC driver (included!) |
| `com.fasterxml.jackson.core:jackson-databind` | 2.20.1 | JSON serialization |

**No JPA, Hibernate, or Spring Data dependencies appear**. The pgvector-java library explicitly works with `java.sql.Connection`, `PreparedStatement`, and `ResultSet` objects. GitHub Issue #2614 confirms this architecture, noting that "PgVectorEmbeddingStore can only be built from a DataSource or from db properties" and manages raw JDBC connections directly.

The builder pattern accepts either a pre-configured `javax.sql.DataSource` or individual connection parameters (`host`, `port`, `database`, `user`, `password`). This is classic JDBC connection management—not EntityManagerFactory configuration.

## Spring Boot starter status and workaround

The official `langchain4j-pgvector-spring-boot-starter` **does not exist**. GitHub Issue #2102 ("PgVector: implement Spring Boot starter") has been open since November 2024 with P3 priority. The `langchain4j-spring` repository currently provides starters only for Milvus, Elasticsearch, and Azure AI Search—not PgVector.

Until the starter ships, manual configuration is required. The recommended approach injects Spring's auto-configured DataSource:

```java
@Configuration
public class PgVectorConfig {
    
    @Bean
    public PgVectorEmbeddingStore embeddingStore(DataSource dataSource) {
        return PgVectorEmbeddingStore.datasourceBuilder()
            .datasource(dataSource)  // Uses Spring's DataSource
            .table("document_embeddings")
            .dimension(1024)          // BGE-M3 dimension
            .createTable(true)        // Auto-creates via SQL DDL
            .useIndex(true)           // Enable IVFFlat index
            .indexListSize(100)
            .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
            .build();
    }
}
```

This approach reuses Spring's connection pooling (HikariCP) and transaction management while letting PgVectorEmbeddingStore handle vector operations via raw JDBC.

## Minimal dependencies for standalone pgvector usage

For a Spring Boot project using **only** PgVectorEmbeddingStore without other JPA entities:

```xml
<!-- Required -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>1.10.0-beta18</version>
</dependency>

<!-- NOT required - can be removed -->
<!-- spring-boot-starter-data-jpa is unnecessary -->
```

The PostgreSQL JDBC driver (`org.postgresql:postgresql:42.7.7`) is a **transitive dependency** of `langchain4j-pgvector`—you do not need to declare it separately unless you require a different version.

## Minimal application.yml configuration

Since no Spring Boot auto-configuration exists for PgVector, only standard datasource properties are needed:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/alexandria
    username: alexandria_user
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10

# These JPA properties are IRRELEVANT for PgVectorEmbeddingStore:
# spring.jpa.hibernate.ddl-auto - NOT USED
# spring.jpa.show-sql - NOT USED
# spring.jpa.properties.* - NOT USED
```

No `langchain4j.*` properties exist because there's no auto-configuration. All PgVectorEmbeddingStore settings must be configured programmatically in the bean definition.

## Schema creation is independent of Hibernate

The `createTable=true` builder option handles table creation via **raw SQL DDL statements**, executed through JDBC. It:

- Creates the embeddings table with columns for id, embedding vector, text content, and metadata
- Executes `CREATE EXTENSION IF NOT EXISTS vector` to enable pgvector
- Is completely independent of Hibernate schema generation
- Does **not** support migrations or schema updates—only initial creation

The generated table structure (when using `MetadataStorageConfig.combinedJsonb()`) resembles:
```sql
CREATE TABLE document_embeddings (
    embedding_id UUID PRIMARY KEY,
    embedding vector(1024),
    text TEXT,
    metadata JSONB
);
```

## Implications for Alexandria project architecture

Based on the research findings, the Alexandria project should:

- **Remove** `spring-boot-starter-data-jpa` if no other JPA entities exist
- **Keep** `spring-boot-starter-jdbc` for DataSource auto-configuration and HikariCP
- **Remove** any `spring.jpa.*` properties from configuration
- **Create** a manual `@Configuration` class defining the `PgVectorEmbeddingStore` bean
- **Use** `.datasourceBuilder().datasource(dataSource)` to leverage Spring's connection pooling
- **Set** `dimension(1024)` to match BGE-M3 embeddings
- **Enable** `.createTable(true)` for initial schema creation (consider Flyway for production migrations)

The architecture simplifies significantly: Spring Boot provides only JDBC connection pooling, while Langchain4j handles all vector operations directly. This reduces complexity, removes unnecessary Hibernate overhead, and eliminates potential conflicts between JPA schema management and PgVectorEmbeddingStore's own table creation logic.