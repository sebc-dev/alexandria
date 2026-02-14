# Pitfalls Research

**Domain:** Self-hosted RAG documentation system (Java 21 + Spring Boot + pgvector + MCP)
**Researched:** 2026-02-14
**Confidence:** HIGH (most pitfalls verified via official docs, GitHub issues, and multiple sources)

---

## Critical Pitfalls

### Pitfall 1: Naive Chunking Destroys Technical Documentation Structure

**What goes wrong:**
A recursive text splitter with a fixed character/token limit cuts mid-code-block, splits tables across chunks, and orphans headings from their content. The embedding of a half-finished Java method or a table row without its header is semantically meaningless. Retrieval returns gibberish chunks that confuse the LLM, producing hallucinated API signatures or mixing framework versions.

**Why it happens:**
LangChain4j's `DocumentSplitters.recursive()` splits on `\n\n`, then `\n`, then space, then character. It has no awareness of Markdown AST structure: fenced code blocks, tables, heading hierarchies, or admonition blocks. Developers use it out of the box and assume "it works." The failure is silent -- the system returns results, they are just low quality.

**How to avoid:**
1. Parse Markdown to AST using flexmark-java before chunking. Treat code blocks (``` ... ```) and tables as atomic units that must never be split.
2. Split at heading boundaries first (`#`, `##`, `###`), then apply token-limit splitting only within sections.
3. Prepend the heading hierarchy path as metadata to each chunk (e.g., `"Spring Boot > Configuration > application.yml"`), so the chunk retains context even without surrounding text.
4. Use different chunk sizes for different content types: 200-300 tokens for API reference entries, 400-512 tokens for narrative documentation, and keep code blocks intact even if they exceed the target size.
5. Overlap 50-100 tokens across non-code chunks, but never overlap into/out of code blocks.

**Warning signs:**
- Search results that start or end mid-code-block
- Retrieved chunks that contain table rows without column headers
- Chunks that begin with continuation text (no heading, no context)
- Claude Code producing answers that mix details from adjacent documentation sections

**Phase to address:** Phase 1 (MVP). Do not defer this. Even a basic heading-aware split is 10x better than naive recursive splitting. The AST-based approach can be refined later, but the heading boundary rule and code-block preservation must be in from day one.

**Severity:** CRITICAL -- This is the #1 cause of RAG quality failures. 80% of RAG failures trace back to chunking decisions, not retrieval algorithms or embedding models.

---

### Pitfall 2: pgvector HNSW Index Silently Degrades Insert Performance

**What goes wrong:**
With an HNSW index present on the embedding column, insert performance drops approximately 5x compared to unindexed inserts. Bulk ingestion of a new framework's documentation (thousands of chunks) takes minutes instead of seconds. Worse, the HNSW graph maintenance during inserts can trigger `maintenance_work_mem` exhaustion, causing the index build to switch from in-memory to on-disk construction, which is orders of magnitude slower.

**Why it happens:**
Inserting a single vector into an HNSW index triggers cascades of node connections across multiple graph layers. This is inherent to the HNSW algorithm, not a pgvector bug. Developers who test with 100 chunks see fast inserts and assume it scales linearly. It does not.

**How to avoid:**
1. **Load data first, build index after.** For initial bulk ingestion, do NOT create the HNSW index beforehand. Insert all vectors into an unindexed table, then `CREATE INDEX`. This is the officially recommended approach.
2. For incremental updates (adding a new framework), consider dropping the index, inserting, and rebuilding -- but only if you can tolerate brief search unavailability. For a single-user system, this is acceptable.
3. Set `maintenance_work_mem` to at least 1 GB (ideally 2-4 GB for 100K+ vectors) before building HNSW indexes. Ensure Docker's `--shm-size` is at least as large to avoid parallel build failures.
4. Use `max_parallel_maintenance_workers = 3` (out of 4 cores) for index builds. This yields ~3x speedup.
5. Batch inserts in transactions of 500-1000 rows rather than one-by-one.

**Warning signs:**
- Ingestion of a new documentation source takes more than a few seconds per thousand chunks
- PostgreSQL logs showing `hnsw graph no longer fits into maintenance_work_mem`
- Docker container memory usage spikes during index operations
- Ingestion pipeline timing shows INSERT as the bottleneck (not embedding, not crawling)

**Phase to address:** Phase 1 (MVP). Design the ingestion pipeline to build the index after initial data load. Phase 3 (robust ingestion) should add the drop/rebuild strategy for large batch additions.

**Severity:** CRITICAL -- A 5x slowdown compounds with dataset size. At 100K chunks, the difference between a "build index after load" strategy and naive indexed inserts is minutes vs. potentially hours.

---

### Pitfall 3: pgvector Query Planner Ignores HNSW Index (Seq Scan Instead)

