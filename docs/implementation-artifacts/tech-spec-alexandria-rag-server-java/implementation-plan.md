# Implementation Plan

## Tasks

### Phase 1: Project Foundation

- [ ] **Task 1: Initialize Maven project with BOM dependencies**
  - File: `pom.xml`
  - Action: Create Maven project with Spring Boot 3.5.9 parent, Java 25, BOMs (Spring AI 1.1.2, Langchain4j 1.10.0, Resilience4j 2.3.0), and all dependencies from tech-spec
  - Notes: Use exact versions from dependencies.md. Testcontainers 2.x requires `testcontainers-postgresql` artifact (not `postgresql`)

- [ ] **Task 2: Create main application class**
  - File: `src/main/java/dev/alexandria/AlexandriaApplication.java`
  - Action: Standard Spring Boot main class with `@SpringBootApplication`
  - Notes: No special configuration needed - Virtual Threads enabled via properties

- [ ] **Task 3: Create base application.yml**
  - File: `src/main/resources/application.yml`
  - Action: Configure Spring Boot, Resilience4j retry instances, RAG properties, timeout budgets, MCP server
  - Notes: Include all properties from tech-spec: `rag.*`, `alexandria.timeouts.*`, `resilience4j.retry.*`, `spring.threads.virtual.enabled=true`
  - **F1/F11/F12 Remediations:**
    - Add `spring.datasource.hikari.auto-commit: false` (transaction safety)
    - Add `spring.datasource.hikari.connection-init-sql` with HNSW config:
      ```yaml
      connection-init-sql: |
        SET hnsw.ef_search = 100;
        SET hnsw.iterative_scan = relaxed_order;
      ```
    - Add graceful shutdown:
      ```yaml
      server:
        shutdown: graceful
      spring:
        lifecycle:
          timeout-per-shutdown-phase: 30s
      ```

- [ ] **Task 3b: Create logback-spring.xml** *(NEW - F5 Remediation)*
  - File: `src/main/resources/logback-spring.xml`
  - Action: Create minimal logback config with correlationId in log pattern
  - Pattern: `%d{HH:mm:ss.SSS} %5p [%X{correlationId:-NO_CID}] %-40.40logger{39} : %m%n`
  - Notes: Required for AC 12 (correlationId in logs). No ECS format needed for MVP

### Phase 2: Exception Hierarchy

- [ ] **Task 4: Create ErrorCategory enum**
  - File: `src/main/java/dev/alexandria/core/ErrorCategory.java`
  - Action: Enum with 6 categories (VALIDATION, NOT_FOUND, SERVICE_UNAVAILABLE, INGESTION_FAILED, DATABASE_ERROR, TIMEOUT) with title and suggestedAction fields
  - Notes: Copy exact implementation from error-handling research

- [ ] **Task 5: Create AlexandriaException base class**
  - File: `src/main/java/dev/alexandria/core/AlexandriaException.java`
  - Action: RuntimeException with ErrorCategory field, two constructors (with/without cause)
  - Notes: All domain exceptions extend this

- [ ] **Task 6: Create specialized exceptions**
  - Files: `src/main/java/dev/alexandria/core/` - DocumentNotFoundException.java, QueryValidationException.java, EmbeddingServiceException.java, IngestionException.java
  - Action: Create 4 exception classes extending AlexandriaException with appropriate ErrorCategory
  - Notes: Each exception pre-sets its category

### Phase 3: Core Entities

- [ ] **Task 7: Create ChunkMetadata record**
  - File: `src/main/java/dev/alexandria/core/ChunkMetadata.java`
  - Action: Record with 10 fields (sourceUri, documentHash, chunkIndex, breadcrumbs, documentTitle, contentHash, createdAt, documentType, **fileSize**, **fileModifiedAt**) + static helper methods (toLogicalUri, computeHash)
  - Notes: computeHash uses NFKC normalization + SHA-256. Langchain4j metadata supports primitives only
  - **F4 Remediation:** Added `fileSize` (long, bytes) and `fileModifiedAt` (long, epoch millis) for two-phase change detection fast path (mtime+size check before SHA-256 hash)

