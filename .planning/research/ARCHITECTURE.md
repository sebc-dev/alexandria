# Architecture Research

**Domain:** Self-hosted RAG documentation system (Java + Python sidecar + pgvector)
**Researched:** 2026-02-14
**Confidence:** HIGH (verified against official LangChain4j, MCP Java SDK, Crawl4AI, and Spring Boot documentation)

## System Overview

```
                     Claude Code (host)
                          |
                     stdio / JSON-RPC
                          |
              +-----------+-----------+
              |    MCP Server Layer   |  (Java process, stdin/stdout)
              |   Tool routing &      |
              |   response formatting |
              +-----------+-----------+
                          |
                   Spring Boot App
                          |
         +--------+-------+--------+---------+
         |        |                |         |
    +----+----+  +------+   +-----+----+ +--+------+
    | Crawl   |  | RAG  |   | Ingest   | | Source  |
    | Service |  | Query|   | Pipeline | | Mgmt    |
    +----+----+  +--+---+   +-----+----+ +---------+
         |          |              |
         | HTTP     | SQL+Vector  | SQL+Vector
         |          |              |
    +----+----+  +--+--------------+--+
    |Crawl4AI |  |  PostgreSQL        |
    | Sidecar |  |  + pgvector        |
    | (Python)|  |  (embeddings +     |
    | :11235  |  |   metadata)        |
    +---------+  +--------------------+

    All services in docker-compose network: alexandria-net
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **MCP Server Layer** | Receives JSON-RPC over stdio from Claude Code, routes to tool handlers, formats responses | MCP Java SDK `McpSyncServer` with `StdioServerTransportProvider` |
| **Crawl Service** | Orchestrates web crawling via Crawl4AI sidecar, manages crawl jobs, handles async polling | Spring `@Service` calling Crawl4AI REST API via `WebClient` |
| **RAG Query Engine** | Semantic search over indexed documents, returns ranked results with source attribution | LangChain4j `EmbeddingStoreContentRetriever` + `PgVectorEmbeddingStore` |
| **Ingest Pipeline** | Transforms crawled HTML/Markdown into chunked, embedded, stored text segments | LangChain4j `DocumentSplitter` + ONNX `EmbeddingModel` + `EmbeddingStoreIngestor` |
| **Source Management** | CRUD for documentation sources (URLs, crawl schedules, status tracking) | Spring Data JPA entities + repository |
| **Crawl4AI Sidecar** | Headless browser-based web crawling, JavaScript rendering, Markdown extraction | Crawl4AI Docker image on port 11235, REST API |
| **PostgreSQL + pgvector** | Relational storage for sources/metadata + vector storage for embeddings | PostgreSQL 16 with pgvector extension, HNSW index |

## Recommended Project Structure

```
alexandria/
├── docker-compose.yml              # Service orchestration
├── Dockerfile                      # Java app (multi-stage build)
├── mcp-wrapper.sh                  # Stdio bridge script for Claude Code
├── src/main/java/dev/alexandria/
│   ├── AlexandriaApplication.java  # Spring Boot entry point
│   ├── mcp/                        # MCP Server layer
│   │   ├── McpServerConfig.java    #   Server setup, transport, capabilities
│   │   ├── McpToolRouter.java      #   Tool registration and dispatch
│   │   ├── tools/                  #   Individual MCP tool handlers
│   │   │   ├── SearchTool.java     #     search_docs tool
│   │   │   ├── CrawlTool.java      #     crawl_url / add_source tools
│   │   │   ├── StatusTool.java     #     pipeline_status tool
│   │   │   └── SourceTool.java     #     list_sources / remove_source tools
│   │   └── format/                 #   Response formatting for Claude
│   │       └── McpResponseFormatter.java
│   ├── crawl/                      # Crawl4AI integration
│   │   ├── CrawlClient.java        #   HTTP client to Crawl4AI REST API
│   │   ├── CrawlJobService.java    #   Async job management, polling
│   │   ├── CrawlConfig.java        #   Crawl4AI request/response DTOs
│   │   └── CrawlResultProcessor.java # Raw crawl output -> Document
│   ├── ingest/                     # Ingestion pipeline
│   │   ├── IngestPipeline.java      #   Orchestrates split -> embed -> store
│   │   ├── DocumentChunker.java     #   Markdown-aware splitting strategy
│   │   ├── MetadataEnricher.java    #   Adds source URL, title, section hierarchy
│   │   └── ChangeDetector.java      #   Content hashing for incremental updates
│   ├── search/                     # RAG query engine
│   │   ├── SearchService.java       #   Semantic search entry point
│   │   ├── SearchConfig.java        #   Retriever + embedding model beans
│   │   └── ResultFormatter.java     #   Formats results with source attribution
│   ├── source/                     # Source management
│   │   ├── Source.java              #   JPA entity
│   │   ├── SourceRepository.java    #   Spring Data JPA repository
│   │   ├── SourceService.java       #   Business logic
│   │   └── CrawlSchedule.java       #   Scheduling metadata
│   ├── document/                   # Document domain model
│   │   ├── DocumentRecord.java      #   JPA entity for crawled pages
│   │   ├── DocumentRepository.java  #   Spring Data JPA repository
│   │   └── ContentHash.java         #   Content fingerprinting for change detection
│   └── config/                     # Cross-cutting configuration
│       ├── EmbeddingConfig.java     #   ONNX model + PgVector store beans
│       ├── WebClientConfig.java     #   Crawl4AI HTTP client config
│       └── AsyncConfig.java         #   Thread pool for crawl jobs
├── src/main/resources/
│   ├── application.yml              # Spring Boot config
│   └── db/migration/               # Flyway migrations
│       ├── V1__create_sources.sql
│       ├── V2__create_documents.sql
│       └── V3__enable_pgvector.sql
└── src/test/java/dev/alexandria/
    └── ...                          # Test mirrors of main
