---
phase: 13-retrieval-evaluation-framework
plan: 02
subsystem: testing
tags: [evaluation, golden-set, csv-export, ir-metrics, spring-boot]

# Dependency graph
requires:
  - phase: 13-01
    provides: "QueryType enum, RelevanceJudgment record, RetrievalMetrics computation"
provides:
  - "100 annotated Spring Boot queries with graded relevance judgments (golden-set.json)"
  - "GoldenSetEntry record for JSON deserialization"
  - "EvaluationResult record with per-query metrics and ChunkResult"
  - "EvaluationExporter service producing aggregate and detailed CSVs"
  - "Configurable eval output directory and pass/fail thresholds in application.yml"
  - "Injectable Clock bean via ClockConfig"
affects: [13-03-integration-test, phase-14, phase-15, phase-16, phase-17, phase-18]

# Tech tracking
tech-stack:
  added: []
  patterns: [csv-export-with-injectable-clock, golden-set-json-structure]

key-files:
  created:
    - src/main/resources/eval/golden-set.json
    - src/main/java/dev/alexandria/search/eval/GoldenSetEntry.java
    - src/main/java/dev/alexandria/search/eval/EvaluationResult.java
    - src/main/java/dev/alexandria/search/eval/EvaluationExporter.java
    - src/main/java/dev/alexandria/config/ClockConfig.java
    - src/test/java/dev/alexandria/search/eval/EvaluationExporterTest.java
  modified:
    - src/main/resources/application.yml

key-decisions:
  - "Clock bean in ClockConfig with @ConditionalOnMissingBean for testable timestamps"
  - "CSV values formatted to 4 decimal places using Locale.US for deterministic output"
  - "Detailed CSV includes per-query metrics only on first chunk row to avoid redundancy"

patterns-established:
  - "Golden set JSON structure: array of {query, queryType, judgments[{chunkId, grade}]}"
  - "CSV export pattern: injectable Clock + @TempDir for filesystem-free unit tests"

requirements-completed: [EVAL-02, EVAL-05]

# Metrics
duration: 8min
completed: 2026-02-21
---

# Phase 13 Plan 02: Golden Set & CSV Export Summary

**100 annotated Spring Boot queries with 4-type distribution and dual-CSV export service with configurable output directory and timestamped filenames**

## Performance

- **Duration:** 8 min
- **Started:** 2026-02-21T18:23:16Z
- **Completed:** 2026-02-21T18:31:39Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Golden set with 100 diverse queries: 30 CODE_LOOKUP, 30 FACTUAL, 25 CONCEPTUAL, 15 TROUBLESHOOTING
- Each query has 2-5 graded relevance judgments with semantic path-style chunk identifiers
- EvaluationExporter producing aggregate CSV (per-type + global averages) and detailed CSV (per-query chunks)
- Application.yml configured with eval output directory and pass/fail thresholds (recall@10: 0.70, MRR: 0.60)

## Task Commits

Each task was committed atomically:

1. **Task 1: Create golden set data model and 100 annotated queries** - `515197a` (feat)
2. **Task 2: Create CSV export service with application configuration** - `06f95d1` (feat)

## Files Created/Modified
- `src/main/resources/eval/golden-set.json` - 100 annotated queries with graded relevance judgments
- `src/main/java/dev/alexandria/search/eval/GoldenSetEntry.java` - Record for parsing golden set JSON
- `src/main/java/dev/alexandria/search/eval/EvaluationResult.java` - Per-query evaluation result with ChunkResult
- `src/main/java/dev/alexandria/search/eval/EvaluationExporter.java` - CSV export service (aggregate + detailed)
- `src/main/java/dev/alexandria/config/ClockConfig.java` - Injectable Clock bean for testable timestamps
- `src/test/java/dev/alexandria/search/eval/EvaluationExporterTest.java` - 7 unit tests for CSV export
- `src/main/resources/application.yml` - Added eval output-dir and thresholds configuration

## Decisions Made
- **Clock bean via ClockConfig:** Provides `Clock.systemDefaultZone()` with `@ConditionalOnMissingBean` so tests can inject fixed clocks. Follows testable architecture principle of pushing side effects to boundaries.
- **CSV formatting with Locale.US:** Ensures decimal points (not commas) regardless of system locale for deterministic, parseable output.
- **Per-query metrics on first chunk row only:** In the detailed CSV, recall@10/MRR/NDCG@10 appear only on the first chunk row for each query, avoiding redundant data while keeping the CSV flat.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Created ClockConfig for injectable Clock bean**
- **Found during:** Task 2 (EvaluationExporter implementation)
- **Issue:** EvaluationExporter requires Clock for testable timestamps, but no Clock bean existed in the application
- **Fix:** Created `ClockConfig` in `dev.alexandria.config` with `@ConditionalOnMissingBean` Clock bean
- **Files modified:** src/main/java/dev/alexandria/config/ClockConfig.java
- **Verification:** Compilation passes, tests use fixed Clock directly via constructor
- **Committed in:** 06f95d1 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Clock bean is standard infrastructure needed for testable time access. No scope creep.

## Issues Encountered
- NullAway error on `@TempDir` field in test class: resolved with `@SuppressWarnings("NullAway.Init")` per project convention for framework-initialized fields.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Golden set and CSV export ready for plan 03 (integration test that runs queries against real indexed data)
- QueryType, RelevanceJudgment, RetrievalMetrics from plan 01 provide the computation layer
- EvaluationExporter can be wired into the integration test to produce CSVs on each eval run

## Self-Check: PASSED

All 6 created files verified present on disk. Both commit hashes (515197a, 06f95d1) verified in git log.

---
*Phase: 13-retrieval-evaluation-framework*
*Completed: 2026-02-21*