- [ ] **Task 8: Create QueryValidator**
  - File: `src/main/java/dev/alexandria/core/QueryValidator.java`
  - Action: Validation class with MIN_CHARS=3, MIN_MEANINGFUL_TOKENS=2, STOPWORDS set (EN + FR). Returns ValidationResult record with QueryProblem enum
  - Notes: Prevents wasted embedding calls on malformed queries
  - **F16 Remediation:** Include French stopwords alongside English:
    ```java
    private static final Set<String> STOPWORDS_FR = Set.of(
        "le", "la", "les", "un", "une", "des", "du", "de", "à", "au", "aux",
        "et", "ou", "où", "que", "qui", "quoi", "ce", "cette", "ces", "mon",
        "ton", "son", "notre", "votre", "leur", "je", "tu", "il", "elle",
        "nous", "vous", "ils", "elles", "est", "sont", "a", "ont", "pour",
        "dans", "sur", "avec", "par", "en", "ne", "pas", "plus", "moins"
    );
    ```

- [ ] **Task 9: Create McpSearchResponse**
  - File: `src/main/java/dev/alexandria/core/McpSearchResponse.java`
  - Action: Record with results list + SearchMetadata. Include nested records: SearchResult, SearchMetadata. Enums: SearchStatus, RelevanceLevel. Factory methods: success, partial, noResults, error
  - Notes: This is the contract for all search responses

### Phase 4: Configuration Classes

- [ ] **Task 10: Create RagProperties**
  - File: `src/main/java/dev/alexandria/config/RagProperties.java`
  - Action: @ConfigurationProperties(prefix="rag") with nested RetrievalConfig (topKInitial, topNFinal, minScore, thresholdType, relativeThresholdRatio, minResultsGuarantee) and RerankingConfig (enabled, model, normalizeScores)
  - Notes: Include Jakarta validation annotations (@Min, @Max, @DecimalMin, @DecimalMax)

- [ ] **Task 11: Create TimeoutProperties**
  - File: `src/main/java/dev/alexandria/config/TimeoutProperties.java`
  - Action: @ConfigurationProperties(prefix="alexandria.timeouts") with Duration fields: globalColdStart, globalWarm, embedding, reranking, database, assembly. Also String fields for base URLs
  - Notes: Use Duration type for timeouts

- [ ] **Task 12: Create HttpClientConfig**
  - File: `src/main/java/dev/alexandria/config/HttpClientConfig.java`
  - Action: @Configuration with two RestClient beans (embeddingRestClient, rerankRestClient) using JdkClientHttpRequestFactory with dedicated timeouts
  - Notes: JDK HttpClient for Virtual Threads compatibility. setReadTimeout available since Spring 6.1

- [ ] **Task 13: Create ResilienceConfig**
  - File: `src/main/java/dev/alexandria/config/ResilienceConfig.java`
  - Action: @Configuration with RetryConfigCustomizer bean for "infinityApi" using IntervalFunction.ofExponentialRandomBackoff(1s, 2.0, 0.1)
  - Notes: Programmatic config required - YAML enableExponentialBackoff + enableRandomizedWait causes IllegalStateException
  - **F2 Remediation (SHOULD HAVE):** Add RateLimiter and Bulkhead for Infinity API protection:
    ```java
    @Bean
    public RateLimiterConfigCustomizer infinityRateLimiter() {
        return RateLimiterConfigCustomizer.of("infinityApi", builder ->
            builder.limitForPeriod(10)
                   .limitRefreshPeriod(Duration.ofSeconds(1))
                   .timeoutDuration(Duration.ofSeconds(5))
        );
    }

    @Bean
    public BulkheadConfigCustomizer infinityBulkhead() {
        return BulkheadConfigCustomizer.of("infinityApi", builder ->
            builder.maxConcurrentCalls(5)
                   .maxWaitDuration(Duration.ofSeconds(10))
        );
    }
    ```

