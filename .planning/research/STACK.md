# Stack Research

**Domain:** Self-hosted RAG documentation system (Java)
**Researched:** 2026-02-14
**Confidence:** MEDIUM-HIGH (versions verified against Maven Central and official repos; some integration patterns from training data)

## Recommended Stack

The user's core technology decisions (Java 21, Spring Boot, LangChain4j, pgvector, ONNX, Crawl4AI, MCP Java SDK) are already locked. This document focuses on **specific versions, integration contracts, and Docker configuration** verified as of February 2026.

### Core Technologies

| Technology | Version | Purpose | Why Recommended | Confidence |
|------------|---------|---------|-----------------|------------|
| Java | 21 (LTS) | Runtime | Current LTS. Virtual threads (Project Loom) for I/O-heavy crawling pipeline. Records for metadata. Spring Boot 3.5.x requires Java 17+, so 21 is the sweet spot. Do NOT jump to Java 25 yet -- Spring Boot 4.0 requires it but has breaking changes (Jakarta EE 11, Kotlin 2.2). | HIGH |
| Spring Boot | 3.5.10 | Application framework | Latest patch (released Jan 22, 2026). Supported until Jun 2032 (commercial). Spring Boot 4.0.2 exists but requires Spring Framework 7, Jakarta EE 11, and has extensive breaking changes -- avoid for a greenfield project starting now unless prepared for the migration tax. | HIGH |
| LangChain4j | 1.11.0 | RAG orchestration | Latest release (Feb 4, 2026). Adds PgVector hybrid search, streaming agents, MCP context injection. Use this for the core `langchain4j` artifact and all `langchain4j-*` integration modules. | HIGH |
| PostgreSQL | 17.8 | Relational + vector DB | Latest PostgreSQL 17 patch. 2x write throughput improvements over PG16 for high-concurrency WAL workloads. Needed for pgvector 0.8.x compatibility (use 17.3+ to avoid linking issues). | HIGH |
| pgvector | 0.8.1 | Vector similarity search | Latest release. Iterative index scans solve the over-filtering problem critical for metadata-filtered RAG queries. halfvec (16-bit) support halves storage. | HIGH |
| Crawl4AI | 0.8.0 | Web crawling sidecar | Latest release. Docker image `unclecode/crawl4ai:0.8.0`. REST API on port 11235. Converts HTML to clean Markdown automatically -- exactly what a RAG pipeline needs. Replaces JSoup + custom crawler entirely. | HIGH |
| MCP Java SDK | 0.17.2 | Claude Code integration | Latest release (Jan 22, 2026). Provides StdioServerTransportProvider for local Claude Code communication. 18 releases in 10 months -- API is stabilizing but still pre-1.0. | MEDIUM |
| ONNX Runtime | 1.24.1 | In-process embedding inference | Latest release (Feb 5, 2026). Quarterly releases. Used transitively via LangChain4j embedding modules. | HIGH |

### Supporting Libraries

