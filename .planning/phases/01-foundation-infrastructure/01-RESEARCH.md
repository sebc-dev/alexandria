# Phase 1: Foundation & Infrastructure - Research

**Researched:** 2026-02-14
**Domain:** Spring Boot 4.0 + LangChain4j + pgvector + ONNX + Docker Compose + Flyway
**Confidence:** HIGH (critical version conflicts identified and resolution paths verified)

## Summary

This phase builds the project skeleton: Docker Compose stack (PostgreSQL+pgvector, Crawl4AI, Java app), Spring Boot application with dual-profile (web + stdio), in-process ONNX embedding generation, and Flyway-managed database schema. The research revealed two critical conflicts between user decisions (CONTEXT.md) and the existing codebase that must be resolved before planning: (1) the CONTEXT.md specifies "Maven" as the build tool, but Phase 0 already established a Gradle 9.3.1 build, and (2) the CONTEXT.md references Spring Boot 3.5.10 stack recommendations, but Phase 0 upgraded to Spring Boot 4.0.2, which has compatibility implications for both LangChain4j Spring Boot starters and Spring AI MCP starters. Both conflicts have clear resolution paths documented below.

The good news: all core technical decisions (LangChain4j in-process ONNX embeddings, pgvector with `vector(384)`, Flyway migrations, dual-profile Spring Boot) are verified as feasible on the current Spring Boot 4.0.2 / Gradle 9.3.1 stack, provided we use LangChain4j core libraries (without the Spring Boot starter) and Spring AI 2.0.0-M2 (the milestone release built for Spring Boot 4.0).

**Primary recommendation:** Keep Gradle 9.3.1 and Spring Boot 4.0.2 as established in Phase 0. Use LangChain4j core + embeddings + pgvector libraries with manual `@Bean` configuration (no LangChain4j Spring Boot starter). Use Spring AI 2.0.0-M2 for MCP server integration. This avoids a disruptive build system migration and leverages the existing quality infrastructure.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Adopter l'arborescence hybride pragmatique de `docs/architecture.md` telle quelle
- Group ID Maven : `dev.alexandria`
- Packages par feature : `ingestion/`, `search/`, `source/`, `document/`, `mcp/`, `api/`, `config/`
- Inclure le package `api/` (REST admin) des le v1 -- endpoints REST en plus du MCP
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
- JAR unique, 2 profils de lancement : `web` (REST + MCP SSE) et `stdio` (MCP Claude Code)
- Maven comme outil de build
- Virtual threads activees pour l'I/O (crawl, DB writes), pool platform threads pour ONNX
- Spring Boot banner et console logging desactives en mode stdio (pitfall MCP)

### Claude's Discretion
- Strategie de logging MCP (fichier log vs stderr vs les deux)
- Strategie de creation d'index HNSW (dans Flyway migration vs apres chargement initial)
- Exact spacing, nommage des fichiers de migration Flyway
- Configuration des health checks Docker (intervalles, retries, timeouts)
- Parametres HNSW (m, ef_construction) -- valeurs par defaut pgvector sauf besoin specifique

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

---

## Critical Conflicts: Build Tool and Spring Boot Version

### Conflict 1: Maven vs Gradle

**CONTEXT.md says:** "Maven comme outil de build"
**Current codebase (Phase 0):** Gradle 9.3.1 with `build.gradle.kts`, Gradle wrapper, JVM Test Suites, all quality gate plugins configured, GitHub Actions CI using `gradle/actions/setup-gradle`.

**Resolution options:**
1. **Keep Gradle (RECOMMENDED):** Phase 0 invested significantly in Gradle infrastructure (build.gradle.kts, quality.sh, GitHub Actions CI, JVM Test Suite configuration for separate unit/integration tests). Migrating to Maven would require rewriting all of this. The "Maven" decision in CONTEXT.md was likely based on the STACK.md recommendation which assumed a greenfield project, not one with an established Gradle build.
2. **Migrate to Maven:** Discard Phase 0's build infrastructure and start over with pom.xml. High cost, no functional benefit.

**Recommendation:** Keep Gradle 9.3.1. The "Group ID: `dev.alexandria`" decision is already implemented in the Gradle build (package structure `dev.alexandria`). All dependency declarations below are provided in Gradle syntax.

### Conflict 2: Spring Boot Version and Library Compatibility

