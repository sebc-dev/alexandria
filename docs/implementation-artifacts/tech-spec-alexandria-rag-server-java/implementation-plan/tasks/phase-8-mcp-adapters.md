# Phase 8: MCP Adapters

- [ ] **Task 23: Create McpResponseFormatter**
  - File: `src/main/java/dev/alexandria/adapters/McpResponseFormatter.java`
  - Action: Static helper class with formatSearchResults (dual-format: JSON + Markdown) and errorResult methods. Token budget management (MAX_TOKENS=8000)
  - Notes: Uses CallToolResult.builder() from MCP SDK. Truncation with count of remaining results

- [ ] **Task 24: Create McpTools**
  - File: `src/main/java/dev/alexandria/adapters/McpTools.java`
  - Action: @Service with @McpTool methods: search_documents (query param), ingest_document (filePath param with 5-file limit for directories, progress reporting via McpSyncRequestContext)
  - Notes: Use @McpToolParam for parameter descriptions. Catch AlexandriaException -> errorResult, other Exception -> generic error