| Library | Version | Artifact | Purpose | Confidence |
|---------|---------|----------|---------|------------|
| langchain4j-embeddings-bge-small-en-v15-q | 1.11.0-beta19 | `dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q` | In-process quantized bge-small-en-v1.5 embedding model (~65 MB ONNX). Zero external service. Single Maven dependency. | HIGH |
| langchain4j-pgvector | 1.11.0-beta19 | `dev.langchain4j:langchain4j-pgvector` | PgVector embedding store with NEW hybrid search (RRF) in 1.11.0. Supports `SearchMode.HYBRID`, text search config, customizable RRF k-constant. | HIGH |
| flexmark-java | 0.64.8 | `com.vladsch.flexmark:flexmark-all` | Markdown AST parsing for structure-aware chunking. Source-level AST with offset tracking -- critical for not splitting code blocks or tables. | MEDIUM |
| Spring AI MCP Server Starter | 1.0.3 | `org.springframework.ai:spring-ai-starter-mcp-server` | Spring Boot auto-configuration for MCP stdio server. GA release. Uses `@Tool` annotation for declarative tool exposure. For stdio transport use `spring-ai-starter-mcp-server` (not `-webmvc`). | MEDIUM |
| Spring Boot Starter Web | 3.5.10 | `org.springframework.boot:spring-boot-starter-web` | REST API for admin/management endpoints alongside MCP stdio. | HIGH |
| Spring Boot Starter Actuator | 3.5.10 | `org.springframework.boot:spring-boot-starter-actuator` | Health checks, metrics. Essential for Docker health probes. | HIGH |
| Jackson | (managed by Spring Boot) | `com.fasterxml.jackson.core:jackson-databind` | JSON serialization for Crawl4AI REST API communication and MCP protocol. | HIGH |
| HikariCP | (managed by Spring Boot) | `com.zaxxon:HikariCP` | JDBC connection pool. Spring Boot default. | HIGH |
| PostgreSQL JDBC | (managed by Spring Boot) | `org.postgresql:postgresql` | JDBC driver. | HIGH |
| Flyway | (managed by Spring Boot) | `org.flywaydb:flyway-core` + `flyway-database-postgresql` | Database schema migrations. Essential for pgvector table creation, index management. | HIGH |

### Development Tools

| Tool | Version | Purpose | Notes |
|------|---------|---------|-------|
| Docker + Docker Compose | 20.10+ / Compose V2 | Container orchestration | Three services: Java app, Crawl4AI sidecar, PostgreSQL+pgvector |
| Maven | 3.9+ | Build tool | Use Maven wrapper (`mvnw`) for reproducibility |
| GraalVM (optional) | N/A | NOT recommended | Native image compilation has issues with ONNX Runtime native memory. Stick with JVM. |
| Testcontainers | Latest | Integration testing | Spin up PostgreSQL+pgvector and Crawl4AI containers for tests |

---

## Version Compatibility Matrix

**Critical: LangChain4j embedding modules use a separate versioning scheme from the core library.**

| Core Artifact | Version | Embedding Artifact | Version | Notes |
|---------------|---------|-------------------|---------|-------|
| `langchain4j` | 1.11.0 | `langchain4j-embeddings-bge-small-en-v15-q` | 1.11.0-beta19 | Beta suffix is normal; these are production-usable. The "-beta" refers to the embedding module release cadence, not stability. |
| `langchain4j` | 1.11.0 | `langchain4j-pgvector` | 1.11.0-beta19 | Same versioning scheme as embeddings. |
| `langchain4j` | 1.11.0 | `langchain4j-document-parser-jsoup` | 1.11.0 | Document parsers follow core versioning. |

| Spring Boot | Spring AI | MCP Java SDK | Notes |
|-------------|-----------|--------------|-------|
| 3.5.10 | 1.0.3 (GA) | 0.17.2 | Spring AI 1.0.3 depends on MCP SDK transitively. Spring AI 2.0 is milestone-only -- avoid. |
| 3.5.10 | 1.1.2 (GA) | 0.17.2 | Spring AI 1.1.2 is latest GA. Consider this if Spring AI MCP features from 1.1 are needed. Verify compatibility. |

**Recommendation:** Use Spring AI 1.0.3 for MCP server starter (most stable, well-documented), LangChain4j 1.11.0 for RAG pipeline. They coexist without conflict -- different concerns, different namespaces.

---

## Crawl4AI Integration (Critical Path)

### Docker Configuration

```yaml
# Crawl4AI sidecar in docker-compose.yml
crawl4ai:
  image: unclecode/crawl4ai:0.8.0
  ports:
    - "11235:11235"
  volumes:
    - /dev/shm:/dev/shm  # Chromium shared memory -- required
  deploy:
    resources:
      limits:
        memory: 4G
      reservations:
        memory: 1G
  restart: unless-stopped
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:11235/health"]
    interval: 30s
    timeout: 10s
    retries: 3
    start_period: 40s
  user: "appuser"
```

No LLM API keys needed -- Alexandria uses Crawl4AI purely for HTML-to-Markdown conversion, not LLM extraction.

### REST API Contract (Java integration)

