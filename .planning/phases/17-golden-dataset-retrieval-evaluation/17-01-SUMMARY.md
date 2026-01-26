---
phase: 17-golden-dataset-retrieval-evaluation
plan: 01
subsystem: testing
tags: [jsonl, jackson, golden-dataset, evaluation, retrieval]

# Dependency graph
requires:
  - phase: 15
    provides: evaluation metrics infrastructure (MeterRegistry)
provides:
  - GoldenQuery record for evaluation entries
  - QuestionType enum for query classification
  - GoldenDatasetLoader port interface
  - JsonlGoldenDatasetLoader implementation
affects: [17-02, 17-03, 18, 19]

# Tech tracking
tech-stack:
  added: []
  patterns: [JSONL streaming parser, validation in record compact constructor]

key-files:
  created:
    - src/main/java/fr/kalifazzia/alexandria/core/evaluation/GoldenQuery.java
    - src/main/java/fr/kalifazzia/alexandria/core/evaluation/QuestionType.java
    - src/main/java/fr/kalifazzia/alexandria/core/port/GoldenDatasetLoader.java
    - src/main/java/fr/kalifazzia/alexandria/infra/evaluation/JsonlGoldenDatasetLoader.java
    - src/test/java/fr/kalifazzia/alexandria/infra/evaluation/JsonlGoldenDatasetLoaderTest.java
    - src/test/resources/evaluation/test-golden-dataset.jsonl
  modified: []

key-decisions:
  - "JSONL format for streaming large datasets line-by-line"
  - "@JsonProperty annotations for snake_case mapping (expected_doc_ids, requires_kg)"
  - "Validation in compact constructor with List.copyOf() for immutability"

patterns-established:
  - "Record compact constructor validation pattern for domain models"
  - "Line-by-line JSONL streaming with error context (line numbers)"

# Metrics
duration: 3min
completed: 2026-01-26
---

# Phase 17 Plan 01: Golden Dataset Schema Summary

**JSONL golden dataset loader with GoldenQuery record, QuestionType enum, and streaming parser for retrieval evaluation**

## Performance

- **Duration:** 3 min
- **Started:** 2026-01-26T14:33:00Z
- **Completed:** 2026-01-26T14:36:22Z
- **Tasks:** 3
- **Files created:** 6

## Accomplishments
- GoldenQuery record with query, expectedDocIds, requiresKg, reasoningHops, questionType fields
- QuestionType enum with FACTUAL, MULTI_HOP, GRAPH_TRAVERSAL values mapped to snake_case JSON
- GoldenDatasetLoader port interface following hexagonal architecture
- JsonlGoldenDatasetLoader with streaming line-by-line parsing and descriptive error messages
- Complete unit test coverage (7 tests) including edge cases

## Task Commits

Each task was committed atomically:

1. **Task 1: Create GoldenQuery record and QuestionType enum** - `766f399` (feat)
2. **Task 2: Create GoldenDatasetLoader port and JsonlGoldenDatasetLoader implementation** - `1c1176a` (feat)
3. **Task 3: Create unit tests and sample golden dataset** - `c5da783` (test)

## Files Created/Modified
- `src/main/java/fr/kalifazzia/alexandria/core/evaluation/GoldenQuery.java` - Record for golden dataset entries with validation
- `src/main/java/fr/kalifazzia/alexandria/core/evaluation/QuestionType.java` - Enum for question classification (FACTUAL, MULTI_HOP, GRAPH_TRAVERSAL)
- `src/main/java/fr/kalifazzia/alexandria/core/port/GoldenDatasetLoader.java` - Port interface for loading golden dataset
- `src/main/java/fr/kalifazzia/alexandria/infra/evaluation/JsonlGoldenDatasetLoader.java` - Jackson-based JSONL loader implementation
- `src/test/java/fr/kalifazzia/alexandria/infra/evaluation/JsonlGoldenDatasetLoaderTest.java` - Unit tests (7 tests)
- `src/test/resources/evaluation/test-golden-dataset.jsonl` - Sample dataset with all question types

## Decisions Made
- JSONL format enables streaming large datasets without loading entire file into memory
- @JsonProperty annotations on enum values and record fields for clean snake_case mapping
- Compact constructor validation ensures domain model invariants (non-null, non-empty, non-negative)
- List.copyOf() makes expectedDocIds immutable after construction

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- GoldenQuery and QuestionType ready for RetrievalEvaluator service (17-02)
- JSONL loader ready for loading actual golden datasets
- Test infrastructure established for evaluation package

---
*Phase: 17-golden-dataset-retrieval-evaluation*
*Completed: 2026-01-26*
