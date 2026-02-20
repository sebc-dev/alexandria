---
phase: 07-crawl-operations
plan: 01
subsystem: crawl
tags: [url-filtering, glob-patterns, sha256, content-hashing, scope-control]

# Dependency graph
requires:
  - phase: 03-web-crawling
    provides: "UrlNormalizer static utility (isSameSite used by UrlScopeFilter)"
provides:
  - "CrawlScope record for immutable scope configuration"
  - "UrlScopeFilter static utility for URL filtering by glob patterns"
  - "ContentHasher static utility for SHA-256 content hashing"
affects: [07-crawl-operations, ingestion]

# Tech tracking
tech-stack:
  added: []
  patterns: [java.nio.file.PathMatcher glob matching on URL paths, HexFormat for hash formatting]

key-files:
  created:
    - src/main/java/dev/alexandria/crawl/CrawlScope.java
    - src/main/java/dev/alexandria/crawl/UrlScopeFilter.java
    - src/main/java/dev/alexandria/crawl/ContentHasher.java
    - src/test/java/dev/alexandria/crawl/CrawlScopeTest.java
    - src/test/java/dev/alexandria/crawl/UrlScopeFilterTest.java
    - src/test/java/dev/alexandria/crawl/ContentHasherTest.java
  modified: []

key-decisions:
  - "PathMatcher glob on URL paths: use java.nio.file.FileSystems.getDefault().getPathMatcher for glob pattern matching against URL path segments"
  - "Block patterns take priority: block patterns are checked before allow patterns, matching the user decision from plan"

patterns-established:
  - "CrawlScope record pattern: null-safe compact constructor with List.copyOf, static withDefaults factory"
  - "Static utility pattern: private constructor, pure static methods (matches UrlNormalizer)"

requirements-completed: [CRWL-03, CRWL-06]

# Metrics
duration: 5min
completed: 2026-02-20
---

# Phase 7 Plan 01: Crawl Scope Utilities Summary

**CrawlScope record, UrlScopeFilter with glob pattern matching, and ContentHasher SHA-256 utility for scope-limited crawling and change detection**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-20T09:30:56Z
- **Completed:** 2026-02-20T09:36:05Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- CrawlScope record with null-safe defensive copies and withDefaults factory
- UrlScopeFilter correctly filters URLs by glob patterns with block-takes-priority semantics
- ContentHasher produces deterministic SHA-256 hex strings verified against known test vectors
- 15 new unit tests covering all edge cases (malformed URLs, empty scope, pattern priority)

## Task Commits

Each task was committed atomically (TDD: RED then GREEN):

1. **Task 1: TDD CrawlScope record and UrlScopeFilter utility**
   - `f011a17` (test) - Add failing tests for CrawlScope and UrlScopeFilter
   - `088ea8c` (feat) - Implement CrawlScope record and UrlScopeFilter utility

2. **Task 2: TDD ContentHasher utility**
   - `6a76037` (test) - Add failing tests for ContentHasher
   - `fd04e50` (feat) - Implement ContentHasher SHA-256 utility

## Files Created/Modified
- `src/main/java/dev/alexandria/crawl/CrawlScope.java` - Immutable scope config record (allow/block patterns, maxDepth, maxPages)
- `src/main/java/dev/alexandria/crawl/UrlScopeFilter.java` - Static URL filtering utility with glob patterns via PathMatcher
- `src/main/java/dev/alexandria/crawl/ContentHasher.java` - Static SHA-256 content hashing utility
- `src/test/java/dev/alexandria/crawl/CrawlScopeTest.java` - 3 tests: null safety, defensive copy, factory method
- `src/test/java/dev/alexandria/crawl/UrlScopeFilterTest.java` - 8 tests: external reject, allow/block patterns, edge cases
- `src/test/java/dev/alexandria/crawl/ContentHasherTest.java` - 4 tests: known vector, determinism, uniqueness, empty string

## Decisions Made
- Used java.nio.file.PathMatcher for glob pattern matching on URL paths (as specified in plan)
- Block patterns checked before allow patterns per user decision

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- CrawlScope, UrlScopeFilter, and ContentHasher are ready for use by CrawlService scope-limited crawling (07-02+)
- UrlScopeFilter depends on UrlNormalizer.isSameSite (existing from Phase 3)
- ContentHasher ready for incremental change detection in crawl pipeline

## Self-Check: PASSED

- All 7 files verified present on disk
- All 4 commits verified in git history (f011a17, 088ea8c, 6a76037, fd04e50)
- 180 tests pass (15 new + 165 existing), 0 failures

---
*Phase: 07-crawl-operations*
*Completed: 2026-02-20*