- [ ] **Task 14: Create LangchainConfig**
  - File: `src/main/java/dev/alexandria/config/LangchainConfig.java`
  - Action: @Configuration with beans: Tokenizer (OpenAiTokenizer "gpt-4o-mini"), AlexandriaMarkdownSplitter, EmbeddingModel (OpenAiEmbeddingModel with custom baseUrl), PgVectorEmbeddingStore
  - Notes: EmbeddingModel uses langchain4j-open-ai with Infinity baseUrl. PgVectorEmbeddingStore uses default column names

- [ ] **Task 15: Create McpConfig**
  - File: `src/main/java/dev/alexandria/config/McpConfig.java`
  - Action: @Configuration for MCP server - Spring AI auto-configuration handles most via spring-ai-starter-mcp-server-webmvc
  - Notes: HTTP Streamable transport on /mcp endpoint. Minimal config needed with Spring AI 1.1.2

- [ ] **Task 16: Create VirtualThreadConfig**
  - File: `src/main/java/dev/alexandria/config/VirtualThreadConfig.java`
  - Action: @Configuration with TaskDecorator bean for MDC context propagation across Virtual Threads
  - Notes: Critical for correlation ID propagation in async contexts

### Phase 5: Markdown Processing

- [ ] **Task 17: Create AlexandriaMarkdownSplitter**
  - File: `src/main/java/dev/alexandria/core/AlexandriaMarkdownSplitter.java`
  - Action: Implements DocumentSplitter. Extracts YAML front matter, protects code blocks/tables with placeholders, splits by headers then recursively, restores protected content, adds breadcrumb metadata
  - Notes: MAX_CHUNK_TOKENS=500, OVERLAP_TOKENS=75, MAX_OVERSIZED_TOKENS=1500, BREADCRUMB_DEPTH=3. Uses CommonMark-java for parsing

- [ ] **Task 18: Create LlmsTxtParser**
  - File: `src/main/java/dev/alexandria/core/LlmsTxtParser.java`
  - Action: Standalone parser (not DocumentParser) for llms.txt format. Parses title, description, sections with links. Returns LlmsTxtDocument record with nested LlmsTxtSection and LlmsTxtLink records
  - Notes: Format spec at llmstxt.org. Regex patterns for title, blockquote, section (H2), links

### Phase 6: HTTP Clients

- [ ] **Task 19: Create InfinityRerankClient**
  - File: `src/main/java/dev/alexandria/adapters/InfinityRerankClient.java`
  - Action: @Service with @Retry(name="infinityApi", fallbackMethod="rerankFallback") on rerank method. Uses RestClient to POST to /rerank with Cohere-style request format
  - Notes: Request: {model, query, documents}. Response: {results: [{index, relevance_score}]}. Fallback throws AlexandriaException(SERVICE_UNAVAILABLE)
  - **F2 Remediation:** Add `@RateLimiter(name="infinityApi")` and `@Bulkhead(name="infinityApi")` annotations alongside @Retry for protection against cold start cascade

### Phase 7: Core Services

- [ ] **Task 20: Create IngestionService**
  - File: `src/main/java/dev/alexandria/core/IngestionService.java`
  - Action: @Service orchestrating ingestion: validateDocument, parseDocument (router by extension), split, embedAndStore. Methods: ingestSingle, ingestBatch
  - Notes: Router returns null for llms.txt (signal to use LlmsTxtParser). Batch processing with Lists.partition from Guava

- [ ] **Task 21: Create DocumentUpdateService**
  - File: `src/main/java/dev/alexandria/core/DocumentUpdateService.java`
  - Action: @Service for document updates. Two-phase change detection (mtime+size fast path, then SHA-256 hash). Pattern: DELETE by sourceUri filter + INSERT new chunks
  - Notes: Uses metadataKey("sourceUri").isEqualTo() filter. Returns UpdateResult enum (NO_CHANGE, CREATED, UPDATED)
  - **F1 Remediation (CRITICAL):** Add `@Transactional(isolation = Isolation.READ_COMMITTED)` on `ingestDocument()` method to ensure atomic DELETE+INSERT
  - **F4 Remediation (MUST HAVE):** Implement `getStoredDocumentInfo(String sourceUri)` method:
    ```java
    /**
     * Retrieves stored document info for fast-path change detection.
     * @return Optional containing fileSize, fileModifiedAt, documentHash if document exists
     */
    public Optional<StoredDocumentInfo> getStoredDocumentInfo(String sourceUri) {
        // Query first chunk's metadata via JSONB:
        // SELECT metadata FROM document_embeddings
        // WHERE metadata->>'sourceUri' = ? AND (metadata->>'chunkIndex')::int = 0
        // Extract: fileSize, fileModifiedAt, documentHash
    }

    public record StoredDocumentInfo(long fileSize, long fileModifiedAt, String documentHash) {}
    ```

