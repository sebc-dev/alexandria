# Phase 3: Exception Hierarchy (TDD)

- [ ] **Task 8: Create ErrorCategory enum** (TDD)
  - **RED**:
    - Test file: `src/test/java/dev/alexandria/core/ErrorCategoryTest.java`
    - Test cases:
      - `shouldHaveSixCategories()` - verify 6 enum values exist
      - `shouldHaveTitleForEachCategory()` - verify non-null titles
      - `shouldHaveSuggestedActionForEachCategory()` - verify non-null actions
  - **GREEN**:
    - File: `src/main/java/dev/alexandria/core/ErrorCategory.java`
    - Action: Enum with 6 categories (VALIDATION, NOT_FOUND, SERVICE_UNAVAILABLE, INGESTION_FAILED, DATABASE_ERROR, TIMEOUT) with title and suggestedAction fields
  - Notes: Copy exact implementation from error-handling research

- [ ] **Task 9: Create AlexandriaException base class** (TDD)
  - **RED**:
    - Test file: `src/test/java/dev/alexandria/core/AlexandriaExceptionTest.java`
    - Test cases:
      - `shouldStoreErrorCategory()` - verify category is accessible
      - `shouldStoreMessage()` - verify message inheritance
      - `shouldStoreCause()` - verify cause chain
  - **GREEN**:
    - File: `src/main/java/dev/alexandria/core/AlexandriaException.java`
    - Action: RuntimeException with ErrorCategory field, two constructors (with/without cause)
  - Notes: All domain exceptions extend this

- [ ] **Task 10: Create specialized exceptions** (TDD)
  - **RED**:
    - Test file: `src/test/java/dev/alexandria/core/SpecializedExceptionsTest.java`
    - Test cases:
      - `documentNotFoundShouldHaveNotFoundCategory()`
      - `queryValidationShouldHaveValidationCategory()`
      - `embeddingServiceShouldHaveServiceUnavailableCategory()`
      - `ingestionShouldHaveIngestionFailedCategory()`
  - **GREEN**:
    - Files: `src/main/java/dev/alexandria/core/` - DocumentNotFoundException.java, QueryValidationException.java, EmbeddingServiceException.java, IngestionException.java
    - Action: Create 4 exception classes extending AlexandriaException with appropriate ErrorCategory
  - Notes: Each exception pre-sets its category
