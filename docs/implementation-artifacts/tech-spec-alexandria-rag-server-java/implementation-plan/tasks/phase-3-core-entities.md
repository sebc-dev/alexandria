# Phase 3: Core Entities

- [ ] **Task 7: Create ChunkMetadata record**
  - File: `src/main/java/dev/alexandria/core/ChunkMetadata.java`
  - Action: Record with 10 fields (sourceUri, documentHash, chunkIndex, breadcrumbs, documentTitle, contentHash, createdAt, documentType, **fileSize**, **fileModifiedAt**) + static helper methods (toLogicalUri, computeHash)
  - Notes: computeHash uses NFKC normalization + SHA-256. Langchain4j metadata supports primitives only
  - **F4 Remediation:** Added `fileSize` (long, bytes) and `fileModifiedAt` (long, epoch millis) for two-phase change detection fast path (mtime+size check before SHA-256 hash)

- [ ] **Task 8: Create QueryValidator**
  - File: `src/main/java/dev/alexandria/core/QueryValidator.java`
  - Action: Validation class with MIN_CHARS=3, MIN_MEANINGFUL_TOKENS=2, STOPWORDS set (EN + FR). Returns ValidationResult record with QueryProblem enum
  - Notes: Prevents wasted embedding calls on malformed queries
  - **F16 Remediation:** Include French stopwords alongside English:
    ```java
    private static final Set<String> STOPWORDS_FR = Set.of(
        "le", "la", "les", "un", "une", "des", "du", "de", "à", "au", "aux",
        "et", "ou", "où", "que", "qui", "quoi", "ce", "cette", "ces", "mon",
        "ton", "son", "notre", "votre", "leur", "je", "tu", "il", "elle",
        "nous", "vous", "ils", "elles", "est", "sont", "a", "ont", "pour",
        "dans", "sur", "avec", "par", "en", "ne", "pas", "plus", "moins"
    );
    ```

- [ ] **Task 9: Create McpSearchResponse**
  - File: `src/main/java/dev/alexandria/core/McpSearchResponse.java`
  - Action: Record with results list + SearchMetadata. Include nested records: SearchResult, SearchMetadata. Enums: SearchStatus, RelevanceLevel. Factory methods: success, partial, noResults, error
  - Notes: This is the contract for all search responses