```

### Structure Rationale

- **mcp/:** Isolated from Spring HTTP layer. The MCP server reads stdio and calls into Spring-managed services. This package is the boundary between the MCP protocol world and the application domain. Keeping it separate means the RAG pipeline works independently of MCP (testable, reusable).
- **crawl/:** Encapsulates all Crawl4AI HTTP communication. If Crawl4AI's API changes or gets replaced, only this package changes. Uses DTOs that mirror Crawl4AI's JSON contract.
- **ingest/:** Pure data transformation. Takes raw crawled content and produces embedded text segments. No I/O concerns beyond calling the embedding model and store. The pipeline stages are composable and individually testable.
- **search/:** Thin layer over LangChain4j's retrieval API. Adds source attribution metadata and formats results for MCP consumption. Separated from ingest because they have different lifecycles and concerns.
- **source/ and document/:** Domain model with JPA entities. `Source` represents a configured documentation URL. `DocumentRecord` represents a crawled page within a source. This separation allows tracking crawl history and enabling incremental updates.
- **config/:** Spring `@Configuration` classes that wire up LangChain4j beans, HTTP clients, and async executors. Keeps infrastructure wiring out of business logic.

## Architectural Patterns

### Pattern 1: MCP Server as Thin Adapter over Spring Services

**What:** The MCP server layer is a protocol adapter that translates JSON-RPC tool calls into Spring service method calls and formats the results back as MCP tool responses. It contains zero business logic.

**When to use:** Always. The MCP layer should never contain domain logic, database access, or crawl orchestration. It only does: parse arguments, call service, format response.

**Trade-offs:** Adds a small mapping layer, but makes the entire RAG pipeline testable without MCP protocol concerns. Also enables future REST API or CLI interfaces without touching core logic.

**Example:**
```java
// mcp/tools/SearchTool.java
public class SearchTool {

    private final SearchService searchService;

    public McpServerFeatures.SyncToolSpecification specification() {
        return new McpServerFeatures.SyncToolSpecification(
            new Tool("search_docs", "Search indexed documentation", SEARCH_SCHEMA),
            (exchange, arguments) -> {
                String query = (String) arguments.get("query");
                int maxResults = (int) arguments.getOrDefault("max_results", 5);

                List<SearchResult> results = searchService.search(query, maxResults);

                String formatted = McpResponseFormatter.formatSearchResults(results);
                return new CallToolResult(
                    List.of(new TextContent(formatted)), false
                );
            }
        );
    }
}
```

### Pattern 2: Async Crawl with Polling (Sidecar Communication)

**What:** The Java app submits crawl jobs to Crawl4AI's async job API (`POST /crawl/job`), receives a `task_id`, then polls (`GET /job/{task_id}`) until completion. This decouples crawl duration from MCP request timeout.

**When to use:** For all crawl operations. Web crawling can take seconds to minutes. The MCP tool returns immediately with a job ID, and the user can check status later.

**Trade-offs:** More complex than synchronous crawling, but necessary because: (1) MCP tool calls should return quickly, (2) Crawl4AI crawls can take 30+ seconds for deep pages, (3) the Java app can process results when ready rather than blocking.

**Example:**
```java
// crawl/CrawlClient.java
@Component
public class CrawlClient {

