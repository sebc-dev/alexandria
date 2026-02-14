# Project Research Summary

**Project:** Alexandria - Self-hosted RAG Documentation System
**Domain:** RAG (Retrieval-Augmented Generation) with MCP integration for AI coding assistants
**Researched:** 2026-02-14
**Confidence:** HIGH

## Executive Summary

Alexandria is a self-hosted RAG documentation system that integrates with Claude Code via the Model Context Protocol (MCP). The research reveals that building production-quality documentation RAG requires focus on three key areas: structure-aware chunking (the #1 quality factor), hybrid search with reciprocal rank fusion (the production standard, not optional), and careful pgvector index management (which can silently degrade performance by 1000x if misconfigured).

The recommended stack is Java 21 with Spring Boot 3.5.10, LangChain4j 1.11.0 for RAG orchestration, pgvector 0.8.1 for hybrid vector search, and Crawl4AI 0.8.0 as a Python sidecar for JavaScript-capable web crawling. This combination delivers in-process ONNX embeddings (zero external services), production-grade hybrid search built into LangChain4j 1.11.0, and clean HTML-to-Markdown conversion without writing custom crawlers. The architecture uses Docker Compose to orchestrate three containers (PostgreSQL+pgvector, Crawl4AI sidecar, Java application) with a 12GB memory footprint fitting comfortably within the 24GB budget.

The critical risks are: (1) naive text splitting destroys code blocks and tables—must use Markdown-aware chunking from day one, (2) pgvector HNSW indexes cause 5x slower inserts and can silently fall back to sequential scans if misconfigured—must build index after bulk load and verify with EXPLAIN ANALYZE, (3) MCP stdio transport breaks with any console output—must redirect all logs to files, and (4) ONNX native memory escapes JVM monitoring and can cause container OOM kills—must size Docker limits with 500MB+ headroom beyond JVM heap. Each of these has clear prevention strategies documented in the pitfalls research.

## Key Findings

### Recommended Stack

The user's core technology choices are validated by research: Java 21 LTS provides virtual threads for I/O-heavy crawling, Spring Boot 3.5.10 is the latest stable release (commercial support until 2032), and LangChain4j 1.11.0 adds critical hybrid search features released February 2026. The stack avoids Spring Boot 4.0 (breaking changes, requires Jakarta EE 11) and GraalVM native image (incompatible with ONNX Runtime).

**Core technologies:**
- **Java 21 + Spring Boot 3.5.10**: Virtual threads for crawl concurrency, stable LTS, commercial support through 2032
- **LangChain4j 1.11.0**: Native PgVector hybrid search with RRF fusion (new in 1.11.0), in-process ONNX embeddings via single Maven dependency
- **PostgreSQL 17.8 + pgvector 0.8.1**: Hybrid vector+FTS search with iterative index scans (solves over-filtering problem), halfvec support halves storage
- **Crawl4AI 0.8.0**: Python Docker sidecar, REST API, headless Chromium, automatic HTML-to-Markdown with structure preservation
- **MCP Java SDK 0.17.2 + Spring AI 1.0.3**: Stdio transport for Claude Code, @Tool annotation support, GA release

**Critical integration points:**
- Crawl4AI uses type-wrapper JSON format (`{type: "BrowserConfig", params: {...}}`), async job API on port 11235
- LangChain4j 1.11.0 embedding modules use separate versioning (1.11.0-beta19 suffix is normal, production-usable)
- MCP stdio requires zero console output—Spring Boot banner must be disabled, all logging redirected to files

### Expected Features

Documentation RAG has clear table-stakes features users assume exist. Missing hybrid search (vector + BM25 + RRF) makes the product feel broken on exact-term queries like error codes or API names. Missing structure-aware chunking destroys code block integrity and produces gibberish retrieval.

**Must have (table stakes):**
- Hybrid search (semantic + keyword + RRF fusion) with source filtering and result citation
- Recursive web crawling with JavaScript rendering, HTML-to-Markdown, depth/scope controls
- Structure-aware chunking that preserves code blocks, tables, and heading hierarchies with metadata
- MCP stdio server with 3-6 tools: search_docs, add_source, list_sources, remove_source
- Docker Compose single-command setup with PostgreSQL+pgvector, Crawl4AI sidecar, Java app

**Should have (competitive advantage):**
- Cross-encoder reranking for top-k results (200-500ms latency but dramatically improves precision)
- Incremental crawling with content-hash change detection (saves embedding costs on unchanged pages)
- Section-path filtering ("search only API Reference section") enabled by heading metadata
- Code example extraction/filtering (tag chunks as "code" vs "prose" during ingestion)
- Version-aware search (filter by framework version, critical for libraries with breaking changes)

**Defer (v2+):**
- Multi-tenant user management (massive complexity, single-tenant by design)
- PDF/Office document ingestion (different parsing pipeline, unreliable)
- Knowledge graph construction (extremely high complexity, marginal benefit vs hierarchical metadata)
- Web UI dashboard (Claude Code via MCP tools IS the interface)
- Custom embedding model hosting (adds GPU requirements, defeats simple docker-compose value prop)

### Architecture Approach

The architecture uses Docker Compose to orchestrate three services: PostgreSQL+pgvector for storage, Crawl4AI Python sidecar for web crawling, and a Java Spring Boot application that mediates all data flow. Claude Code connects via stdio transport (JSON-RPC over stdin/stdout) to the Java app running as an MCP server in a Docker container.

**Major components:**

1. **MCP Server Layer** — Thin adapter that translates JSON-RPC tool calls into Spring service method calls. Zero business logic, only parses arguments and formats responses. Enables testing RAG pipeline without MCP protocol concerns.

2. **Crawl Service** — Orchestrates async web crawling via Crawl4AI REST API. Submits jobs to `/crawl/job`, polls `/job/{task_id}` until completion, handles retries with exponential backoff. Decouples crawl duration from MCP request timeout.

3. **Ingest Pipeline** — Transforms crawled Markdown into chunked, embedded, stored segments. Markdown-aware chunking splits at heading boundaries, preserves code blocks, enriches chunks with section hierarchy metadata. Content-hash change detection skips re-embedding unchanged pages.

4. **RAG Query Engine** — LangChain4j `EmbeddingStoreContentRetriever` + `PgVectorEmbeddingStore` with hybrid search mode. Combines vector similarity (HNSW index) and full-text search (PostgreSQL tsvector) using RRF fusion. Returns results with source attribution.

5. **Source Management** — JPA entities for documentation sources (URLs, crawl schedules, status). Cascade deletes chunks and embeddings when source is removed.

**Data flow:** User adds source URL via MCP tool -> Crawl4AI async job -> Markdown extraction -> Content-hash change detection -> Markdown-aware chunking with metadata enrichment -> In-process ONNX embedding (bge-small-en-v1.5-q, 384d) -> PgVector storage with HNSW index -> Searchable via hybrid query.

**Memory budget (target 12GB within 24GB machine):** PostgreSQL 2GB, Crawl4AI 4GB (headless Chromium), Java app 4GB (2GB heap + 1-1.5GB ONNX native), OS overhead 2GB.

### Critical Pitfalls

1. **Naive chunking destroys technical documentation structure** — Recursive text splitters cut mid-code-block, split tables, orphan headings. Must parse Markdown AST, split at heading boundaries, treat code blocks as atomic units, preserve heading hierarchy as metadata. Address in Phase 1 (MVP)—even basic heading-aware splitting is 10x better than naive splitting. 80% of RAG quality failures trace to chunking decisions.

2. **pgvector HNSW index silently degrades insert performance and query planning** — HNSW inserts are 5x slower than unindexed. Bulk ingestion requires "load data first, build index after" approach. Query planner can ignore HNSW index if distance operator mismatches (`<=>` for cosine vs `<->` for L2), stale statistics, or incorrect ORDER BY direction. Must run ANALYZE after bulk inserts and verify with EXPLAIN ANALYZE integration test. Phase 1 (MVP) must include index strategy and verification.

3. **MCP stdio corruption from console output** — Any stdout output (Spring Boot banner, logs, System.out.println) breaks JSON-RPC protocol. Must redirect all logging to files, disable banner, disable web server, never use System.out/err. Test by piping stdout through jq. Address in Phase 2 (MCP integration) as first configuration step.

4. **ONNX native memory escapes JVM metrics** — ONNX Runtime allocates 100MB+ native memory for model weights. JVM heap metrics don't account for this. Docker memory limit must be JVM heap + 500MB+ headroom. Must enable NativeMemoryTracking and monitor via `docker stats`. Address in Phase 1 (infrastructure)—size Docker limits correctly from start.

5. **Crawl4AI Chromium processes leak memory** — Browser contexts accumulate after ~150 URLs, consuming container memory until OOM. Requires Crawl4AI 0.8.x with smart browser pooling, aggressive Docker resource limits (4GB), low concurrency settings, and circuit breaker in Java app. Address in Phase 3 (robust ingestion) when automated high-volume crawling begins.

## Implications for Roadmap

Based on research, the natural build order follows component dependencies:

### Phase 1: Foundation & Core RAG
**Rationale:** Database and embedding infrastructure must exist before anything else. Verify search works with test data before adding crawling complexity.

**Delivers:**
- PostgreSQL+pgvector schema with Flyway migrations
- Spring Boot skeleton with health checks
- ONNX embedding model configuration (LangChain4j in-process)
- PgVectorEmbeddingStore with hybrid search mode
- SearchService with manual test data validation
- Docker Compose with health checks and startup ordering

**Addresses:**
- Table stakes: Embedding generation, vector storage, hybrid search foundation
- Pitfalls: HNSW index strategy (build after load), Docker memory sizing, startup race conditions, embedding model metadata storage

**Avoids:**
- Pitfall #2: HNSW performance—design index build strategy correctly from start
- Pitfall #4: ONNX memory—size Docker limits with headroom
- Pitfall #8: Startup ordering—health checks with condition: service_healthy

**Research flag:** Standard patterns—LangChain4j has excellent docs, skip phase research.

---

### Phase 2: Web Crawling Integration
**Rationale:** Crawling produces raw content that ingestion processes. Establish Crawl4AI integration before building ingestion pipeline.

**Delivers:**
- Crawl4AI Docker sidecar with health check
- CrawlClient (async HTTP client to Crawl4AI REST API)
- CrawlJobService (job submission, polling, timeout handling)
- CrawlResultProcessor (extract Markdown and links from crawl results)
- Integration test validating crawl -> Markdown extraction

**Uses:**
- Crawl4AI 0.8.0 Docker image on port 11235
- Spring WebClient for async HTTP with retry/backoff
- Crawl4AI type-wrapper JSON format

**Implements:**
- Crawl Service component from architecture
- Async crawl with polling pattern (decouples duration from MCP timeout)

**Addresses:**
- Table stakes: Recursive web crawling, JavaScript rendering, HTML-to-Markdown
- Pitfall #6 setup: Docker resource limits, shm_size configuration

**Research flag:** Medium complexity—Crawl4AI API contract is documented, but integration patterns may need validation. Consider `/gsd:research-phase` if API behavior differs from docs.

---

### Phase 3: Ingestion Pipeline
**Rationale:** With crawling working, build the transformation pipeline that produces searchable chunks. This is the quality-critical path.

**Delivers:**
- DocumentChunker with Markdown-aware splitting (heading boundaries, code block preservation)
- MetadataEnricher (section hierarchy, source attribution, content hashing)
- ChangeDetector (SHA-256 content hashing for incremental updates)
- IngestPipeline orchestration (chunk -> embed -> store)
- Wire CrawlJobService -> IngestPipeline (automatic ingestion on crawl completion)
- Integration test: verify no chunks start/end mid-code-block

**Addresses:**
- Table stakes: Structure-aware chunking with metadata, chunk overlap
- Differentiators: Incremental crawling with change detection
- Pitfall #1: Structure-aware chunking—prevents code block destruction

**Avoids:**
- Pitfall #1: Critical—Markdown AST parsing with flexmark-java, heading-boundary splits, code block preservation

**Research flag:** High complexity—Markdown AST manipulation patterns need validation. Recommend `/gsd:research-phase` for optimal chunking strategy.

---

### Phase 4: MCP Server Integration
**Rationale:** Pipeline must work before MCP tools can expose it. This phase connects everything to Claude Code.

**Delivers:**
- MCP stdio server (StdioServerTransportProvider + McpSyncServer)
- Tool handlers: SearchTool, CrawlTool, SourceTool, StatusTool (3-6 tools)
- MCP response formatting with source attribution, token budget awareness
- mcp-wrapper.sh script for Docker stdio bridge
- .mcp.json configuration for Claude Code
- Logging configuration: all logs to file, zero console output
- End-to-end test: Claude Code -> search_docs -> formatted results

**Uses:**
- MCP Java SDK 0.17.2 with stdio transport
- Spring AI 1.0.3 MCP server starter (optional, for @Tool annotation)
- Docker run -i for stdio bridge

**Implements:**
- MCP Server Layer from architecture (thin adapter over Spring services)

**Addresses:**
- Table stakes: MCP stdio transport, clear tool descriptions, error handling, token budget
- Pitfall #3: MCP stdout corruption—logging redirection, banner disable

**Avoids:**
- Pitfall #3: Critical—all logging to file, test stdout with jq
- Pitfall #10: Token bloat—cap responses at 1500-3000 tokens, structured format

**Research flag:** Standard patterns—MCP SDK and Spring AI docs are excellent. Skip phase research unless stdio bridge proves problematic.

---

### Phase 5: Source Management & Polish
**Rationale:** Core functionality works; add operational features for managing multiple documentation sources.

**Delivers:**
- add_source MCP tool (submit crawl with URL, depth, pattern filters)
- remove_source MCP tool (cascade delete chunks and embeddings)
- list_sources MCP tool (show status, last crawl time, chunk count)
- Source JPA entities and repository
- SourceService business logic
- Crawl status tracking and error reporting

**Addresses:**
- Table stakes: Source CRUD operations, crawl depth/scope controls
- Differentiators: Source freshness monitoring, crawl error diagnostics

**Research flag:** Standard patterns—Spring Data JPA, skip phase research.

---

### Phase 6: Quality Enhancements (Post-MVP)
**Rationale:** Core system validated; add features that multiply quality and robustness.

**Delivers:**
- Cross-encoder reranking (optional parameter in search_docs)
- Section-path filtering in search (parameter: section_filter)
- Code example extraction (tag chunks as "code" vs "prose")
- Version tagging per source (filterable in search)
- Scheduled re-crawling with incremental updates
- Crawl progress reporting (crawl_status tool)
- Circuit breaker for Crawl4AI memory management

**Addresses:**
- Differentiators: Reranking, section filtering, code extraction, version awareness
- Pitfall #6: Crawl4AI memory leak—circuit breaker, monitoring

**Research flag:** Medium complexity—Cross-encoder model selection and reranking integration patterns need validation. Recommend `/gsd:research-phase` for reranking approach.

---

### Phase Ordering Rationale

- **Foundation before crawling:** Database schema and embedding infrastructure are dependencies for everything else. Verifying search works with manual test data validates the core retrieval path before adding crawling complexity.

- **Crawling before ingestion:** Crawl produces raw Markdown that ingestion processes. Testing crawl -> Markdown extraction independently simplifies debugging the ingestion pipeline.

- **Ingestion before MCP:** The pipeline must work before MCP tools can expose it. Building MCP layer first leads to mocking and rework.

- **MCP as integration layer:** Phase 4 connects all previous work to Claude Code. It's the last piece that makes everything usable.

- **Source management after core:** CRUD operations for sources are simpler than pipeline work. Get the hard parts (chunking, search, MCP) working first.

- **Quality enhancements post-MVP:** Reranking and advanced features multiply the quality of a working system. Premature optimization before the core works wastes effort.

**Critical path:** Phases 1-4 are the MVP. Phase 5 adds operational completeness. Phase 6 is quality optimization after validation.

### Research Flags

**Phases needing deeper research during planning:**
- **Phase 3 (Ingestion Pipeline):** Markdown AST chunking patterns are complex. flexmark-java API usage for heading extraction and code block preservation needs detailed research. Recommend `/gsd:research-phase`.
- **Phase 6 (Quality Enhancements):** Cross-encoder model selection and reranking integration patterns. Recommend `/gsd:research-phase` when starting Phase 6.

**Phases with standard patterns (skip research-phase):**
- **Phase 1 (Foundation):** LangChain4j, Spring Boot, pgvector all have excellent official documentation. Standard Spring Data JPA patterns.
- **Phase 2 (Crawling):** Crawl4AI API is documented. Spring WebClient is standard.
- **Phase 4 (MCP Integration):** MCP Java SDK and Spring AI MCP starter have clear examples.
- **Phase 5 (Source Management):** Standard Spring Data JPA CRUD patterns.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Versions verified against Maven Central and official repos (Feb 2026). LangChain4j 1.11.0 hybrid search feature confirmed in release notes. Crawl4AI API contract verified in official docs. |
| Features | HIGH | Table-stakes features verified across Context7, kapa.ai, R2R, and RAG ecosystem analysis. Hybrid search as production standard confirmed by multiple sources (Superlinked, Elastic, IBM). MCP tool design validated against Context7 and Merge.dev best practices. |
| Architecture | HIGH | LangChain4j RAG tutorial, MCP Java SDK docs, Crawl4AI self-hosting guide all official sources. Docker Compose patterns standard. Incremental update patterns confirmed in multiple RAG implementations. |
| Pitfalls | HIGH | pgvector HNSW issues verified in GitHub issues and Supabase/Crunchy Data guides. MCP stdio corruption confirmed in multiple sources. ONNX memory behavior documented in official ONNX Runtime docs. Crawl4AI memory leaks tracked in GitHub issues. Virtual thread pinning documented in JEP 491. |

**Overall confidence:** HIGH

The research is well-sourced with official documentation, verified version numbers, and cross-referenced across multiple implementations. The main areas of uncertainty are operational patterns (how the pieces work together at scale), which is expected and will be validated during implementation.

### Gaps to Address

**Flexmark-java chunking API:** Research verified flexmark-java 0.64.8 exists and supports Markdown AST parsing, but specific API usage for heading hierarchy extraction needs validation during Phase 3. Recommend hands-on prototyping before finalizing chunking strategy.

**Crawl4AI memory behavior at scale:** Research confirms memory leaks exist and identifies mitigation strategies (v0.8.x pooling, resource limits), but actual behavior with 100+ page documentation sites needs empirical testing. Plan to monitor `/monitor/health` during early crawls and adjust limits.

**LangChain4j hybrid search API:** Research confirms PgVector hybrid search exists in 1.11.0 (release notes, PR #1633), but exact builder method names and RRF parameter tuning should be verified against actual 1.11.0-beta19 code during Phase 1 implementation.

**Spring AI MCP @Tool annotation with stdio:** Documented for webmvc transport; stdio path may differ. If Spring AI starter proves problematic, fall back to raw MCP SDK (Approach B from stack research). Test early in Phase 4.

**Cross-encoder model selection:** Research identifies reranking as a quality multiplier but doesn't recommend specific models. Defer to Phase 6 research when reranking is prioritized.

## Sources

### Primary (HIGH confidence)
- LangChain4j GitHub Releases v1.11.0 (Feb 4, 2026) — hybrid search, PgVector integration
- LangChain4j official docs — RAG tutorial, Spring Boot integration, in-process ONNX embeddings
- MCP Java SDK GitHub v0.17.2 (Jan 22, 2026) — stdio transport, tool specification
- MCP Specification 2025-11-25 — protocol specification
- Crawl4AI docs v0.8.x — self-hosting guide, API endpoints, CrawlResult structure, Markdown generation
- Spring Boot endoflife.date — version 3.5.10 (Jan 22, 2026), commercial support dates
- pgvector GitHub v0.8.1 — iterative index scans, halfvec support
- PostgreSQL releases — version 17.8
- ONNX Runtime releases — version 1.24.1 (Feb 5, 2026)
- Docker Compose official docs — health checks, startup order
- JEP 491 (OpenJDK) — virtual thread pinning elimination in Java 24

### Secondary (MEDIUM confidence)
- Context7 MCP Server GitHub — tool design patterns, parameter conventions
- Merge.dev MCP tool description best practices — naming, description guidelines
- Superlinked VectorHub — hybrid search and reranking optimization
- kapa.ai documentation — competitor feature set
- R2R Framework GitHub — production RAG framework features
- Databricks chunking guide — chunking best practices for RAG
- Firecrawl chunking strategies 2025 — Markdown-aware splitting patterns
- pgvector GitHub issues (#835, #877, #844) — HNSW index behavior, performance pitfalls
- Crawl4AI GitHub issues (#943, #1256) — memory leaks, browser pool management
- Supabase HNSW guide, Crunchy Data HNSW blog — index tuning
- VersionRAG arxiv 2510.08109 — version-aware retrieval patterns

### Tertiary (LOW confidence, needs validation)
- Spring AI @Tool annotation with stdio — documented for webmvc; stdio path unverified
- LangChain4j SearchMode.HYBRID exact API — referenced in release notes but needs code verification
- flexmark-java 0.64.8 version — confirmed by search but not verified on Maven Central directly
- Crawl4AI memory usage in practice — 4GB limit is documented recommendation; actual usage may vary

---
*Research completed: 2026-02-14*
*Ready for roadmap: yes*
