---
phase: 09-source-management-completion
plan: 02
subsystem: mcp, source, document
tags: [mcp-tools, index-statistics, chunk-count, source-management, content-type-breakdown]

# Dependency graph
requires:
  - phase: 09-source-management-completion
    plan: 01
    provides: "source_id FK population, countBySourceId, countBySourceIdGroupedByContentType, cancelCrawl/isCancelled"
  - phase: 08-advanced-search-quality
    provides: "DocumentChunkRepository with metadata queries, updateVersionMetadata, updateSourceNameMetadata"
provides:
  - "7 fully functional MCP tools: search_docs, list_sources, add_source, remove_source, crawl_status, recrawl_source, index_statistics"
  - "Real-time chunk count with content_type breakdown in list_sources and crawl_status"
  - "remove_source with crawl cancellation, cascade delete, and chunk count feedback"
  - "index_statistics global stats tool (total chunks, sources, storage size, embedding dims, last activity)"
  - "recrawl_source name update parameter wiring updateSourceNameMetadata"
  - "SourceRepository.findMaxLastCrawledAt for last activity timestamp"
  - "DocumentChunkRepository.countAllChunks and getStorageSizeBytes global stat queries"
affects: [source-management, mcp-tools]

# Tech tracking
tech-stack:
  added: []
  patterns: ["formatChunkCount helper for content_type grouped breakdown", "formatBytes for human-readable storage sizes", "Cooperative crawl cancellation in remove_source via progressTracker.cancelCrawl"]

key-files:
  created: []
  modified:
    - "src/main/java/dev/alexandria/mcp/McpToolService.java"
    - "src/main/java/dev/alexandria/source/SourceRepository.java"
    - "src/main/java/dev/alexandria/source/Source.java"
    - "src/main/java/dev/alexandria/document/DocumentChunkRepository.java"
    - "src/test/java/dev/alexandria/mcp/McpToolServiceTest.java"

key-decisions:
  - "formatChunkCount returns plain total for single content type, breakdown for multiple (e.g. '1247 (892 prose, 355 code)')"
  - "removeSource uses Thread.sleep(500) brief wait after cancelCrawl for cooperative cancellation observation"
  - "recrawlSource name parameter is 8th (last) param to maintain backward compatibility with existing call patterns"
  - "Source.setName setter added to enable name updates (was previously immutable after construction)"

patterns-established:
  - "formatChunkCount pattern: query grouped counts, sum total, format breakdown string"
  - "formatBytes pattern: cascading KB/MB/GB thresholds with %.1f formatting"

requirements-completed: [SRC-02, SRC-03, SRC-04, SRC-05]

# Metrics
duration: 7min
completed: 2026-02-20
---

# Phase 09 Plan 02: MCP Tool Enhancements Summary

**All 7 MCP tools functional with real-time chunk counts, index_statistics global stats, crawl cancellation in remove_source, and updateSourceNameMetadata wiring in recrawl_source**

## Performance

- **Duration:** 7 min
- **Started:** 2026-02-20T14:45:41Z
- **Completed:** 2026-02-20T14:53:08Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments

- list_sources and crawl_status now show real-time chunk count with content_type breakdown (e.g. "1247 (892 prose, 355 code)") instead of stale source.getChunkCount()
- remove_source cancels active crawls, counts chunks before cascade delete, returns "Source 'X' removed (N chunks deleted)."
- New index_statistics tool returns total chunks, total sources, embedding dimensions, storage size (human-readable), and last activity timestamp
- recrawl_source accepts optional name parameter, updates Source entity and calls updateSourceNameMetadata for chunk metadata sync
- 12 new unit tests covering all enhancements (285 total, all passing)

## Task Commits

Each task was committed atomically:

1. **Task 1: Enhanced MCP tools + index_statistics + updateSourceNameMetadata wiring** - `59bae13` (feat)
2. **Task 2: Unit tests for all MCP tool enhancements** - `c4ad06a` (test)

## Files Created/Modified

- `src/main/java/dev/alexandria/mcp/McpToolService.java` - Enhanced listSources, removeSource, crawlStatus, recrawlSource; added indexStatistics tool, formatChunkCount/formatBytes helpers; updated Javadoc to list 7 tools
- `src/main/java/dev/alexandria/source/SourceRepository.java` - Added findMaxLastCrawledAt JPQL query
- `src/main/java/dev/alexandria/source/Source.java` - Added setName setter for name update support
- `src/main/java/dev/alexandria/document/DocumentChunkRepository.java` - Added countAllChunks and getStorageSizeBytes native queries
- `src/test/java/dev/alexandria/mcp/McpToolServiceTest.java` - 12 new tests, updated existing tests for 8-arg recrawlSource and real chunk count mocking

## Decisions Made

- **formatChunkCount output format**: Returns plain total for single content type (e.g. "150"), breakdown with parenthesized detail for multiple types (e.g. "1247 (892 prose, 355 code)"). Keeps output clean for single-type sources.
- **Thread.sleep(500) in removeSource**: Brief wait after cancelCrawl allows cooperative crawl loop to observe cancellation flag before proceeding with deletion. InterruptedException restores interrupt flag and continues.
- **recrawl_source name as 8th parameter**: Added as last parameter to maintain backward compatibility. Existing 7-arg calls just add null for name.
- **Source.setName setter**: Added to Source entity which previously had name set only via constructor. Required for recrawl name update feature.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added Source.setName setter missing from entity**
- **Found during:** Task 1 (recrawlSource name update wiring)
- **Issue:** Source entity had no setName method; plan called for source.setName(name) in recrawlSource
- **Fix:** Added public setName(String name) setter to Source.java
- **Files modified:** src/main/java/dev/alexandria/source/Source.java
- **Verification:** Compilation passes, tests pass
- **Committed in:** 59bae13 (Task 1 commit)

**2. [Rule 3 - Blocking] Updated existing tests for 8-arg recrawlSource signature**
- **Found during:** Task 1 (recrawlSource name parameter addition)
- **Issue:** 10 existing test calls used 7-arg recrawlSource, but method now has 8 parameters (added name)
- **Fix:** Added null as 8th argument to all existing recrawlSource test calls
- **Files modified:** src/test/java/dev/alexandria/mcp/McpToolServiceTest.java
- **Verification:** All 273 existing tests pass after update
- **Committed in:** 59bae13 (Task 1 commit)

**3. [Rule 3 - Blocking] Updated existing tests for real chunk count mocking**
- **Found during:** Task 1 (listSources and crawlStatus now call formatChunkCount)
- **Issue:** listSourcesFormatsAllSources and crawlStatusShowsCompletedSummary tests did not mock countBySourceIdGroupedByContentType
- **Fix:** Added countBySourceIdGroupedByContentType mock stubs to affected tests
- **Files modified:** src/test/java/dev/alexandria/mcp/McpToolServiceTest.java
- **Verification:** Tests pass with real chunk count format
- **Committed in:** 59bae13 (Task 1 commit)

---

**Total deviations:** 3 auto-fixed (3 blocking)
**Impact on plan:** All auto-fixes necessary for compilation and test correctness after signature changes. No scope creep.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All 7 MCP tools are fully functional with accurate data
- SRC-01 through SRC-05 requirements are complete
- Phase 09 source management completion is finished
- Ready for next phase planning

---
*Phase: 09-source-management-completion*
*Completed: 2026-02-20*