    private final WebClient webClient;

    // Submit async crawl job
    public Mono<String> submitCrawlJob(String url, CrawlConfig config) {
        return webClient.post()
            .uri("/crawl/job")
            .bodyValue(Map.of(
                "urls", List.of(url),
                "crawler_config", Map.of(
                    "type", "CrawlerRunConfig",
                    "params", Map.of(
                        "cache_mode", "bypass",
                        "markdown_generator", Map.of(
                            "type", "DefaultMarkdownGenerator",
                            "params", Map.of("content_filter", Map.of(
                                "type", "PruningContentFilter"
                            ))
                        )
                    )
                )
            ))
            .retrieve()
            .bodyToMono(CrawlJobResponse.class)
            .map(CrawlJobResponse::taskId);
    }

    // Poll for completion
    public Mono<CrawlResult> getJobResult(String taskId) {
        return webClient.get()
            .uri("/job/{taskId}", taskId)
            .retrieve()
            .bodyToMono(CrawlJobResult.class)
            .filter(r -> "completed".equals(r.status()))
            .map(CrawlJobResult::result);
    }
}
```

### Pattern 3: Content-Hash Change Detection for Incremental Updates

**What:** Before re-indexing a crawled page, compute a SHA-256 hash of the cleaned content. Compare against the stored hash for that URL. Only re-embed and re-store if the hash differs. This avoids reprocessing unchanged pages during scheduled re-crawls.

**When to use:** Every re-crawl cycle. Without this, re-crawling N pages means re-embedding N pages, which is wasteful when most documentation pages change infrequently.

**Trade-offs:** Adds a content fingerprinting step and requires storing hashes per document. Very low overhead compared to the embedding cost it saves. The hash comparison is O(1) per document; embedding is O(seconds) per document.

**Example:**
```java
// ingest/ChangeDetector.java
@Component
public class ChangeDetector {

    private final DocumentRepository documentRepository;

    public boolean hasChanged(String url, String content) {
        String newHash = sha256(content);
        return documentRepository.findByUrl(url)
            .map(doc -> !newHash.equals(doc.getContentHash()))
            .orElse(true); // New document, always index
    }

    public void updateHash(String url, String contentHash) {
        documentRepository.updateContentHash(url, contentHash);
    }

    private String sha256(String content) {
        // Normalize whitespace before hashing to ignore trivial changes
        String normalized = content.replaceAll("\\s+", " ").trim();
        return DigestUtils.sha256Hex(normalized);
    }
}
```

### Pattern 4: Markdown-Aware Document Chunking

**What:** Use LangChain4j's `DocumentByParagraphSplitter` as the primary splitter, but pre-process the Markdown to inject section hierarchy metadata (H1/H2/H3 headers) into each chunk's metadata. This means search results carry breadcrumb context (e.g., "API Reference > Authentication > OAuth2 Flow") even for deeply nested content.

**When to use:** For all documentation ingestion. Technical documentation is highly structured; exploiting that structure improves retrieval quality significantly.

**Trade-offs:** Requires a custom pre-processing step before the standard splitter. The Markdown parsing adds minimal overhead but substantially improves result quality.

**Example:**
```java
// ingest/DocumentChunker.java
@Component
public class DocumentChunker {

    private final DocumentSplitter splitter;

    public DocumentChunker() {
        // Recursive splitter: 512 tokens max, 50 token overlap
        this.splitter = DocumentSplitters.recursive(512, 50);
    }

