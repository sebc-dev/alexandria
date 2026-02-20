---
phase: 07-crawl-operations
plan: 03
subsystem: crawl, database
tags: [flyway, jpa, concurrenthashmap, llms-txt, sitemap, restclient, scope-filtering]

# Dependency graph
requires:
  - phase: 07-01
    provides: "CrawlScope record and UrlScopeFilter for URL filtering"
  - phase: 07-02
    provides: "LlmsTxtParser for parsing llms.txt/llms-full.txt content"
provides:
  - "V2 Flyway migration adding scope columns to sources table"
  - "Source entity with scope config fields and pattern parsing helpers"
  - "CrawlScope.fromSource() factory method for building scope from Source"
  - "IngestionStateRepository query extensions for incremental crawling"
  - "CrawlProgressTracker for thread-safe in-memory crawl progress"
  - "CrawlProgress immutable record with Status enum"
  - "PageDiscoveryService llms.txt > sitemap > link crawl discovery cascade"
  - "DiscoveryResult with llmsFullContent for hybrid ingestion"
affects: [07-04, 07-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ConcurrentHashMap.computeIfPresent for atomic read-modify-write on immutable records"
    - "Answer-based URI routing in RestClient mock chains for multi-endpoint tests"
    - "CrawlScope.fromSource() factory to avoid bidirectional package dependency"

key-files:
  created:
    - src/main/resources/db/migration/V2__source_scope_columns.sql
    - src/main/java/dev/alexandria/crawl/CrawlProgress.java
    - src/main/java/dev/alexandria/crawl/CrawlProgressTracker.java
    - src/test/java/dev/alexandria/crawl/CrawlProgressTrackerTest.java
  modified:
    - src/main/java/dev/alexandria/source/Source.java
    - src/main/java/dev/alexandria/ingestion/IngestionStateRepository.java
    - src/main/java/dev/alexandria/crawl/CrawlScope.java
    - src/main/java/dev/alexandria/crawl/PageDiscoveryService.java
    - src/test/java/dev/alexandria/crawl/PageDiscoveryServiceTest.java
    - src/test/java/dev/alexandria/crawl/CrawlServiceTest.java
    - src/test/java/dev/alexandria/fixture/SourceBuilder.java

key-decisions:
  - "CrawlProgress uses own Status enum instead of SourceStatus to avoid crawl<->source package cycle"
  - "CrawlScope.fromSource() factory method on CrawlScope instead of Source.toCrawlScope() to maintain unidirectional crawl->source dependency"
  - "Comma-separated TEXT columns for allow/block patterns (simplest JPA mapping, no Hibernate array type complexity)"
  - "Answer-based URI routing for PageDiscoveryService RestClient mock chain"

patterns-established:
  - "ConcurrentHashMap.computeIfPresent pattern: immutable records in concurrent map with atomic updates"
  - "Factory method on consuming class (CrawlScope.fromSource) to prevent bidirectional package deps"

requirements-completed: [CRWL-03, CRWL-06, CRWL-09, CRWL-11]

# Metrics
duration: 7min
completed: 2026-02-20
---

# Phase 7 Plan 3: Infrastructure Layer Summary

**V2 schema migration with Source scope fields, CrawlProgressTracker for thread-safe crawl tracking, and PageDiscoveryService llms.txt discovery cascade**

## Performance

- **Duration:** 7 min
- **Started:** 2026-02-20T09:40:33Z
- **Completed:** 2026-02-20T09:48:29Z
- **Tasks:** 2
- **Files modified:** 11

## Accomplishments
- V2 Flyway migration adds allow_patterns, block_patterns, max_depth, max_pages, llms_txt_url columns to sources table
- Source entity extended with scope config fields, comma-separated pattern parsing, and CrawlScope.fromSource() factory
- IngestionStateRepository extended with findAllBySourceId, deleteAllBySourceId, deleteAllBySourceIdAndPageUrlNotIn for incremental crawling
- CrawlProgressTracker provides thread-safe in-memory progress tracking with ConcurrentHashMap atomic updates
- PageDiscoveryService implements 4-level discovery cascade: llms-full.txt > llms.txt > sitemap.xml > link crawl
- 13 new tests (9 CrawlProgressTracker + 4 PageDiscoveryService llms.txt) with 0 regressions (193 total)

## Task Commits

Each task was committed atomically:

1. **Task 1: V2 migration, Source scope fields, IngestionStateRepository extensions** - `2782771` (feat)
2. **Task 2: CrawlProgressTracker and PageDiscoveryService llms.txt cascade** - `b4547de` (feat)

## Files Created/Modified
- `src/main/resources/db/migration/V2__source_scope_columns.sql` - Schema migration for scope columns on sources table
- `src/main/java/dev/alexandria/source/Source.java` - Scope config fields with comma-separated pattern parsing helpers
- `src/main/java/dev/alexandria/ingestion/IngestionStateRepository.java` - Extended with source-level query methods for incremental crawling
- `src/main/java/dev/alexandria/crawl/CrawlScope.java` - Added fromSource() factory method
- `src/main/java/dev/alexandria/crawl/CrawlProgress.java` - Immutable crawl progress snapshot record with Status enum
- `src/main/java/dev/alexandria/crawl/CrawlProgressTracker.java` - Thread-safe in-memory progress tracker using ConcurrentHashMap
- `src/main/java/dev/alexandria/crawl/PageDiscoveryService.java` - llms.txt > sitemap > link crawl discovery cascade
- `src/test/java/dev/alexandria/crawl/CrawlProgressTrackerTest.java` - 9 unit tests for progress tracker
- `src/test/java/dev/alexandria/crawl/PageDiscoveryServiceTest.java` - Updated with 4 new llms.txt cascade tests
- `src/test/java/dev/alexandria/crawl/CrawlServiceTest.java` - Updated 10 DiscoveryResult call sites for 3-arg constructor
- `src/test/java/dev/alexandria/fixture/SourceBuilder.java` - Builder methods for all new scope fields

## Decisions Made
- **CrawlProgress.Status enum instead of SourceStatus**: CrawlProgress record needs a status field. Using SourceStatus would create a crawl->source dependency; combined with Source importing from crawl (for toCrawlScope), this would create a package cycle violating ArchUnit's no_package_cycles rule. Solution: dedicated CrawlProgress.Status enum (CRAWLING, COMPLETED, FAILED) in the crawl package.
- **CrawlScope.fromSource() instead of Source.toCrawlScope()**: Plan specified Source.toCrawlScope() which would create source->crawl dependency. Moving to CrawlScope.fromSource() maintains unidirectional crawl->source flow and avoids the cycle.
- **Comma-separated TEXT for patterns**: Plan deliberated between TEXT[], JSONB, and comma-separated TEXT. Chose simplest JPA mapping with helper methods for parsing.
- **Answer-based URI routing in tests**: RestClient mock chain uses answer-based routing on uri() calls to route different URLs to different responses, avoiding wildcard capture issues with Mockito generics.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] CrawlProgress uses own Status enum instead of SourceStatus**
- **Found during:** Task 2 (CrawlProgress record creation)
- **Issue:** Plan specified `SourceStatus status` field in CrawlProgress. This would create crawl->source dependency. Combined with Source.toCrawlScope() creating source->crawl dependency, this would create a package cycle detected by ArchUnit.
- **Fix:** Created CrawlProgress.Status enum (CRAWLING, COMPLETED, FAILED) within crawl package. Also moved toCrawlScope() to CrawlScope.fromSource() factory.
- **Files modified:** CrawlProgress.java, CrawlScope.java, Source.java
- **Verification:** `./quality.sh test` passes, ArchUnit cycle detection passes
- **Committed in:** 2782771, b4547de

---

**Total deviations:** 1 auto-fixed (1 blocking - package cycle prevention)
**Impact on plan:** Necessary to maintain clean architecture. Same functionality, different location.

## Issues Encountered
- Mockito wildcard capture type mismatch with `RestClient.RequestHeadersUriSpec<?>`: resolved by using answer-based URI routing pattern instead of direct thenReturn() stubs

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Schema migration, scope config, progress tracking, and discovery cascade all ready for crawl orchestrator (Plan 04)
- CrawlScope.fromSource() available for orchestrator to build scope from Source entity
- CrawlProgressTracker ready for real-time progress tracking during orchestrated crawls
- DiscoveryResult.llmsFullContent() enables hybrid ingestion in Plan 04

---
*Phase: 07-crawl-operations*
*Completed: 2026-02-20*
