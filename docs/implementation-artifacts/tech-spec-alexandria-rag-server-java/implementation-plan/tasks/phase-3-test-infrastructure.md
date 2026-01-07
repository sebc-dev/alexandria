# Phase 3: Test Infrastructure

> **Prérequis TDD** : Cette phase doit être complétée AVANT toute phase TDD. Elle fournit les fixtures et stubs nécessaires au cycle Red-Green-Refactor.

- [ ] **Task 4: Create EmbeddingFixtures**
  - File: `src/test/java/dev/alexandria/test/EmbeddingFixtures.java`
  - Action: Static methods: generate(seed) for deterministic 1024D normalized vectors, similar(base, similarity) for controlled cosine similarity
  - Notes: L2 normalization required. Use Random with seed for reproducibility

- [ ] **Task 5: Create PgVectorTestConfiguration**
  - File: `src/test/java/dev/alexandria/test/PgVectorTestConfiguration.java`
  - Action: @TestConfiguration with @Bean @ServiceConnection PostgreSQLContainer using image "pgvector/pgvector:0.8.1-pg18"
  - Notes: Testcontainers 2.x import: org.testcontainers.postgresql.PostgreSQLContainer

- [ ] **Task 6: Create InfinityStubs**
  - File: `src/test/java/dev/alexandria/test/InfinityStubs.java`
  - Action: Static methods for WireMock stubs: stubEmbeddings, stubColdStart (logNormalRandomDelay), stubRerank, stubRetryScenario (503 then success using scenarios)
  - Notes: Response formats match Infinity API (OpenAI for embeddings, Cohere for rerank)

- [ ] **Task 7: Create McpTestSupport**
  - File: `src/test/java/dev/alexandria/test/McpTestSupport.java`
  - Action: Static createClient(port) method returning McpSyncClient with HttpClientStreamableHttpTransport on /mcp endpoint
  - Notes: Uses MCP Java SDK 0.17.0 (via Spring AI). Set 30s request timeout