    public List<TextSegment> chunk(String markdown, Metadata baseMetadata) {
        // Enrich each segment with section hierarchy from headers
        List<TextSegment> segments = new ArrayList<>();
        List<MarkdownSection> sections = parseMarkdownSections(markdown);

        for (MarkdownSection section : sections) {
            Document doc = Document.from(section.content(), baseMetadata.copy()
                .put("section_path", section.headerPath())    // "API > Auth > OAuth2"
                .put("section_level", section.level())         // 2
                .put("section_title", section.title()));       // "OAuth2 Flow"

            segments.addAll(splitter.split(doc));
        }
        return segments;
    }
}
```

## Data Flow

### Primary Data Flow: URL to Searchable Embeddings (Ingestion)

```
1. User adds source URL via MCP tool (add_source)
        |
        v
2. Source saved to PostgreSQL (sources table)
        |
        v
3. Crawl job submitted to Crawl4AI sidecar
   POST http://crawl4ai:11235/crawl/job
   Body: { urls: ["https://docs.example.com/..."],
           crawler_config: { type: "CrawlerRunConfig",
                             params: { cache_mode: "bypass" } } }
        |
        v
4. Crawl4AI renders page (headless Chromium), extracts Markdown
   Returns: { task_id: "crawl_123456" }
        |
        v
5. Java app polls GET /job/crawl_123456 until status: "completed"
   Result: { markdown: "# API Reference\n...", links: {...} }
        |
        v
6. Change detection: SHA-256(normalized_content) vs stored hash
   If unchanged -> skip (no re-embedding needed)
   If changed or new -> continue
        |
        v
7. Markdown-aware chunking:
   - Parse header hierarchy (H1/H2/H3 breadcrumbs)
   - Split into ~512-token segments with 50-token overlap
   - Each TextSegment carries metadata:
     { source_url, section_path, section_title, content_hash }
        |
        v
8. In-process ONNX embedding (all-MiniLM-L6-v2, 384 dimensions)
   Each TextSegment -> float[384] vector
   ~50ms per segment on CPU
        |
        v
9. Store in pgvector:
   INSERT INTO embeddings (id, embedding, text, metadata)
   HNSW index enables fast approximate nearest-neighbor search
        |
        v
10. Update document record: set content_hash, last_indexed timestamp
```

### Query Flow: MCP Search Request to Formatted Response

```
1. Claude Code sends MCP tool call: search_docs(query="how to authenticate")
        |
        v
2. MCP Server Layer parses JSON-RPC, extracts tool name + arguments
        |
        v
3. SearchService.search(query, maxResults):
   a. Embed query string using same ONNX model -> float[384]
   b. EmbeddingStoreContentRetriever queries pgvector:
      SELECT * FROM embeddings
      ORDER BY embedding <=> query_vector  -- cosine distance
      LIMIT maxResults
   c. Returns List<Content> with text + metadata
        |
        v
4. ResultFormatter adds source attribution:
   For each result:
   - Extract source_url, section_path from metadata
   - Format with score, breadcrumb, and text excerpt
        |
        v
5. MCP Server returns CallToolResult with formatted text
   Claude Code receives searchable, attributed results
```

### Crawl Management Flow: Status and Control

```
1. Claude Code: list_sources() -> returns all configured sources with status
2. Claude Code: crawl_url(url) -> submits new crawl, returns job ID
3. Claude Code: pipeline_status() -> active crawls, index stats, health
4. Background: scheduled re-crawls run on configurable intervals
   - For each source: re-crawl -> change detect -> re-index if changed
```

## Docker-Compose Service Topology

### Service Definitions

```yaml
# docker-compose.yml
version: "3.9"

