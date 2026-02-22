---
phase: 15-search-fusion-overhaul
plan: 01
subsystem: search
tags: [fusion, convex-combination, min-max-normalisation, hybrid-search]

# Dependency graph
requires:
  - phase: 14-parent-child-chunking
    provides: "EmbeddingMatch pipeline with deduplicateByParent and reranker"
provides:
  - "ConvexCombinationFusion static utility for score-weighted hybrid search fusion"
  - "ScoredCandidate record for typed search source results"
affects: [15-02-PLAN, search-pipeline]

# Tech tracking
tech-stack:
  added: []
  patterns: ["min-max normalisation for score fusion", "convex combination weighting"]

key-files:
  created:
    - "src/main/java/dev/alexandria/search/ConvexCombinationFusion.java"
    - "src/main/java/dev/alexandria/search/ScoredCandidate.java"
    - "src/test/java/dev/alexandria/search/ConvexCombinationFusionTest.java"
  modified: []

key-decisions:
  - "DoubleSummaryStatistics for single-pass min/max computation instead of separate stream operations"
  - "EMPTY_EMBEDDING placeholder (float[0]) for FTS-only results to maintain pipeline compatibility"

patterns-established:
  - "Convex combination fusion: combined = alpha * normVector + (1 - alpha) * normFTS"
  - "Min-max normalisation edge case: max == min yields 1.0"

requirements-completed: [FUSE-01, FUSE-02]

# Metrics
duration: 5min
completed: 2026-02-22
---

# Phase 15 Plan 01: ConvexCombinationFusion Summary

**Pure computation class for min-max normalised convex combination fusion of vector + FTS search scores**

## Performance

- **Duration:** 5 min
- **Started:** 2026-02-22T17:59:29Z
- **Completed:** 2026-02-22T18:04:45Z
- **Tasks:** 3 (RED-GREEN-REFACTOR TDD cycle)
- **Files created:** 3

## Accomplishments
- TDD-driven ConvexCombinationFusion with 13 unit tests covering all edge cases
- Min-max normalisation scales heterogeneous score ranges (cosine similarity vs ts_rank) to [0, 1]
- Alpha-weighted convex combination allows tunable vector vs FTS balance
- Pure static utility with no Spring dependencies, ready for Plan 02 pipeline wiring

## Task Commits

Each TDD phase was committed atomically:

1. **RED: Failing tests** - `08210ce` (test)
2. **GREEN: Implementation** - `a5c6879` (feat)
3. **REFACTOR: DoubleSummaryStatistics** - `0040a43` (refactor)

## Files Created/Modified
- `src/main/java/dev/alexandria/search/ConvexCombinationFusion.java` - Pure static utility: min-max normalisation + convex combination fusion (129 lines)
- `src/main/java/dev/alexandria/search/ScoredCandidate.java` - Package-private record holding embeddingId, segment, nullable embedding, raw score (17 lines)
- `src/test/java/dev/alexandria/search/ConvexCombinationFusionTest.java` - 13 unit tests covering empty inputs, single source, overlap, normalisation, alpha extremes, max==min, maxResults, sort order, embedding placeholders (213 lines)

## Decisions Made
- Used DoubleSummaryStatistics for single-pass min/max instead of separate stream operations (minor efficiency + cleaner API)
- EMPTY_EMBEDDING as float[0] placeholder for FTS-only results since reranker does not use embedding vectors
- ScoredCandidate.embedding() is @Nullable to support FTS results; ConvexCombinationFusion null-coalesces to EMPTY_EMBEDDING

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- NullAway flagged passing @Nullable ScoredCandidate.embedding() to @NonNull FusedEntry constructor; resolved by null-coalescing to EMPTY_EMBEDDING before construction

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- ConvexCombinationFusion.fuse() ready for Plan 02 to wire into SearchService
- ScoredCandidate record ready for SearchService to construct from separate vector/FTS query results
- Full test coverage provides safety net for Plan 02 integration

## Self-Check: PASSED

All 3 created files verified on disk. All 3 commits (08210ce, a5c6879, 0040a43) verified in git log.

---
*Phase: 15-search-fusion-overhaul*
*Completed: 2026-02-22*