**Endpoint:** `POST http://crawl4ai:11235/crawl`

**Request:**
```json
{
  "urls": ["https://docs.spring.io/spring-boot/reference/index.html"],
  "browser_config": {
    "type": "BrowserConfig",
    "params": {
      "headless": true
    }
  },
  "crawler_config": {
    "type": "CrawlerRunConfig",
    "params": {
      "cache_mode": "bypass"
    }
  }
}
```

**Response (CrawlResult):**
```json
{
  "url": "string",
  "html": "string (original HTML)",
  "success": true,
  "cleaned_html": "string (scripts/styles removed)",
  "markdown": {
    "raw_markdown": "string (full HTML-to-Markdown)",
    "markdown_with_citations": "string (with academic-style link refs)",
    "references_markdown": "string (footnotes/references)",
    "fit_markdown": "string or null",
    "fit_html": "string or null"
  },
  "links": {
    "internal": [{"href": "...", "text": "...", "title": "..."}],
    "external": [{"href": "...", "text": "...", "title": "..."}]
  },
  "media": {
    "images": [],
    "audio": [],
    "video": []
  },
  "tables": [
    {
      "headers": ["col1", "col2"],
      "rows": [["val1", "val2"]],
      "caption": "string or null"
    }
  ],
  "metadata": {},
  "status_code": 200,
  "error_message": "string or null"
}
```

**Key fields for RAG pipeline:**
- `markdown.raw_markdown` -- primary content for chunking and embedding
- `links.internal` -- for recursive crawl discovery
- `url` -- source URL metadata for chunks
- `success` -- error handling

### Java Client Pattern (Spring Boot RestClient)

Use Spring Boot 3.2+ `RestClient` (synchronous, fluent API). Do NOT use deprecated `RestTemplate`.

```java
@Service
public class Crawl4AiClient {

    private final RestClient restClient;

    public Crawl4AiClient(@Value("${crawl4ai.base-url:http://crawl4ai:11235}") String baseUrl) {
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public CrawlResult crawl(String url) {
        CrawlRequest request = new CrawlRequest(
            List.of(url),
            new BrowserConfig(true),
            new CrawlerConfig("bypass")
        );

        return restClient.post()
            .uri("/crawl")
            .body(request)
            .retrieve()
            .body(CrawlResult.class);
    }
}

// Records for request/response mapping
record CrawlRequest(
    List<String> urls,
    @JsonProperty("browser_config") BrowserConfig browserConfig,
    @JsonProperty("crawler_config") CrawlerConfig crawlerConfig
) {}

record BrowserConfig(String type, Map<String, Object> params) {
    BrowserConfig(boolean headless) {
        this("BrowserConfig", Map.of("headless", headless));
    }
}

record CrawlerConfig(String type, Map<String, Object> params) {
    CrawlerConfig(String cacheMode) {
        this("CrawlerRunConfig", Map.of("cache_mode", cacheMode));
    }
}

record CrawlResult(
    String url,
    boolean success,
    MarkdownResult markdown,
    CrawlLinks links,
    @JsonProperty("error_message") String errorMessage,
    @JsonProperty("status_code") Integer statusCode
) {}

record MarkdownResult(
    @JsonProperty("raw_markdown") String rawMarkdown,
    @JsonProperty("markdown_with_citations") String markdownWithCitations,
    @JsonProperty("references_markdown") String referencesMarkdown
) {}

record CrawlLinks(
    List<CrawlLink> internal,
    List<CrawlLink> external
) {}

record CrawlLink(String href, String text, String title) {}
```

### Additional Crawl4AI Endpoints

| Endpoint | Method | Purpose | When to Use |
|----------|--------|---------|-------------|
| `/crawl` | POST | Synchronous crawl | Single page crawl during ingestion |
| `/crawl/stream` | POST | Streaming NDJSON results | Batch crawling multiple pages |
| `/crawl/job` | POST | Async job submission | Large site crawls with webhook callback |
| `/job/{task_id}` | GET | Check async job status | Poll for completion of async jobs |
| `/monitor/health` | GET | System health | Docker health check, readiness probe |
| `/md` | POST | Quick markdown extraction | Lightweight alternative to full crawl |

