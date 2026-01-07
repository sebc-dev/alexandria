# Phase 14: E2E Validation

> **Note**: Ces tests valident le système complet après l'implémentation de toutes les phases. Ils ne suivent pas le cycle TDD car ils testent l'intégration de tous les composants ensemble.

- [ ] **Task 36: Create MCP E2E test for search flow**
  - File: `src/test/java/dev/alexandria/e2e/McpSearchE2ETest.java`
  - Action: @SpringBootTest(webEnvironment=RANDOM_PORT) avec McpSyncClient
  - Test scenarios:
    - `shouldListToolsWithSearchAndIngest()` - tool discovery
    - `shouldSearchDocumentsEndToEnd()` - ingest → search → verify results
    - `shouldReturnTieredResponseBasedOnConfidence()` - HIGH/MEDIUM/LOW in real results
    - `shouldHandleNoResultsGracefully()` - empty search
  - Notes: Use McpTestSupport.createClient. Initialize, verify, execute, assert

- [ ] **Task 37: Create MCP E2E test for ingestion flow**
  - File: `src/test/java/dev/alexandria/e2e/McpIngestionE2ETest.java`
  - Action: @SpringBootTest with real file ingestion
  - Test scenarios:
    - `shouldIngestMarkdownViaMe()` - single file ingestion
    - `shouldReportProgressDuringIngestion()` - progress updates
    - `shouldUpdateExistingDocument()` - re-ingest modified file
    - `shouldRejectUnsupportedFormat()` - error handling
  - Notes: Use temp files for test content

- [ ] **Task 38: Create health check E2E test**
  - File: `src/test/java/dev/alexandria/e2e/HealthCheckE2ETest.java`
  - Action: @SpringBootTest verifying /actuator/health
  - Test scenarios:
    - `shouldShowHealthyWhenAllServicesUp()` - full system health
    - `shouldShowComponentDetails()` - infinity, reranking, pgvector
    - `shouldIncludeCorrelationIdInResponse()` - observability
  - Notes: Integration with real (testcontainers) or mocked services