**What goes wrong:**
PostgreSQL's query planner chooses a Parallel Sequential Scan + Sort instead of using the HNSW index for KNN queries. Queries that should return in milliseconds take 30-70 seconds. This has been reported specifically with halfvec columns in pgvector 0.8.0 on PostgreSQL 17.x.

**Why it happens:**
Multiple causes documented in pgvector issues:
1. **Distance operator mismatch:** The index uses `halfvec_cosine_ops` but the query uses `<->` (L2 distance) instead of `<=>` (cosine distance). If the operator does not match, PostgreSQL will not use the index. Period.
2. **DESC ordering:** HNSW indexes only work with ascending order queries. Using `ORDER BY similarity DESC` bypasses the index entirely.
3. **Stale statistics:** After bulk inserts, `ANALYZE` was not run, so the planner has incorrect cardinality estimates. The `avg_width` statistics for halfvec columns have been reported as incorrect in some pgvector versions.
4. **Cost estimation quirks:** Low `random_page_cost` or missing `effective_cache_size` tuning can trick the planner.

**How to avoid:**
1. **Always match the distance operator to the index ops class.** If index uses `halfvec_cosine_ops`, query MUST use `<=>`. This is the most common mistake.
2. Always use ascending order (`ORDER BY embedding <=> query_vec ASC LIMIT k`), never descending.
3. Run `ANALYZE document_chunks` after every bulk insert or index rebuild.
4. Test query plans with `EXPLAIN ANALYZE` during development. Never assume the index is being used.
5. Stay on PostgreSQL 16.x for now. The halfvec planner issues are most severe on PG 17.x. If PG 17 is used, monitor for the `avg_width` bug.

**Warning signs:**
- Query latency above 100ms for simple KNN queries
- `EXPLAIN ANALYZE` showing `Parallel Seq Scan` instead of `Index Scan using hnsw`
- Search performance degrading as table grows

**Phase to address:** Phase 1 (MVP). Add an integration test that runs `EXPLAIN ANALYZE` on the search query and asserts `Index Scan` is present. This is a 5-minute test that catches a multi-day debugging session.

**Severity:** CRITICAL -- Silent performance cliff. The system "works" but is 1000x slower than it should be.

---

### Pitfall 4: MCP Server Console Output Corrupts stdio Protocol

**What goes wrong:**
The MCP stdio transport communicates via JSON-RPC 2.0 over the process's stdin/stdout pipes. Any text printed to stdout that is not a valid JSON-RPC message -- Spring Boot banner, log4j output, System.out.println debugging, JVM warnings -- corrupts the protocol stream. Claude Code fails to parse responses, the connection drops, and the MCP server appears "broken" with no useful error message.

**Why it happens:**
Spring Boot defaults to logging to console (stdout). The Spring Boot banner prints on startup. Libraries like Hibernate print SQL to stdout. Developers add `System.out.println` for debugging. All of these corrupt the stdio transport. The failure mode is particularly insidious because the server process starts successfully but the protocol is broken.

**How to avoid:**
1. **Redirect ALL logging to a file.** Configure logback/log4j2 to write to `logs/mcp-server.log`, not to console. No console appender at all.
2. **Disable the Spring Boot banner:** `spring.main.banner-mode=off` in application.properties.
3. **Disable the web server:** `spring.main.web-application-type=none` (for stdio MCP servers, there is no need for a web server).
4. **Never use System.out/System.err.** Use SLF4J exclusively. Consider adding a Checkstyle or ArchUnit rule to enforce this.
5. **Suppress JVM warnings:** The `-XX:+DisableAttachMechanism` and `-Xlog:disable` flags prevent JVM-level stdout output.
6. **Test the raw stdout:** Pipe the MCP server's stdout through `jq` during development to verify every line is valid JSON. Any non-JSON line means corruption.

**Warning signs:**
- MCP server starts but Claude Code reports "Failed to connect" or hangs
- MCP tool calls return empty results or timeout
- Adding a new library causes MCP to stop working (the library logs to stdout)
- Works in IDE but fails when launched via `.mcp.json`

**Phase to address:** Phase 2 (MCP integration). This must be the very first thing configured. A single rogue println can waste hours of debugging.

**Severity:** CRITICAL -- Complete functionality loss with zero useful diagnostics. The most common MCP stdio pitfall.

---

### Pitfall 5: ONNX Runtime Native Memory Escapes JVM Metrics and Docker Limits

**What goes wrong:**
The ONNX Runtime model loaded via LangChain4j's in-process embedding allocates memory in native (off-heap) space. The JVM heap metrics (`-Xmx`) do not account for this memory. Docker's memory limit applies to the entire process (heap + native + metaspace + thread stacks), but standard JVM monitoring (Actuator, VisualVM heap charts) shows plenty of free memory while the container is approaching its limit. The container gets OOM-killed (exit code 137) with no warning in application logs.

