---
phase: 02-ingestion-core
plan: 02
subsystem: ingestion
tags: [langchain4j, hierarchical-chunking, document-splitters, parent-child-chunks]

# Dependency graph
requires:
  - phase: 02-01
    provides: Domain models (Document, Chunk, ChunkType) and MarkdownParser
provides:
  - HierarchicalChunker for two-pass parent/child chunking
  - ChunkPair record linking parent to children
  - Unit tests verifying chunking behavior
affects: [02-03-embeddings, 04-search]

# Tech tracking
tech-stack:
  added: []
  patterns: [hierarchical-chunking, character-based-token-approximation]

key-files:
  created:
    - src/main/java/fr/kalifazzia/alexandria/core/ingestion/ChunkPair.java
    - src/main/java/fr/kalifazzia/alexandria/core/ingestion/HierarchicalChunker.java
    - src/test/java/fr/kalifazzia/alexandria/core/ingestion/HierarchicalChunkerTest.java
  modified: []

key-decisions:
  - "Character-based token approximation (~4 chars/token) for LangChain4j DocumentSplitters"
  - "10% overlap for both parent (400 chars) and child (80 chars) chunks"
  - "ChunkPair uses String content to keep domain model independent of LangChain4j types"

patterns-established:
  - "Two-pass hierarchical chunking: parent 4000 chars, child 800 chars"
  - "Immutable records with defensive copying for domain objects"

# Metrics
duration: 2min
completed: 2026-01-20
---

# Phase 02 Plan 02: Hierarchical Chunker Summary

**Two-pass hierarchical chunker using LangChain4j DocumentSplitters with parent chunks (~1000 tokens) and child chunks (~200 tokens) with 10% overlap**

## Performance

- **Duration:** 2 min
- **Started:** 2026-01-20T10:07:56Z
- **Completed:** 2026-01-20T10:10:14Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- HierarchicalChunker implementing two-pass chunking with LangChain4j DocumentSplitters.recursive()
- ChunkPair record linking parent content to list of child contents with position tracking
- Comprehensive test suite with 15 test cases covering empty, short, medium, long content and overlap behavior

## Task Commits

Each task was committed atomically:

1. **Task 1: Create ChunkPair and HierarchicalChunker** - `e49fa51` (feat)
2. **Task 2: Create unit tests for HierarchicalChunker** - `0342878` (test)

## Files Created/Modified

- `src/main/java/fr/kalifazzia/alexandria/core/ingestion/ChunkPair.java` - Record linking parent to children with position
- `src/main/java/fr/kalifazzia/alexandria/core/ingestion/HierarchicalChunker.java` - Two-pass hierarchical chunking using DocumentSplitters
- `src/test/java/fr/kalifazzia/alexandria/core/ingestion/HierarchicalChunkerTest.java` - Unit tests for all chunking scenarios

## Decisions Made

- **Character-based approximation:** Using ~4 chars per token approximation for all-MiniLM-L6-v2 compatibility (no explicit tokenizer needed)
- **String-based ChunkPair:** Using String instead of LangChain4j TextSegment to keep domain model framework-agnostic
- **Defensive copying:** ChunkPair uses List.copyOf() for childContents to ensure immutability

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- **Set.of() duplicate elements:** Initial test implementation used Set.of() which doesn't allow duplicates. Fixed by using HashSet constructor with List.of() input for word overlap tests.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- HierarchicalChunker ready for use by EmbeddingGenerator (02-03)
- ChunkPair structure enables parent-child relationship tracking for context retrieval
- **Next:** 02-03 will implement embedding generation for child chunks

---
*Phase: 02-ingestion-core*
*Completed: 2026-01-20*
