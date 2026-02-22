---
phase: 13-retrieval-evaluation-framework
plan: 03
subsystem: search
tags: [evaluation, integration-test, orchestration, golden-set, retrieval-quality, ir-metrics]

# Dependency graph
requires:
  - phase: 13-01
    provides: "RetrievalMetrics static methods and MetricsResult record for IR metric computation"
  - phase: 13-02
    provides: "GoldenSetEntry record, EvaluationResult record, EvaluationExporter CSV service, golden-set.json"
provides:
  - "RetrievalEvaluationService orchestrating end-to-end golden set evaluation"
  - "EvaluationSummary record with global/per-type metrics and pass/fail status"
  - "RetrievalEvaluationIT tagged @Tag('eval') for on-demand quality gate testing"
  - "Build config excluding eval tests from normal CI, includable via -PincludeEvalTag"
affects: [14-parent-child-retrieval, 15-convex-combination, 16-query-understanding, 17-adaptive-chunking, 18-feedback-loop]

# Tech tracking
tech-stack:
  added: []
  patterns: [evaluation-orchestration-service, tag-based-test-exclusion, fuzzy-chunk-id-matching]

key-files:
  created:
    - src/main/java/dev/alexandria/search/eval/RetrievalEvaluationService.java
    - src/main/java/dev/alexandria/search/eval/EvaluationSummary.java
    - src/integrationTest/java/dev/alexandria/search/eval/RetrievalEvaluationIT.java
  modified:
    - build.gradle.kts

key-decisions:
  - "Chunk ID matching uses sourceUrl + '#' + sectionPath with exact-then-substring fallback"
  - "MRR computed at k=10 depth (not k=20) since k=10 is the primary evaluation depth"
  - "Per-query failure detection compares individual query recall and MRR against thresholds"
  - "useJUnitPlatform with excludeTags('eval') conditional on -PincludeEvalTag property"

patterns-established:
  - "Evaluation orchestration: load golden set -> execute queries -> compute metrics -> export CSV -> build summary"
  - "Tag-based test exclusion: @Tag('eval') + Gradle property toggle for on-demand execution"
  - "Fuzzy chunk ID matching: exact first, substring fallback for mismatched chunk boundaries"

requirements-completed: [EVAL-03]

# Metrics
duration: 4min
completed: 2026-02-21
---

# Phase 13 Plan 03: Evaluation Orchestration & Integration Test Summary

**End-to-end RetrievalEvaluationService wiring golden set, SearchService, metrics computation, and CSV export with @Tag("eval") integration test and configurable quality thresholds**

## Performance

- **Duration:** 4 min
- **Started:** 2026-02-21T18:34:24Z
- **Completed:** 2026-02-21T18:38:28Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- RetrievalEvaluationService orchestrates the full evaluation pipeline: golden set loading, search execution, metric computation at k=5/10/20, CSV export, and summary aggregation
- EvaluationSummary record provides global averages, per-query-type breakdowns, pass/fail status, and failed query list for diagnosis
- RetrievalEvaluationIT integration test validates configurable thresholds (Recall@10 >= 0.70, MRR >= 0.60) with @Tag("eval") exclusion from normal CI
- Build config supports `./gradlew integrationTest -PincludeEvalTag` for on-demand evaluation runs

## Task Commits

Each task was committed atomically:

1. **Task 1: Create RetrievalEvaluationService and integration test** - `1855895` (feat)
2. **Task 2: Configure build to exclude @Tag("eval") from normal test runs** - `293901b` (chore)

## Files Created/Modified
- `src/main/java/dev/alexandria/search/eval/RetrievalEvaluationService.java` - Orchestration service: loads golden set, runs queries, computes metrics, exports CSV (225 lines)
- `src/main/java/dev/alexandria/search/eval/EvaluationSummary.java` - Summary record with global/per-type metrics and pass/fail status (40 lines)
- `src/integrationTest/java/dev/alexandria/search/eval/RetrievalEvaluationIT.java` - JUnit 5 integration test with @Tag("eval") and threshold assertions (67 lines)
- `build.gradle.kts` - Added eval tag exclusion with -PincludeEvalTag property toggle

## Decisions Made
- **Chunk ID matching strategy:** Uses `sourceUrl + "#" + sectionPath` with exact match first, then substring fallback. This accommodates the golden set's semantic path IDs (e.g., `spring-boot/web/rest-controllers#creating-rest-controller`) against real search metadata which may have different URL prefixes or path formats.
- **MRR depth at k=10:** The MRR threshold check uses k=10 metrics (the primary evaluation depth), consistent with the configured threshold `alexandria.eval.thresholds.mrr`.
- **Per-query failure tracking:** Individual queries are flagged as failed if either their recall@10 OR their MRR falls below the configured thresholds. This provides granular diagnosis beyond the global averages.
- **Gradle property toggle:** `useJUnitPlatform { excludeTags("eval") }` is conditional on the absence of `-PincludeEvalTag`. When the property is present, `useJUnitPlatform()` runs without exclusions, including eval tests.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Phase 13 retrieval evaluation framework is now complete: metrics (13-01), golden set + export (13-02), and orchestration + integration test (13-03)
- Baseline evaluation can be run with `./gradlew integrationTest -PincludeEvalTag` against a populated index
- Phases 14-18 (parent-child retrieval, convex combination, query understanding, adaptive chunking, feedback loop) can use this evaluation framework to measure improvements

## Self-Check: PASSED

- All 3 created files FOUND on disk
- Both commit hashes (1855895, 293901b) FOUND in git log
- RetrievalEvaluationService.java: 227 lines
- EvaluationSummary.java: 37 lines
- RetrievalEvaluationIT.java: 68 lines
- @Tag("eval") annotation FOUND in integration test
- excludeTags("eval") FOUND in build.gradle.kts

---
*Phase: 13-retrieval-evaluation-framework*
*Completed: 2026-02-21*
