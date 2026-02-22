---
phase: 15-search-fusion-overhaul
plan: 02
subsystem: search
tags: [convex-combination, hybrid-search, parallel-queries, virtual-threads, configuration-properties]

# Dependency graph
requires:
  - phase: 15-search-fusion-overhaul
    plan: 01
    provides: "ConvexCombinationFusion static utility and ScoredCandidate record"
  - phase: 14-parent-child-chunking
    provides: "Parent-child dedup, resolve, substitute pipeline in SearchService"
provides:
  - "Fully functional CC-based hybrid search pipeline with configurable alpha and rerank-candidates"
  - "SearchProperties @ConfigurationProperties for externalised search tuning"
  - "Standalone FTS query in DocumentChunkRepository for dual-source retrieval"
  - "Complete RRF removal from all production code"
affects: [search-pipeline, mcp-tools, evaluation]

# Tech tracking
tech-stack:
  added: []
  patterns: ["dual-query parallel search with CompletableFuture", "SearchProperties @ConfigurationProperties for search config", "standalone FTS native query with metadata extraction"]

key-files:
  created:
    - "src/main/java/dev/alexandria/search/SearchProperties.java"
  modified:
    - "src/main/java/dev/alexandria/search/SearchService.java"
    - "src/main/java/dev/alexandria/config/EmbeddingConfig.java"
    - "src/main/java/dev/alexandria/document/DocumentChunkRepository.java"
    - "src/main/java/dev/alexandria/search/SearchRequest.java"
    - "src/main/java/dev/alexandria/mcp/McpToolService.java"
    - "src/main/java/dev/alexandria/search/SearchResult.java"
    - "src/main/java/dev/alexandria/search/RerankerService.java"
    - "src/main/resources/application.yml"
    - "src/test/java/dev/alexandria/search/SearchServiceTest.java"
    - "src/test/java/dev/alexandria/search/SearchRequestTest.java"
    - "src/test/java/dev/alexandria/mcp/McpToolServiceTest.java"

key-decisions:
  - "CompletableFuture.supplyAsync() for parallel vector+FTS queries (virtual threads enabled at Spring Boot level)"
  - "FTS query returns individual metadata fields (not JSONB blob) to avoid ObjectMapper dependency in SearchService"
  - "SearchProperties as @Configuration + @ConfigurationProperties class with @PostConstruct validation (not record)"
  - "FTS without metadata filtering; fusion + dedup + rerank naturally filter irrelevant candidates"

patterns-established:
  - "Dual-query parallel search: vector + FTS -> CC fusion -> dedup -> rerank -> parent text"
  - "SearchProperties for externalised alpha/rerankCandidates with startup validation"

requirements-completed: [FUSE-01, FUSE-02, FUSE-03]

# Metrics
duration: 10min
completed: 2026-02-22
---

# Phase 15 Plan 02: SearchService CC Fusion Pipeline Wiring Summary

**Dual-query parallel search pipeline with Convex Combination fusion replacing RRF, configurable alpha/rerank-candidates via application.yml**

## Performance

- **Duration:** 10 min
- **Started:** 2026-02-22T18:07:15Z
- **Completed:** 2026-02-22T18:17:29Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- Complete RRF removal from all production code (SearchRequest, EmbeddingConfig, McpToolService, application.yml)
- SearchService rewired for parallel vector + FTS queries fused via ConvexCombinationFusion
- SearchProperties validates alpha [0.0, 1.0] and rerankCandidates [10, 100] at startup
- EmbeddingStore switched from HYBRID to VECTOR mode for standalone vector search
- Standalone fullTextSearch() native query added to DocumentChunkRepository with metadata field extraction
- All 378 tests pass (5 new tests added for dual-query pipeline)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create SearchProperties, add FTS query, configure VECTOR-only store** - `d42c981` (feat)
2. **Task 2: Rewire SearchService for dual-query parallel fusion pipeline** - `0961d9f` (feat)