---

## MCP Server Integration

### Architecture Decision: Stdio vs HTTP Transport

**Use stdio transport** for Claude Code integration. This is non-negotiable for local development:
- Claude Code launches the MCP server as a subprocess
- Communication via stdin/stdout (JSON-RPC)
- Zero network configuration required
- Configured in `.mcp.json` at project root

### Two Implementation Approaches

**Approach A: Spring AI MCP Starter (Recommended)**

Uses `spring-ai-starter-mcp-server` for declarative `@Tool` annotation support with auto-configuration.

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server</artifactId>
    <version>1.0.3</version>
</dependency>
```

Spring configuration for stdio:
```properties
# application.properties
spring.main.banner-mode=off
# Disable console logging to avoid corrupting stdio transport
logging.file.name=logs/alexandria.log
logging.pattern.console=
spring.ai.mcp.server.transport=stdio
```

**Approach B: Raw MCP SDK (Fallback)**

If Spring AI starter causes issues or the project needs tighter control:

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.17.2</version>
</dependency>
```

```java
StdioServerTransportProvider transport = new StdioServerTransportProvider(new ObjectMapper());

McpSyncServer server = McpServer.sync(transport)
    .serverInfo("alexandria", "1.0.0")
    .capabilities(ServerCapabilities.builder()
        .tools(true)
        .build())
    .build();

server.addTool(new McpServerFeatures.SyncToolSpecification(
    new Tool("search_docs", "Search indexed documentation", searchSchema),
    (exchange, arguments) -> {
        String query = (String) arguments.get("query");
        // ... RAG retrieval logic ...
        return new CallToolResult(resultText, false);
    }
));
```

### Claude Code Configuration

```json
// .mcp.json (project root)
{
  "mcpServers": {
    "alexandria": {
      "command": "java",
      "args": [
        "-jar",
        "target/alexandria.jar",
        "--spring.profiles.active=mcp"
      ],
      "env": {
        "SPRING_DATASOURCE_URL": "jdbc:postgresql://localhost:5432/alexandria"
      }
    }
  }
}
```

**Critical:** Disable banner and console logging when running as stdio MCP server. Any stdout output that is not valid JSON-RPC will break the protocol.

---

## Docker Compose Configuration

### Complete docker-compose.yml

```yaml
version: '3.8'

services:
  # PostgreSQL with pgvector extension
  postgres:
    image: pgvector/pgvector:pg17
    environment:
      POSTGRES_DB: alexandria
      POSTGRES_USER: alexandria
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-alexandria_dev}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    deploy:
      resources:
        limits:
          memory: 6G
        reservations:
          memory: 2G
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U alexandria"]
      interval: 10s
      timeout: 5s
      retries: 5
    restart: unless-stopped

  # Crawl4AI web crawling sidecar
  crawl4ai:
    image: unclecode/crawl4ai:0.8.0
    ports:
      - "11235:11235"
    volumes:
      - /dev/shm:/dev/shm
    deploy:
      resources:
        limits:
          memory: 4G
        reservations:
          memory: 1G
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:11235/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
    user: "appuser"
    restart: unless-stopped

  # Alexandria Java application
  alexandria:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"   # REST API (admin/management)
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alexandria
      SPRING_DATASOURCE_USERNAME: alexandria
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-alexandria_dev}
      CRAWL4AI_BASE_URL: http://crawl4ai:11235
      JAVA_OPTS: >-
        -Xmx2g
        -XX:NativeMemoryTracking=summary
        -Djdk.tracePinnedThreads=true
    depends_on:
      postgres:
        condition: service_healthy
      crawl4ai:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 3G
        reservations:
          memory: 1G
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
    restart: unless-stopped

volumes:
  pgdata:
```

### Memory Budget (24 GB target)

