---
phase: 02-core-search
plan: 01
subsystem: search
tags: [pgvector, langchain4j, hybrid-search, rrf, gin-index, flyway, embedding-store]

# Dependency graph
requires:
  - phase: 01-foundation-infrastructure
    plan: 02
    provides: "EmbeddingConfig with BgeSmallEnV15 ONNX model and PgVectorEmbeddingStore beans, V1 Flyway schema"
provides:
  - "V2 Flyway migration fixing GIN index expression for LangChain4j hybrid search compatibility"
  - "EmbeddingStore bean reconfigured with SearchMode.HYBRID, textSearchConfig english, rrfK 60"
  - "SearchService wrapping EmbeddingStore with domain DTOs and citation metadata extraction"
  - "SearchRequest record with input validation and default maxResults of 10"
  - "SearchResult record with text, score, sourceUrl, sectionPath"
affects: [02-02-integration-tests, 05-mcp-server, 08-advanced-search]

# Tech tracking
tech-stack:
  added: [hybrid-search-mode, rrf-fusion]
  patterns: [search-service-wrapping-embedding-store, domain-dtos-for-search, snake-case-metadata-keys]

key-files:
  created:
    - src/main/resources/db/migration/V2__fix_gin_index_for_hybrid_search.sql
    - src/main/java/dev/alexandria/search/SearchRequest.java
    - src/main/java/dev/alexandria/search/SearchResult.java
    - src/main/java/dev/alexandria/search/SearchService.java
  modified:
    - src/main/java/dev/alexandria/config/EmbeddingConfig.java

key-decisions:
  - "SearchMode is nested enum PgVectorEmbeddingStore.SearchMode, not top-level class (research doc was incorrect)"
  - "Metadata keys use snake_case convention: source_url, section_path -- must match ingestion-time keys"
  - "RRF k=60 (standard default from original RRF paper) for v1, tunable in Phase 8"

patterns-established:
  - "SearchService wraps EmbeddingStore.search() and maps EmbeddingMatch to domain SearchResult DTO"
  - "SearchRequest validates input (non-blank query, maxResults >= 1) and defaults maxResults to 10"
  - "Hybrid search always passes both queryEmbedding AND query text to EmbeddingSearchRequest"

# Metrics
duration: 4min
completed: 2026-02-15
---

# Phase 2 Plan 01: Hybrid Search Infrastructure Summary

**Hybrid-configured EmbeddingStore with V2 GIN index migration, SearchService mapping EmbeddingMatch results to domain DTOs with citation metadata**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-15T10:38:41Z
- **Completed:** 2026-02-15T10:42:58Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- V2 Flyway migration drops and recreates GIN index with `coalesce(text, '')` expression matching LangChain4j's hybrid search SQL, ensuring index usage instead of sequential scans
- EmbeddingStore bean reconfigured with `SearchMode.HYBRID`, `textSearchConfig("english")`, and `rrfK(60)` for vector + keyword search with Reciprocal Rank Fusion
- SearchService orchestrating hybrid search: embeds query via ONNX model, passes both embedding and text query to EmbeddingStore, maps results extracting citation metadata (source_url, section_path) from TextSegment metadata
- SearchRequest record with compact constructor validation and convenience constructor defaulting to 10 results
- SearchResult record carrying text, score, sourceUrl, and sectionPath for downstream consumption by MCP server

## Task Commits

Each task was committed atomically:

1. **Task 1: V2 Flyway migration and hybrid EmbeddingStore configuration** - `0ac2f02` (feat)
2. **Task 2: SearchRequest, SearchResult DTOs and SearchService** - `7dd88f3` (feat)

## Files Created/Modified
- `src/main/resources/db/migration/V2__fix_gin_index_for_hybrid_search.sql` - Drops old GIN index, recreates with coalesce(text, '') matching LangChain4j SQL
- `src/main/java/dev/alexandria/config/EmbeddingConfig.java` - Added SearchMode.HYBRID, textSearchConfig, rrfK to EmbeddingStore bean
- `src/main/java/dev/alexandria/search/SearchRequest.java` - Domain request record with validation and default maxResults=10
- `src/main/java/dev/alexandria/search/SearchResult.java` - Domain result record with text, score, sourceUrl, sectionPath
- `src/main/java/dev/alexandria/search/SearchService.java` - Search orchestration with embedding + hybrid store delegation + metadata mapping

## Decisions Made
- **PgVectorEmbeddingStore.SearchMode (nested enum):** Research doc assumed `dev.langchain4j.store.embedding.pgvector.SearchMode` as a top-level class, but it is actually `PgVectorEmbeddingStore.SearchMode` (a nested enum). Import corrected to use the inner class reference.
- **Metadata key convention:** Using snake_case keys (`source_url`, `section_path`) for consistency. These must match the keys used during ingestion in Phase 4.
- **RRF k=60:** Standard default from the original RRF paper. Tunable later in Phase 8 with real usage data.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed SearchMode import path: nested enum, not top-level class**
- **Found during:** Task 1 (EmbeddingConfig hybrid configuration)
- **Issue:** Research doc and plan specified `import dev.langchain4j.store.embedding.pgvector.SearchMode` but the class is actually a nested enum `PgVectorEmbeddingStore$SearchMode`
- **Fix:** Changed import to `import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore.SearchMode`
- **Files modified:** src/main/java/dev/alexandria/config/EmbeddingConfig.java
- **Verification:** `./gradlew build -x integrationTest -x test` compiles successfully
- **Committed in:** 0ac2f02

---

**Total deviations:** 1 auto-fixed (1 blocking import issue)
**Impact on plan:** Auto-fix necessary for compilation. No scope creep.

## Issues Encountered
None beyond the SearchMode import deviation.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Hybrid search infrastructure is complete: V2 migration, HYBRID-configured EmbeddingStore, SearchService with DTOs
- Phase 2 Plan 02 (integration tests) can proceed: SearchService, SearchRequest, SearchResult are available for injection and testing
- Phase 4 (Ingestion) must use `source_url` and `section_path` as metadata keys to match SearchService extraction
- Integration tests will need Docker runtime (Testcontainers with pgvector) -- available in CI or with Docker Desktop WSL integration

## Self-Check: PASSED

- All 5 created/modified source files verified present on disk
- SUMMARY.md verified present
- Both task commits verified in git log (0ac2f02, 7dd88f3)
- `./gradlew build -x integrationTest` passes (compilation + unit tests green)

---
*Phase: 02-core-search*
*Completed: 2026-02-15*
