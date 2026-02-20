---
phase: 08-advanced-search-quality
plan: 01
subsystem: ingestion, database
tags: [flyway, pgvector, jsonb, metadata, version-tagging, content-type]

# Dependency graph
requires:
  - phase: 01-foundation-infrastructure
    provides: "Source entity, DocumentChunk entity, Flyway migrations"
  - phase: 04.5-code-quality-consolidation
    provides: "DocumentChunkData record, MarkdownChunker, test infrastructure"
provides:
  - "Source.version field persisted via Flyway V3 migration"
  - "DocumentChunkData with version/sourceName metadata denormalization"
  - "IngestionService 5-param ingestPage() with version/sourceName passthrough"
  - "DocumentChunkRepository batch JSONB update queries for version/source_name"
  - "ContentType.parseSearchFilter() for case-insensitive search with MIXED -> null"
  - "findDistinctVersions() and findDistinctSourceNames() queries"
affects: [08-02, 08-03, 08-04]

# Tech tracking
tech-stack:
  added: []
  patterns: ["JSONB metadata denormalization for search filtering", "parseSearchFilter pattern for search-layer enum parsing"]

key-files:
  created:
    - src/main/resources/db/migration/V3__source_version_column.sql
    - src/test/java/dev/alexandria/ingestion/chunking/ContentTypeTest.java
    - src/test/java/dev/alexandria/ingestion/chunking/DocumentChunkDataTest.java
  modified:
    - src/main/java/dev/alexandria/source/Source.java
    - src/main/java/dev/alexandria/ingestion/chunking/DocumentChunkData.java
    - src/main/java/dev/alexandria/ingestion/IngestionService.java
    - src/main/java/dev/alexandria/document/DocumentChunkRepository.java
    - src/main/java/dev/alexandria/ingestion/chunking/ContentType.java
    - src/main/java/dev/alexandria/ingestion/chunking/MarkdownChunker.java
    - src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedChunk.java
    - src/test/java/dev/alexandria/ingestion/IngestionServiceTest.java

key-decisions:
  - "DocumentChunkData extended with nullable version/sourceName (no requireNonNull) to preserve backward compat"
  - "Batch update queries use metadata source_url matching (not source_id FK) for consistency with MCP layer"
  - "parseSearchFilter() on ContentType as separate method from fromValue() for clean separation of concerns"

patterns-established:
  - "JSONB metadata denormalization: store filterable fields in chunk metadata for search-time filtering without JOINs"
  - "Search filter parsing: separate parseSearchFilter() method handles search concepts (MIXED -> null) distinct from deserialization"

requirements-completed: [CHUNK-06, SRCH-10]

# Metrics
duration: 6min
completed: 2026-02-20
---

# Phase 8 Plan 1: Version Tagging & Metadata Denormalization Summary

**Source version field with Flyway V3 migration, chunk metadata denormalization for version/source_name, batch JSONB update queries, and case-insensitive ContentType search filter parsing**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-20T12:38:59Z
- **Completed:** 2026-02-20T12:45:02Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments
- Source entity now has a `version` column (Flyway V3) for documentation version tagging
- DocumentChunkData carries version and source_name into chunk JSONB metadata at ingestion time
- IngestionService 5-param overload passes version/sourceName through to chunks with enrichChunks() helper
- DocumentChunkRepository has native JSONB batch update queries for retroactive metadata denormalization
- ContentType.parseSearchFilter() handles case-insensitive input with MIXED -> null for search layer
- 32 new test methods across 3 test files (ContentTypeTest, DocumentChunkDataTest, IngestionServiceTest)

## Task Commits

Each task was committed atomically:

1. **Task 1: Schema migration + Source version field + metadata denormalization** - `8e9f045` (feat)
2. **Task 2: ContentType case-insensitive parsing + unit tests for metadata changes** - `7f44d38` (feat)

## Files Created/Modified
- `src/main/resources/db/migration/V3__source_version_column.sql` - Flyway migration adding version column to sources table
- `src/main/java/dev/alexandria/source/Source.java` - Added version field with getter/setter
- `src/main/java/dev/alexandria/ingestion/chunking/DocumentChunkData.java` - Extended with nullable version and sourceName fields, conditionally included in toMetadata()
- `src/main/java/dev/alexandria/ingestion/IngestionService.java` - Added 5-param ingestPage() overload with enrichChunks() private helper
- `src/main/java/dev/alexandria/document/DocumentChunkRepository.java` - Added updateVersionMetadata(), updateSourceNameMetadata(), findDistinctVersions(), findDistinctSourceNames() native queries
- `src/main/java/dev/alexandria/ingestion/chunking/ContentType.java` - Case-insensitive fromValue(), new parseSearchFilter() method
- `src/main/java/dev/alexandria/ingestion/chunking/MarkdownChunker.java` - Updated DocumentChunkData constructor calls with null version/sourceName
- `src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedChunk.java` - Updated DocumentChunkData constructor call with null version/sourceName
- `src/test/java/dev/alexandria/ingestion/chunking/ContentTypeTest.java` - New: 10 tests for case-insensitive parsing and parseSearchFilter
- `src/test/java/dev/alexandria/ingestion/chunking/DocumentChunkDataTest.java` - New: 6 tests for version/source_name metadata round-trip
- `src/test/java/dev/alexandria/ingestion/IngestionServiceTest.java` - Updated: 2 new tests for version passthrough and backward compat

## Decisions Made
- DocumentChunkData extended with nullable version/sourceName fields (no requireNonNull) to maintain backward compatibility with existing callers that pass null
- Batch update queries use metadata `source_url` matching rather than `source_id` FK column, consistent with MCP layer's string-based source identification and avoiding JOINs
- Created separate `parseSearchFilter()` method on ContentType rather than overloading `fromValue()` -- keeps `@JsonCreator` deserialization strict while search filter parsing handles MIXED -> null concept

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Version tagging infrastructure complete, ready for Plan 02 (cross-encoder reranking) and Plan 03/04 (search filtering)
- All chunk metadata fields (version, source_name, content_type) available for search filters
- Batch update queries ready for retroactive metadata denormalization of existing chunks

## Self-Check: PASSED

All 10 key files verified present. Both task commits (8e9f045, 7f44d38) verified in git log. 243 tests pass. 0 new SpotBugs findings.

---
*Phase: 08-advanced-search-quality*
*Completed: 2026-02-20*