**Why it happens:**
ONNX Runtime uses native C++ allocators for model weights and inference buffers. bge-small-en-v1.5-q loads ~65 MB of native memory for the model, plus inference buffers that scale with batch size. The JVM's `-Xmx` only controls Java heap. Docker sees total process RSS. The gap between "JVM thinks it has X free" and "Docker thinks the process uses Y" is the native allocation.

Additionally, ONNX Runtime has documented memory leak issues when OrtSession or OrtEnvironment objects are repeatedly created/closed. LangChain4j's pre-packaged models create a singleton, but custom ONNX model usage can trigger this.

**How to avoid:**
1. **Size Docker memory limit = JVM heap + 500 MB headroom.** For `-Xmx1g`, set Docker `mem_limit: 2g`. The extra 1 GB covers native memory (ONNX model ~100 MB, metaspace ~100 MB, thread stacks ~200 MB, OS overhead).
2. **Enable NativeMemoryTracking:** Add `-XX:NativeMemoryTracking=summary` to JVM args. Periodically check with `jcmd <pid> VM.native_memory summary`.
3. **Never repeatedly create/destroy OrtSession.** Use LangChain4j's singleton embedding model. If using a custom ONNX model, initialize once at startup and hold the reference for the application's lifetime.
4. **Set `-XX:MaxDirectMemorySize`** to cap NIO direct buffers used by some ONNX operations.
5. Monitor container memory via `docker stats` during load testing, not just JVM metrics.

**Warning signs:**
- Container restarts with exit code 137 (OOM-killed)
- JVM heap usage looks fine but container memory is at limit
- Memory usage climbs during batch embedding operations and does not return to baseline
- `dmesg | grep -i oom` on the host shows kills

**Phase to address:** Phase 1 (MVP). Set the Docker memory limit correctly from the start. Add NativeMemoryTracking in Phase 3 (robust pipeline) when batch processing stress-tests the limits.

**Severity:** CRITICAL -- OOM kills with no application-level diagnostics. The system crashes without any log entry explaining why.

---

### Pitfall 6: Crawl4AI Chromium Processes Leak Memory in Docker

**What goes wrong:**
Crawl4AI's Docker container runs headless Chromium instances for JavaScript rendering. After processing ~150 URLs, orphaned Chrome processes accumulate, consuming all available container memory. The container either OOM-kills or becomes unresponsive. The `/health` endpoint may still return 200 even when the container is effectively dead.

**Why it happens:**
Browser contexts are not properly closed after crawl completion in high-throughput scenarios. Each unclosed context retains ~180-270 MB of memory (depending on pool tier). A permanent browser instance uses ~270 MB, hot pool instances ~180 MB each. Without explicit cleanup, these accumulate.

Additionally, Crawl4AI's monitoring system uses host-level memory metrics rather than container-specific limits, so the janitor cleanup triggers are miscalibrated when running inside Docker with memory constraints.

**How to avoid:**
1. **Use Crawl4AI v0.8.x or later.** Version 0.7.7 introduced smart browser pooling (3-tier: permanent/hot/cold) with automatic janitor cleanup. Earlier versions have severe resource leaks.
2. **Set aggressive resource limits in Docker:**
   ```yaml
   crawl4ai:
     image: unclecode/crawl4ai:latest
     mem_limit: 4g
     shm_size: 1g
     environment:
       - PLAYWRIGHT_MAX_CONCURRENCY=2  # Low for 4-core CPU
   ```
3. **Limit max pages:** Configure `crawler.pool.max_pages` to a conservative value (10-20 for a 4-core/24 GB system).
4. **Add rate limiting between crawl requests:** Use `mean_delay` and `max_range` parameters to space out requests, giving the janitor time to clean up.
5. **Implement a circuit breaker in the Java app:** If Crawl4AI's `/monitor/health` shows memory above 80%, pause crawl submissions until it recovers.
6. **Consider restarting the Crawl4AI container between large ingestion jobs** as a pragmatic safety valve.

**Warning signs:**
- Crawl4AI container memory climbing steadily during ingestion
- `/monitor/browsers` showing growing number of browser instances
- Crawl latency increasing over time within a single ingestion run
- Container health check passes but crawl requests timeout

**Phase to address:** Phase 3 (robust ingestion pipeline). During MVP, crawl only a few documentation sites manually. The memory leak becomes critical only with automated, high-volume crawling.

**Severity:** CRITICAL -- Container crash during long-running ingestion jobs. Data loss if crawl progress is not checkpointed.

---

## High Pitfalls

### Pitfall 7: Embedding Model Change Requires Full Reindexation

