# Phase 1: Foundation & Infrastructure - Research

**Researched:** 2026-02-14
**Domain:** Spring Boot 3.5.7 + PostgreSQL/pgvector + LangChain4j ONNX embeddings + Docker Compose + Flyway
**Confidence:** HIGH

## Summary

Phase 1 establishes the foundational infrastructure: a Docker Compose stack with PostgreSQL+pgvector, a Spring Boot 3.5.7 application with dual-profile configuration (web + stdio), in-process ONNX embedding generation via LangChain4j, and Flyway-managed database schema. All locked versions have been verified on Maven Central and are confirmed compatible.

Key findings that differ from prior research (which used Boot 4.0.2): (1) Spring Boot 3.5.7 manages Flyway 11.x and Testcontainers 1.x via its BOM -- no explicit version declarations needed for these; (2) There is NO `langchain4j-pgvector-spring-boot-starter` -- PgVectorEmbeddingStore must be configured as a manual `@Bean`; (3) LangChain4j's PgVectorEmbeddingStore only supports IVFFlat indexes via its builder -- HNSW indexes must be created manually in Flyway migrations; (4) The `spring-ai-starter-mcp-server-webmvc` starter supports both SSE and STDIO transports from a single dependency, controlled by properties.

**Primary recommendation:** Use `spring-ai-starter-mcp-server-webmvc` for dual-transport MCP (handles both stdio and web profiles from one dependency), configure LangChain4j pgvector and ONNX embedding beans manually via `@Configuration` classes, and create HNSW indexes in Flyway migrations rather than relying on LangChain4j's built-in IVFFlat.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Adopter l'arborescence hybride pragmatique de `docs/architecture.md` telle quelle
- Group ID : `dev.alexandria`
- Packages par feature : `ingestion/`, `search/`, `source/`, `document/`, `mcp/`, `api/`, `config/`
- Interfaces uniquement aux frontieres d'integration : `ContentCrawler` pour Crawl4AI + interfaces LangChain4j (`EmbeddingModel`, `EmbeddingStore`)
- Pas de pattern ServiceImpl -- classes concretes sauf polymorphisme reel
- Records Java 21 pour tous les objets de transit immuables
- 3 services uniquement : app Java, Crawl4AI, PostgreSQL
- PostgreSQL 16 (pas 17 -- eviter le bug halfvec `avg_width` potentiel)
- Image pgvector : `pgvector/pgvector:pg16`
- Ports exposes sur l'hote : PostgreSQL 5432 uniquement (pour debug/admin)
- App Java et Crawl4AI restent internes au reseau Docker
- Volume nomme pour la persistence des donnees PostgreSQL entre restarts
- Health checks pour chaque service
- Flyway pour les migrations (standard Spring Boot, fichiers SQL versiones)
- 3 tables initiales : `document_chunks`, `sources`, `ingestion_state`
- Colonnes embedding en `vector(384)` (precision maximale, pas halfvec)
- Index HNSW et GIN pour FTS crees dans les migrations Flyway
- Gradle comme outil de build (Phase 0 a etabli l'infrastructure Gradle 8.14.4 avec quality gates)
- Spring Boot 3.5.7 (LangChain4j incompatible avec Boot 4.x -- issue #4268)
- JAR unique, 2 profils de lancement : `web` (REST + MCP SSE) et `stdio` (MCP Claude Code)
- Virtual threads activees pour l'I/O (crawl, DB writes), pool platform threads pour ONNX
- Spring Boot banner et console logging desactives en mode stdio (pitfall MCP)
- Spring Boot 3.5.7, Gradle 8.14.4
- LangChain4j 1.11.0 (avec spring-boot-starter compatible Boot 3.5.x)
- Spring AI 1.0.3 GA (MCP server -- pas le milestone 2.0.0-M2)
- Testcontainers 1.x (gere par le BOM Spring Boot 3.5.x)
- Flyway : flyway-core + flyway-database-postgresql (auto-config Boot 3.x)

### Claude's Discretion
- Strategie de logging MCP (fichier log vs stderr vs les deux)
- Strategie de creation d'index HNSW (dans Flyway migration vs apres chargement initial)
- Exact spacing, nommage des fichiers de migration Flyway
- Configuration des health checks Docker (intervalles, retries, timeouts)
- Parametres HNSW (m, ef_construction) -- valeurs par defaut pgvector sauf besoin specifique

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

## Standard Stack

### Core

| Library | Version | Artifact (Gradle) | Purpose | Why Standard | Confidence |
|---------|---------|-------------------|---------|--------------|------------|
| Spring Boot | 3.5.7 | `org.springframework.boot:spring-boot-starter-web` | Application framework, REST API, embedded Tomcat | LTS-track, LangChain4j compatible, manages transitive deps | HIGH |
| LangChain4j Core | 1.11.0 | `dev.langchain4j:langchain4j:1.11.0` | RAG orchestration, embedding API | Latest GA, hybrid search support, active development | HIGH |
| LangChain4j Spring Boot Starter | 1.11.0-beta19 | `dev.langchain4j:langchain4j-spring-boot-starter:1.11.0-beta19` | Auto-configuration for LangChain4j with Spring Boot | Required for Spring Boot integration; "beta" refers to release cadence, not stability | HIGH |
| LangChain4j BGE-small embedding | 1.11.0-beta19 | `dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.11.0-beta19` | In-process 384d ONNX embedding model (~65 MB) | Single dependency, zero external services, quantized for performance | HIGH |
| LangChain4j PgVector | 1.11.0-beta19 | `dev.langchain4j:langchain4j-pgvector:1.11.0-beta19` | PgVectorEmbeddingStore with hybrid search (RRF) | Native vector+FTS+RRF fusion, eliminates custom SQL | HIGH |
| Spring AI MCP Server | 1.0.3 | `org.springframework.ai:spring-ai-starter-mcp-server-webmvc:1.0.3` | MCP server with @Tool annotation, dual transport (stdio+SSE) | GA release, declarative tool exposure, supports both transports | HIGH |
| Flyway | managed by Boot 3.5.7 (~11.7.x) | `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql` | Database schema migrations | Auto-configured by Boot when on classpath, version managed by BOM | HIGH |
| PostgreSQL JDBC | managed by Boot | `org.postgresql:postgresql` (runtime) | JDBC driver | Version managed by Spring Boot BOM | HIGH |

### Supporting

| Library | Version | Artifact (Gradle) | Purpose | When to Use |
|---------|---------|-------------------|---------|-------------|
| Spring Boot Starter Actuator | managed by Boot | `org.springframework.boot:spring-boot-starter-actuator` | Health endpoints for Docker health checks | Always -- needed for `service_healthy` condition |
| Spring Boot Starter Data JPA | managed by Boot | `org.springframework.boot:spring-boot-starter-data-jpa` | JPA/Hibernate for entity management | Needed for `Source`, `IngestionState` entities |
| Testcontainers PostgreSQL | managed by Boot | `org.testcontainers:postgresql` (integrationTest) | Integration testing with real pgvector | All integration tests involving DB |
| Spring Boot Testcontainers | managed by Boot | `org.springframework.boot:spring-boot-testcontainers` (integrationTest) | `@ServiceConnection` auto-config | Eliminates @DynamicPropertySource boilerplate |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Manual PgVectorEmbeddingStore @Bean | Wait for langchain4j-pgvector-spring-boot-starter | Starter does NOT exist yet (GitHub issue #2102 open). Manual bean config is the only option. |
| HNSW via Flyway migration | LangChain4j `.useIndex(true)` | LangChain4j only creates IVFFlat indexes, not HNSW. Manual Flyway is required for HNSW. |
| `spring-ai-starter-mcp-server` (stdio only) | `spring-ai-starter-mcp-server-webmvc` (stdio+SSE) | WebMVC starter includes both transports. Use it for the dual-profile approach. |
| Spring AI for embeddings | LangChain4j ONNX in-process | Spring AI's `TransformersEmbeddingModel` has documented compatibility issues. LangChain4j is one Maven dep. |

**Installation (Gradle):**

```kotlin
// build.gradle.kts - dependencies block
dependencies {
    // Spring Boot starters (version managed by Boot BOM)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // LangChain4j
    implementation("dev.langchain4j:langchain4j:1.11.0")
    implementation("dev.langchain4j:langchain4j-spring-boot-starter:1.11.0-beta19")
    implementation("dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.11.0-beta19")
    implementation("dev.langchain4j:langchain4j-pgvector:1.11.0-beta19")

    // Spring AI MCP Server (dual transport: stdio + SSE)
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc:1.0.3")

    // Flyway (version managed by Boot BOM)
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // PostgreSQL JDBC driver
    runtimeOnly("org.postgresql:postgresql")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Integration test dependencies (via JVM Test Suite)
    // These go in the integrationTest suite configuration
}
```

**Integration test suite additions (in existing `testing.suites` block):**

```kotlin
val integrationTest by getting(JvmTestSuite::class) {
    dependencies {
        // ... existing deps ...
        implementation("org.testcontainers:postgresql")
        implementation("org.testcontainers:junit-jupiter")
        implementation("org.springframework.boot:spring-boot-testcontainers")
        // Add LangChain4j deps needed for embedding integration tests
        implementation("dev.langchain4j:langchain4j:1.11.0")
        implementation("dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.11.0-beta19")
        implementation("dev.langchain4j:langchain4j-pgvector:1.11.0-beta19")
    }
}
```

## Architecture Patterns

### Recommended Project Structure (Phase 1 scope)

Phase 1 creates the package skeleton with placeholder classes. Not all packages have code in Phase 1, but the structure is established.

```
src/main/java/dev/alexandria/
├── AlexandriaApplication.java           # @SpringBootApplication
├── config/
│   └── EmbeddingConfig.java             # @Configuration: EmbeddingModel + PgVectorEmbeddingStore beans
├── document/
│   ├── DocumentChunk.java               # JPA entity with pgvector column
│   └── DocumentChunkRepository.java     # Spring Data JPA repository
├── source/
│   └── Source.java                      # JPA entity (placeholder for later phases)
└── (other packages created empty or with package-info.java)

src/main/resources/
├── application.yml                      # Shared config
├── application-web.yml                  # Web profile (REST + MCP SSE)
├── application-stdio.yml                # Stdio profile (MCP Claude Code)
└── db/migration/
    ├── V1__create_pgvector_extension.sql
    ├── V2__create_sources_table.sql
    ├── V3__create_document_chunks_table.sql
    ├── V4__create_ingestion_state_table.sql
    └── V5__create_indexes.sql
```

### Pattern 1: Manual PgVectorEmbeddingStore Bean Configuration

**What:** Since no Spring Boot starter exists for LangChain4j pgvector, the embedding store and model must be configured as explicit `@Bean` definitions.

**When to use:** Always -- this is the only option until a starter is published.

**Example:**

```java
// Source: LangChain4j docs + Maven Central verification
@Configuration
public class EmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallEnV15QuantizedEmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String user,
            @Value("${spring.datasource.password}") String password) {

        // Parse host/port/database from JDBC URL
        // jdbc:postgresql://host:port/database
        return PgVectorEmbeddingStore.builder()
                .host(/* parsed from jdbcUrl */)
                .port(/* parsed from jdbcUrl */)
                .database(/* parsed from jdbcUrl */)
                .user(user)
                .password(password)
                .table("document_chunks")
                .dimension(384)
                .createTable(false)  // Flyway manages schema
                .useIndex(false)     // HNSW created by Flyway, not IVFFlat
                .build();
    }
}
```

**Important notes:**
- `createTable(false)` because Flyway manages the schema
- `useIndex(false)` because LangChain4j only creates IVFFlat; we want HNSW via Flyway
- The builder requires individual host/port/database fields, not a JDBC URL
- Consider using a `@ConfigurationProperties` class to centralize pgvector connection config

### Pattern 2: Dual-Profile Spring Boot Configuration

**What:** One JAR, two launch modes via Spring profiles.

**When to use:** Always -- required by the locked decision.

**Example:**

```yaml
# application.yml (shared)
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:alexandria}
    username: ${DB_USER:alexandria}
    password: ${DB_PASSWORD:alexandria_dev}
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema; Hibernate only validates
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
  threads:
    virtual:
      enabled: true  # Virtual threads for I/O

# application-web.yml
server:
  port: 8080
spring:
  ai:
    mcp:
      server:
        name: alexandria
        version: 1.0.0
        type: SYNC
        stdio: false

# application-stdio.yml
spring:
  main:
    web-application-type: none
    banner-mode: off
  ai:
    mcp:
      server:
        name: alexandria
        version: 1.0.0
        type: SYNC
        stdio: true
logging:
  pattern:
    console: ""
  file:
    name: ./logs/alexandria-mcp.log
```

### Pattern 3: Flyway Migration for pgvector Extension + HNSW Index

**What:** Database schema managed entirely by Flyway, including pgvector extension creation and HNSW index.

**When to use:** Always -- the locked decision specifies Flyway for all schema management.

**Example:**

```sql
-- V1__create_pgvector_extension.sql
CREATE EXTENSION IF NOT EXISTS vector;

-- V3__create_document_chunks_table.sql
CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding vector(384) NOT NULL,
    text TEXT NOT NULL,
    metadata JSONB,
    source_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- V5__create_indexes.sql
-- HNSW index for cosine similarity search
CREATE INDEX idx_document_chunks_embedding_hnsw
    ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- GIN index for full-text search
CREATE INDEX idx_document_chunks_text_fts
    ON document_chunks
    USING gin (to_tsvector('english', text));
```

### Anti-Patterns to Avoid

- **Using LangChain4j `createTable(true)`:** Bypasses Flyway, creates tables outside migration history. Always use `createTable(false)` and let Flyway own the schema.
- **Using LangChain4j `useIndex(true)`:** Creates an IVFFlat index, not HNSW. HNSW is superior for this use case. Create HNSW manually in Flyway.
- **Mixing halfvec and vector types:** The locked decision specifies `vector(384)` (full precision). Do not use `halfvec(384)` even though it saves storage -- it has known planner bugs on PG17 and was explicitly rejected.
- **Using `@DynamicPropertySource` instead of `@ServiceConnection`:** Spring Boot 3.1+ provides `@ServiceConnection` which is cleaner and auto-configures DataSource from Testcontainers. Use it.
- **Running ONNX embedding on virtual threads:** ONNX Runtime uses JNI native calls that pin virtual threads. Use a dedicated platform thread pool for embedding operations.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Vector similarity search | Custom SQL with `<=>` operator | `PgVectorEmbeddingStore.search()` | LangChain4j handles distance operator matching, score normalization, metadata filtering |
| Hybrid search (vector + FTS + RRF) | Custom RRF SQL query | `PgVectorEmbeddingStore` with `SearchMode.HYBRID` | Built-in since LangChain4j 1.11.0, handles RRF fusion natively |
| ONNX model loading | Manual OrtSession management | `BgeSmallEnV15QuantizedEmbeddingModel` | Handles model lifecycle, tokenization, batch processing, memory management |
| Database migrations | Manual SQL scripts or Hibernate `ddl-auto=update` | Flyway versioned migrations | Reproducible, version-controlled, supports rollback planning |
| Health check endpoints | Custom `/health` controller | Spring Boot Actuator | Standard health endpoint, auto-discovers DataSource health |
| Docker container readiness | Sleep-based waits, `wait-for-it.sh` | Docker Compose `healthcheck` + `condition: service_healthy` | Native Docker Compose feature, no extra scripts |
| JDBC connection details for Testcontainers | `@DynamicPropertySource` boilerplate | `@ServiceConnection` annotation | Spring Boot 3.1+ feature, auto-configures from container |

**Key insight:** LangChain4j 1.11.0's PgVectorEmbeddingStore with `SearchMode.HYBRID` eliminates the need for any custom SQL for search operations. The only manual SQL is in Flyway migrations for schema and index creation.

## Common Pitfalls

### Pitfall 1: LangChain4j PgVectorEmbeddingStore Creates IVFFlat, Not HNSW
**What goes wrong:** Using `.useIndex(true)` in the PgVectorEmbeddingStore builder creates an IVFFlat index. IVFFlat has faster build times but significantly worse query performance than HNSW for this dataset size.
**Why it happens:** LangChain4j's pgvector integration only supports IVFFlat via its builder API. HNSW support is not exposed.
**How to avoid:** Set `.useIndex(false)` and create the HNSW index in a Flyway migration. Use `vector_cosine_ops` operator class to match cosine distance queries.
**Warning signs:** `EXPLAIN ANALYZE` on search queries shows IVFFlat scan instead of HNSW scan.

### Pitfall 2: Distance Operator Mismatch Causes Sequential Scan
**What goes wrong:** The HNSW index uses `vector_cosine_ops` but the query uses `<->` (L2 distance) instead of `<=>` (cosine distance). PostgreSQL silently falls back to sequential scan.
**Why it happens:** Operator class in the index must match the distance operator in queries.
**How to avoid:** Always use `vector_cosine_ops` for the index and `<=>` for queries. LangChain4j's PgVectorEmbeddingStore uses cosine distance by default, so this should work if the index is `vector_cosine_ops`. Add an integration test that runs `EXPLAIN ANALYZE` and asserts `Index Scan`.
**Warning signs:** Query latency above 100ms, `EXPLAIN ANALYZE` showing `Parallel Seq Scan`.

### Pitfall 3: ONNX Native Memory Escapes JVM Metrics
**What goes wrong:** ONNX Runtime allocates ~65 MB native memory for the bge-small model plus inference buffers. Docker sees total process RSS, but JVM heap metrics do not account for this. Container gets OOM-killed (exit code 137) with no application-level warning.
**Why it happens:** ONNX Runtime uses C++ allocators outside the JVM heap.
**How to avoid:** Size Docker memory limit = JVM heap + 500 MB minimum headroom. Use `-XX:NativeMemoryTracking=summary` in JVM args. Monitor with `docker stats`, not JVM metrics alone.
**Warning signs:** Container restarts with exit code 137 while JVM heap usage looks fine.

### Pitfall 4: MCP stdio Corruption from Console Output
**What goes wrong:** Any non-JSON text on stdout (Spring Boot banner, log output, System.out.println) corrupts the MCP JSON-RPC protocol. Claude Code cannot parse responses.
**Why it happens:** Spring Boot defaults to console logging. The banner prints on startup.
**How to avoid:** In `application-stdio.yml`: set `spring.main.banner-mode=off`, `logging.pattern.console=` (empty), redirect all logging to file.
**Warning signs:** MCP server starts but Claude Code reports "Failed to connect" or hangs.

### Pitfall 5: Docker Compose Startup Race Conditions
**What goes wrong:** Java app starts before PostgreSQL is ready. Flyway migration fails because pgvector extension does not exist. App crash-loops.
**Why it happens:** Docker Compose `depends_on` without `condition` only waits for container start, not service readiness.
**How to avoid:** Use `condition: service_healthy` with proper health checks. Create pgvector extension in `docker-entrypoint-initdb.d/` init script OR as the first Flyway migration (both work -- Flyway runs as the same user, and in Docker dev the default user has superuser privileges).
**Warning signs:** `PSQLException: Connection refused` or `type "vector" does not exist` on first startup.

### Pitfall 6: Virtual Thread Pinning from ONNX Native Calls
**What goes wrong:** ONNX embedding calls are JNI native calls that pin virtual threads to carrier threads. With only a few carrier threads, concurrent embedding requests block all virtual thread progress.
**Why it happens:** JNI calls prevent virtual thread unmounting. This is not fixed even in Java 24+ (JEP 491 only fixes `synchronized` pinning, not native calls).
**How to avoid:** Run ONNX embedding on a dedicated `Executors.newFixedThreadPool(N)` with platform threads. Use virtual threads only for I/O (HTTP, DB).
**Warning signs:** `-Djdk.tracePinnedThreads=short` producing output during embedding operations.

### Pitfall 7: pgvector Extension Must Exist Before Flyway Runs
**What goes wrong:** The first Flyway migration uses `vector(384)` column type, but the `vector` extension has not been created yet. Migration fails.
**Why it happens:** The `pgvector/pgvector:pg16` Docker image ships with pgvector installed but does NOT auto-create the extension. You must explicitly run `CREATE EXTENSION`.
**How to avoid:** Either (a) make `V1__create_pgvector_extension.sql` the very first migration containing `CREATE EXTENSION IF NOT EXISTS vector;`, or (b) mount an init script in `docker-entrypoint-initdb.d/`. Option (a) is simpler and keeps everything in Flyway. Both work because the default `postgres` user has superuser privileges in Docker, and the app connects as the same user.
**Warning signs:** Flyway migration error: `type "vector" does not exist`.

## Code Examples

### Creating and Using the Embedding Model

```java
// Source: LangChain4j docs - In-process (ONNX) Embedding Models
// https://docs.langchain4j.dev/integrations/embedding-models/in-process/

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.Response;

EmbeddingModel model = new BgeSmallEnV15QuantizedEmbeddingModel();
Response<Embedding> response = model.embed("How to configure Spring Boot");
Embedding embedding = response.content();
// embedding.vector() returns float[384]
```

### PgVectorEmbeddingStore with Hybrid Search

```java
// Source: LangChain4j PgVector docs
// https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/

import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
    .host("localhost")
    .port(5432)
    .database("alexandria")
    .user("alexandria")
    .password("alexandria_dev")
    .table("document_chunks")
    .dimension(384)
    .createTable(false)      // Flyway manages schema
    .useIndex(false)         // HNSW created by Flyway
    .searchMode(SearchMode.HYBRID)
    .textSearchConfig("english")
    .rrfK(60)                // RRF fusion constant
    .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
    .build();
```

### Testcontainers with @ServiceConnection for pgvector

```java
// Source: Spring Boot 3.1+ Testcontainers documentation
// https://testcontainers.com/modules/pgvector/

@SpringBootTest
@Testcontainers
class EmbeddingStoreIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg16")
            .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    EmbeddingModel embeddingModel;

    @Autowired
    EmbeddingStore<TextSegment> embeddingStore;

    @Test
    void embed_store_retrieve_roundtrip() {
        // Generate embedding
        Embedding embedding = embeddingModel.embed("test content").content();

        // Store
        String id = embeddingStore.add(embedding, TextSegment.from("test content"));

        // Retrieve and verify
        assertThat(id).isNotNull();
        // Search
        var results = embeddingStore.search(
            EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build()
        );
        assertThat(results.matches()).hasSize(1);
    }
}
```

### Docker Compose Health Checks

```yaml
# Source: Docker Compose official docs
# https://docs.docker.com/compose/how-tos/startup-order/
services:
  postgres:
    image: pgvector/pgvector:pg16
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U alexandria -d alexandria"]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 10s

  crawl4ai:
    image: unclecode/crawl4ai:0.8.0
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:11235/health"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  app:
    depends_on:
      postgres:
        condition: service_healthy
      crawl4ai:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s
```

## Discretion Recommendations

### MCP Logging Strategy
**Recommendation:** Log to file only in stdio mode. In web mode, log to console (default).

Rationale: Any stdout output corrupts MCP stdio transport. File logging is the only safe option. Use `./logs/alexandria-mcp.log` as the log file path in `application-stdio.yml`. In web mode, console logging is fine and more convenient for Docker log aggregation.

### HNSW Index Creation Strategy
**Recommendation:** Create HNSW index directly in Flyway migration (V5).

Rationale: For Phase 1, the dataset is small (test data only). Creating the index in a migration is simpler and ensures it exists from the start. The "load data first, build index after" optimization only matters for bulk ingestion of thousands of chunks, which is Phase 4 territory. In Phase 1, the index creation on an empty table is instant.

### Flyway Migration Naming
**Recommendation:** Use `V{N}__{description}.sql` with descriptive names. One concern per migration.

```
V1__create_pgvector_extension.sql
V2__create_sources_table.sql
V3__create_document_chunks_table.sql
V4__create_ingestion_state_table.sql
V5__create_indexes.sql
```

Rationale: Sequential integers are clearer than timestamps for a solo project. One migration per table keeps changes reviewable and rollback-plannable.

### Docker Health Check Configuration
**Recommendation:**

| Service | Interval | Timeout | Retries | Start Period |
|---------|----------|---------|---------|--------------|
| PostgreSQL | 5s | 5s | 10 | 10s |
| Crawl4AI | 10s | 5s | 5 | 30s |
| App | 15s | 5s | 5 | 30s |

Rationale: PostgreSQL starts fast (~5s) but needs reliable detection. Crawl4AI needs longer start_period due to Chromium browser pool initialization (10-30s on first start). App needs time for Flyway migrations + Spring context initialization.

### HNSW Parameters
**Recommendation:** Use pgvector defaults: `m = 16`, `ef_construction = 64`.

Rationale: For datasets under 100K vectors, the defaults provide excellent recall (>95%) with reasonable build time. The pitfalls research recommends increasing `ef_construction` to 128 only at >100K scale, which is well beyond Phase 1 scope.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `RestTemplate` for HTTP clients | `RestClient` (Spring Boot 3.2+) | Spring Boot 3.2 | Simpler fluent API, no deprecation warnings |
| `@DynamicPropertySource` for Testcontainers | `@ServiceConnection` (Spring Boot 3.1+) | Spring Boot 3.1 | Auto-configures DataSource from container, less boilerplate |
| Manual ONNX model loading | `BgeSmallEnV15QuantizedEmbeddingModel` one-liner | LangChain4j 1.0+ | Single dependency handles model loading, tokenization, inference |
| Custom RRF SQL for hybrid search | `PgVectorEmbeddingStore.SearchMode.HYBRID` | LangChain4j 1.11.0 | Eliminates custom SQL, handles RRF fusion natively |
| IVFFlat via LangChain4j `.useIndex(true)` | HNSW via Flyway migration | Always for HNSW | LangChain4j pgvector only supports IVFFlat; HNSW requires manual creation |
| `spring-ai-starter-mcp-server` (stdio only) | `spring-ai-starter-mcp-server-webmvc` (both transports) | Spring AI 1.0.x | Single dependency handles both stdio and SSE transports |

**Deprecated/outdated:**
- `RestTemplate`: Deprecated since Spring Boot 3.2. Use `RestClient` for synchronous HTTP.
- `ankane/pgvector` Docker image: Legacy. Use `pgvector/pgvector` (official).
- Hibernate `ddl-auto=update/create`: Never use in production. Flyway is the standard.
- `version: '3.8'` in docker-compose.yml: The `version` key is obsolete in modern Docker Compose. Omit it entirely.

## Open Questions

1. **PgVectorEmbeddingStore connection configuration**
   - What we know: The builder requires individual `host`, `port`, `database` fields, not a JDBC URL.
   - What's unclear: Whether it can share the same connection pool as Spring Data JPA (HikariCP), or if it creates its own connections. The builder uses `javax.sql.DataSource` OR individual connection params.
   - Recommendation: Try passing the Spring-managed `DataSource` bean via `.datasource(dataSource)` builder method first. If it works, this avoids duplicate connection configs. If not, parse individual fields from Spring DataSource properties.

2. **LangChain4j + Spring AI Coexistence on Classpath**
   - What we know: They use different namespaces (`dev.langchain4j.*` vs `org.springframework.ai.*`). In theory, no conflict.
   - What's unclear: Whether Spring AI's MCP auto-configuration interferes with LangChain4j's Spring Boot starter auto-configuration.
   - Recommendation: Test early. If conflicts arise, use `@SpringBootApplication(exclude = ...)` to exclude specific auto-configurations.

3. **Flyway `CREATE EXTENSION` Permissions in Docker**
   - What we know: In Docker, the default PostgreSQL user typically has superuser privileges, so `CREATE EXTENSION IF NOT EXISTS vector` works in a Flyway migration.
   - What's unclear: Whether this will break in a non-Docker environment (managed PostgreSQL where the app user is not superuser).
   - Recommendation: Use Flyway migration for dev/Docker. Document that production deployments may need DBA to pre-create the extension.

## Sources

### Primary (HIGH confidence)
- [Maven Central: langchain4j-pgvector 1.11.0-beta19](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-pgvector) -- verified latest version
- [Maven Central: langchain4j-embeddings-bge-small-en-v15-q 1.11.0-beta19](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-embeddings-bge-small-en-v15-q) -- verified latest version
- [Maven Central: langchain4j core 1.11.0](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j) -- verified latest version
- [Maven Central: langchain4j-spring-boot-starter 1.11.0-beta19](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-spring-boot-starter) -- verified latest version
- [LangChain4j PgVector Docs](https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/) -- builder params, SearchMode.HYBRID, IVFFlat (not HNSW)
- [LangChain4j In-Process Embedding Models](https://docs.langchain4j.dev/integrations/embedding-models/in-process/) -- ONNX model usage
- [LangChain4j Spring Boot Integration](https://docs.langchain4j.dev/tutorials/spring-boot-integration/) -- requires Spring Boot 3.5, Java 17+
- [LangChain4j Spring repo: NO pgvector starter](https://github.com/langchain4j/langchain4j-spring) -- verified pgvector starter does NOT exist
- [Spring Boot endoflife.date](https://endoflife.date/spring-boot) -- 3.5.7 confirmed, support until 2026-06-30
- [Spring Boot 3.5.7 dependency BOM](https://github.com/spring-projects/spring-boot/blob/v3.5.7/spring-boot-project/spring-boot-dependencies/build.gradle) -- Flyway 11.7.2 managed
- [Maven Central: spring-ai-starter-mcp-server-webmvc 1.0.3](https://mvnrepository.com/artifact/org.springframework.ai/spring-ai-starter-mcp-server-webmvc/1.0.3) -- verified
- [pgvector/pgvector Docker Hub](https://hub.docker.com/r/pgvector/pgvector/tags) -- pg16 tags confirmed (0.8.1-pg16)
- [pgvector GitHub Issue #512](https://github.com/pgvector/pgvector/issues/512) -- extension NOT auto-created in Docker image
- [pgvector GitHub Issue #355](https://github.com/pgvector/pgvector/issues/355) -- `.sql` init scripts work fine with correct extension name `vector`
- [Docker Compose Startup Order](https://docs.docker.com/compose/how-tos/startup-order/) -- `condition: service_healthy`
- [pgvector HNSW Index](https://github.com/pgvector/pgvector) -- `CREATE INDEX ... USING hnsw ... WITH (m, ef_construction)` syntax

### Secondary (MEDIUM confidence)
- [Spring AI MCP Server dual transport](https://www.baeldung.com/spring-ai-model-context-protocol-mcp) -- webmvc starter supports both stdio and SSE
- [Alibaba Spring AI MCP docs](https://java2ai.com/en/docs/1.0.0.2/practices/mcp/spring-ai-mcp-starter-server/) -- confirms stdio vs webmvc starter differences
- [LangChain4j PR #2485](https://github.com/langchain4j/langchain4j/pull/2485) -- search query rewrite for index usage
- [Crunchy Data HNSW Blog](https://www.crunchydata.com/blog/hnsw-indexes-with-postgres-and-pgvector) -- HNSW parameters and usage
- [Supabase HNSW Index Guide](https://supabase.com/docs/guides/ai/vector-indexes/hnsw-indexes) -- build recommendations

### From Project Research (HIGH confidence)
- `docs/architecture.md` -- package structure, patterns, anti-patterns
- `.planning/research/PITFALLS.md` -- pitfalls #1-#10 directly applicable
- `.planning/research/STACK.md` -- version compatibility matrix (updated for 3.5.7)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all versions verified on Maven Central, compatibility confirmed via official docs
- Architecture: HIGH -- patterns from `docs/architecture.md` + verified LangChain4j API
- Pitfalls: HIGH -- verified via GitHub issues, official docs, and project research
- LangChain4j + Spring AI coexistence: MEDIUM -- logically sound but not explicitly tested
- PgVectorEmbeddingStore DataSource sharing: MEDIUM -- builder supports DataSource param but behavior with Spring-managed pools unverified

**Research date:** 2026-02-14
**Valid until:** 2026-03-14 (30 days -- stable stack, no expected breaking changes)
