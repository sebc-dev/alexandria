---
phase: 12-performance-quick-wins
plan: 02
subsystem: config, search
tags: [onnx-runtime, threading, bge-embeddings, query-prefix, performance]

# Dependency graph
requires:
  - phase: 03-hybrid-search
    provides: SearchService and EmbeddingConfig that this plan extends
provides:
  - ONNX Runtime environment initialized with optimized threading (spinning off, 4 intra-op, 2 inter-op)
  - BGE query prefix injection in search embedding pipeline
affects: [13-evaluation-framework, 14-parent-child-retrieval, 15-convex-combination]

# Tech tracking
tech-stack:
  added: [ai.onnxruntime.OrtEnvironment threading API]
  patterns: [BeanFactoryPostProcessor for early singleton initialization, query prefix injection for retrieval models]

key-files:
  created:
    - src/main/java/dev/alexandria/config/OnnxRuntimeConfig.java
  modified:
    - src/main/java/dev/alexandria/search/SearchService.java
    - src/test/java/dev/alexandria/search/SearchServiceTest.java

key-decisions:
  - "OnnxRuntimeConfig uses BeanFactoryPostProcessor to initialize OrtEnvironment before any ONNX model bean loads"
  - "BGE query prefix applied only to search queries, not to documents at ingestion time"
  - "IllegalStateException from OrtEnvironment handled as non-fatal warning (environment works with defaults)"

patterns-established:
  - "BeanFactoryPostProcessor pattern: use for native library configuration that must happen before bean instantiation"
  - "Query prefix injection: search-time only, keeps ingestion pipeline unchanged"

requirements-completed: [PERF-01, CHUNK-03]

# Metrics
duration: 3min
completed: 2026-02-21
---

# Phase 12 Plan 02: ONNX Runtime Threading and BGE Query Prefix Summary

**ONNX Runtime configured with thread spinning disabled and optimal pool sizes; BGE query prefix added to search embeddings for improved retrieval relevance**

## Performance

- **Duration:** 3 min
- **Started:** 2026-02-21T09:56:43Z
- **Completed:** 2026-02-21T09:59:40Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- ONNX Runtime environment initialized early via BeanFactoryPostProcessor with spinning=off, 4 intra-op threads, 2 inter-op threads
- BGE query prefix ("Represent this sentence for searching relevant passages: ") prepended to all search queries before embedding
- Ingestion pipeline unchanged (documents not prefixed)
- All 286 tests pass (285 existing + 1 new)

## Task Commits

Each task was committed atomically:

1. **Task 1: Configure ONNX Runtime thread pools and disable spinning** - `54459b6` (feat)
2. **Task 2: Add BGE query prefix to search embedding and update tests** - `3f53ba6` (feat)

## Files Created/Modified
- `src/main/java/dev/alexandria/config/OnnxRuntimeConfig.java` - BeanFactoryPostProcessor configuring OrtEnvironment threading options before model bean creation
- `src/main/java/dev/alexandria/search/SearchService.java` - Added BGE_QUERY_PREFIX constant and prepend to search query embedding
- `src/test/java/dev/alexandria/search/SearchServiceTest.java` - Updated mock setup with prefix, added explicit prefix verification test

## Decisions Made
- OnnxRuntimeConfig uses BeanFactoryPostProcessor to guarantee OrtEnvironment is initialized with threading options before Spring creates embedding model beans (the environment is a singleton)
- BGE query prefix applied only to search queries (not ingestion) per BGE model documentation
- IllegalStateException from already-initialized OrtEnvironment handled as non-fatal warning with clear logging

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- ONNX Runtime threading optimized for reduced idle CPU usage
- Search query embeddings now use proper BGE prefix for better retrieval quality
- Ready for evaluation framework (Phase 13) to measure retrieval improvements

## Self-Check: PASSED

All files verified present. All commits verified in git log.

---
*Phase: 12-performance-quick-wins*
*Completed: 2026-02-21*