**What goes wrong:**
Switching from bge-small-en-v1.5 (384 dimensions) to bge-base-en-v1.5 (768 dimensions) or nomic-embed-text-v1.5 (768 dimensions) means every existing vector in the database is incompatible. You cannot mix vectors from different models in the same column. The entire table must be re-embedded and the HNSW index rebuilt from scratch. For 100K chunks at ~100 embeddings/sec on CPU, this is a 15-20 minute downtime. For 1M chunks, it is 2-3 hours.

**Why it happens:**
Different embedding models produce vectors in different semantic spaces. Even two models with the same dimensionality (e.g., 384d) produce incompatible vectors because they were trained differently. There is no "translation layer" that works reliably in practice (adapter approaches recover 95-99% recall at best, and add complexity a solo developer should avoid).

**How to avoid:**
1. **Commit to the initial model for at minimum the first 3 months.** bge-small-en-v1.5-q is good enough. Do not upgrade "just to try it."
2. **Store the model identifier in a metadata table.** When the app starts, verify the stored model matches the configured model. Refuse to start if there is a mismatch, with a clear error message and instructions.
3. **Design the reindexation as an explicit migration command**, not an automatic process. Something like `./alexandria reindex --model=bge-base-en-v1.5 --confirm`.
4. **Track chunk text content hashes.** During reindexation, skip re-embedding chunks whose text has not changed since last embed with the current model. (The text is the same; only the model changed, so all must be re-embedded. But the content hash lets you skip re-chunking and re-crawling.)
5. If the system ever needs to support A/B testing old vs. new embeddings, use separate tables/columns rather than mixing models.

**Warning signs:**
- Retrieval quality stagnates despite good chunks (temptation to "just try a bigger model")
- Schema changes to the embedding column dimension
- Accidental model change in config file causing startup failures or corrupted search

**Phase to address:** Phase 1 (schema design). Store model metadata in the database from day one. Phase 4 (quality optimization) is when model upgrades might actually be considered.

**Severity:** HIGH -- Hours of downtime and CPU time. Not a bug, but a planned event that must be treated as a migration, not a config change.

---

### Pitfall 8: Docker-Compose Startup Order Race Conditions

**What goes wrong:**
The Java application starts before PostgreSQL is ready to accept connections, or before pgvector extension is created. The app throws `PSQLException: Connection refused` or `ERROR: type "halfvec" does not exist` and crashes. With `restart: unless-stopped`, it enters a crash loop consuming CPU and filling logs.

Separately, the Java app may try to call Crawl4AI before its Chromium browser pool is initialized (~10-30 seconds after container start), getting connection refused or timeout errors.

**Why it happens:**
Docker Compose `depends_on` without a `condition` only waits for the container to start, not for the service inside to be ready. PostgreSQL needs time to initialize WAL, run recovery, and accept connections. The pgvector extension must be created via `CREATE EXTENSION IF NOT EXISTS vector` before the schema can use `halfvec` types.

**How to avoid:**
1. **Use health checks with `condition: service_healthy`:**
   ```yaml
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
       image: unclecode/crawl4ai:latest
       healthcheck:
         test: ["CMD", "curl", "-f", "http://localhost:11235/"]
         interval: 10s
         timeout: 5s
         retries: 5
         start_period: 30s  # Chromium pool needs time

     app:
       depends_on:
         postgres:
           condition: service_healthy
         crawl4ai:
           condition: service_healthy
   ```
2. **Create the pgvector extension in an init script**, not in the application. Mount a `init.sql` file:
   ```yaml
   postgres:
     volumes:
       - ./init.sql:/docker-entrypoint-initdb.d/init.sql
   ```
   With `init.sql` containing `CREATE EXTENSION IF NOT EXISTS vector;`.
3. **Add connection retry logic in the Java app** as defense-in-depth. Spring Boot's HikariCP has `connectionTimeout` and `initializationFailTimeout` settings, but it does not retry failed initial connections by default. Add `spring.datasource.hikari.initializationFailTimeout=0` to let HikariCP start without an immediate connection, then retry.
4. **Set `start_period` generously for Crawl4AI** (at least 30s). Its Chromium download and browser pool initialization takes 10-30 seconds on first start.

**Warning signs:**
- Application crash loops in Docker logs on first `docker-compose up`
- `PSQLException: Connection refused` in startup logs
- Intermittent startup failures that "fix themselves" on retry
- Crawl4AI calls failing only immediately after `docker-compose up`

**Phase to address:** Phase 1 (MVP). The docker-compose.yml health checks must be correct from the first commit. This is 20 minutes of work that prevents hours of frustration.

**Severity:** HIGH -- Blocks development on every `docker-compose up` if misconfigured. Wastes time on "works sometimes" debugging.

---

### Pitfall 9: Virtual Thread Pinning from synchronized Blocks in Dependencies

