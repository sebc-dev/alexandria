---
phase: 14-parent-child-chunking
plan: 02
subsystem: search
tags: [parent-child, small-to-big, search, deduplication, context-resolution, ingestion]

# Dependency graph
requires:
  - phase: 14-01
    provides: "DocumentChunkData with chunkType/parentId fields, MarkdownChunker parent-child hierarchy"
provides:
  - "SearchService parent-child context resolution (child match -> parent text)"
  - "Deduplication of multiple children from same parent in search results"
  - "DocumentChunkRepository.findParentTextsByKeys batch native query"
  - "IngestionService stores parent+child chunks with embeddings (verified)"
affects: [14-03, 15, 18, search, ingestion]

# Tech tracking
tech-stack:
  added: []
  patterns: [small-to-big retrieval, parent text substitution, child deduplication by parent_id]

key-files:
  created: []
  modified:
    - src/main/java/dev/alexandria/search/SearchService.java
    - src/main/java/dev/alexandria/document/DocumentChunkRepository.java
    - src/test/java/dev/alexandria/search/SearchServiceTest.java
    - src/test/java/dev/alexandria/ingestion/IngestionServiceTest.java

key-decisions:
  - "Both parent and child chunks embedded for search (no separate context-only storage)"
  - "Child text -> parent text substitution via childText map after reranking"
  - "Deduplication before reranking: best-scoring child per parent_id kept"
  - "Reranker scores child text (precision match), result carries parent text (context richness)"
  - "Batch parent text lookup via native query concatenating source_url#section_path"

patterns-established:
  - "Small-to-big retrieval: children for precision matching, parents for context delivery"
  - "Parent text resolved via DocumentChunkRepository.findParentTextsByKeys in single batch query"
  - "Legacy chunks (null chunk_type) pass through deduplication and resolution unchanged"

requirements-completed: [CHUNK-02]

# Metrics
duration: 14min
completed: 2026-02-22
---

# Phase 14 Plan 02: Ingestion and Search Pipeline Parent-Child Integration Summary

**Search returns parent section context when child chunks match via deduplication-by-parent, cross-encoder reranking on child text, and batch parent text substitution from PostgreSQL**

## Performance

- **Duration:** 14 min
- **Started:** 2026-02-22T14:50:12Z
- **Completed:** 2026-02-22T15:04:39Z
- **Tasks:** 2 (Task 1: ingestion verification + tests, Task 2: search pipeline parent-child wiring)
- **Files modified:** 4

## Accomplishments
- SearchService implements full parent-child context resolution: deduplicateByParent, resolveParentTexts, substituteParentText
- Deduplication groups child matches by parent_id, keeping only the highest-scoring child per parent before reranking
- Parent text batch-fetched from PostgreSQL via native query (findParentTextsByKeys) with single DB round-trip
- Backward compatibility preserved: legacy chunks without chunk_type metadata pass through unchanged
- IngestionService verified to store both parent and child chunks with embeddings (enrichChunks preserves chunkType/parentId)
- 7 new unit tests covering all parent-child search scenarios (substitution, deduplication, passthrough, legacy, scoring)

## Task Commits

Each task was committed atomically:

1. **Task 1: Ingestion stores parent+child chunks** - `cb36bf7` (pre-committed by parallel execution)
2. **Task 2: Search parent-child context resolution** - production code in `1f5b210` (pre-committed), tests in `8ebed0a` (test)

**Plan metadata:** (pending docs commit)

_Note: Production code for both tasks was committed by a parallel Plan 14-03 execution that included these files. Task 2 tests were committed separately._

## Files Created/Modified
- `src/main/java/dev/alexandria/search/SearchService.java` - Added deduplicateByParent(), resolveParentTexts(), substituteParentText(); injected DocumentChunkRepository; updated search() pipeline flow
- `src/main/java/dev/alexandria/document/DocumentChunkRepository.java` - Added findParentTextsByKeys() native query for batch parent text lookup
- `src/test/java/dev/alexandria/search/SearchServiceTest.java` - 5 new tests: substitution, deduplication, parent passthrough, legacy chunks, highest-scoring child retention; added DocumentChunkRepository mock
- `src/test/java/dev/alexandria/ingestion/IngestionServiceTest.java` - 2 new tests: parent+child ingestion verification, enrichChunks preserves chunkType/parentId

## Decisions Made
- Both parents AND children are embedded and stored (no separate context-only mechanism) for simplicity and direct parent matching
- Reranker scores child text for precision (child is what matched the query), then SearchService substitutes parent text in the returned result for context richness
- Deduplication happens BEFORE reranking to reduce the candidate set efficiently
- Parent text resolved via childText->parentText map built from batch DB query, then matched against SearchResult.text after reranking
- Native query uses metadata->>'source_url' || '#' || metadata->>'section_path' to match parentId format

## Deviations from Plan

None - plan executed exactly as written. The parallel Plan 14-03 execution pre-committed some production code, but all planned functionality was implemented.

## Issues Encountered
- Production code changes (SearchService, DocumentChunkRepository) were committed by a parallel Plan 14-03 execution under commit `1f5b210`. This was detected and handled: my edits matched what was already committed, so only the new test methods needed a separate commit.
- No functional issues encountered; all tests passed on first run after compilation fixes.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Parent-child pipeline fully wired: ingestion stores both, search resolves parent context
- All 3 Phase 14 plans now complete (14-01: data model, 14-02: pipeline wiring, 14-03: property tests)
- Ready for Phase 15 (Search Fusion Overhaul - Convex Combination)
- 9 pre-existing medium SpotBugs findings unrelated to this change (out of scope)

## Self-Check: PASSED

All files exist, all commits verified, all tests pass.

---
*Phase: 14-parent-child-chunking*
*Completed: 2026-02-22*
