---
phase: 05-recherche-avancee
plan: 01
subsystem: search
tags: [pgvector, tsvector, rrf, hybrid-search, full-text]

# Dependency graph
requires:
  - phase: 04-recherche-base
    provides: SearchRepository interface, SearchService, SearchResult, SearchFilters
provides:
  - HybridSearchFilters record for RRF configuration
  - hybridSearch method combining pgvector and tsvector
  - CTE-based SQL with FULL OUTER JOIN for result fusion
affects: [05-02-graph-traversal, 06-mcp-server]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "RRF (Reciprocal Rank Fusion) for score-independent ranking"
    - "CTE pattern with FULL OUTER JOIN for result fusion"
    - "websearch_to_tsquery for user-friendly full-text queries"

key-files:
  created:
    - src/main/java/fr/kalifazzia/alexandria/core/search/HybridSearchFilters.java
  modified:
    - src/main/java/fr/kalifazzia/alexandria/core/port/SearchRepository.java
    - src/main/java/fr/kalifazzia/alexandria/infra/persistence/JdbcSearchRepository.java
    - src/main/java/fr/kalifazzia/alexandria/core/search/SearchService.java
    - src/test/java/fr/kalifazzia/alexandria/core/search/SearchServiceTest.java

key-decisions:
  - "RRF k=60 default for balanced rank sensitivity"
  - "websearch_to_tsquery for user-friendly query syntax (handles quotes, OR, negation)"
  - "FULL OUTER JOIN to include results from either vector or text search"
  - "COALESCE(1.0/(k+rank), 0.0) formula handles NULL ranks gracefully"

patterns-established:
  - "HybridSearchFilters: separate filter record for hybrid search with RRF params"
  - "CTE pattern: vector_search + text_search CTEs joined with FULL OUTER JOIN"

# Metrics
duration: 4min
completed: 2026-01-20
---

# Phase 05 Plan 01: Hybrid Search Summary

**CTE-based hybrid search combining pgvector semantic similarity with tsvector full-text using Reciprocal Rank Fusion (RRF)**

## Performance

- **Duration:** 4 min
- **Started:** 2026-01-20T17:22:43Z
- **Completed:** 2026-01-20T17:26:26Z
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- HybridSearchFilters record with vectorWeight, textWeight, rrfK for RRF configuration
- hybridSearch method on SearchRepository using CTE-based SQL with FULL OUTER JOIN
- SearchService orchestrates embedding generation and hybrid search execution
- Unit tests verify hybrid search flow with default RRF parameters

## Task Commits

Each task was committed atomically:

1. **Task 1: Create HybridSearchFilters record** - `cb524b4` (feat)
2. **Task 2: Add hybridSearch to SearchRepository and implement in JdbcSearchRepository** - `7d45129` (feat)
3. **Task 3: Add hybridSearch to SearchService and update tests** - `dc0b87a` (feat)

## Files Created/Modified

- `src/main/java/fr/kalifazzia/alexandria/core/search/HybridSearchFilters.java` - Filter record with RRF parameters (vectorWeight, textWeight, rrfK)
- `src/main/java/fr/kalifazzia/alexandria/core/port/SearchRepository.java` - Added hybridSearch interface method
- `src/main/java/fr/kalifazzia/alexandria/infra/persistence/JdbcSearchRepository.java` - CTE-based hybrid search with RRF scoring
- `src/main/java/fr/kalifazzia/alexandria/core/search/SearchService.java` - Hybrid search orchestration
- `src/test/java/fr/kalifazzia/alexandria/core/search/SearchServiceTest.java` - Unit tests for hybrid search

## Decisions Made

- **RRF k=60:** Standard default from literature for balanced rank sensitivity
- **websearch_to_tsquery:** User-friendly query syntax (handles quotes for phrases, OR, negation with -)
- **FULL OUTER JOIN:** Ensures results from either search method are included in fusion
- **COALESCE for NULL ranks:** When a chunk appears in only one CTE, COALESCE(1.0/(k+rank), 0.0) gives it score from that method only

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Hybrid search ready for integration testing
- Graph traversal (plan 05-02) can proceed independently
- MCP server (phase 06) can expose both search and hybrid search endpoints

---
*Phase: 05-recherche-avancee*
*Completed: 2026-01-20*
