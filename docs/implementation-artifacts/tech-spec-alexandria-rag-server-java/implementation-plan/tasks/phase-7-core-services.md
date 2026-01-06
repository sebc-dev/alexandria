# Phase 7: Core Services

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