## Files Created/Modified
- `src/main/java/dev/alexandria/search/SearchProperties.java` - @ConfigurationProperties for alpha (0.7) and rerankCandidates (30) with @PostConstruct validation
- `src/main/java/dev/alexandria/search/SearchService.java` - Dual-query parallel pipeline: embed -> parallel (vector + FTS) -> CC fusion -> dedup -> rerank -> parent text
- `src/main/java/dev/alexandria/config/EmbeddingConfig.java` - Switched from SearchMode.HYBRID to SearchMode.VECTOR, removed rrfK parameter
- `src/main/java/dev/alexandria/document/DocumentChunkRepository.java` - Added fullTextSearch() native query with individual metadata field extraction
- `src/main/java/dev/alexandria/search/SearchRequest.java` - Removed rrfK field (8-arg to 7-arg record)
- `src/main/java/dev/alexandria/mcp/McpToolService.java` - Removed rrfK @ToolParam from searchDocs
- `src/main/java/dev/alexandria/search/SearchResult.java` - Updated Javadoc (RRF -> CC fused score)
- `src/main/java/dev/alexandria/search/RerankerService.java` - Updated Javadoc (RRF -> fused candidates)
- `src/main/resources/application.yml` - Replaced rrf-k: 60 with alpha: 0.7 and rerank-candidates: 30
- `src/test/java/dev/alexandria/search/SearchServiceTest.java` - Rewrote for dual-query pipeline with SearchProperties stub, 5 new tests
- `src/test/java/dev/alexandria/search/SearchRequestTest.java` - Updated for 7-arg record
- `src/test/java/dev/alexandria/mcp/McpToolServiceTest.java` - Updated for 7-param searchDocs

## Decisions Made
- Used CompletableFuture.supplyAsync() for parallel vector+FTS queries; virtual threads already enabled at Spring Boot level so default executor uses them
- FTS query extracts individual metadata fields (source_url, section_path, chunk_type, etc.) via JSON operators, avoiding ObjectMapper dependency in SearchService
- SearchProperties uses @Configuration + @ConfigurationProperties with @PostConstruct validation (not a record) since startup validation needs imperative logic
- FTS query does not apply metadata filters; fusion + dedup + rerank pipeline naturally handles irrelevant candidates (filters only applied on vector side via EmbeddingStore)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated McpToolServiceTest for rrfK removal**
- **Found during:** Task 1 (rrfK removal from McpToolService)
- **Issue:** McpToolServiceTest called searchDocs with 8 arguments; needed update to 7
- **Fix:** Updated all 15 searchDocs() calls in McpToolServiceTest to remove rrfK argument
- **Files modified:** src/test/java/dev/alexandria/mcp/McpToolServiceTest.java
- **Committed in:** d42c981 (Task 1 commit)

**2. [Rule 3 - Blocking] Updated RerankerService Javadoc**
- **Found during:** Task 1 (RRF reference cleanup)
- **Issue:** RerankerService Javadoc still referenced "RRF hybrid search candidates"
- **Fix:** Updated to "fused search candidates"
- **Files modified:** src/main/java/dev/alexandria/search/RerankerService.java
- **Committed in:** d42c981 (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (2 blocking)
**Impact on plan:** Both fixes required for complete RRF removal and compilation. No scope creep.

## Issues Encountered
- Java type inference issue with `List.of(objectArrayRow)` resolving to `List<Object>` instead of `List<Object[]>`; fixed with explicit `List.<Object[]>of(row)` type witness

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Search fusion overhaul complete; CC-based hybrid search fully operational
- Phase 15 done: ConvexCombinationFusion (Plan 01) + pipeline wiring (Plan 02) both shipped
- Ready for Phase 16 (next phase in roadmap)

## Self-Check: PASSED

All created files verified on disk. Both commits (d42c981, 0961d9f) verified in git log.

---
*Phase: 15-search-fusion-overhaul*
*Completed: 2026-02-22*
