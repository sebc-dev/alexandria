# Phase 6: HTTP Clients

- [ ] **Task 19: Create InfinityRerankClient**
  - File: `src/main/java/dev/alexandria/adapters/InfinityRerankClient.java`
  - Action: @Service with @Retry(name="infinityApi", fallbackMethod="rerankFallback") on rerank method. Uses RestClient to POST to /rerank with Cohere-style request format
  - Notes: Request: {model, query, documents}. Response: {results: [{index, relevance_score}]}. Fallback throws AlexandriaException(SERVICE_UNAVAILABLE)
  - **F2 Remediation:** Add `@RateLimiter(name="infinityApi")` and `@Bulkhead(name="infinityApi")` annotations alongside @Retry for protection against cold start cascade