**What goes wrong:**
Virtual threads get "pinned" to their carrier (platform) threads when they enter a `synchronized` block or call native code. With only 4 carrier threads (matching 4 CPU cores), pinning 4 virtual threads simultaneously blocks all other virtual threads from running. The application appears hung or extremely slow despite low CPU utilization.

**Why it happens:**
On Java 21, `synchronized` blocks prevent virtual thread unmounting. This is fixed in Java 24 (JEP 491), but ONNX Runtime native calls remain pinning even on Java 24+. Key pinning sources in this stack:
- **ONNX Runtime inference:** Every embedding call is a native JNI call. The virtual thread is pinned for the duration of the inference (~5-50ms per batch). This is unavoidable.
- **JDBC drivers:** Older PostgreSQL JDBC driver versions use `synchronized` internally.
- **HikariCP connection pool:** Uses `synchronized` for connection acquisition.
- **Third-party libraries:** Any library using `synchronized` internally.

**How to avoid:**
1. **Do not run ONNX embedding on virtual threads.** Use a fixed-size `Executors.newFixedThreadPool(4)` (matching CPU cores) for embedding work. Virtual threads gain nothing for CPU-bound ONNX inference and cause pinning.
2. **Use virtual threads for I/O:** Crawling, database writes, Crawl4AI HTTP calls -- these are I/O-bound and benefit from virtual threads.
3. **Detect pinning early:** Start the JVM with `-Djdk.tracePinnedThreads=short` during development. This prints stack traces when virtual threads are pinned for blocking operations.
4. **Use PostgreSQL JDBC 42.7.x+** which has better virtual thread compatibility (reduced `synchronized` usage).
5. **Consider upgrading to Java 24** when stable, which eliminates `synchronized` pinning (JEP 491). Native call pinning (ONNX) remains regardless.
6. **Replace `synchronized` with `ReentrantLock`** in any custom code that runs on virtual threads.

**Warning signs:**
- Application throughput plateaus despite low CPU utilization
- `-Djdk.tracePinnedThreads=short` produces frequent output
- JDK Flight Recorder shows `jdk.VirtualThreadPinned` events >20ms
- Crawling/ingestion is much slower than expected with virtual threads vs. platform threads

**Phase to address:** Phase 1 (MVP). Separate the thread pools from the start: fixed pool for embedding, virtual threads for I/O. Retrofitting later requires restructuring the entire pipeline.

**Severity:** HIGH -- Silent throughput ceiling. System "works" but at a fraction of its potential, and the cause is non-obvious.

---

### Pitfall 10: MCP Tool Response Token Bloat Degrades Claude Code Performance

**What goes wrong:**
MCP tool responses that return too much text consume Claude Code's context window. Each tool call's response is injected into the conversation context. Returning 10 chunks of 500 tokens each (5,000 tokens per search) across 3-4 tool calls in a session consumes 15-20K tokens of context just on RAG results, leaving less room for Claude to reason. Additionally, Claude Code collapses long tool outputs to ~700 characters in the terminal display (expandable with Ctrl+R), making debugging difficult.

Separately, each MCP tool's schema (name, description, parameter JSON schema) is loaded into Claude Code's context at session start. A server with verbose tool descriptions and many parameters wastes tokens before any work begins.

**Why it happens:**
Developers default to returning "as much context as possible" to help the LLM. But RAG tool responses should be concise and relevant, not comprehensive. Additionally, Claude Code's context window has practical limits, and MCP tool overhead can consume 10%+ of available context with just tool definitions.

**How to avoid:**
1. **Cap responses at 1,500-3,000 tokens per tool call** (3-5 chunks maximum). Quality of selection matters more than quantity.
2. **Return structured, formatted results** -- title, source URL, content snippet, relevance score. Not raw document text.
3. **Keep tool descriptions concise.** Short name, one-sentence description, minimal parameter schemas. Aim for <500 tokens total across all 3 tools.
4. **Use the `MAX_MCP_OUTPUT_TOKENS` environment variable** in Claude Code if you need to increase the display limit for debugging.
5. **Return JSON-formatted responses** rather than plain text. Claude Code renders these more completely.
6. **Limit the MCP server to 3-5 tools maximum.** Claude Code's tool search optimization kicks in at ~10% context usage, but fewer tools means less overhead regardless.

**Warning signs:**
- Claude Code responses becoming shorter or less detailed as conversation progresses (context window filling up)
- Tool results appearing truncated in terminal (normal -- use Ctrl+R to expand)
- Claude Code suggesting "let me search again" for information already retrieved (context eviction)
- Session token usage climbing rapidly

**Phase to address:** Phase 2 (MCP integration). Design the response format and token budget from the start. Much harder to reduce response size later without changing the MCP API contract.

**Severity:** HIGH -- Degrades Claude Code effectiveness gradually. Not a crash, but a death by a thousand cuts to session quality.

