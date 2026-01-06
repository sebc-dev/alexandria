# Phase 2: Exception Hierarchy

- [ ] **Task 4: Create ErrorCategory enum**
  - File: `src/main/java/dev/alexandria/core/ErrorCategory.java`
  - Action: Enum with 6 categories (VALIDATION, NOT_FOUND, SERVICE_UNAVAILABLE, INGESTION_FAILED, DATABASE_ERROR, TIMEOUT) with title and suggestedAction fields
  - Notes: Copy exact implementation from error-handling research

- [ ] **Task 5: Create AlexandriaException base class**
  - File: `src/main/java/dev/alexandria/core/AlexandriaException.java`
  - Action: RuntimeException with ErrorCategory field, two constructors (with/without cause)
  - Notes: All domain exceptions extend this

- [ ] **Task 6: Create specialized exceptions**
  - Files: `src/main/java/dev/alexandria/core/` - DocumentNotFoundException.java, QueryValidationException.java, EmbeddingServiceException.java, IngestionException.java
  - Action: Create 4 exception classes extending AlexandriaException with appropriate ErrorCategory
  - Notes: Each exception pre-sets its category
