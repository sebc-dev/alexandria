---
phase: 07-crawl-operations
plan: 04
subsystem: crawl, ingestion
tags: [crawl-orchestration, scope-filtering, incremental-ingestion, content-hash, progress-tracking, llms-txt]

# Dependency graph
requires:
  - phase: 07-01
    provides: UrlScopeFilter for scope pattern matching, ContentHasher for SHA-256 hashing
  - phase: 07-02
    provides: PageDiscoveryService with llms.txt/llms-full.txt discovery and LlmsTxtParser
  - phase: 07-03
    provides: CrawlProgressTracker, CrawlProgress, CrawlScope, IngestionState, IngestionStateRepository
provides:
  - CrawlService with full scope-filtered, depth-tracked, incremental crawling
  - IngestionService with deleteChunksForUrl and clearIngestionState
  - Hash-based change detection avoiding re-ingestion of unchanged pages
  - Deleted page cleanup removing orphaned chunks post-crawl
  - llms-full.txt hybrid ingestion (direct ingest + gap filling)
affects: [07-05-mcp-tools, future-recrawl]

# Tech tracking
tech-stack:
  added: []
  patterns: [incremental-ingestion, hash-change-detection, depth-tracked-bfs, scope-filtered-crawl]

key-files:
  created: []
  modified:
    - src/main/java/dev/alexandria/crawl/CrawlService.java
    - src/main/java/dev/alexandria/ingestion/IngestionService.java
    - src/test/java/dev/alexandria/crawl/CrawlServiceTest.java
    - src/test/java/dev/alexandria/ingestion/IngestionServiceTest.java
    - src/integrationTest/java/dev/alexandria/ingestion/IngestionServiceIT.java

key-decisions:
  - "Incremental ingestion logic in CrawlService (not IngestionService) to avoid ArchUnit package cycle"
  - "Removed ingest(List<CrawlResult>) from IngestionService to break ingestion->crawl dependency"
  - "Added deleteChunksForUrl() to IngestionService as URL-scoped chunk deletion API"
  - "CrawlService uses LinkedHashMap<URL, depth> for BFS with depth tracking"
  - "llms-full.txt covered URLs tracked in HashSet and skipped during crawl loop"

patterns-established:
  - "Unidirectional dependency: crawl -> ingestion (never reverse)"
  - "Incremental ingestion: hash check -> delete old -> re-ingest -> update state"
  - "Post-crawl cleanup: compare ingestion state against crawled URLs set"

requirements-completed: [CRWL-03, CRWL-06, CRWL-11]

# Metrics
duration: 10min
completed: 2026-02-20
---

# Phase 7 Plan 4: Crawl Orchestration Summary

**Scope-filtered BFS crawl with depth tracking, hash-based incremental ingestion, llms-full.txt hybrid ingest, progress reporting, and deleted page cleanup**

## Performance

- **Duration:** 10 min
- **Started:** 2026-02-20T09:51:54Z
- **Completed:** 2026-02-20T10:02:41Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- CrawlService evolved from simple BFS to full scope-aware orchestrator with depth tracking, incremental ingestion, progress reporting, and llms-full.txt hybrid support
- Hash-based change detection skips unchanged pages entirely, deletes old chunks and re-ingests changed pages
- Post-crawl cleanup detects and removes orphaned pages (chunks + ingestion state)
- ArchUnit package cycle resolved by moving incremental logic to CrawlService

## Task Commits

Each task was committed atomically:

1. **Task 1: IngestionService incremental ingestion** - `a5011f5` (feat)
2. **Task 2: CrawlService scope/depth/progress/llms-full.txt** - `6150bee` (feat)

## Files Created/Modified
- `src/main/java/dev/alexandria/crawl/CrawlService.java` - Evolved with scope filtering, depth tracking, incremental ingestion, progress, llms-full.txt hybrid, deleted page cleanup
- `src/main/java/dev/alexandria/ingestion/IngestionService.java` - Added deleteChunksForUrl, clearIngestionState; removed ingest(List<CrawlResult>) and crawl package imports
- `src/test/java/dev/alexandria/crawl/CrawlServiceTest.java` - 17 tests covering existing + scope + depth + progress + llms-full.txt + cleanup + incremental
- `src/test/java/dev/alexandria/ingestion/IngestionServiceTest.java` - 7 tests for ingestPage, deleteChunksForUrl, clearIngestionState
- `src/integrationTest/java/dev/alexandria/ingestion/IngestionServiceIT.java` - Updated to use ingestPage instead of removed ingest(List<CrawlResult>)

## Decisions Made
- **Incremental ingestion in CrawlService**: Moved hash comparison and state management from IngestionService to CrawlService to avoid ArchUnit no_package_cycles violation (crawl<->ingestion bidirectional dependency)
- **Removed ingest(List<CrawlResult>)**: This method created ingestion->crawl dependency via CrawlResult import; replaced with per-page ingestPage calls
- **deleteChunksForUrl API**: Added to IngestionService as clean abstraction for URL-scoped chunk deletion, used by CrawlService for both incremental re-ingestion and orphaned page cleanup
- **LinkedHashMap for depth-tracked BFS**: Maps URL to discovery depth, replaces LinkedHashSet; enables maxDepth enforcement without separate data structure

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] ArchUnit package cycle between crawl and ingestion packages**
- **Found during:** Task 2 (CrawlService evolution)
- **Issue:** CrawlService imported IngestionService/IngestionState/IngestionStateRepository (crawl->ingestion) while IngestionService imported CrawlResult/ContentHasher/UrlNormalizer (ingestion->crawl), creating a bidirectional dependency cycle detected by ArchUnit no_package_cycles rule
- **Fix:** Moved incremental ingestion logic (hash check, state management) from IngestionService into CrawlService. Removed ingest(List<CrawlResult>) to eliminate CrawlResult import. Added deleteChunksForUrl() to IngestionService as abstraction for embedding store deletion. Updated integration test to use ingestPage() directly.
- **Files modified:** IngestionService.java, CrawlService.java, IngestionServiceTest.java, CrawlServiceTest.java, IngestionServiceIT.java
- **Verification:** All 199 tests pass including ArchUnit no_package_cycles
- **Committed in:** 6150bee (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Architectural change was necessary to maintain ArchUnit package cycle enforcement. No scope creep -- same functionality, different code location.

## Issues Encountered
None beyond the ArchUnit deviation described above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- CrawlService ready for MCP tool integration (Plan 05)
- crawlSite(UUID, String, CrawlScope) is the new primary API for source-aware crawling
- clearIngestionState(UUID) available for full recrawl reset
- All Plan 01-04 utilities integrated into working crawl orchestrator

---
*Phase: 07-crawl-operations*
*Completed: 2026-02-20*
