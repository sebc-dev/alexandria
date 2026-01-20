---
phase: 02-ingestion-core
plan: 03
subsystem: ingestion
tags: [langchain4j, pgvector, embeddings, all-minilm-l6-v2, onnx, jdbc, ingestion-pipeline]

# Dependency graph
requires:
  - phase: 02-01
    provides: Domain models (Document, Chunk, ChunkType), MarkdownParser, DocumentRepository
  - phase: 02-02
    provides: HierarchicalChunker for two-pass parent/child chunking
provides:
  - EmbeddingGenerator port interface and LangChain4j ONNX adapter
  - ChunkRepository port interface and JDBC adapter with pgvector
  - IngestionService orchestrating full parse-chunk-embed-store pipeline
  - ChunkerPort and MarkdownParserPort interfaces for hexagonal architecture
  - Unit tests for IngestionService with mocks
  - Integration test ready for Docker environment
affects: [03-search, 04-mcp, 06-cli]

# Tech tracking
tech-stack:
  added: [testcontainers-postgresql, testcontainers-junit-jupiter]
  patterns: [port-adapter-pattern-for-ingestion, content-hash-upsert-pattern]

key-files:
  created:
    - src/main/java/fr/kalifazzia/alexandria/core/port/EmbeddingGenerator.java
    - src/main/java/fr/kalifazzia/alexandria/infra/embedding/LangChain4jEmbeddingGenerator.java
    - src/main/java/fr/kalifazzia/alexandria/core/port/ChunkRepository.java
    - src/main/java/fr/kalifazzia/alexandria/infra/persistence/JdbcChunkRepository.java
    - src/main/java/fr/kalifazzia/alexandria/core/ingestion/IngestionService.java
    - src/main/java/fr/kalifazzia/alexandria/core/port/ChunkerPort.java
    - src/main/java/fr/kalifazzia/alexandria/core/port/MarkdownParserPort.java
    - src/test/java/fr/kalifazzia/alexandria/core/ingestion/IngestionServiceTest.java
    - src/test/java/fr/kalifazzia/alexandria/infra/ingestion/IngestionIT.java
  modified:
    - pom.xml
    - src/main/java/fr/kalifazzia/alexandria/core/ingestion/MarkdownParser.java
    - src/main/java/fr/kalifazzia/alexandria/core/ingestion/HierarchicalChunker.java

key-decisions:
  - "Port interfaces for MarkdownParser and Chunker to enable unit testing with mocks"
  - "SHA-256 content hash prevents redundant re-indexing of unchanged files"
  - "Upsert pattern: delete old chunks then insert new, not update in place"
  - "Testcontainers for PostgreSQL integration testing (pgvector/pgvector:pg17)"
  - "Embed both parent and child chunks for potential future use cases"

patterns-established:
  - "Content hash check for skip-if-unchanged ingestion pattern"
  - "All port interfaces in core/port/, all adapters in infra/ subdirectories"
  - "Unit tests with mocked port interfaces, integration tests with Testcontainers"

# Metrics
duration: 7min
completed: 2026-01-20
---

# Phase 02 Plan 03: Embedding and Ingestion Pipeline Summary

**Complete ingestion pipeline with AllMiniLmL6V2EmbeddingModel (ONNX 384-dim), pgvector chunk storage, and IngestionService orchestrating parse-chunk-embed-store workflow**

## Performance

- **Duration:** 7 min
- **Started:** 2026-01-20T10:11:51Z
- **Completed:** 2026-01-20T10:18:44Z
- **Tasks:** 3
- **Files modified:** 12

## Accomplishments

- EmbeddingGenerator port with LangChain4j adapter using all-MiniLM-L6-v2 ONNX model (384 dimensions)
- ChunkRepository port with JDBC adapter storing embeddings via PGvector type
- IngestionService orchestrating full pipeline: ingestDirectory and ingestFile methods
- SHA-256 content hash prevents re-indexing unchanged files
- Port interfaces (ChunkerPort, MarkdownParserPort) enable clean unit testing
- 8 unit tests for IngestionService covering all orchestration scenarios
- Integration test ready for Docker (IngestionIT with Testcontainers)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create EmbeddingGenerator port and LangChain4j adapter** - `74147fc` (feat)
2. **Task 2: Create ChunkRepository port and JDBC adapter with pgvector** - `7f536b6` (feat)
3. **Task 3: Create IngestionService orchestrating full pipeline** - `d415279` (feat)

## Files Created/Modified

- `src/main/java/fr/kalifazzia/alexandria/core/port/EmbeddingGenerator.java` - Port interface for embeddings
- `src/main/java/fr/kalifazzia/alexandria/infra/embedding/LangChain4jEmbeddingGenerator.java` - ONNX adapter
- `src/main/java/fr/kalifazzia/alexandria/core/port/ChunkRepository.java` - Port interface for chunks
- `src/main/java/fr/kalifazzia/alexandria/infra/persistence/JdbcChunkRepository.java` - pgvector JDBC adapter
- `src/main/java/fr/kalifazzia/alexandria/core/ingestion/IngestionService.java` - Pipeline orchestration
- `src/main/java/fr/kalifazzia/alexandria/core/port/ChunkerPort.java` - Chunker port interface
- `src/main/java/fr/kalifazzia/alexandria/core/port/MarkdownParserPort.java` - Parser port interface
- `src/main/java/fr/kalifazzia/alexandria/core/ingestion/MarkdownParser.java` - Added interface implementation
- `src/main/java/fr/kalifazzia/alexandria/core/ingestion/HierarchicalChunker.java` - Added interface implementation
- `src/test/java/fr/kalifazzia/alexandria/core/ingestion/IngestionServiceTest.java` - Unit tests
- `src/test/java/fr/kalifazzia/alexandria/infra/ingestion/IngestionIT.java` - Integration test
- `pom.xml` - Added Testcontainers dependencies

## Decisions Made

- **Port interfaces for testability:** Created ChunkerPort and MarkdownParserPort interfaces to enable mocking concrete classes in unit tests (Java 25 Mockito limitation with concrete class mocking)
- **Embed both parent and child chunks:** Embeddings generated for all chunks (parent and child) - child used for search, parent embedding available for future use
- **Testcontainers for integration:** Using pgvector/pgvector:pg17 Docker image for realistic integration testing

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added port interfaces for MarkdownParser and HierarchicalChunker**
- **Found during:** Task 3 (unit tests)
- **Issue:** Mockito couldn't mock concrete classes in Java 25 - tests failed with "Cannot mock this class"
- **Fix:** Created ChunkerPort and MarkdownParserPort interfaces; MarkdownParser and HierarchicalChunker now implement them
- **Files modified:** ChunkerPort.java, MarkdownParserPort.java, MarkdownParser.java, HierarchicalChunker.java, IngestionService.java, IngestionServiceTest.java
- **Verification:** All 40 unit tests pass
- **Committed in:** d415279 (Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Auto-fix improved architecture by following port/adapter pattern consistently. No scope creep.

## Issues Encountered

- **Java 25 Mockito limitation:** Mockito inline mock maker cannot mock concrete classes in Java 25. Resolved by introducing port interfaces, which aligns better with hexagonal architecture anyway.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Full ingestion pipeline ready: parse markdown -> extract frontmatter -> chunk hierarchically -> embed -> store
- Ready for Phase 03 (Search) to implement vector similarity search using stored embeddings
- Ready for Phase 06 (CLI) to expose ingestDirectory as command
- **Blocker:** Integration tests require Docker for PostgreSQL container

---
*Phase: 02-ingestion-core*
*Completed: 2026-01-20*
