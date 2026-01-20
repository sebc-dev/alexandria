---
phase: 02-ingestion-core
plan: 01
subsystem: ingestion
tags: [commonmark, yaml-frontmatter, markdown-parser, domain-models, jdbc, postgresql]

# Dependency graph
requires:
  - phase: 01-infrastructure
    provides: Maven project structure with LangChain4j and database schema
provides:
  - Document, Chunk, ChunkType domain models
  - MarkdownParser with YAML frontmatter extraction
  - DocumentRepository port interface
  - JdbcDocumentRepository adapter for PostgreSQL
affects: [02-02-chunking, 02-03-embeddings, 04-search]

# Tech tracking
tech-stack:
  added: [commonmark-0.22.0, commonmark-ext-yaml-front-matter-0.22.0]
  patterns: [hexagonal-architecture-port-adapter, java-21-records, defensive-immutability]

key-files:
  created:
    - src/main/java/fr/kalifazzia/alexandria/core/model/Document.java
    - src/main/java/fr/kalifazzia/alexandria/core/model/Chunk.java
    - src/main/java/fr/kalifazzia/alexandria/core/model/ChunkType.java
    - src/main/java/fr/kalifazzia/alexandria/core/ingestion/DocumentMetadata.java
    - src/main/java/fr/kalifazzia/alexandria/core/ingestion/ParsedDocument.java
    - src/main/java/fr/kalifazzia/alexandria/core/ingestion/MarkdownParser.java
    - src/main/java/fr/kalifazzia/alexandria/core/port/DocumentRepository.java
    - src/main/java/fr/kalifazzia/alexandria/infra/persistence/JdbcDocumentRepository.java
    - src/test/java/fr/kalifazzia/alexandria/core/ingestion/MarkdownParserTest.java
  modified:
    - pom.xml

key-decisions:
  - "CommonMark 0.22.0 for markdown parsing (10-20x faster than pegdown)"
  - "Java 21 records for immutable domain models with defensive copying"
  - "Port/adapter pattern for repository (core defines contract, infra implements)"
  - "JSONB for frontmatter storage with Jackson serialization"
  - "TEXT[] for tags storage with PostgreSQL array support"

patterns-established:
  - "Domain models as immutable records with factory methods"
  - "Port interfaces in core/port/, adapters in infra/persistence/"
  - "Unit tests with nested @DisplayName classes for organization"

# Metrics
duration: 4min
completed: 2026-01-20
---

# Phase 02 Plan 01: Domain Models and Markdown Parser Summary

**CommonMark-based markdown parser with YAML frontmatter extraction, domain models (Document, Chunk), and DocumentRepository port/adapter following hexagonal architecture**

## Performance

- **Duration:** 4 min
- **Started:** 2026-01-20T10:03:14Z
- **Completed:** 2026-01-20T10:07:XX
- **Tasks:** 3
- **Files modified:** 10

## Accomplishments

- Domain models (Document, Chunk, ChunkType) matching database schema with immutable Java 21 records
- MarkdownParser extracting title, category, tags from YAML frontmatter with clean content output
- DocumentRepository port interface following hexagonal architecture
- JdbcDocumentRepository adapter with PostgreSQL TEXT[] and JSONB support
- Comprehensive unit tests for MarkdownParser covering all edge cases

## Task Commits

Each task was committed atomically:

1. **Task 1: Add CommonMark dependencies and create domain models** - `3061f82` (feat)
2. **Task 2: Create MarkdownParser with frontmatter extraction** - `1437f9a` (feat)
3. **Task 3: Create DocumentRepository port and JDBC adapter** - `f204ed1` (feat)

## Files Created/Modified

- `pom.xml` - Added CommonMark 0.22.0 dependencies
- `src/main/java/fr/kalifazzia/alexandria/core/model/Document.java` - Domain entity for indexed documents
- `src/main/java/fr/kalifazzia/alexandria/core/model/Chunk.java` - Domain entity for text chunks
- `src/main/java/fr/kalifazzia/alexandria/core/model/ChunkType.java` - Enum for PARENT/CHILD chunk types
- `src/main/java/fr/kalifazzia/alexandria/core/ingestion/DocumentMetadata.java` - Structured frontmatter output
- `src/main/java/fr/kalifazzia/alexandria/core/ingestion/ParsedDocument.java` - Parsed markdown result
- `src/main/java/fr/kalifazzia/alexandria/core/ingestion/MarkdownParser.java` - CommonMark parser with YAML extension
- `src/main/java/fr/kalifazzia/alexandria/core/port/DocumentRepository.java` - Port interface for persistence
- `src/main/java/fr/kalifazzia/alexandria/infra/persistence/JdbcDocumentRepository.java` - JDBC adapter
- `src/test/java/fr/kalifazzia/alexandria/core/ingestion/MarkdownParserTest.java` - Unit tests

## Decisions Made

- **CommonMark over Flexmark:** CommonMark 0.22.0 is lighter and sufficient for frontmatter extraction
- **Immutable records with defensive copying:** All collections (tags, frontmatter) are copied in constructors
- **Factory methods for new entities:** `Document.create()` and `Chunk.create()` for pre-persistence entities
- **SQL cast for JSONB:** Using `?::jsonb` cast instead of PGobject to avoid compile-time dependency on PostgreSQL driver

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- **PostgreSQL driver scope:** Initial import of `org.postgresql.util.PGobject` failed because PostgreSQL driver is declared as `runtime` scope. Fixed by removing unused import and relying on SQL cast (`?::jsonb`) for JSONB insertion.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Domain models ready for use by HierarchicalChunker (02-02)
- MarkdownParser ready for use by IngestionService
- DocumentRepository ready for document persistence
- **Blocker:** Integration tests require Docker for PostgreSQL container

---
*Phase: 02-ingestion-core*
*Completed: 2026-01-20*