---

## Technical Debt Patterns

Shortcuts that seem reasonable but create long-term problems.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Skip Markdown AST parsing, use recursive split only | Ship MVP faster, avoid flexmark-java dependency | Poor retrieval quality for code-heavy docs, eventually requires rewrite of chunking layer | Never for technical documentation. Even a minimal heading-boundary split is worth the effort. |
| Store raw HTML instead of cleaned Markdown | Skip the extraction/cleaning step | Embeddings polluted with nav bars, footers, script tags. Retrieval quality drops. Storage bloat. | Acceptable for prototyping with 1-2 sources, but must be replaced before adding more. |
| No metadata enrichment (framework, version, content type) | Simpler schema, faster ingestion | Cannot filter searches by framework or version. Cross-framework result pollution. | MVP with a single framework only. Must add before second framework. |
| Single `docker-compose.yml` without profiles | Simpler config | Cannot run just DB for development, or just app for testing. All-or-nothing startup. | Always acceptable for solo dev. Use `profiles` only if it saves time. |
| Hardcode embedding model in code instead of config | One less config file | Model change requires code change, rebuild, redeployment. Blocks A/B comparison. | Acceptable for MVP. Extract to config before Phase 4 (quality optimization). |
| Skip EXPLAIN ANALYZE integration test | Faster test suite | HNSW index regression goes undetected. Seq scan kills production performance. | Never. This is a 10-line test that catches a critical pitfall. |

## Integration Gotchas

Common mistakes when connecting components.

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Java app to Crawl4AI | Using `localhost` as Crawl4AI URL from inside the Java container | Use the Docker Compose service name: `http://crawl4ai:11235`. `localhost` inside a container refers to the container itself. |
| Java app to PostgreSQL | Using `localhost:5432` as JDBC URL | Use `jdbc:postgresql://postgres:5432/alexandria` (service name). Or use Docker Compose `network_mode: host` (not recommended). |
| Crawl4AI health check | Using `wget` in healthcheck when only `curl` is installed in the container | Check what tools are available in the Crawl4AI image. Use `curl -f http://localhost:11235/` which is documented. |
| pgvector extension | Creating extension in application code (Flyway/Liquibase migration) | Create in `docker-entrypoint-initdb.d/init.sql`. Extension creation requires superuser; app user may not have permission. |
| MCP server jar path | Hardcoding absolute path in `.mcp.json` | Use relative path from project root or use environment variables. Absolute paths break on other machines. |
| Crawl4AI shared memory | Not setting `shm_size` in Docker Compose | Chromium requires shared memory for rendering. Without `shm_size: 1g`, pages may crash or render incorrectly. Default Docker shm is only 64 MB. |
| PostgreSQL shared_buffers | Using Docker default (128 MB) | Set `shared_buffers = 1GB` (or 25% of container memory). Default 128 MB is too low for HNSW index performance. HNSW indexes must fit in shared buffers for fast queries. |

## Performance Traps

Patterns that work at small scale but fail as usage grows.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Embedding one chunk at a time | Works fine for 100 chunks | Batch embeddings in groups of 32-64. LangChain4j's `EmbeddingModel.embedAll()` processes batches. | >1,000 chunks: 10x slower than batched |
| HNSW default parameters (m=16, ef_construction=64) | Fast index build, decent recall | Fine for <100K vectors. Increase `ef_construction=128` for better recall at >100K scale. Leave `m=16`. | >100K vectors: recall drops below 95% |
| No connection pooling for Crawl4AI HTTP calls | Works for sequential crawling | Use an HTTP client with connection pooling (Spring WebClient or OkHttp). Creating a new connection per request adds 50-100ms each. | >50 concurrent crawl requests |
| Storing full document content in chunk metadata JSONB | Convenient for debugging | JSONB bloats table size. Slows VACUUM and backups. Store content in `TEXT` column, keep JSONB lean. | >50K chunks: table size 3-5x larger than necessary |
| No pagination in MCP list_frameworks tool | Returns all frameworks instantly | As framework count grows, response size grows linearly. Add pagination or summary mode early. | >20 frameworks: response exceeds token budget |

## Security Mistakes

Domain-specific security issues.

| Mistake | Risk | Prevention |
|---------|------|------------|
| Crawl4AI container with network access to internal services | Crawled pages could trigger SSRF via JavaScript (Chromium renders arbitrary JS) | Run Crawl4AI in an isolated Docker network. Only allow outbound HTTP/HTTPS. Block access to internal Docker network services. |
| Storing crawled documentation without sanitization | XSS payloads in documentation could be stored and returned via MCP | Sanitize HTML before storing. Use text-only extraction (Markdown), not raw HTML. |
| MCP server exposes database connection details | Error messages may leak JDBC URLs, credentials | Configure Spring Boot `server.error.include-message=never`. Catch all exceptions in MCP tool handlers. |
| PostgreSQL exposed on host port 5432 | Direct database access from the network | Only expose PostgreSQL port in `docker-compose.yml` if needed for development. In production, remove `ports:` mapping entirely. |
| Crawl4AI API exposed without authentication | Anyone on the network can submit crawl jobs | Bind Crawl4AI only to the Docker internal network. Do not publish its port to the host unless needed for debugging. |

