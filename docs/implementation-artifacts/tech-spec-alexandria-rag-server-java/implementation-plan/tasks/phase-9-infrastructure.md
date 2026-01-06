# Phase 9: Infrastructure

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
