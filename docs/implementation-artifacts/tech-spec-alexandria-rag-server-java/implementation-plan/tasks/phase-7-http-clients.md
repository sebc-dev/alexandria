# Phase 7: HTTP Clients (TDD)

- [ ] **Task 23: Create InfinityRerankClient** (TDD)
  - **RED**:
    - Test file: `src/test/java/dev/alexandria/adapters/InfinityRerankClientTest.java`
    - Test cases (with WireMock):
      - `shouldRerankDocumentsSuccessfully()` - happy path
      - `shouldReturnScoresInDescendingOrder()` - verify sorting
      - `shouldRetryOn503AndSucceed()` - retry scenario using InfinityStubs
      - `shouldThrowAfterMaxRetries()` - exhausted retries → AlexandriaException
      - `shouldRespectRateLimiter()` - F2 remediation test
      - `shouldRespectBulkhead()` - F2 remediation test
  - **GREEN**:
    - File: `src/main/java/dev/alexandria/adapters/InfinityRerankClient.java`
    - Action: @Service with @Retry, @RateLimiter, @Bulkhead annotations. Uses RestClient to POST /rerank
  - Notes: Cohere-style request format. F2 Remediation annotations included