- [ ] **Task 22: Create RetrievalService**
  - File: `src/main/java/dev/alexandria/core/RetrievalService.java`
  - Action: @Service with search method implementing tiered response. Steps: validate query, check empty DB, vector search, rerank (with budget check), apply confidence thresholds (HIGH=0.7, MEDIUM=0.4, LOW=0.2), build McpSearchResponse
  - Notes: Uses JdbcTemplate for COUNT(*) query. Graceful degradation: skip rerank if <5s remaining. Fallback to raw vector if reranker too aggressive

### Phase 8: MCP Adapters

- [ ] **Task 23: Create McpResponseFormatter**
  - File: `src/main/java/dev/alexandria/adapters/McpResponseFormatter.java`
  - Action: Static helper class with formatSearchResults (dual-format: JSON + Markdown) and errorResult methods. Token budget management (MAX_TOKENS=8000)
  - Notes: Uses CallToolResult.builder() from MCP SDK. Truncation with count of remaining results

- [ ] **Task 24: Create McpTools**
  - File: `src/main/java/dev/alexandria/adapters/McpTools.java`
  - Action: @Service with @McpTool methods: search_documents (query param), ingest_document (filePath param with 5-file limit for directories, progress reporting via McpSyncRequestContext)
  - Notes: Use @McpToolParam for parameter descriptions. Catch AlexandriaException -> errorResult, other Exception -> generic error

### Phase 9: Infrastructure

- [ ] **Task 25: Create CorrelationIdFilter**
  - File: `src/main/java/dev/alexandria/filter/CorrelationIdFilter.java`
  - Action: OncePerRequestFilter that extracts or generates X-Correlation-Id header, sets MDC, clears MDC in finally block
  - Notes: UUID generation for missing header. MDC key: "correlationId"

- [ ] **Task 26: Create InfinityEmbeddingHealthIndicator**
  - File: `src/main/java/dev/alexandria/health/InfinityEmbeddingHealthIndicator.java`
  - Action: AbstractHealthIndicator checking Infinity /health endpoint
  - Notes: Simple HTTP GET with timeout

- [ ] **Task 27: Create RerankingHealthIndicator**
  - File: `src/main/java/dev/alexandria/health/RerankingHealthIndicator.java`
  - Action: AbstractHealthIndicator checking reranking service availability
  - Notes: Can use same /health endpoint or lightweight probe

- [ ] **Task 28: Create PgVectorHealthIndicator**
  - File: `src/main/java/dev/alexandria/health/PgVectorHealthIndicator.java`
  - Action: AbstractHealthIndicator checking pgvector extension loaded and HNSW index exists
  - Notes: SQL: SELECT extversion FROM pg_extension WHERE extname='vector'

### Phase 10: CLI

- [ ] **Task 29: Create IngestCommand**
  - File: `src/main/java/dev/alexandria/cli/IngestCommand.java`
  - Action: @Component @Command(name="ingest") implementing Runnable. Parameters: path (positional). Options: -r/--recursive, -b/--batch-size (default 25), --dry-run
  - Notes: Uses Picocli spring boot starter. Batch processing with Guava Lists.partition. Filter supported formats (.md, .txt, .html, llms.txt)

### Phase 11: Database Schema

- [ ] **Task 30: Create database schema SQL**
  - File: `src/main/resources/schema.sql` (or Flyway migration)
  - Action: CREATE TABLE document_embeddings (embedding_id UUID PRIMARY KEY, embedding vector(1024), text TEXT, metadata JSONB). CREATE INDEX HNSW, B-tree on sourceUri/documentHash, GIN on metadata
  - Notes: HNSW params: m=16, ef_construction=128. Runtime: SET hnsw.ef_search=100, hnsw.iterative_scan=on