| Service | Limit | Reserved | Notes |
|---------|-------|----------|-------|
| PostgreSQL + pgvector | 6 GB | 2 GB | HNSW index + shared_buffers + work_mem |
| Crawl4AI (Chromium) | 4 GB | 1 GB | Headless browser is memory-hungry |
| Alexandria JVM | 3 GB | 1 GB | 2 GB heap + ~1 GB native (ONNX model) |
| OS + buffers | ~3 GB | -- | Linux page cache, kernel |
| **Total used** | **~16 GB** | **4 GB** | **8 GB headroom** for spikes |

### Java Application Dockerfile (Multi-stage)

```dockerfile
# Build stage
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser
EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:NativeMemoryTracking=summary", \
  "-Djdk.tracePinnedThreads=true", \
  "-jar", "app.jar"]
```

**Key Docker decisions:**
- `eclipse-temurin:21-jre-jammy` -- Adoptium Temurin is the standard OpenJDK distribution. JRE-only for smaller image. Jammy (Ubuntu 22.04) base for ONNX Runtime native library compatibility.
- `pgvector/pgvector:pg17` -- official pgvector Docker image, pre-built with pgvector 0.8.1 on PostgreSQL 17. Preferred over `ankane/pgvector` (legacy).
- Multi-stage build reduces image from ~800 MB (JDK) to ~300 MB (JRE + app).

---

## LangChain4j PgVector Hybrid Search Configuration

This is a new feature in 1.11.0 and central to Alexandria's retrieval quality.

```java
PgVectorEmbeddingStore embeddingStore = PgVectorEmbeddingStore.builder()
    .host("postgres")
    .port(5432)
    .database("alexandria")
    .user("alexandria")
    .password("...")
    .table("document_chunks")
    .dimension(384)           // bge-small-en-v1.5 dimensions
    .createTable(true)
    .useIndex(true)           // Enable HNSW index
    .indexListSize(100)       // IVFFlat partitions (for large datasets)
    // NEW in 1.11.0: Hybrid search
    .searchMode(SearchMode.HYBRID)     // Vector + full-text search
    .textSearchConfig("english")       // PostgreSQL text search config
    .rrfK(60)                          // RRF fusion constant (default: 60)
    .metadataStorageConfig(
        MetadataStorageConfig.combinedJsonb(
            List.of("framework", "version", "content_type", "section_path")
        )
    )
    .build();
```

**This eliminates the need for manual RRF SQL queries** that the original stack.md described. LangChain4j 1.11.0 handles vector + FTS + RRF fusion natively.

---

## Maven Dependencies (pom.xml)

