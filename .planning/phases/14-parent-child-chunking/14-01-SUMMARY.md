---
phase: 14-parent-child-chunking
plan: 01
subsystem: ingestion
tags: [chunking, parent-child, small-to-big, markdown, commonmark, tdd]

# Dependency graph
requires:
  - phase: none
    provides: "Existing MarkdownChunker flat chunking"
provides:
  - "DocumentChunkData with chunkType and parentId fields"
  - "MarkdownChunker producing parent-child chunk hierarchy"
  - "Parent chunks containing full section raw markdown (heading + prose + code fences)"
  - "Child chunks with parentId linking to parent via {sourceUrl}#{sectionPath}"
affects: [14-02, 14-03, search, ingestion]

# Tech tracking
tech-stack:
  added: []
  patterns: [parent-child chunking, small-to-big retrieval data model]

key-files:
  created: []
  modified:
    - src/main/java/dev/alexandria/ingestion/chunking/DocumentChunkData.java
    - src/main/java/dev/alexandria/ingestion/chunking/MarkdownChunker.java
    - src/main/java/dev/alexandria/ingestion/IngestionService.java
    - src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedChunk.java
    - src/test/java/dev/alexandria/ingestion/chunking/DocumentChunkDataTest.java
    - src/test/java/dev/alexandria/ingestion/chunking/MarkdownChunkerTest.java
    - src/test/java/dev/alexandria/ingestion/IngestionServiceTest.java

key-decisions:
  - "Parent chunk text built from raw source lines including code fences, merged in source order"
  - "parentId format: {sourceUrl}#{sectionPath} for deterministic child-to-parent linking"
  - "appendRawNodeText consolidated into single appendNodeText method (identical logic)"

patterns-established:
  - "Parent-child chunk hierarchy: emitSection produces parent first, then children"
  - "chunkType='parent' with parentId=null; chunkType='child' with parentId=sourceUrl#sectionPath"
  - "Backward compatibility: null chunkType/parentId for legacy callers (PreChunkedImporter)"

requirements-completed: [CHUNK-01]

# Metrics
duration: 11min
completed: 2026-02-22
---

# Phase 14 Plan 01: Parent-Child Chunking Summary

**MarkdownChunker refactored to produce two-level parent-child chunk hierarchy with TDD: parent chunks contain full section markdown, child chunks are individual blocks linked via parentId metadata**

## Performance

- **Duration:** 11 min
- **Started:** 2026-02-22T14:36:23Z
- **Completed:** 2026-02-22T14:47:28Z
- **Tasks:** 2 (Task 1: data model, Task 2: TDD chunker refactor)
- **Files modified:** 7

## Accomplishments
- Extended DocumentChunkData with chunkType and parentId fields, backward-compatible (null for legacy callers)
- Refactored MarkdownChunker to produce parent chunks (full H2/H3 section text with raw code fences) followed by child chunks (individual prose/code blocks)
- All locked decisions from CONTEXT.md implemented: H3 sub-parents, H2 direct parent only without H3 children, H4+ in H3 parent, preamble root parent, no size limits on parents
- 10 new TDD tests covering every parent-child scenario; all 346 unit tests pass

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend DocumentChunkData** - `ea181cc` (feat)
2. **Task 2 RED: Failing parent-child tests** - `486652a` (test)
3. **Task 2 GREEN: Implement parent-child chunking** - `0695d3b` (feat)
4. **Task 2 REFACTOR: Consolidate duplicate method** - `e847cda` (refactor)

## Files Created/Modified
- `src/main/java/dev/alexandria/ingestion/chunking/DocumentChunkData.java` - Added chunkType/parentId fields, updated toMetadata(), constructor validation
- `src/main/java/dev/alexandria/ingestion/chunking/MarkdownChunker.java` - Parent-child chunk emission with buildParentText(), emitChildProseChunks(), emitChildCodeChunks()
- `src/main/java/dev/alexandria/ingestion/IngestionService.java` - enrichChunks() preserves chunkType/parentId through enrichment
- `src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedChunk.java` - Updated constructor call for new fields
- `src/test/java/dev/alexandria/ingestion/chunking/DocumentChunkDataTest.java` - 5 new tests for metadata inclusion/exclusion, round-trip, validation
- `src/test/java/dev/alexandria/ingestion/chunking/MarkdownChunkerTest.java` - 10 new parent-child tests + updated 20+ existing tests for new output structure
- `src/test/java/dev/alexandria/ingestion/IngestionServiceTest.java` - Updated constructor calls for backward compatibility

## Decisions Made
- Parent chunk text reconstructed from raw source lines (via source spans) to include code fences verbatim, merging content nodes and code blocks in original source order
- parentId format is `{sourceUrl}#{sectionPath}` (e.g., `https://docs.example.com/guide#setup/configuration`) -- deterministic, no UUID needed
- Consolidated identical `appendRawNodeText` and `appendNodeText` into single method during REFACTOR phase

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Parent-child data model complete and all tests green
- Ready for Plan 02 (search service changes to leverage parent-child hierarchy)
- 9 pre-existing medium SpotBugs findings unrelated to this change (out of scope)

---
*Phase: 14-parent-child-chunking*
*Completed: 2026-02-22*