### Phase 12: Test Infrastructure

- [ ] **Task 31: Create EmbeddingFixtures**
  - File: `src/test/java/dev/alexandria/test/EmbeddingFixtures.java`
  - Action: Static methods: generate(seed) for deterministic 1024D normalized vectors, similar(base, similarity) for controlled cosine similarity
  - Notes: L2 normalization required. Use Random with seed for reproducibility

- [ ] **Task 32: Create PgVectorTestConfiguration**
  - File: `src/test/java/dev/alexandria/test/PgVectorTestConfiguration.java`
  - Action: @TestConfiguration with @Bean @ServiceConnection PostgreSQLContainer using image "pgvector/pgvector:0.8.1-pg18"
  - Notes: Testcontainers 2.x import: org.testcontainers.postgresql.PostgreSQLContainer

- [ ] **Task 33: Create InfinityStubs**
  - File: `src/test/java/dev/alexandria/test/InfinityStubs.java`
  - Action: Static methods for WireMock stubs: stubEmbeddings, stubColdStart (logNormalRandomDelay), stubRerank, stubRetryScenario (503 then success using scenarios)
  - Notes: Response formats match Infinity API (OpenAI for embeddings, Cohere for rerank)

- [ ] **Task 34: Create McpTestSupport**
  - File: `src/test/java/dev/alexandria/test/McpTestSupport.java`
  - Action: Static createClient(port) method returning McpSyncClient with HttpClientStreamableHttpTransport on /mcp endpoint
  - Notes: Uses MCP Java SDK 0.17.0 (via Spring AI). Set 30s request timeout

### Phase 13: Tests

- [ ] **Task 35: Write unit tests for QueryValidator**
  - File: `src/test/java/dev/alexandria/core/QueryValidatorTest.java`
  - Action: Test TOO_SHORT, TOO_VAGUE, valid cases
  - Notes: Parameterized tests recommended

- [ ] **Task 36: Write unit tests for AlexandriaMarkdownSplitter**
  - File: `src/test/java/dev/alexandria/core/AlexandriaMarkdownSplitterTest.java`
  - Action: Test YAML extraction, code block preservation, table preservation, header splitting, breadcrumb generation
  - Notes: Use multiline string literals for test content

- [ ] **Task 37: Write unit tests for LlmsTxtParser**
  - File: `src/test/java/dev/alexandria/core/LlmsTxtParserTest.java`
  - Action: Test title/description extraction, section parsing, link parsing with optional description
  - Notes: Use sample from llmstxt.org

- [ ] **Task 38: Write integration tests for Resilience4j retry**
  - File: `src/test/java/dev/alexandria/adapters/InfinityClientRetryTest.java`
  - Action: @SpringBootTest @AutoConfigureWireMock. Test retry on 503 succeeds on second attempt, verify 2 requests made
  - Notes: Use InfinityStubs.stubRetryScenario

- [ ] **Task 39: Write integration tests for RetrievalService**
  - File: `src/test/java/dev/alexandria/core/RetrievalServiceIntegrationTest.java`
  - Action: @SpringBootTest with PgVectorTestConfiguration. Test tiered response: HIGH confidence, PARTIAL, noResults, empty database
  - Notes: Pre-populate test data, mock Infinity with WireMock

- [ ] **Task 40: Write E2E tests for MCP tools**
  - File: `src/test/java/dev/alexandria/McpToolsE2ETest.java`
  - Action: @SpringBootTest(webEnvironment=RANDOM_PORT). Test listTools contains search_documents/ingest_document, test callTool search_documents returns results
  - Notes: Use McpTestSupport.createClient. Initialize client, verify tools, execute search

### Phase 14: Docker & Deployment

- [ ] **Task 41: Create Dockerfile**
  - File: `Dockerfile`
  - Action: Multi-stage build: builder with Maven, runtime with eclipse-temurin:25-jre. EXPOSE 8080. HEALTHCHECK on /actuator/health
  - Notes: Use --enable-preview if needed for Java 25 features. Set JVM memory limits