services:
  # === PostgreSQL with pgvector ===
  postgres:
    image: pgvector/pgvector:pg16
    container_name: alexandria-db
    environment:
      POSTGRES_DB: alexandria
      POSTGRES_USER: alexandria
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-alexandria}
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"     # Expose for development; remove in production
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U alexandria -d alexandria"]
      interval: 5s
      timeout: 5s
      retries: 5
      start_period: 15s
    networks:
      - alexandria-net
    deploy:
      resources:
        limits:
          memory: 2G

  # === Crawl4AI Python Sidecar ===
  crawl4ai:
    image: unclecode/crawl4ai:latest
    container_name: alexandria-crawler
    shm_size: "2g"     # Required for headless Chromium
    environment:
      - CRAWL4AI_API_TOKEN=${CRAWL4AI_TOKEN:-}
    ports:
      - "11235:11235"   # Expose for development; remove in production
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:11235/monitor/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 30s  # Chromium pool takes time to initialize
    networks:
      - alexandria-net
    deploy:
      resources:
        limits:
          memory: 4G      # Headless Chromium is memory-hungry

  # === Alexandria Java App ===
  alexandria:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: alexandria-app
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alexandria
      SPRING_DATASOURCE_USERNAME: alexandria
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-alexandria}
      CRAWL4AI_BASE_URL: http://crawl4ai:11235
      CRAWL4AI_API_TOKEN: ${CRAWL4AI_TOKEN:-}
    depends_on:
      postgres:
        condition: service_healthy
      crawl4ai:
        condition: service_healthy
    # NOTE: No port mapping needed for MCP stdio mode.
    # The app runs as an MCP server (stdin/stdout), not an HTTP server.
    # Claude Code spawns it via: docker run -i --rm --network alexandria-net ...
    stdin_open: true     # Keep stdin open for MCP stdio transport
    networks:
      - alexandria-net
    deploy:
      resources:
        limits:
          memory: 4G      # ONNX embedding model needs ~1-2GB

networks:
  alexandria-net:
    driver: bridge

volumes:
  pgdata:
```

### Memory Budget (Target: ~10-14 GB)

| Service | Allocation | Rationale |
|---------|-----------|-----------|
| PostgreSQL + pgvector | 2 GB | Shared buffers + vector index in memory |
| Crawl4AI sidecar | 4 GB | Headless Chromium browser pool (~2GB shm + process overhead) |
| Alexandria Java app | 4 GB | JVM heap (~2GB) + ONNX runtime (~1-1.5GB) + overhead |
| OS + Docker overhead | 2 GB | Linux kernel, Docker daemon, filesystem cache |
| **Total** | **~12 GB** | Within 10-14 GB budget on 24 GB machine |

### Network Architecture

All services share `alexandria-net` bridge network. Service names resolve as hostnames:
- Java app reaches Crawl4AI at `http://crawl4ai:11235`
- Java app reaches PostgreSQL at `postgres:5432`
- No inter-service communication between Crawl4AI and PostgreSQL (Java app mediates all data flow)

### MCP Server Connectivity

Claude Code connects to Alexandria's MCP server via **stdio transport**. Two approaches:

**Approach A: Wrapper script (recommended for simplicity)**

A shell script bridges Claude Code's stdio to the Dockerized Java app:

```bash
#!/bin/bash
# mcp-wrapper.sh -- Alexandria MCP bridge
# Claude Code spawns this as a subprocess; it relays stdio to/from Docker
exec docker run -i --rm \
  --network alexandria_alexandria-net \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/alexandria \
  -e SPRING_DATASOURCE_USERNAME=alexandria \
  -e SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD:-alexandria} \
  -e CRAWL4AI_BASE_URL=http://crawl4ai:11235 \
  alexandria-app
```

Claude Code configuration (`.mcp.json` in project root):
```json
{
  "mcpServers": {
    "alexandria": {
      "type": "stdio",
      "command": "./mcp-wrapper.sh",
      "args": []
    }
  }
}
```

**Approach B: `docker exec` into running container**

If the Java app is already running via `docker-compose up`:
```json
{
  "mcpServers": {
    "alexandria": {
      "type": "stdio",
      "command": "docker",
      "args": ["exec", "-i", "alexandria-app", "java", "-jar", "/app/alexandria.jar", "--mcp-stdio"]
    }
  }
}
```

**Recommendation:** Use Approach A. It is simpler, the container is ephemeral (clean state per session), and the `docker run -i` pattern is the established convention for MCP stdio servers in Docker. The long-running postgres and crawl4ai services start separately with `docker-compose up postgres crawl4ai`.

## Crawl4AI API Contract

### Request Format (Java -> Crawl4AI)

Crawl4AI uses a **type-wrapper pattern** for non-primitive configuration values:

```json
{
  "urls": ["https://docs.example.com/api"],
  "browser_config": {
    "type": "BrowserConfig",
    "params": {
      "headless": true,
      "text_mode": true
    }
  },
  "crawler_config": {
    "type": "CrawlerRunConfig",
    "params": {
      "cache_mode": "bypass",
      "markdown_generator": {
        "type": "DefaultMarkdownGenerator",
        "params": {
          "content_filter": {
            "type": "PruningContentFilter"
          }
        }
      }
    }
  }
}
```

