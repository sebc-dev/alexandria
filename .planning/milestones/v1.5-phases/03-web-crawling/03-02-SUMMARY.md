---
phase: 03-web-crawling
plan: 02
subsystem: crawl
tags: [sitemap, url-normalization, bfs-crawl, crawler-commons, page-discovery, crawl-orchestration]

# Dependency graph
requires:
  - phase: 03-web-crawling
    plan: 01
    provides: Crawl4AiClient bean for single-page crawling, CrawlResult DTO, internal link extraction
provides:
  - UrlNormalizer utility for URL deduplication (fragment removal, trailing slash, host lowercasing, tracking param filtering)
  - SitemapParser component using crawler-commons for sitemap.xml and sitemap index discovery
  - PageDiscoveryService with sitemap-first strategy and link-crawl fallback signaling
  - CrawlService orchestrator that crawls entire sites with BFS link following and maxPages safety limit
affects: [04-markdown-chunking, 07-crawl-operations]

# Tech tracking
tech-stack:
  added: []
  patterns: [Sitemap-first URL discovery with link-crawl fallback, BFS queue with LinkedHashSet for ordered dedup, URL normalization before visited-set checks]

key-files:
  created:
    - src/main/java/dev/alexandria/crawl/UrlNormalizer.java
    - src/main/java/dev/alexandria/crawl/SitemapParser.java
    - src/main/java/dev/alexandria/crawl/PageDiscoveryService.java
    - src/main/java/dev/alexandria/crawl/CrawlService.java
    - src/test/java/dev/alexandria/crawl/UrlNormalizerTest.java
    - src/integrationTest/java/dev/alexandria/crawl/CrawlServiceIT.java
  modified: []

key-decisions:
  - "UrlNormalizer as static utility (no Spring bean) since it has no dependencies"
  - "SitemapParser uses URI.create().toURL() instead of deprecated new URL() constructor"
  - "CrawlService follows links only in LINK_CRAWL mode; trusts sitemap URL list when available"
  - "Sequential page crawling (no concurrency) to keep Crawl4AI sidecar stable"

patterns-established:
  - "URL normalization pipeline: fragment removal -> trailing slash -> host lowercasing -> tracking param filtering"
  - "DiscoveryResult record with method enum to signal discovery strategy to caller"
  - "BFS crawl queue with LinkedHashSet preserving insertion order + HashSet visited for O(1) dedup"

# Metrics
duration: 5min
completed: 2026-02-15
---

# Phase 3 Plan 2: Page Discovery and Crawl Orchestration Summary

**Sitemap-first page discovery with URL normalization and BFS crawl orchestration via CrawlService**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-15T10:41:05Z
- **Completed:** 2026-02-15T10:46:40Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- UrlNormalizer handles fragment removal, trailing slashes, host casing, and tracking param filtering with 10 passing unit tests
- SitemapParser discovers URLs from sitemap.xml and sitemap index files using crawler-commons (no deprecated URL constructor)
- PageDiscoveryService tries sitemap first, returns DiscoveryResult with method indicator for caller strategy selection
- CrawlService.crawlSite() orchestrates full site crawl with BFS link following, maxPages safety limit, and URL dedup
- Integration test proves end-to-end crawl orchestration against real Crawl4AI container (3 tests passing)

## Task Commits

Each task was committed atomically:

1. **Task 1: SitemapParser, UrlNormalizer, and PageDiscoveryService** - `2a17c26` (feat)
2. **Task 2: CrawlService orchestrator and integration test** - `6881f0e` (feat)

## Files Created/Modified
- `src/main/java/dev/alexandria/crawl/UrlNormalizer.java` - Static utility: normalize URLs for dedup (fragments, slashes, host casing, tracking params)
- `src/main/java/dev/alexandria/crawl/SitemapParser.java` - Spring component: parse sitemap.xml/sitemap_index.xml via crawler-commons
- `src/main/java/dev/alexandria/crawl/PageDiscoveryService.java` - Spring service: sitemap-first discovery with link-crawl fallback
- `src/main/java/dev/alexandria/crawl/CrawlService.java` - Spring service: BFS crawl orchestrator with maxPages limit
- `src/test/java/dev/alexandria/crawl/UrlNormalizerTest.java` - 10 unit tests for URL normalization edge cases
- `src/integrationTest/java/dev/alexandria/crawl/CrawlServiceIT.java` - 3 integration tests with real Crawl4AI container

## Decisions Made
- UrlNormalizer implemented as static utility class (no Spring bean) since it has no dependencies and is used across multiple classes
- SitemapParser uses `URI.create().toURL()` instead of deprecated `new URL()` constructor for JDK 21 compliance
- CrawlService follows links only in LINK_CRAWL mode (no sitemap found); when sitemap provides URL list, it trusts the sitemap and skips link following
- Sequential page crawling chosen over concurrent to keep Crawl4AI sidecar stable and avoid Chromium OOM

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed deprecated URL constructor in SitemapParser**
- **Found during:** Task 1 (SitemapParser implementation)
- **Issue:** `new URL(String)` is deprecated in JDK 21; compiler warning on every build
- **Fix:** Replaced with `URI.create(sitemapUrl).toURL()` throughout SitemapParser
- **Files modified:** src/main/java/dev/alexandria/crawl/SitemapParser.java
- **Verification:** Compilation produces no deprecation warnings
- **Committed in:** 2a17c26 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Minor improvement for JDK 21 compliance. No scope creep.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Complete crawl pipeline available: give CrawlService a root URL, get back clean Markdown for every discovered page
- CrawlService.crawlSite() ready for Phase 4 (Ingestion Pipeline) to consume CrawlResult objects
- URL normalization utilities available for any component needing URL dedup
- Phase 3 (Web Crawling) fully complete -- both plans delivered

## Self-Check: PASSED

All 6 created files verified present. Both commit hashes (2a17c26, 6881f0e) verified in git log.

---
*Phase: 03-web-crawling*
*Completed: 2026-02-15*