- [ ] **Task 42: Create docker-compose.yml**
  - File: `docker-compose.yml`
  - Action: Services: alexandria (app), postgres (pgvector/pgvector:0.8.1-pg18 with volumes), optional infinity mock for local dev
  - Notes: Include environment variables for connection. Health checks for startup order

## Acceptance Criteria

### AC 1: MCP Server Startup
- [ ] Given a properly configured environment, when the application starts, then the MCP server is available at `/mcp` endpoint within 30 seconds
- [ ] Given the MCP server is running, when a client connects and lists tools, then `search_documents` and `ingest_document` are returned

### AC 2: Document Ingestion (CLI)
- [ ] Given a directory with markdown files, when running `java -jar alexandria.jar ingest /path/to/docs -r`, then all .md files are chunked and stored in pgvector
- [ ] Given a file with YAML front matter, when ingested, then metadata is extracted and stored in chunk metadata
- [ ] Given a code block in markdown, when chunked, then the code block is preserved intact (not split mid-block)

### AC 3: Document Ingestion (MCP)
- [ ] Given a single file path, when calling ingest_document tool, then the document is processed with progress updates (0.1 -> 0.3 -> 0.5 -> 0.7 -> 1.0)
- [ ] Given a directory with more than 5 files, when calling ingest_document tool, then an error is returned suggesting CLI usage

### AC 4: Semantic Search
- [ ] Given indexed documents, when calling search_documents with a relevant query, then results are returned with relevance scores and source references
- [ ] Given indexed documents, when calling search_documents, then results include dual-format response (JSON + Markdown)
- [ ] Given a query matching high-confidence documents (score > 0.7), when searching, then status is SUCCESS and confidence is HIGH

### AC 5: Tiered Response
- [ ] Given a query with medium-confidence matches (0.4-0.7), when searching, then status is PARTIAL with caveat message
- [ ] Given a query with no matches above threshold, when searching, then status is NO_RESULTS with helpful message
- [ ] Given an empty database, when searching, then message indicates "Knowledge base not indexed yet"

### AC 6: Query Validation
- [ ] Given a query with less than 3 characters, when searching, then error is returned immediately without embedding call
- [ ] Given a query with only stopwords, when searching, then error indicates "query needs more specific terms"

### AC 7: Retry Resilience
- [ ] Given Infinity API returns 503, when embedding request is made, then retry occurs with exponential backoff (1s -> 2s -> 4s)
- [ ] Given Infinity API fails 3 times then succeeds, when request completes, then result is returned successfully
- [ ] Given Infinity API fails all 4 attempts, when retries exhausted, then AlexandriaException(SERVICE_UNAVAILABLE) is thrown

### AC 8: Timeout Budget
- [ ] Given a cold start scenario (first request), when search completes, then total time is under 90 seconds
- [ ] Given insufficient time budget (<5s remaining), when reranking would occur, then reranking is skipped with LOW confidence results

### AC 9: Document Updates
- [ ] Given an existing document is modified, when re-ingested, then old chunks are deleted and new chunks inserted (DELETE + INSERT)
- [ ] Given an unchanged document, when re-ingested, then no database operations occur (NO_CHANGE)
- [ ] Given a document hash change, when detected, then update is triggered regardless of filename

### AC 10: Health Checks
- [ ] Given all services healthy, when accessing /actuator/health, then status is UP with details for infinity, reranking, pgvector
- [ ] Given Infinity endpoint unavailable, when health checked, then infinity indicator is DOWN

### AC 11: Error Handling
- [ ] Given any AlexandriaException thrown, when MCP tool responds, then isError=true with category-appropriate message and suggested action
- [ ] Given unexpected RuntimeException, when caught, then generic error message returned without exposing internals

### AC 12: Observability
- [ ] Given any request, when processed, then X-Correlation-Id is present in logs (generated if not provided)
- [ ] Given retry events, when /actuator/retryevents accessed, then retry history is visible