### Key Endpoints Used

| Endpoint | Method | Purpose | Response |
|----------|--------|---------|----------|
| `/crawl/job` | POST | Submit async crawl | `{ task_id: "crawl_..." }` |
| `/job/{task_id}` | GET | Poll job status | `{ status: "pending/completed/failed", result: {...} }` |
| `/crawl` | POST | Synchronous crawl (single page) | `{ success: true, markdown: "...", links: {...} }` |
| `/monitor/health` | GET | Health check | `{ container: {...}, pool: {...} }` |

### Response Handling

The Java `CrawlClient` should:
1. Use `WebClient` (non-blocking) for all Crawl4AI communication
2. Implement retry with exponential backoff for poll operations
3. Set a maximum poll duration (e.g., 5 minutes) to prevent indefinite waiting
4. Extract `markdown` field from successful results (primary content for indexing)
5. Optionally extract `links` for crawl-depth expansion (discover sub-pages)

## Internal Architecture: Key Spring Beans Wiring

```
┌─────────────────────────────────────────────────────────────────┐
│ McpServerConfig                                                  │
│  Creates: McpSyncServer + StdioServerTransportProvider           │
│  Registers: SearchTool, CrawlTool, StatusTool, SourceTool        │
│  Each tool receives injected Spring @Service beans               │
├─────────────────────────────────────────────────────────────────┤
│ EmbeddingConfig                                                  │
│  Creates: AllMiniLmL6V2EmbeddingModel (ONNX, in-process)        │
│  Creates: PgVectorEmbeddingStore (connects to postgres:5432)     │
│  Creates: EmbeddingStoreContentRetriever                         │
├─────────────────────────────────────────────────────────────────┤
│ WebClientConfig                                                  │
│  Creates: WebClient bean for Crawl4AI (base URL, timeouts)       │
├─────────────────────────────────────────────────────────────────┤
│ AsyncConfig                                                      │
│  Creates: TaskExecutor for crawl job polling (bounded pool)      │
├─────────────────────────────────────────────────────────────────┤
│ Domain Services (@Service)                                       │
│  SearchService     <- EmbeddingModel, ContentRetriever           │
│  CrawlJobService   <- CrawlClient, IngestPipeline, ChangeDetector│
│  IngestPipeline    <- DocumentChunker, EmbeddingModel, EmbedStore│
│  SourceService     <- SourceRepository, CrawlJobService          │
└─────────────────────────────────────────────────────────────────┘
```

## Database Schema

### PostgreSQL Tables

```sql
-- V3__enable_pgvector.sql
CREATE EXTENSION IF NOT EXISTS vector;

-- Sources: configured documentation URLs
CREATE TABLE sources (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    base_url    VARCHAR(2048) NOT NULL UNIQUE,
    crawl_depth INTEGER DEFAULT 1,
    schedule    VARCHAR(50),            -- cron expression or "manual"
    status      VARCHAR(20) DEFAULT 'active',
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

-- Documents: individual crawled pages within a source
CREATE TABLE documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_id    UUID REFERENCES sources(id) ON DELETE CASCADE,
    url          VARCHAR(2048) NOT NULL UNIQUE,
    title        VARCHAR(512),
    content_hash VARCHAR(64),           -- SHA-256 for change detection
    last_crawled TIMESTAMP,
    last_indexed TIMESTAMP,
    status       VARCHAR(20) DEFAULT 'pending',
    created_at   TIMESTAMP DEFAULT NOW()
);

-- Embeddings: pgvector-managed (created by LangChain4j PgVectorEmbeddingStore)
-- LangChain4j creates this table automatically with:
--   id UUID, embedding vector(384), text TEXT, metadata JSONB
-- The metadata JSONB stores: source_url, section_path, section_title,
-- document_id, source_id

-- HNSW index for fast approximate nearest-neighbor search
-- Created by LangChain4j when useIndex=true, or manually:
-- CREATE INDEX ON embeddings USING hnsw (embedding vector_cosine_ops);
```

### Metadata Strategy

