---
phase: 07-crawl-operations
plan: 05
subsystem: mcp
tags: [mcp, crawl, virtual-threads, async, scope-filtering]

requires:
  - phase: 07-04
    provides: "CrawlService orchestration with incremental ingestion, CrawlProgressTracker, CrawlScope.fromSource()"
  - phase: 05-mcp-server
    provides: "McpToolService with stub tool implementations, TokenBudgetTruncator"
provides:
  - "Fully functional add_source MCP tool with scope parameters and async crawl dispatch"
  - "Real-time crawl_status MCP tool showing active progress and completed summaries"
  - "recrawl_source MCP tool with incremental/full modes and one-time scope overrides"
affects: [08-polish-hardening]

tech-stack:
  added: []
  patterns: [virtual-thread-dispatch, spy-based-async-testing, scope-override-without-persist]

key-files:
  created: []
  modified:
    - src/main/java/dev/alexandria/mcp/McpToolService.java
    - src/test/java/dev/alexandria/mcp/McpToolServiceTest.java

key-decisions:
  - "dispatchCrawl extracted as package-private method for testability -- spy pattern suppresses virtual thread in unit tests"
  - "Scope overrides on recrawl are one-time: CrawlScope built from overrides, Source entity unchanged"
  - "results.size() used for chunkCount on Source after crawl (page count proxy; actual chunk counts tracked internally by CrawlService)"

patterns-established:
  - "suppressAsyncDispatch() test helper: spy on service, doNothing on dispatchCrawl to avoid virtual thread races in unit tests"
  - "Reject recrawl when source status is CRAWLING or UPDATING (active crawl guard)"

requirements-completed: [CRWL-03, CRWL-07, CRWL-09, CRWL-10]

duration: 6min
completed: 2026-02-20
---

# Phase 7 Plan 5: MCP Tool Integration Summary

**Fully functional MCP tools: add_source with scope-controlled async crawl, real-time crawl_status with filtered/error URL reporting, recrawl_source with incremental/full modes and one-time scope overrides**

## Performance

- **Duration:** 6 min
- **Started:** 2026-02-20T10:05:42Z
- **Completed:** 2026-02-20T10:11:47Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments
- Replaced all 3 stub MCP tools (add_source, crawl_status, recrawl_source) with full orchestration implementations
- add_source now accepts scope parameters (allow/block patterns, maxDepth, maxPages, llmsTxtUrl) and triggers async crawl via virtual threads
- crawl_status shows real-time progress (pages crawled/total, skipped, errors with URLs, filtered URLs) for active crawls and summary for completed sources
- recrawl_source supports incremental (default) and full modes with optional one-time scope overrides that don't persist to Source entity
- 30 unit tests covering all 6 MCP tools (211 total tests pass, 0 regressions)

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace MCP tool stubs with real crawl orchestration** - `6114d6e` (feat)
2. **Task 2: Update McpToolService unit tests for real implementations** - `2975e9e` (test)

## Files Created/Modified
- `src/main/java/dev/alexandria/mcp/McpToolService.java` - Full MCP tool implementations with CrawlService/CrawlProgressTracker/IngestionService integration, async dispatch via virtual threads
- `src/test/java/dev/alexandria/mcp/McpToolServiceTest.java` - 30 unit tests covering all tools: scope params, active/completed status, filtered URLs, full/incremental recrawl, error handling

## Decisions Made
- **dispatchCrawl as package-private method**: Extracted virtual thread dispatch for testability. Tests use Mockito spy with doNothing() to suppress async execution, avoiding race conditions.
- **Scope overrides are one-time**: recrawlSource builds CrawlScope from override params without modifying Source entity fields. Per user decision from plan.
- **results.size() for chunkCount**: Since CrawlService handles ingestion internally, the page count from results is used as a proxy for chunk count on Source entity. Actual chunk counts are tracked per-page inside CrawlService.
- **Reject recrawl for CRAWLING and UPDATING statuses**: Both indicate an active crawl operation; allowing concurrent crawls would cause data races.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added UPDATING status check to recrawl rejection**
- **Found during:** Task 1
- **Issue:** Plan only mentioned rejecting CRAWLING status, but UPDATING also indicates an active recrawl in progress
- **Fix:** Added `source.getStatus() == SourceStatus.UPDATING` check alongside CRAWLING
- **Files modified:** src/main/java/dev/alexandria/mcp/McpToolService.java
- **Verification:** recrawlSourceRejectsUpdatingSource test passes
- **Committed in:** 6114d6e (Task 1 commit)

**2. [Rule 1 - Bug] Used CrawlScope.fromSource() instead of source.toCrawlScope()**
- **Found during:** Task 1
- **Issue:** Plan code snippets used `source.toCrawlScope()` which doesn't exist. Per 07-03/07-04 architectural decision, CrawlScope.fromSource(Source) is the correct factory to maintain unidirectional crawl->source dependency.
- **Fix:** Used `CrawlScope.fromSource(source)` throughout
- **Files modified:** src/main/java/dev/alexandria/mcp/McpToolService.java
- **Verification:** ArchUnit architecture tests pass (no package cycles)
- **Committed in:** 6114d6e (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (2 bugs)
**Impact on plan:** Both fixes were necessary for correctness and ArchUnit compliance. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 7 (Crawl Operations) is fully complete: all 5 plans executed
- All MCP tools are functional end-to-end: add_source -> crawl_status -> recrawl_source
- Ready for Phase 8 (Polish & Hardening)

## Self-Check: PASSED

All files verified present, all commits verified in git log.

---
*Phase: 07-crawl-operations*
*Completed: 2026-02-20*