```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.5.10</spring-boot.version>
    <langchain4j.version>1.11.0</langchain4j.version>
    <langchain4j-embeddings.version>1.11.0-beta19</langchain4j-embeddings.version>
    <mcp-sdk.version>0.17.2</mcp-sdk.version>
    <spring-ai.version>1.0.3</spring-ai.version>
    <flexmark.version>0.64.8</flexmark.version>
</properties>

<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- LangChain4j Core -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-spring-boot-starter</artifactId>
        <version>${langchain4j.version}</version>
    </dependency>

    <!-- In-process embedding model (ONNX, quantized) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-embeddings-bge-small-en-v15-q</artifactId>
        <version>${langchain4j-embeddings.version}</version>
    </dependency>

    <!-- PgVector embedding store with hybrid search -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-pgvector</artifactId>
        <version>${langchain4j-embeddings.version}</version>
    </dependency>

    <!-- MCP Server (via Spring AI starter for @Tool support) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server</artifactId>
        <version>${spring-ai.version}</version>
    </dependency>

    <!-- Markdown parsing for structure-aware chunking -->
    <dependency>
        <groupId>com.vladsch.flexmark</groupId>
        <artifactId>flexmark-all</artifactId>
        <version>${flexmark.version}</version>
    </dependency>

    <!-- Database migrations -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <!-- PostgreSQL JDBC -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

---

## Alternatives Considered

| Category | Recommended | Alternative | Why Not |
|----------|-------------|-------------|---------|
| Crawling | Crawl4AI (Docker sidecar) | JSoup + custom crawler | JSoup cannot render JavaScript. Crawl4AI handles JS-heavy doc sites (React-based, Docusaurus SSR), produces clean Markdown, and has a mature REST API. The sidecar cost (4 GB) is worth the capability. |
| Crawling | Crawl4AI | Firecrawl self-hosted | Firecrawl requires Redis as additional dependency and is AGPL-licensed. Crawl4AI is Apache 2.0 and self-contained. |
| Vector DB | pgvector | Qdrant | Qdrant adds a separate container, separate backup strategy, and a learning curve. pgvector reuses PostgreSQL, supports SQL-native filtering, and LangChain4j 1.11.0 now handles hybrid search natively. Only consider Qdrant if pgvector hits performance limits at >5M chunks. |
| RAG Framework | LangChain4j | Spring AI | LangChain4j has in-process ONNX embeddings (1 Maven dep), native RRF, re-ranking, and query routing. Spring AI has better observability but lacks in-process embeddings and native hybrid search. Use Spring AI only for MCP server starter. |
| MCP Transport | stdio | SSE / Streamable HTTP | Claude Code launches MCP servers as subprocesses via stdio. SSE/HTTP is for remote server scenarios -- not applicable for local developer tooling. |
| Embedding Model | bge-small-en-v1.5-q (384d) | bge-base-en-v1.5 (768d) | Start with bge-small for faster iteration. 384d vs 768d doubles storage and index size. Upgrade only if retrieval quality evaluation shows insufficient precision. |
| Build Tool | Maven | Gradle | Maven is simpler for Spring Boot projects, better IDE support, and the team knows it. Gradle offers faster builds but adds Groovy/Kotlin DSL complexity. |
| Java Version | 21 | 25 | Java 25 is the newest LTS but requires Spring Boot 4.0 which has breaking changes. Java 21 virtual threads cover all project needs. |
| Spring Boot | 3.5.10 | 4.0.2 | 4.0 requires Jakarta EE 11, Spring Framework 7, and Kotlin 2.2+. Massive migration effort for no benefit to this project. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Spring AI for embeddings | `TransformersEmbeddingModel` has documented compatibility issues with certain models. Manual ONNX setup required. | LangChain4j in-process ONNX embeddings (single Maven dep) |
| `RestTemplate` | Deprecated in favor of `RestClient` since Spring Boot 3.2. | `RestClient` (synchronous, fluent API) |
| Milvus | Requires etcd + MinIO + Pulsar -- consumes 12-20 GB RAM standalone. Exceeds budget. | pgvector on PostgreSQL |
| Weaviate (Java client) | Java client v6 is labelled "BETA -- not recommended for production" by Weaviate themselves. | pgvector |
| ChromaDB / LanceDB | No production-quality Java clients. | pgvector |
| Crawler4j | Abandoned for 5+ years. No maintenance. | Crawl4AI sidecar |
| GraalVM Native Image | ONNX Runtime native memory is incompatible with native-image constraints. Startup time is not a concern for a long-running server. | Standard JVM with eclipse-temurin |
| `flexmark` (individual modules) | Too many small dependencies to manage. | `flexmark-all` (single dependency, includes everything) |
| Spring AI 2.0.x | Milestone-only (M1, M2). Not GA. API will change. | Spring AI 1.0.3 or 1.1.2 (GA) |

---

## Stack Patterns by Variant

**If running Alexandria as MCP server (primary mode -- launched by Claude Code):**
- Use Spring profile `mcp` that disables web server, enables stdio transport
- Disable banner: `spring.main.banner-mode=off`
- Redirect all logging to file (stdout must be clean JSON-RPC only)
- JVM args: `-Xmx2g -XX:NativeMemoryTracking=summary`

**If running Alexandria as web service (admin/management mode):**
- Use Spring profile `web` that enables REST endpoints for pipeline management
- Enable Actuator endpoints for health/metrics
- Runs inside Docker Compose alongside PostgreSQL and Crawl4AI

**If running both simultaneously (recommended):**
- Docker Compose runs the web service (admin API + scheduled ingestion)
- `.mcp.json` runs a separate JVM process for Claude Code MCP (stdio)
- Both connect to the same PostgreSQL database
- Consider extracting shared JAR with two entry points (profiles)

---

## Key Version Updates Since Original stack.md

| Component | Original stack.md | Current (Feb 2026) | Impact |
|-----------|-------------------|---------------------|--------|
| LangChain4j | 1.0.0 | 1.11.0 | Major: PgVector hybrid search built-in, streaming agents, MCP context injection |
| MCP Java SDK | 0.17.2 | 0.17.2 | No change -- still latest |
| Spring Boot | 3.5 (unspecified patch) | 3.5.10 | Use latest patch for bug fixes |
| ONNX Runtime | 1.23.2 | 1.24.1 | Minor: transitive via LangChain4j, no action needed |
| pgvector | 0.8.x | 0.8.1 | Minor: latest stable |
| Crawl4AI | Not in original (used JSoup) | 0.8.0 | Major: replaces custom JSoup crawler entirely |
| Spring AI | Not specified | 1.0.3 (GA) | Added: MCP server starter for @Tool annotation |
| Spring Boot 4.0 | Did not exist | 4.0.2 released | NOT recommended -- stick with 3.5.x |

---

## Sources

### Verified (HIGH confidence)
- [LangChain4j GitHub Releases](https://github.com/langchain4j/langchain4j/releases) -- v1.11.0, Feb 4, 2026
- [LangChain4j PgVector Docs](https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/) -- hybrid search config
- [Maven Central: langchain4j-pgvector](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-pgvector) -- 1.11.0-beta19
- [Maven Central: langchain4j-embeddings-bge-small-en-v15-q](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-embeddings-bge-small-en-v15-q) -- 1.11.0-beta19
- [MCP Java SDK GitHub](https://github.com/modelcontextprotocol/java-sdk) -- v0.17.2, Jan 22, 2026
- [MCP Java SDK Server Docs](https://modelcontextprotocol.io/sdk/java/mcp-server) -- stdio transport, tool specification
- [Spring Boot endoflife.date](https://endoflife.date/spring-boot) -- 3.5.10, Jan 22, 2026
- [Spring AI GitHub Releases](https://github.com/spring-projects/spring-ai/releases) -- 1.0.3 GA, Oct 1, 2025
- [Crawl4AI Self-Hosting Guide](https://docs.crawl4ai.com/core/self-hosting/) -- Docker, API endpoints, v0.8.0
- [Crawl4AI CrawlResult Docs](https://docs.crawl4ai.com/core/crawler-result/) -- response JSON structure
- [pgvector GitHub](https://github.com/pgvector/pgvector) -- v0.8.1
- [pgvector Docker Hub](https://hub.docker.com/r/pgvector/pgvector) -- pg17 image
- [PostgreSQL Releases](https://www.postgresql.org/about/news/postgresql-182-178-1612-1516-and-1421-released-3235/) -- 17.8
- [ONNX Runtime Releases](https://github.com/microsoft/onnxruntime/releases) -- 1.24.1, Feb 5, 2026

### Training Data (MEDIUM confidence, verify before relying)
- flexmark-java 0.64.8 version -- confirmed by multiple search results but not verified on Maven Central directly
- Crawl4AI REST API request/response JSON structure -- verified via official docs but actual field names should be tested against running instance
- Spring AI 1.0.3 + LangChain4j 1.11.0 coexistence -- logically sound (different namespaces) but not explicitly tested

### Needs Validation (LOW confidence)
- LangChain4j `SearchMode.HYBRID` exact API -- referenced in PR #1633 and release notes, but specific builder method names should be verified against actual 1.11.0-beta19 code
- Spring AI `@Tool` annotation with stdio transport -- documented for webmvc; stdio path may differ. Test early.
- Crawl4AI memory usage in practice -- 4 GB limit is documented recommendation; actual usage with documentation site crawls may differ

---
*Stack research for: Alexandria (Self-hosted RAG Documentation System)*
*Researched: 2026-02-14*