Use `COMBINED_JSONB` for metadata storage in pgvector. This stores all metadata as a single JSONB column, which:
- Supports arbitrary keys without schema changes
- Enables filtering via PostgreSQL JSONB operators (`@>`, `?`, `->>`)
- Is the best option for documentation metadata where keys vary per source

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 1-10 sources, < 10K chunks | Current architecture. Single docker-compose. ONNX embedding on CPU is fast enough. |
| 10-50 sources, 10K-100K chunks | Add IVFFlat or HNSW index to pgvector. Increase PostgreSQL shared_buffers. Consider parallel crawling (multiple Crawl4AI workers). |
| 50+ sources, 100K+ chunks | Move to external embedding service for faster throughput. Consider pgvector partitioning. Add Redis for crawl job queue. Possibly separate read replicas. |

### Scaling Priorities

1. **First bottleneck: Embedding throughput.** ONNX CPU embedding at ~50ms/segment means 1000 segments takes ~50 seconds. For initial bulk ingestion of large doc sites, this is acceptable. For ongoing incremental updates (typically 10-50 segments), this is fast. Mitigation: process segments in batches, parallelize across available cores.
2. **Second bottleneck: pgvector query performance.** Without an index, vector search is brute-force O(n). HNSW index reduces this to O(log n) approximate search. Enable HNSW from the start since Alexandria will grow over time.
3. **Third bottleneck: Crawl4AI concurrency.** The default Chromium browser pool handles 3-5 concurrent crawls. For bulk-adding many sources, crawls will queue. Mitigated by async job API.

## Anti-Patterns

### Anti-Pattern 1: Synchronous Crawling in MCP Tool Handler

**What people do:** Call Crawl4AI synchronously from within the MCP tool handler, making Claude Code wait for the crawl to complete.
**Why it's wrong:** Web crawls take 5-60+ seconds. MCP tool calls should return within seconds. Claude Code will appear frozen, and the user experience degrades.
**Do this instead:** Submit async crawl job, return job ID immediately. Provide a separate `pipeline_status` tool for checking progress. Optionally: index and report when the crawl completes on next status check.

### Anti-Pattern 2: Storing Embeddings Without Source Metadata

**What people do:** Store text chunks with only the embedding vector, no metadata about which source URL, section, or document they came from.
**Why it's wrong:** Search results become useless without attribution. Users cannot verify sources. Incremental updates cannot target specific documents. Deletion of a source leaves orphan embeddings.
**Do this instead:** Every TextSegment must carry: `source_url`, `document_id`, `source_id`, `section_path`, `section_title`, `content_hash`. Use LangChain4j's `Metadata` object on each `TextSegment`.

### Anti-Pattern 3: Re-embedding Everything on Every Crawl

**What people do:** On re-crawl, delete all embeddings for a source and re-ingest from scratch.
**Why it's wrong:** Wastes CPU on unchanged content (most pages don't change between crawls). Creates a window where the source has no search results (between delete and re-insert). Increases database churn.
**Do this instead:** Content-hash change detection per document URL. Only re-embed documents whose hash has changed. Delete-then-insert only for changed documents, within a transaction.

### Anti-Pattern 4: Running MCP Server as HTTP Service

**What people do:** Expose the MCP server over HTTP/SSE and configure Claude Code to connect via network.
**Why it's wrong for this project:** Claude Code's stdio transport is simpler, more reliable, and has no network configuration requirements. HTTP adds CORS, authentication, port management, and firewall concerns. Stdio just works.
**Do this instead:** Use `StdioServerTransportProvider` with a wrapper script that bridges Claude Code's process-spawning to `docker run -i`.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Crawl4AI | REST API over HTTP (docker network) | Type-wrapper JSON format. Use async job API for crawls. Poll with backoff. |
| PostgreSQL | JDBC via Spring Data JPA + LangChain4j PgVectorEmbeddingStore | Two access paths: JPA for source/document entities, LangChain4j for embeddings. Same database. |
| Claude Code | MCP stdio (JSON-RPC 2.0 over stdin/stdout) | Via wrapper script + `docker run -i`. No port exposure needed. |
| ONNX Runtime | In-process via LangChain4j embedding model | Loads model into JVM memory on startup (~500MB-1.5GB). No external service. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| MCP Layer <-> Domain Services | Direct method calls (Spring DI) | MCP tools get services injected. No network boundary. |
| CrawlService <-> Crawl4AI | HTTP REST (WebClient) | Async. Fire-and-poll. Retry with backoff. |
| IngestPipeline <-> EmbeddingStore | LangChain4j API (in-process) | `EmbeddingStoreIngestor.ingest(segments)` writes to pgvector. |
| IngestPipeline <-> EmbeddingModel | LangChain4j API (in-process) | ONNX model runs in-process. No network call. |