## UX Pitfalls

Common user experience mistakes in the MCP/Claude Code integration domain.

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Returning chunk content without source URL | User cannot verify or navigate to original documentation | Always include source URL and section path in every search result. |
| Search results without relevance scoring | User cannot tell if results are good matches or scraping the barrel | Include cosine similarity score. Let the user (Claude) decide if 0.3 similarity is worth citing. |
| Tool names that are unclear (`query`, `find`, `get`) | Claude Code may call the wrong tool or not know which to use | Use descriptive names: `search_documentation`, `list_indexed_frameworks`, `find_code_examples`. |
| No "no results" indicator | Claude Code assumes empty response means error, retries | Return explicit "No results found for query X in framework Y" message. Suggest alternative queries. |
| Returning results from all frameworks when user is working on one | Noise: Spring Boot results when the user is coding React | Support framework filter parameter. If Claude Code knows the current project stack, it can filter automatically. |

## "Looks Done But Isn't" Checklist

Things that appear complete but are missing critical pieces.

- [ ] **Chunking pipeline:** Often missing code block preservation -- verify that a chunk never starts or ends mid-fenced-code-block
- [ ] **pgvector search:** Often missing EXPLAIN ANALYZE validation -- verify the HNSW index is actually used, not a seq scan
- [ ] **Docker memory:** Often missing native memory accounting -- verify container `mem_limit` covers JVM heap + ONNX native + headroom
- [ ] **MCP stdio:** Often missing log redirection -- verify zero non-JSON output on stdout by piping through `jq`
- [ ] **Crawl4AI integration:** Often missing `shm_size` configuration -- verify Chromium rendering works on complex pages, not just simple HTML
- [ ] **Health checks:** Often missing `start_period` -- verify the app does not crash-loop on slow cold starts
- [ ] **Incremental updates:** Often missing content hash comparison -- verify that unchanged documents are not re-embedded on every sync
- [ ] **Search quality:** Often missing evaluation -- verify search returns relevant results for 10-20 manual test queries before declaring "done"
- [ ] **pgvector schema:** Often missing distance operator match -- verify the query operator (`<=>` for cosine) matches the index ops class
- [ ] **MCP response format:** Often missing source attribution -- verify every returned chunk includes URL and framework/version metadata

## Recovery Strategies

When pitfalls occur despite prevention, how to recover.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Naive chunking deployed, poor retrieval quality | MEDIUM (hours) | Re-run chunking pipeline with AST-aware splitter. Re-embed all chunks. No data loss, but requires full reprocessing. |
| HNSW index not being used (seq scan) | LOW (minutes) | Run `ANALYZE`. Check distance operator match. Fix query. Verify with `EXPLAIN ANALYZE`. |
| MCP stdout corruption | LOW (minutes) | Redirect all logging to file. Disable banner. Restart MCP server. |
| ONNX OOM kill in Docker | LOW (minutes) | Increase `mem_limit` in docker-compose.yml. Add NativeMemoryTracking. Restart. |
| Crawl4AI memory leak during ingestion | MEDIUM (hours) | Restart container. Implement checkpoint/resume in crawl pipeline so partially completed jobs can resume. |
| Embedding model accidentally changed | HIGH (hours) | Full reindexation required. No shortcut. Restore previous model config to unblock, then plan migration. |
| Docker startup race condition | LOW (minutes) | Add health checks and `condition: service_healthy`. Restart stack. |
| Virtual thread pinning causing slowdown | MEDIUM (hours) | Separate thread pools: fixed pool for CPU work (embedding), virtual threads for I/O. Requires pipeline restructuring. |

## Pitfall-to-Phase Mapping

