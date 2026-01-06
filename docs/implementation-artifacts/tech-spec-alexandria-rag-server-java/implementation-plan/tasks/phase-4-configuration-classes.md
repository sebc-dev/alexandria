# Phase 4: Configuration Classes

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