## Build Order Implications

The component dependencies dictate a natural build order:

```
Phase 1: Foundation
  PostgreSQL + pgvector (docker-compose)
  └── Database schema (Flyway migrations)
  └── Source/Document JPA entities + repositories
  └── Spring Boot skeleton with health checks

Phase 2: Embedding Infrastructure
  ONNX Embedding Model (LangChain4j config)
  └── PgVectorEmbeddingStore (LangChain4j config)
  └── EmbeddingStoreContentRetriever
  └── SearchService (query path works end-to-end with manual test data)

Phase 3: Crawl Integration
  Crawl4AI sidecar (docker-compose)
  └── CrawlClient (HTTP client to Crawl4AI API)
  └── CrawlJobService (async job management)
  └── CrawlResultProcessor (raw crawl -> Document)

Phase 4: Ingestion Pipeline
  DocumentChunker (Markdown-aware splitting)
  └── MetadataEnricher (section hierarchy, source attribution)
  └── ChangeDetector (content hash comparison)
  └── IngestPipeline (orchestrates chunk -> embed -> store)
  └── Wire CrawlJobService -> IngestPipeline (crawl results feed ingestion)

Phase 5: MCP Server
  StdioServerTransportProvider + McpSyncServer
  └── Tool handlers (SearchTool, CrawlTool, SourceTool, StatusTool)
  └── mcp-wrapper.sh + .mcp.json configuration
  └── End-to-end: Claude Code -> MCP -> search/crawl -> response

Phase 6: Incremental Updates & Polish
  Scheduled re-crawls (Spring @Scheduled or cron)
  └── Change detection integration into re-crawl flow
  └── Source management (add/remove/list)
  └── Pipeline status reporting
```

**Rationale for ordering:**
- Phase 1 must come first: everything depends on the database
- Phase 2 before Phase 3: you want to verify search works with test data before adding crawling complexity
- Phase 3 before Phase 4: crawling produces the raw content that ingestion processes
- Phase 4 before Phase 5: the pipeline must work before MCP tools can expose it
- Phase 5 is the integration layer that connects everything to Claude Code
- Phase 6 adds operational maturity (scheduling, incremental updates)

## Sources

- [LangChain4j Official Documentation - RAG Tutorial](https://docs.langchain4j.dev/tutorials/rag/) -- HIGH confidence
- [LangChain4j Spring Boot Integration](https://docs.langchain4j.dev/tutorials/spring-boot-integration/) -- HIGH confidence
- [LangChain4j In-Process ONNX Embeddings](https://docs.langchain4j.dev/integrations/embedding-models/in-process/) -- HIGH confidence
- [LangChain4j PgVector Integration](https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/) -- HIGH confidence
- [MCP Java SDK - Server Documentation](https://modelcontextprotocol.io/sdk/java/mcp-server) -- HIGH confidence
- [Claude Code MCP Configuration](https://code.claude.com/docs/en/mcp) -- HIGH confidence
- [Crawl4AI Self-Hosting Guide (v0.8.x)](https://docs.crawl4ai.com/core/self-hosting/) -- HIGH confidence
- [Crawl4AI Docker Hub](https://hub.docker.com/r/unclecode/crawl4ai) -- HIGH confidence
- [Docker Compose Healthcheck Patterns](https://docs.docker.com/compose/how-tos/startup-order/) -- HIGH confidence
- [Incremental Updates in RAG Systems](https://dasroot.net/posts/2026/01/incremental-updates-rag-dynamic-documents/) -- MEDIUM confidence
- [RAG Chunking Strategies for Java](https://medium.com/@visrow/rag-chunking-strategies-practical-guide-for-retrieval-augmented-generation-in-java-0e73dce33623) -- MEDIUM confidence
- [pgvector/pgvector Docker Image](https://hub.docker.com/r/pgvector/pgvector) -- HIGH confidence

---
*Architecture research for: Alexandria self-hosted RAG documentation system*
*Researched: 2026-02-14*