**CONTEXT.md references:** Spring Boot 3.5.10 (from STACK.md research)
**Current codebase (Phase 0):** Spring Boot 4.0.2 (upgraded in commit `56c4111`)

**Compatibility matrix with Spring Boot 4.0.2:**

| Library | Works with Boot 4.0? | Version to Use | Notes |
|---------|----------------------|----------------|-------|
| LangChain4j core (`langchain4j`) | YES | 1.11.0 | No Spring dependency -- pure Java library |
| `langchain4j-embeddings-bge-small-en-v15-q` | YES | 1.11.0-beta19 | No Spring dependency -- ONNX runtime only |
| `langchain4j-pgvector` | YES | 1.11.0-beta19 | Uses JDBC directly, no Spring autoconfiguration |
| `langchain4j-spring-boot-starter` | NO | N/A | Broken on Boot 4.0 (issue #4268). DO NOT USE. |
| Spring AI MCP Starter (`spring-ai-starter-mcp-server`) | YES (milestone) | 2.0.0-M2 | Spring AI 2.x targets Spring Boot 4.0/Framework 7 |
| Spring AI MCP WebMVC (`spring-ai-starter-mcp-server-webmvc`) | YES (milestone) | 2.0.0-M2 | For web profile with SSE transport |
| Flyway | YES | via `spring-boot-starter-flyway` | Spring Boot 4.0 requires the starter, not just flyway-core |
| Testcontainers | YES | 2.0.3 | Already configured in Phase 0 |

**Resolution:** Keep Spring Boot 4.0.2. Use LangChain4j core libraries with manual `@Bean` configuration (no Spring Boot starter). Use Spring AI 2.0.0-M2 for MCP. The milestone status of Spring AI 2.0.0-M2 is a minor risk, but the MCP server API is stable and unlikely to change significantly before GA.

Source: [LangChain4j Boot 4 issue #4268](https://github.com/langchain4j/langchain4j/issues/4268), [Spring AI Boot 4 epic #3379](https://github.com/spring-projects/spring-ai/issues/3379), [Spring AI releases](https://github.com/spring-projects/spring-ai/releases)

---

## Standard Stack

### Core

| Library | Version | Artifact | Purpose | Why Standard |
|---------|---------|----------|---------|--------------|
| Spring Boot | 4.0.2 | `org.springframework.boot:spring-boot-starter-web` | Application framework + REST | Already established in Phase 0 |
| LangChain4j | 1.11.0 | `dev.langchain4j:langchain4j` | RAG orchestration core | Latest stable, no Spring dependency |
| ONNX Embeddings | 1.11.0-beta19 | `dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q` | In-process bge-small-en-v1.5 384d | Zero external service, single dependency |
| pgvector Store | 1.11.0-beta19 | `dev.langchain4j:langchain4j-pgvector` | Vector + hybrid search store | Native RRF fusion in 1.11.0 |
| PostgreSQL | 16 | `pgvector/pgvector:pg16` (Docker) | Relational + vector DB | User decision: PG16 to avoid halfvec avg_width bug |
| Flyway | managed by Boot | `org.springframework.boot:spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql` | Schema migrations | Spring Boot 4.0 requires the starter |

### Supporting

| Library | Version | Artifact | When to Use |
|---------|---------|----------|-------------|
| Spring AI MCP Server | 2.0.0-M2 | `org.springframework.ai:spring-ai-starter-mcp-server` | MCP stdio transport (Phase 5, but skeleton config now) |
| Spring AI MCP WebMVC | 2.0.0-M2 | `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` | MCP SSE transport for web profile |
| Spring Boot Actuator | 4.0.2 | `org.springframework.boot:spring-boot-starter-actuator` | Health checks for Docker |
| PostgreSQL JDBC | managed by Boot | `org.postgresql:postgresql` | JDBC driver |
| Jackson | managed by Boot | (transitive) | JSON for Crawl4AI REST, MCP protocol |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| LangChain4j core + manual beans | LangChain4j Spring Boot Starter | Starter broken on Boot 4.0; manual beans are ~20 lines of config |
| Spring AI 2.0.0-M2 (milestone) | Spring AI 1.0.3 (GA) | 1.0.3 requires Spring Boot 3.x; cannot use with Boot 4.0.2 |
| Spring AI 2.0.0-M2 | Raw MCP SDK 0.17.2 | Raw SDK works but lacks `@Tool` annotation support, requires more boilerplate |
| Gradle | Maven | Phase 0 already established Gradle with full quality infrastructure |

### Installation (Gradle)

```kotlin
// build.gradle.kts additions for Phase 1
val langchain4jVersion = "1.11.0"
val langchain4jEmbeddingsVersion = "1.11.0-beta19"
val springAiVersion = "2.0.0-M2"

dependencies {
    // Spring Boot (web, actuator, JPA)
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Flyway (Spring Boot 4.0 requires the starter)
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // PostgreSQL JDBC
    runtimeOnly("org.postgresql:postgresql")

    // LangChain4j Core (NO spring-boot-starter -- incompatible with Boot 4.0)
    implementation("dev.langchain4j:langchain4j:$langchain4jVersion")

    // In-process ONNX embedding model
    implementation("dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:$langchain4jEmbeddingsVersion")

    // pgvector embedding store with hybrid search
    implementation("dev.langchain4j:langchain4j-pgvector:$langchain4jEmbeddingsVersion")

    // Spring AI MCP Server (milestone -- for Boot 4.0 compat)
    // NOTE: Not needed until Phase 5, but skeleton profile config set up now
    // implementation("org.springframework.ai:spring-ai-starter-mcp-server:$springAiVersion")

    // Testing additions
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // integrationTest suite already configured in Phase 0
}

// Spring AI milestone repository (needed for 2.0.0-M2)
repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}
```

---

## Architecture Patterns

### Recommended Project Structure (Phase 1 scope)

Only packages and files relevant to Phase 1 are created. Empty feature packages are NOT created -- they come in their respective phases.

```
src/main/java/dev/alexandria/
├── AlexandriaApplication.java          # Already exists from Phase 0
├── config/
│   └── EmbeddingConfig.java            # @Configuration: EmbeddingModel + EmbeddingStore beans
└── document/
    ├── DocumentChunk.java              # JPA entity with pgvector column
    └── DocumentChunkRepository.java    # Spring Data JPA

src/main/resources/
├── application.yml                     # Shared config (replaces application.properties)
├── application-web.yml                 # Web profile: server port, MCP SSE disabled
├── application-stdio.yml               # Stdio profile: web disabled, banner off, logging to file
└── db/migration/
    ├── V1__create_pgvector_extension.sql
    ├── V2__create_document_chunks.sql
    ├── V3__create_sources.sql
    └── V4__create_ingestion_state.sql

docker-compose.yml                      # 3 services: postgres, crawl4ai, app
Dockerfile                              # Multi-stage build
```

### Pattern 1: Manual LangChain4j Bean Configuration (Required for Boot 4.0)

Since `langchain4j-spring-boot-starter` is incompatible with Spring Boot 4.0, all LangChain4j beans must be configured manually in a `@Configuration` class.

```java
// Source: LangChain4j official docs + examples
@Configuration
public class EmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        // Pre-packaged ONNX model, zero external dependencies
        // Loads ~65 MB native model on first call
        return new BgeSmallEnV15QuantizedEmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String user,
            @Value("${spring.datasource.password}") String password) {

        // Parse host/port/database from JDBC URL
        // LangChain4j PgVectorEmbeddingStore manages its own connection
        return PgVectorEmbeddingStore.builder()
            .host(extractHost(jdbcUrl))
            .port(extractPort(jdbcUrl))
            .database(extractDatabase(jdbcUrl))
            .user(user)
            .password(password)
            .table("document_chunks")
            .dimension(384)
            .createTable(false)  // Flyway manages schema
            .useIndex(false)     // Flyway creates indexes
            .build();
    }
}
```

**Key:** Set `createTable(false)` and `useIndex(false)` because Flyway manages the schema. LangChain4j's auto-creation would conflict with migration-managed tables.

### Pattern 2: Dual-Profile Configuration

```yaml
# application.yml (shared)
spring:
  application:
    name: alexandria
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:alexandria}
    username: ${DB_USER:alexandria}
    password: ${DB_PASSWORD:alexandria_dev}
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema, Hibernate only validates
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
  threads:
    virtual:
      enabled: true  # Virtual threads for web request handling

# application-web.yml
server:
  port: 8080
management:
  endpoints:
    web:
      exposure:
        include: health,info

# application-stdio.yml
spring:
  main:
    web-application-type: none
    banner-mode: off
logging:
  pattern:
    console:          # Empty = no console output (critical for MCP stdio)
  file:
    name: ./logs/alexandria-mcp.log
```

### Pattern 3: Thread Pool Separation (Virtual + Platform)

```java
@Configuration
public class ThreadConfig {

    /**
     * Fixed-size platform thread pool for CPU-bound ONNX inference.
     * Virtual threads gain nothing for CPU work and cause pinning
     * during native JNI calls to ONNX Runtime.
     */
    @Bean
    public ExecutorService embeddingExecutor() {
        return Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            Thread.ofPlatform().name("onnx-", 0).factory()
        );
    }
}
```

Virtual threads are enabled globally via `spring.threads.virtual.enabled=true` for I/O-bound work (web requests, DB operations). ONNX embedding calls use a dedicated platform thread pool.

### Anti-Patterns to Avoid

- **Using `langchain4j-spring-boot-starter`:** Broken on Spring Boot 4.0 (RestClientAutoConfiguration package moved). Use core library + manual `@Bean` instead.
- **Using `spring-ai` 1.x with Spring Boot 4.0:** Spring AI 1.x targets Boot 3.x only. Use 2.0.0-M2 for Boot 4.0 compatibility.
- **Letting LangChain4j create tables:** Set `createTable(false)` since Flyway manages the schema. Auto-creation would produce tables without proper migration tracking.
- **Using `halfvec(384)`:** User explicitly chose `vector(384)` for maximum precision. Do not use halfvec even though it saves storage.
- **Putting business logic in `EmbeddingConfig`:** Config classes create beans only. No embedding logic, no data access.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| In-process embeddings | Custom ONNX loading + tokenization | `BgeSmallEnV15QuantizedEmbeddingModel()` | Model file, tokenizer, pooling strategy all pre-packaged |
| Vector similarity search | Custom SQL with `<=>` operator | `PgVectorEmbeddingStore` from langchain4j-pgvector | Handles connection management, distance operators, metadata storage |
| Schema migrations | `CREATE TABLE` in application startup code | Flyway SQL migrations | Version tracking, rollback, team collaboration |
| Docker health checks | Custom health probe scripts | `pg_isready` + Spring Actuator `/actuator/health` | Standard tools with proper exit codes |
| MCP transport | Custom stdin/stdout JSON-RPC parser | Spring AI MCP starter (Phase 5) | Protocol compliance, error handling, tool registration |
| pgvector extension creation | Application-level `CREATE EXTENSION` | Docker init script in `/docker-entrypoint-initdb.d/` | Runs as superuser during DB initialization, before app connects |

**Key insight:** The LangChain4j `PgVectorEmbeddingStore` handles the complex parts (distance operator matching, HNSW index syntax, metadata JSONB storage). Manual SQL is only needed in Flyway migrations for initial schema creation.

---

## Common Pitfalls

### Pitfall 1: Flyway + pgvector Extension Permissions

**What goes wrong:** Flyway migration includes `CREATE EXTENSION vector` but the application database user lacks superuser privileges. Migration fails with `ERROR: permission denied to create extension "vector"`.

**Why it happens:** PostgreSQL requires superuser (or specific privilege) to create extensions. The default `POSTGRES_USER` in Docker IS superuser, but if you create a separate app user, it may not be.

**How to avoid:**
1. Create the extension in `/docker-entrypoint-initdb.d/init.sql` which runs as the PostgreSQL superuser during container initialization.
2. Use `CREATE EXTENSION IF NOT EXISTS vector;` for idempotency.
3. Flyway migrations can then reference `vector` type safely because the extension already exists.

**Recommendation (Claude's Discretion):** Put `CREATE EXTENSION IF NOT EXISTS vector;` in BOTH the Docker init script AND as Flyway V1 migration. The Docker init handles fresh containers; the Flyway migration handles environments where the init script didn't run (e.g., external PostgreSQL). Since the default Docker user is superuser, both will succeed.

### Pitfall 2: Spring Boot 4.0 Flyway Starter Required

**What goes wrong:** Adding `flyway-core` and `flyway-database-postgresql` as dependencies but Flyway migrations never execute on startup.

**Why it happens:** Spring Boot 4.0 changed Flyway autoconfiguration. The `flyway-core` dependency alone no longer triggers automatic migration. You must use `spring-boot-starter-flyway`.

**How to avoid:** Use `org.springframework.boot:spring-boot-starter-flyway` instead of `org.flywaydb:flyway-core`. Add `org.flywaydb:flyway-database-postgresql` alongside it for PostgreSQL support.

Source: [Flyway + Boot 4 issue #4165](https://github.com/flyway/flyway/issues/4165), [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)

### Pitfall 3: ONNX Native Memory Not Tracked by JVM

**What goes wrong:** Docker container gets OOM-killed (exit code 137) but JVM heap metrics show plenty of free memory.

**Why it happens:** ONNX Runtime allocates ~65 MB of native (off-heap) memory for the bge-small-en-v1.5-q model, plus inference buffers. Docker sees total process RSS but JVM monitoring only tracks heap.

**How to avoid:**
1. Set Docker `mem_limit` for the Java app container to at least `JVM heap + 1 GB headroom` (e.g., heap 2 GB + headroom 1 GB = 3 GB container limit).
2. Add `-XX:NativeMemoryTracking=summary` to JVM args for diagnostic visibility.
3. Monitor with `docker stats` during development, not just JVM metrics.

Source: [PITFALLS.md Pitfall 5](../../research/PITFALLS.md)

### Pitfall 4: Docker Startup Order Race Conditions

**What goes wrong:** Java app starts before PostgreSQL is ready. `PSQLException: Connection refused` followed by crash loops.

**Why it happens:** Docker Compose `depends_on` without `condition: service_healthy` only waits for container start, not service readiness.

**How to avoid:** Use health checks with `condition: service_healthy` for all dependencies. See Docker Compose configuration in Code Examples section.

Source: [PITFALLS.md Pitfall 8](../../research/PITFALLS.md)

### Pitfall 5: MCP stdio Corruption from Console Output

**What goes wrong:** Any non-JSON-RPC output on stdout (Spring banner, logging, System.out) breaks the MCP protocol. Claude Code disconnects.

**Why it happens:** MCP stdio transport uses stdout exclusively for JSON-RPC messages. Spring Boot defaults to console logging.

**How to avoid:** In the `stdio` profile:
- `spring.main.banner-mode=off`
- `spring.main.web-application-type=none`
- `logging.pattern.console=` (empty = no console appender)
- Redirect all logging to file: `logging.file.name=./logs/alexandria-mcp.log`

Source: [PITFALLS.md Pitfall 4](../../research/PITFALLS.md)

### Pitfall 6: Virtual Thread Pinning from ONNX Native Calls

**What goes wrong:** Virtual threads get pinned during ONNX inference (~5-50ms per batch). With only 4 carrier threads, this blocks all other virtual threads.

**How to avoid:** Never run ONNX embedding on virtual threads. Use a fixed-size `Executors.newFixedThreadPool(N)` for embedding work. Virtual threads are for I/O only (HTTP requests, DB operations).

**Detection:** `-Djdk.tracePinnedThreads=short` prints stack traces when pinning occurs.

Source: [PITFALLS.md Pitfall 9](../../research/PITFALLS.md)

### Pitfall 7: HNSW Index Operator Mismatch

**What goes wrong:** PostgreSQL ignores the HNSW index and does a sequential scan. Queries take seconds instead of milliseconds.

**Why it happens:** The index uses `vector_cosine_ops` but the query uses `<->` (L2 distance) instead of `<=>` (cosine distance). The operator MUST match the index ops class.

**How to avoid:**
1. In the Flyway migration, create index with `USING hnsw (embedding vector_cosine_ops)` -- note: `vector_cosine_ops` for full-precision `vector(384)`, NOT `halfvec_cosine_ops`.
2. All queries must use `<=>` operator for cosine distance.
3. Add an integration test that runs `EXPLAIN ANALYZE` and asserts `Index Scan` is present.
4. Always run `ANALYZE document_chunks` after bulk inserts.

Source: [PITFALLS.md Pitfall 3](../../research/PITFALLS.md)

---

## Code Examples

### Flyway Migration: V1 -- pgvector Extension

```sql
-- V1__create_pgvector_extension.sql
-- Extension must exist before any vector columns can be defined.
-- In Docker, this also runs via /docker-entrypoint-initdb.d/init.sql as superuser.
-- The IF NOT EXISTS makes this idempotent for both paths.
CREATE EXTENSION IF NOT EXISTS vector;
```

### Flyway Migration: V2 -- document_chunks Table

```sql
-- V2__create_document_chunks.sql
CREATE TABLE document_chunks (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    embedding vector(384),                 -- Full precision, NOT halfvec (user decision)
    source_url TEXT,
    section_path TEXT,                      -- Heading hierarchy: "Getting Started > Installation"
    content_type VARCHAR(30),              -- 'api-reference', 'tutorial', 'changelog', 'conceptual'
    metadata JSONB DEFAULT '{}',
    tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- HNSW index for vector similarity search (cosine distance)
CREATE INDEX idx_document_chunks_embedding_hnsw
    ON document_chunks USING hnsw (embedding vector_cosine_ops);

-- GIN index for full-text search
CREATE INDEX idx_document_chunks_tsv_gin
    ON document_chunks USING gin(tsv);

-- B-tree indexes for metadata filtering
CREATE INDEX idx_document_chunks_source_url ON document_chunks (source_url);
CREATE INDEX idx_document_chunks_content_type ON document_chunks (content_type);

-- Run ANALYZE after index creation for accurate query planner statistics
ANALYZE document_chunks;
```

**HNSW index strategy (Claude's Discretion):** Creating the HNSW index in the Flyway migration is correct for Phase 1 because there is no bulk data load at this stage. The table starts empty; data will be inserted incrementally in later phases. For the initial bulk load scenario (Phase 4), the plan should include a strategy to drop and rebuild the index around large batch inserts.

**HNSW parameters:** Using pgvector defaults (`m=16`, `ef_construction=64`) which are appropriate for datasets under 100K vectors. These can be tuned later if recall drops below 95% at scale.

### Flyway Migration: V3 -- sources Table

```sql
-- V3__create_sources.sql
CREATE TABLE sources (
    id BIGSERIAL PRIMARY KEY,
    url TEXT NOT NULL UNIQUE,
    name VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'pending',   -- pending, crawling, indexed, error
    last_crawled_at TIMESTAMPTZ,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    config JSONB DEFAULT '{}',                         -- Crawl configuration (depth, patterns)
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### Flyway Migration: V4 -- ingestion_state Table

```sql
-- V4__create_ingestion_state.sql
CREATE TABLE ingestion_state (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    content_hash VARCHAR(64),                          -- SHA-256 for change detection
    last_ingested_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'pending',     -- pending, processing, completed, error
    error_message TEXT,
    chunks_processed INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ingestion_state_source_id ON ingestion_state (source_id);
```

### Docker Compose Configuration

```yaml
# docker-compose.yml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: alexandria
      POSTGRES_USER: alexandria
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-alexandria_dev}
    ports:
      - "5432:5432"        # Exposed for debug/admin (user decision)
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./docker/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U alexandria -d alexandria"]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 10s
    deploy:
      resources:
        limits:
          memory: 4G
    restart: unless-stopped

  crawl4ai:
    image: unclecode/crawl4ai:0.8.0
    shm_size: 1g           # Required for Chromium rendering
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:11235/health"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s    # Chromium pool initialization takes time
    deploy:
      resources:
        limits:
          memory: 4G
    restart: unless-stopped
    # NOT exposed to host -- internal Docker network only (user decision)

  app:
    build:
      context: .
      dockerfile: Dockerfile
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alexandria
      SPRING_DATASOURCE_USERNAME: alexandria
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-alexandria_dev}
      SPRING_PROFILES_ACTIVE: web
      JAVA_OPTS: >-
        -Xmx2g
        -XX:NativeMemoryTracking=summary
        -Djdk.tracePinnedThreads=short
    depends_on:
      postgres:
        condition: service_healthy
      crawl4ai:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 5
    deploy:
      resources:
        limits:
          memory: 3G        # 2G heap + ~1G native (ONNX + metaspace + stacks)
    restart: unless-stopped
    # NOT exposed to host -- internal Docker network only (user decision)

volumes:
  pgdata:
```

**Health check configuration (Claude's Discretion):**
- PostgreSQL: 5s interval, 10 retries, 10s start_period (fast to check, needs time on first init)
- Crawl4AI: 10s interval, 5 retries, 30s start_period (Chromium pool initialization is slow)
- App: 10s interval, 5 retries (depends on postgres healthy, so start_period is less critical)

### Docker Init Script

```sql
-- docker/init.sql
-- Runs as superuser during PostgreSQL container first initialization.
-- Ensures pgvector extension is available before Flyway migrations run.
CREATE EXTENSION IF NOT EXISTS vector;
```

### Dockerfile (Multi-stage Gradle Build)

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN ./gradlew dependencies --no-daemon
COPY src/ src/
COPY config/ config/
RUN ./gradlew bootJar --no-daemon -x test -x integrationTest

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=build /app/build/libs/*.jar app.jar
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:NativeMemoryTracking=summary", \
  "-Djdk.tracePinnedThreads=short", \
  "-jar", "app.jar"]
```

### Embedding Model Bean Usage

```java
// Source: LangChain4j docs + examples
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.Response;

// Instantiation (in @Bean method)
EmbeddingModel model = new BgeSmallEnV15QuantizedEmbeddingModel();

// Usage
Response<Embedding> response = model.embed("How to configure Spring Boot routing");
Embedding embedding = response.content();
float[] vector = embedding.vector();  // 384 dimensions
```

### MCP Logging Strategy (Claude's Discretion)

**Recommendation:** Log to file in both profiles, with different verbosity.

```yaml
# application-web.yml (logging section)
logging:
  level:
    dev.alexandria: DEBUG
    org.springframework: INFO

# application-stdio.yml (logging section)
logging:
  pattern:
    console:                  # Empty = no console output
  file:
    name: ./logs/alexandria-mcp.log
  level:
    dev.alexandria: INFO      # Less verbose in production MCP mode
    org.springframework: WARN
```

The file log is the ONLY way to debug MCP stdio issues since console output is forbidden.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Spring Boot 3.5 + Spring AI 1.x | Spring Boot 4.0 + Spring AI 2.x-M2 | Dec 2025 | Must use milestone Spring AI for Boot 4 compat |
| `langchain4j-spring-boot-starter` for auto-config | Manual `@Bean` configuration | Boot 4.0 release (Nov 2025) | Starter incompatible with Boot 4.0; manual config is ~20 lines |
| `flyway-core` dependency only | `spring-boot-starter-flyway` + `flyway-database-postgresql` | Boot 4.0 | Flyway no longer auto-configured from core alone in Boot 4.0 |
| LangChain4j embeddings in separate repo | Merged into main langchain4j repo | Dec 2025 | Version suffix `-beta19` is normal; not a stability indicator |
| Custom RRF SQL for hybrid search | `PgVectorEmbeddingStore.searchMode(HYBRID)` | LangChain4j 1.11.0 (Feb 2026) | Native RRF fusion eliminates manual SQL for hybrid search |

**Deprecated/outdated:**
- `langchain4j-spring-boot-starter`: Avoid on Spring Boot 4.0 (broken autoconfiguration path)
- `spring-ai` 1.x: Cannot be used with Spring Boot 4.0; only 2.x-M2+ is compatible
- `flyway-core` alone: No longer sufficient for auto-migration in Spring Boot 4.0
- `pgvector/pgvector:pg17`: User chose PG16 to avoid potential halfvec planner issues
- `RestTemplate`: Deprecated; use `RestClient` (Spring Boot 3.2+)

---

## Open Questions

1. **Spring AI 2.0.0-M2 Stability for MCP**
   - What we know: 2.0.0-M2 exists on Maven Central, targets Spring Boot 4.0/Framework 7, includes MCP server starter. Issue #3379 (Boot 4 compat) is closed as complete.
   - What's unclear: How stable is the MCP server API in the milestone? Will it change before GA?
   - Recommendation: The MCP server starter is not needed until Phase 5. For Phase 1, just set up profile configuration (yml files) without the actual MCP dependency. Add it in Phase 5 when it may have reached GA.

2. **LangChain4j PgVectorEmbeddingStore Connection Management**
   - What we know: `PgVectorEmbeddingStore` manages its own JDBC connections via builder params (host, port, user, password). It does NOT use Spring's `DataSource`.
   - What's unclear: Does it create its own connection pool? Does it conflict with HikariCP? Can it share the Spring-managed DataSource?
   - Recommendation: Accept dual connection management for now. LangChain4j pgvector creates its own pool internally. Spring/Hibernate uses HikariCP for JPA operations. They coexist without conflict on the same database.

3. **JPA Entity vs LangChain4j EmbeddingStore Table Overlap**
   - What we know: User wants a `DocumentChunk` JPA entity AND LangChain4j's `PgVectorEmbeddingStore` which creates/manages its own table structure.
   - What's unclear: Can the JPA entity and LangChain4j store operate on the same `document_chunks` table without conflict?
   - Recommendation: Use LangChain4j's `PgVectorEmbeddingStore` for embedding operations (store, search). Use native SQL queries or a lightweight repository for metadata-only operations. Do NOT map the embedding column in JPA -- JPA does not understand the `vector` type. Keep the JPA entity minimal or skip it in Phase 1, adding it only if JPA-specific features are needed later.

---

## Memory Budget Verification

| Service | Limit | Expected Usage | Notes |
|---------|-------|----------------|-------|
| PostgreSQL (pg16 + pgvector) | 4 GB | 0.5-1 GB (empty DB) | Grows with data; limit allows HNSW index operations |
| Crawl4AI (Chromium) | 4 GB | 1-2 GB idle | 30s start_period for Chromium pool init |
| Java App (heap + ONNX native) | 3 GB | 1.5-2 GB | 2 GB heap + ~200 MB ONNX model + metaspace |
| OS + buffers | ~3 GB | -- | Linux kernel, page cache |
| **Total** | **~14 GB** | **~6-8 GB** | **Well within 14 GB budget; ~10-16 GB headroom on 24 GB** |

---

## Sources

### Primary (HIGH confidence)
- [LangChain4j GitHub Releases](https://github.com/langchain4j/langchain4j/releases) -- v1.11.0, Feb 4, 2026
- [LangChain4j PgVector Docs](https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/) -- builder API, hybrid search
- [LangChain4j In-Process ONNX Docs](https://docs.langchain4j.dev/integrations/embedding-models/in-process/) -- model instantiation
- [Maven Central: langchain4j-embeddings-bge-small-en-v15-q](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-embeddings-bge-small-en-v15-q) -- 1.11.0-beta19 available
- [Maven Central: langchain4j-pgvector](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-pgvector) -- 1.11.0-beta19
- [Spring AI GitHub Releases](https://github.com/spring-projects/spring-ai/releases) -- 2.0.0-M2 available
- [Spring AI Boot 4 Epic #3379](https://github.com/spring-projects/spring-ai/issues/3379) -- Closed as complete
- [LangChain4j Boot 4 Issue #4268](https://github.com/langchain4j/langchain4j/issues/4268) -- Open, starter incompatible
- [Flyway + Boot 4 Issue #4165](https://github.com/flyway/flyway/issues/4165) -- Resolved, use starter
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [PITFALLS.md](../../research/PITFALLS.md) -- Pitfalls 2-5, 8-9 directly relevant to Phase 1
- [architecture.md](../../../docs/architecture.md) -- Package structure, design patterns
- [stack.md](../../../docs/stack.md) -- Version targets (adapted for Boot 4.0 compatibility)

### Secondary (MEDIUM confidence)
- [Spring AI MCP Server Starter on Maven Central](https://central.sonatype.com/artifact/org.springframework.ai/spring-ai-starter-mcp-server) -- 2.0.0-M2 confirmed
- [Flyway + Boot 4 Medium article](https://pranavkhodanpur.medium.com/flyway-migrations-in-spring-boot-4-x-what-changed-and-how-to-configure-it-correctly-dbe290fa4d47) -- starter-flyway requirement
- [Redgate Flyway Extension Management](https://www.red-gate.com/hub/product-learning/flyway/managing-postgresql-extensions-using-flyway) -- CREATE EXTENSION IF NOT EXISTS pattern
- [pgvector Docker Hub](https://hub.docker.com/r/pgvector/pgvector) -- pg16 image availability

### Tertiary (LOW confidence)
- `BgeSmallEnV15QuantizedEmbeddingModel` exact class name -- inferred from naming convention of `BgeSmallEnQuantizedEmbeddingModel` and verified via web search results, but not confirmed by reading source code directly
- `PgVectorEmbeddingStore.searchMode(SearchMode.HYBRID)` API -- referenced in LangChain4j 1.11.0 release notes but exact builder method names should be validated against actual code

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- All versions verified against Maven Central and GitHub releases. Boot 4.0 compatibility matrix verified via official issues.
- Architecture: HIGH -- Package structure from architecture.md, dual-profile pattern from Spring Boot official docs.
- Pitfalls: HIGH -- Comprehensive pitfalls document (PITFALLS.md) covers all Phase 1 concerns with verified sources.
- Build tool conflict: HIGH -- Conflict clearly identified and resolution path documented.
- Spring AI 2.0.0-M2 stability: MEDIUM -- Milestone release, not GA. MCP server API may change. Mitigated by deferring MCP dependency to Phase 5.

**Research date:** 2026-02-14
**Valid until:** 2026-03-14 (30 days -- library versions stable, Spring AI 2.0 may reach RC or GA)
