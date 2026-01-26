---
phase: 17-golden-dataset-retrieval-evaluation
plan: 03
subsystem: testing
tags: [evaluation, retrieval-metrics, cli, precision, recall, mrr, ndcg]

# Dependency graph
requires:
  - phase: 17-01
    provides: GoldenQuery record and GoldenDatasetLoader
  - phase: 17-02
    provides: RetrievalMetrics static utility class
provides:
  - EvaluationService orchestrating search and metrics calculation
  - SearchPort interface for testable search abstraction
  - EvaluationReport with breakdown by question type
  - CLI evaluate command with table/json output
affects: [17-04, 18, evaluation-runners]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "SearchPort interface for decoupling evaluation from SearchService"
    - "Aggregation by question type using Stream.groupingBy"

key-files:
  created:
    - src/main/java/fr/kalifazzia/alexandria/core/evaluation/QueryEvaluation.java
    - src/main/java/fr/kalifazzia/alexandria/core/evaluation/OverallMetrics.java
    - src/main/java/fr/kalifazzia/alexandria/core/evaluation/EvaluationReport.java
    - src/main/java/fr/kalifazzia/alexandria/core/evaluation/EvaluationService.java
    - src/main/java/fr/kalifazzia/alexandria/core/port/SearchPort.java
    - src/main/java/fr/kalifazzia/alexandria/api/cli/EvaluationCommands.java
    - src/test/java/fr/kalifazzia/alexandria/core/evaluation/EvaluationServiceTest.java
  modified:
    - src/main/java/fr/kalifazzia/alexandria/core/search/SearchService.java

key-decisions:
  - "SearchPort interface introduced for testability (Mockito cannot mock concrete classes on Java 25)"
  - "Document deduplication: multiple chunks from same doc counted once"
  - "Pass thresholds default: P@5>=0.5, NDCG@10>=0.5"

patterns-established:
  - "Port interface pattern for search operations enabling mocking"
  - "Metrics aggregation overall and by category using stream groupingBy"

# Metrics
duration: 4.3min
completed: 2026-01-26
---

# Phase 17 Plan 03: Evaluation Service Summary

**EvaluationService orchestrating golden dataset evaluation with CLI command outputting P@5, P@10, R@10, R@20, MRR, NDCG@10 metrics segmented by question type**

## Performance

- **Duration:** 4.3 min
- **Started:** 2026-01-26T14:38:42Z
- **Completed:** 2026-01-26T14:43:02Z
- **Tasks:** 3
- **Files created:** 7
- **Files modified:** 1

## Accomplishments

- EvaluationService orchestrates dataset loading, hybrid search execution, and metric calculation
- SearchPort interface enables mocking SearchService in unit tests (Java 25 Mockito limitation)
- EvaluationReport provides overall metrics and breakdown by QuestionType (FACTUAL, MULTI_HOP, GRAPH_TRAVERSAL)
- CLI evaluate command with table and JSON output formats
- Pass/fail indication based on configurable thresholds

## Task Commits

Each task was committed atomically:

1. **Task 1: Create evaluation result records** - `65d2e05` (feat)
2. **Task 2: Create EvaluationService** - `9416fa7` (feat)
3. **Task 3: Create CLI evaluate command** - `3857c1c` (feat)

## Files Created/Modified

- `src/main/java/fr/kalifazzia/alexandria/core/evaluation/QueryEvaluation.java` - Per-query evaluation with all 6 metrics
- `src/main/java/fr/kalifazzia/alexandria/core/evaluation/OverallMetrics.java` - Aggregated metrics with pass/fail
- `src/main/java/fr/kalifazzia/alexandria/core/evaluation/EvaluationReport.java` - Full report with byQuestionType breakdown
- `src/main/java/fr/kalifazzia/alexandria/core/evaluation/EvaluationService.java` - Orchestration service
- `src/main/java/fr/kalifazzia/alexandria/core/port/SearchPort.java` - Search abstraction interface
- `src/main/java/fr/kalifazzia/alexandria/core/search/SearchService.java` - Implements SearchPort
- `src/main/java/fr/kalifazzia/alexandria/api/cli/EvaluationCommands.java` - CLI evaluate command
- `src/test/java/fr/kalifazzia/alexandria/core/evaluation/EvaluationServiceTest.java` - 7 unit tests

## Decisions Made

- **SearchPort interface:** Introduced to enable mocking in unit tests. Java 25 with Mockito cannot mock concrete classes like SearchService. SearchPort follows hexagonal architecture pattern already used in the project.
- **Document deduplication:** Search may return multiple chunks from same document. EvaluationService deduplicates using `distinct()` to avoid inflating metrics.
- **Pass thresholds:** Default P@5>=0.5 and NDCG@10>=0.5 as conservative starting thresholds. Configurable via setter methods.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Created SearchPort interface for testability**
- **Found during:** Task 2 (EvaluationService tests)
- **Issue:** Mockito on Java 25 cannot mock concrete classes like SearchService
- **Fix:** Created SearchPort interface, made SearchService implement it, updated EvaluationService to use SearchPort
- **Files modified:** SearchPort.java (new), SearchService.java, EvaluationService.java
- **Verification:** All 7 EvaluationServiceTest tests pass
- **Committed in:** 9416fa7 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** SearchPort follows existing hexagonal architecture patterns. No scope creep, improved testability.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Complete evaluation pipeline: load dataset -> run searches -> compute metrics -> report
- RETR-01 to RETR-07 functional requirements implemented
- Ready for golden dataset creation and actual evaluation runs
- CLI `evaluate --dataset path.jsonl --output table|json` command available

---
*Phase: 17-golden-dataset-retrieval-evaluation*
*Plan: 03*
*Completed: 2026-01-26*
