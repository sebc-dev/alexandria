# Phase 13: Tests

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