How roadmap phases should address these pitfalls.

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Naive chunking (#1) | Phase 1: MVP | Integration test: no chunk starts/ends mid-code-block. Manual review of 20 sample chunks. |
| HNSW insert slowness (#2) | Phase 1: MVP | Ingestion benchmark: time to insert 10K chunks with and without index. |
| Query planner ignores index (#3) | Phase 1: MVP | Integration test: `EXPLAIN ANALYZE` asserts Index Scan on search query. |
| MCP stdout corruption (#4) | Phase 2: MCP | Startup test: pipe MCP server stdout through `jq --slurp` for 5 seconds, verify all lines parse. |
| ONNX native memory (#5) | Phase 1: MVP | Docker `mem_limit` set. `docker stats` monitored during 1K-chunk ingestion test. |
| Crawl4AI memory leak (#6) | Phase 3: Ingestion | Monitor `/monitor/health` during 500-URL crawl. Memory stays below 80% limit. |
| Embedding model change (#7) | Phase 1: Schema | Model identifier stored in DB. App refuses to start on mismatch. |
| Docker startup order (#8) | Phase 1: MVP | `docker-compose down && docker-compose up` succeeds on first try, 10/10 times. |
| Virtual thread pinning (#9) | Phase 1: MVP | `-Djdk.tracePinnedThreads=short` produces zero output during normal operation (I/O paths). Embedding uses fixed pool. |
| MCP token bloat (#10) | Phase 2: MCP | Measure total tokens per search_docs call. Must be <3,000. |

## Sources

### pgvector
- [pgvector HNSW Index Not Used with halfvec - Issue #835](https://github.com/pgvector/pgvector/issues/835) -- MEDIUM confidence
- [Slow Inserts with HNSW Index - Issue #877](https://github.com/pgvector/pgvector/issues/877) -- HIGH confidence
- [HNSW Memory Estimation - Issue #844](https://github.com/pgvector/pgvector/issues/844) -- MEDIUM confidence
- [Supabase HNSW Index Guide](https://supabase.com/docs/guides/ai/vector-indexes/hnsw-indexes) -- HIGH confidence
- [Crunchy Data HNSW Blog](https://www.crunchydata.com/blog/hnsw-indexes-with-postgres-and-pgvector) -- HIGH confidence

### MCP / Claude Code
- [Truncated MCP Tool Responses - Issue #2638](https://github.com/anthropics/claude-code/issues/2638) -- HIGH confidence
- [MCP Context Bloat - Issue #3406](https://github.com/anthropics/claude-code/issues/3406) -- HIGH confidence
- [Claude Code MCP Docs](https://code.claude.com/docs/en/mcp) -- HIGH confidence
- [MCP Java SDK Repository](https://github.com/modelcontextprotocol/java-sdk) -- HIGH confidence
- [Spring Boot MCP stdio logging pitfall](https://medium.com/@saphynogenov/mcp-server-with-spring-ai-d38639e6391a) -- MEDIUM confidence

### Crawl4AI
- [Crawl4AI Self-Hosting Guide](https://docs.crawl4ai.com/core/self-hosting/) -- HIGH confidence
- [Chrome Process Leak - Issue #943](https://github.com/unclecode/crawl4ai/issues/943) -- HIGH confidence
- [Memory Leak on Repeated Requests - Issue #1256](https://github.com/unclecode/crawl4ai/issues/1256) -- HIGH confidence
- [Crawl4AI API Parameters](https://docs.crawl4ai.com/api/parameters/) -- HIGH confidence

### Java / ONNX Runtime
- [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491) -- HIGH confidence (official JDK)
- [Virtual Thread Pinning Detection](https://www.theserverside.com/tip/How-to-solve-the-pinning-problem-in-Java-virtual-threads) -- MEDIUM confidence
- [ONNX Runtime Memory Consumption Docs](https://onnxruntime.ai/docs/performance/tune-performance/memory.html) -- HIGH confidence
- [ONNX Runtime Memory Leak - Issue #6058](https://github.com/microsoft/onnxruntime/issues/6058) -- MEDIUM confidence
- [Java Container OOM Causes](https://dzone.com/articles/root-causes-of-OOM-issues-in-Java-containers) -- MEDIUM confidence

### RAG Chunking
- [Firecrawl Chunking Strategies 2025](https://www.firecrawl.dev/blog/best-chunking-strategies-rag-2025) -- MEDIUM confidence
- [Databricks Chunking Guide](https://community.databricks.com/t5/technical-blog/the-ultimate-guide-to-chunking-strategies-for-rag-applications/ba-p/113089) -- MEDIUM confidence
- [Weaviate Chunking Strategies](https://weaviate.io/blog/chunking-strategies-for-rag) -- MEDIUM confidence

### Docker Compose
- [Docker Compose Startup Order - Official Docs](https://docs.docker.com/compose/how-tos/startup-order/) -- HIGH confidence
- [Docker Compose Health Checks Guide](https://oneuptime.com/blog/post/2026-01-16-docker-compose-depends-on-healthcheck/view) -- MEDIUM confidence

### Embedding Migration
- [Vector Database Migration Lessons](https://nimblewasps.medium.com/vector-database-migration-and-implementation-lessons-from-20-enterprise-deployments-027f09f7daa3) -- LOW confidence (single source, enterprise-focused)

---
*Pitfalls research for: Alexandria -- Self-hosted RAG documentation system*
*Researched: 2026-02-14*
