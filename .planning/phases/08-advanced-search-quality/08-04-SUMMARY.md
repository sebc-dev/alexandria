---
phase: 08-advanced-search-quality
plan: 04
subsystem: mcp
tags: [mcp-tools, search-filters, version-metadata, reranking-score, crawl-integration, token-budget]

# Dependency graph
requires:
  - phase: 08-advanced-search-quality (plan 01)
    provides: "DocumentChunkRepository batch queries, version/sourceName metadata fields"
  - phase: 08-advanced-search-quality (plan 02)
    provides: "RerankerService, SearchResult.rerankScore, cross-encoder scoring"
  - phase: 08-advanced-search-quality (plan 03)
    provides: "SearchRequest with filter fields, SearchService two-stage pipeline"
provides:
  - "search_docs MCP tool with source, sectionPath, version, contentType, minScore, rrfK filter params"
  - "add_source and recrawl_source MCP tools with version parameter"
  - "Batch version metadata update on recrawl with version change"
  - "CrawlService version+sourceName passthrough to ingestPage at all call sites"
  - "TokenBudgetTruncator with reranking score in formatted output"
  - "Empty filter result explanatory messages with available versions and sources"
affects: [mcp-tools, claude-code-integration]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Empty result explanatory messages with available metadata values", "Version metadata batch-update before crawl dispatch"]

key-files:
  created: []
  modified:
    - src/main/java/dev/alexandria/mcp/McpToolService.java
    - src/main/java/dev/alexandria/mcp/TokenBudgetTruncator.java
    - src/main/java/dev/alexandria/crawl/CrawlService.java
    - src/test/java/dev/alexandria/mcp/McpToolServiceTest.java
    - src/test/java/dev/alexandria/mcp/TokenBudgetTruncatorTest.java
    - src/test/java/dev/alexandria/crawl/CrawlServiceTest.java

key-decisions:
  - "DocumentChunkRepository injected into McpToolService for available-values queries on empty filter results"
  - "SourceRepository injected into CrawlService for version/sourceName lookup (loaded once per crawl)"
  - "rrfK accepted as search_docs parameter with debug log noting store-level-only application"
  - "Version batch-update happens before crawl dispatch so new chunks inherit updated version"

patterns-established:
  - "Empty filter result pattern: detect active filters, query available values, return guidance message"
  - "Metadata passthrough pattern: load Source once at crawl start, thread version/sourceName through all ingestPage calls"

requirements-completed: [SRCH-07, SRCH-08, SRCH-09, SRCH-10, SRCH-04, CHUNK-06]

# Metrics
duration: 8min
completed: 2026-02-20
---

# Phase 8 Plan 04: MCP Search Tool Integration Summary

**Extended search_docs with 6 filter params (source, sectionPath, version, contentType, minScore, rrfK), add_source/recrawl_source with version parameter, reranking scores in output, and CrawlService metadata passthrough**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-20T12:57:02Z
- **Completed:** 2026-02-20T13:05:59Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- search_docs MCP tool extended with source, sectionPath, version, contentType, minScore, and rrfK filter parameters (all optional, AND-logic filtering)
- add_source and recrawl_source MCP tools accept optional version parameter; recrawl batch-updates existing chunk metadata when version changes
- TokenBudgetTruncator outputs reranking score (Score: 0.850) in each formatted result for Claude Code confidence assessment
- CrawlService loads Source entity once per crawl and passes version+sourceName to ingestPage at both llms-full.txt and incremental crawl call sites
- Empty filter results return explanatory messages with available versions and sources from the index
- 265 unit tests pass (12 new), 0 new SpotBugs findings (6 pre-existing), architecture tests pass

## Task Commits

Each task was committed atomically:

1. **Task 1: MCP tool extensions + CrawlService version passthrough** - `a013c38` (feat)
2. **Task 2: Unit tests for MCP extensions + quality gates** - `f15fe8f` (test)

## Files Created/Modified
- `src/main/java/dev/alexandria/mcp/McpToolService.java` - Extended search_docs (8 params), add_source (version), recrawl_source (version + batch update), empty result messages with available values, DocumentChunkRepository injection
- `src/main/java/dev/alexandria/mcp/TokenBudgetTruncator.java` - Added Score: %.3f line using rerankScore in formatResult()
- `src/main/java/dev/alexandria/crawl/CrawlService.java` - Added SourceRepository for version/sourceName lookup, passed metadata through ingestPage at llms-full.txt and incremental call sites
- `src/test/java/dev/alexandria/mcp/McpToolServiceTest.java` - 9 new tests: filter param forwarding, minScore, empty result with/without filters, addSource version, recrawlSource version update/same/null
- `src/test/java/dev/alexandria/mcp/TokenBudgetTruncatorTest.java` - 3 new tests: rerank score formatting, zero score, backward-compatible constructor
- `src/test/java/dev/alexandria/crawl/CrawlServiceTest.java` - Updated constructor and stubs for new SourceRepository dependency and 5-arg ingestPage calls

## Decisions Made
- DocumentChunkRepository injected into McpToolService (alongside existing SourceRepository) for querying available versions and source names when filter results are empty
- SourceRepository injected into CrawlService to look up Source.version/name once at crawl start, threaded through private methods as parameters (no field caching)
- rrfK parameter accepted on search_docs with debug log noting it is store-level config only (consistent with SearchService behavior from 08-03)
- Version batch-update on recrawl executes before crawl dispatch and before status change, ensuring both existing and new chunks have consistent version metadata

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated CrawlServiceTest for new SourceRepository dependency**
- **Found during:** Task 1 (CrawlService modification)
- **Issue:** CrawlService constructor gained SourceRepository parameter; existing tests needed mock added and ingestPage stubs updated from 3-arg to 5-arg
- **Fix:** Added SourceRepository mock, updated setUp() constructor call, changed all ingestPage stubs/verifications to use 5-arg matchers with isNull() for version/sourceName
- **Files modified:** src/test/java/dev/alexandria/crawl/CrawlServiceTest.java
- **Verification:** All 265 tests pass
- **Committed in:** a013c38 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary test adaptation for constructor change. No scope creep.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 8 (Advanced Search & Quality) is fully complete
- All MCP tools expose Phase 8 capabilities to Claude Code
- Search pipeline: query -> embed -> metadata filter -> over-fetch 50 -> cross-encoder rerank -> token budget truncation with scores
- Version management: add_source/recrawl_source set version, CrawlService denormalizes into chunks, search_docs filters by version

## Self-Check: PASSED

All 6 key files verified present. Both task commits (a013c38, f15fe8f) verified in git log. 265 tests pass. 0 new SpotBugs findings. Architecture tests pass.

---
*Phase: 08-advanced-search-quality*
*Completed: 2026-02-20*
