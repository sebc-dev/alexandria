# Phase 11: Infrastructure (TDD)

- [ ] **Task 29: Create CorrelationIdFilter** (TDD)
  - **RED**:
    - Test file: `src/test/java/dev/alexandria/filter/CorrelationIdFilterTest.java`
    - Test cases:
      - `shouldExtractCorrelationIdFromHeader()` - existing header
      - `shouldGenerateCorrelationIdWhenMissing()` - UUID generation
      - `shouldSetMdcForLogging()` - MDC contains correlationId
      - `shouldClearMdcAfterRequest()` - cleanup in finally
      - `shouldAddCorrelationIdToResponseHeader()` - echo back
  - **GREEN**:
    - File: `src/main/java/dev/alexandria/filter/CorrelationIdFilter.java`
    - Action: OncePerRequestFilter with MDC management
  - Notes: MDC key: "correlationId". UUID for missing header

- [ ] **Task 30: Create InfinityEmbeddingHealthIndicator** (TDD)
  - **RED**:
    - Test file: `src/test/java/dev/alexandria/health/InfinityEmbeddingHealthIndicatorTest.java`
    - Test cases (with WireMock):
      - `shouldReturnUpWhenHealthy()` - /health returns 200
      - `shouldReturnDownWhenUnhealthy()` - /health returns 503
      - `shouldReturnDownOnTimeout()` - no response
      - `shouldIncludeServiceUrl()` - details contain URL
  - **GREEN**:
    - File: `src/main/java/dev/alexandria/health/InfinityEmbeddingHealthIndicator.java`
    - Action: AbstractHealthIndicator checking /health endpoint
  - Notes: Simple HTTP GET with timeout

- [ ] **Task 31: Create RerankingHealthIndicator** (TDD)
  - **RED**:
    - Test file: `src/test/java/dev/alexandria/health/RerankingHealthIndicatorTest.java`
    - Test cases: (same pattern as embedding health)
  - **GREEN**:
    - File: `src/main/java/dev/alexandria/health/RerankingHealthIndicator.java`
    - Action: AbstractHealthIndicator for reranking service
  - Notes: Can share health endpoint or use separate probe

- [ ] **Task 32: Create PgVectorHealthIndicator** (TDD)
  - **RED**:
    - Test file: `src/test/java/dev/alexandria/health/PgVectorHealthIndicatorTest.java`
    - Test cases (with Testcontainers):
      - `shouldReturnUpWhenExtensionLoaded()` - vector extension present
      - `shouldReturnDownWhenExtensionMissing()` - no vector extension
      - `shouldIncludeExtensionVersion()` - details contain version
      - `shouldCheckHnswIndexExists()` - verify index presence
  - **GREEN**:
    - File: `src/main/java/dev/alexandria/health/PgVectorHealthIndicator.java`
    - Action: AbstractHealthIndicator with SQL check
  - Notes: SQL: SELECT extversion FROM pg_extension WHERE extname='vector'
