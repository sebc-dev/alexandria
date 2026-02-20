---
phase: 09-source-management-completion
plan: 01
subsystem: ingestion, crawl, database
tags: [source-id, fk-population, cancellation, flyway, pgvector, langchain4j]

# Dependency graph
requires:
  - phase: 07-crawl-operations
    provides: "CrawlService, CrawlProgressTracker, IngestionService pipeline, Source entity with source_id FK"
  - phase: 08-advanced-search-quality
    provides: "DocumentChunkRepository with metadata queries, version/sourceName enrichment"
provides:
  - "source_id FK populated on all new document_chunks during ingestion"
  - "updateSourceIdBatch, countBySourceId, countBySourceIdGroupedByContentType repository queries"
  - "CrawlProgressTracker cancellation support (cancelCrawl/isCancelled)"
  - "V4 Flyway migration cleaning up orphan chunks"
affects: [09-02, 09-03, source-management, mcp-tools]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Best-effort source_id linking after embeddingStore.addAll", "ConcurrentHashMap.newKeySet() for thread-safe cancellation flags"]

key-files:
  created:
    - "src/main/resources/db/migration/V4__cleanup_orphan_chunks.sql"
  modified:
    - "src/main/java/dev/alexandria/document/DocumentChunkRepository.java"
    - "src/main/java/dev/alexandria/ingestion/IngestionService.java"
    - "src/main/java/dev/alexandria/crawl/CrawlService.java"
    - "src/main/java/dev/alexandria/crawl/CrawlProgressTracker.java"
    - "src/test/java/dev/alexandria/ingestion/IngestionServiceTest.java"
    - "src/test/java/dev/alexandria/crawl/CrawlServiceTest.java"
    - "src/test/java/dev/alexandria/crawl/CrawlProgressTrackerTest.java"

key-decisions:
  - "Best-effort source_id update: updateSourceIdBatch runs as separate transaction after embeddingStore.addAll -- not atomic, but failure leaves chunks with NULL source_id (no worse than before)"
  - "6-arg ingestPage overload: sourceId as first parameter, existing 3-arg and 5-arg overloads delegate with null sourceId for backward compatibility"
  - "ConcurrentHashMap.newKeySet() for cancellation flags: thread-safe, cleaned up in completeCrawl/failCrawl/removeCrawl"

patterns-established:
  - "Post-store FK linking: capture addAll return IDs, batch-update source_id via native query"
  - "Cancellation pattern: flag-based cooperative cancellation checked at top of crawl loop"

requirements-completed: [SRC-03, SRC-01]

# Metrics
duration: 8min
completed: 2026-02-20
---

# Phase 09 Plan 01: Source ID FK Population Summary

**source_id FK populated on all ingested chunks via post-store batch UPDATE, crawl cancellation support via CrawlProgressTracker, and V4 Flyway migration to clean up historical orphans**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-20T14:33:32Z
- **Completed:** 2026-02-20T14:42:03Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments

- source_id is now populated on every document_chunks row during ingestion via batch UPDATE after embeddingStore.addAll
- CrawlProgressTracker supports cooperative crawl cancellation (cancelCrawl/isCancelled) with proper cleanup
- CrawlService checks cancellation flag at start of each crawl loop iteration
- V4 Flyway migration deletes all historical orphan chunks (source_id IS NULL)
- 3 new repository queries (updateSourceIdBatch, countBySourceId, countBySourceIdGroupedByContentType) ready for use by remove_source and chunk count features
- All 273 unit tests pass (8 new tests added)

## Task Commits

Each task was committed atomically:

1. **Task 1: source_id FK population and ingestion pipeline threading** - `80759ea` (feat)
2. **Task 2: CrawlProgressTracker cancellation + unit tests for pipeline changes** - `a4292b3` (feat)

## Files Created/Modified

- `src/main/java/dev/alexandria/document/DocumentChunkRepository.java` - Added updateSourceIdBatch, countBySourceId, countBySourceIdGroupedByContentType native queries
- `src/main/java/dev/alexandria/ingestion/IngestionService.java` - Added DocumentChunkRepository dependency, 6-arg ingestPage overload with sourceId, storeChunks captures addAll IDs and calls updateSourceIdBatch
- `src/main/java/dev/alexandria/crawl/CrawlService.java` - Passes sourceId to 6-arg ingestPage, cancellation check in crawl loop
- `src/main/java/dev/alexandria/crawl/CrawlProgressTracker.java` - cancelCrawl/isCancelled methods, cancellation cleanup in completeCrawl/failCrawl/removeCrawl
- `src/main/resources/db/migration/V4__cleanup_orphan_chunks.sql` - DELETE FROM document_chunks WHERE source_id IS NULL
- `src/test/java/dev/alexandria/ingestion/IngestionServiceTest.java` - 2 new tests for source_id batch update behavior
- `src/test/java/dev/alexandria/crawl/CrawlServiceTest.java` - Updated stubs for 6-arg ingestPage, 1 new cancellation test
- `src/test/java/dev/alexandria/crawl/CrawlProgressTrackerTest.java` - 5 new cancellation lifecycle tests

## Decisions Made

- **Best-effort source_id linking**: updateSourceIdBatch runs in its own @Transactional after embeddingStore.addAll. Not atomic, but failure only leaves chunks with NULL source_id which is no worse than before. Avoids complex transaction coordination with LangChain4j's internal JDBC.
- **6-arg ingestPage signature**: sourceId as first parameter to distinguish from existing 5-arg overload. Existing 3-arg and 5-arg overloads delegate with null sourceId for full backward compatibility.
- **ConcurrentHashMap.newKeySet()**: Thread-safe cancellation flag set, cleaned up in all terminal crawl states (complete, fail, remove).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated CrawlServiceTest stubs for new 6-arg ingestPage signature**
- **Found during:** Task 1 (source_id FK population)
- **Issue:** CrawlServiceTest stubs used 5-arg ingestPage(String, String, String, String, String) matchers, but CrawlService now calls 6-arg ingestPage(UUID, String, String, String, String, String). Mockito PotentialStubbingProblem on 4 tests.
- **Fix:** Updated all ingestPage stubs and verifications to match the 6-arg signature with any(UUID.class) or eq(sourceId) for the first parameter.
- **Files modified:** src/test/java/dev/alexandria/crawl/CrawlServiceTest.java
- **Verification:** All 265 existing tests pass after update
- **Committed in:** 80759ea (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Essential fix to keep existing tests passing after signature change. No scope creep.

## Issues Encountered

- Integration tests fail across the board due to pre-existing OrtException (ONNX reranker model file not available in this environment). Verified failure is identical on stashed clean branch -- not caused by plan changes.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- source_id FK is now populated, unblocking cascade delete (remove_source) in 09-02
- countBySourceId and countBySourceIdGroupedByContentType queries ready for source status/stats features
- CrawlProgressTracker cancellation support ready for cancel_crawl MCP tool integration
- Orphan chunks cleaned up by V4 migration -- all chunks now have source_id

---
*Phase: 09-source-management-completion*
*Completed: 2026-02-20*
