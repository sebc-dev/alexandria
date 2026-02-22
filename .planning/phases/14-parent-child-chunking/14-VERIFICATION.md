---
phase: 14-parent-child-chunking
verified: 2026-02-22T16:00:00Z
status: passed
score: 3/3 success criteria verified
re_verification: false
---

# Phase 14: Parent-Child Chunking Verification Report

**Phase Goal:** Search returns complete context (code + surrounding prose) by linking child chunks to their parent sections
**Verified:** 2026-02-22T16:00:00Z
**Status:** PASSED
**Re-verification:** No - initial verification

---

## Goal Achievement

### Observable Truths (from ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|---------|
| 1 | The chunker produces parent chunks (full H2/H3 sections with code+prose) and child chunks (individual paragraphs/blocks) with parent-child links in metadata | VERIFIED | `MarkdownChunker.emitSection()` emits one parent (`chunkType="parent"`, `parentId=null`) followed by child chunks (`chunkType="child"`, `parentId=sourceUrl#sectionPath`). All 4 `new DocumentChunkData(...)` calls in `MarkdownChunker.java` pass correct `chunkType` and `parentId`. 10 TDD scenario tests in `MarkdownChunkerTest.java` (1114 lines, 47 `@Test`). |
| 2 | When a child chunk matches a search query, the search service returns the parent chunk's full content, reuniting code and prose in context | VERIFIED | `SearchService.search()` pipeline: `deduplicateByParent()` groups children by `parent_id`, `resolveParentTexts()` batch-fetches parent text from PostgreSQL via `DocumentChunkRepository.findParentTextsByKeys()`, `substituteParentText()` replaces child text with parent text in `SearchResult`. 5 targeted unit tests in `SearchServiceTest.java` (lines 254-415) cover substitution, deduplication, passthrough, legacy compatibility, and highest-score retention. |
| 3 | jqwik property-based tests verify chunker invariants: content conservation (no data loss), size bounds respected, code blocks balanced, tables complete | VERIFIED | `MarkdownChunkerPropertyTest.java` (504 lines, 7 `@Property(tries=200)`) covers: `contentConservation`, `childChunksRespectMaxSize`, `codeBlockBalance`, `tableCompleteness`, `parentChildStructuralIntegrity`, `parentChunkTypeConsistency`, `sectionPathNeverContainsRawHeadingText`. jqwik 1.9.2 declared in `gradle/libs.versions.toml` and wired in `build.gradle.kts`. |

**Score:** 3/3 truths verified

---

## Required Artifacts

### Plan 14-01 Artifacts

| Artifact | Provides | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/dev/alexandria/ingestion/chunking/MarkdownChunker.java` | Parent-child chunking with two-level hierarchy | VERIFIED | 463 lines. Contains `buildParentText()`, `emitChildProseChunks()`, `emitChildCodeChunks()`, `emitSection()`. All `new DocumentChunkData(...)` calls pass `chunkType` and `parentId`. |
| `src/main/java/dev/alexandria/ingestion/chunking/DocumentChunkData.java` | Extended record with `chunkType` and `parentId` fields | VERIFIED | 82 lines. Compact constructor validates `parent` chunks have null `parentId`. `toMetadata()` conditionally emits `chunk_type` and `parent_id` keys. |
| `src/main/java/dev/alexandria/ingestion/chunking/ContentType.java` | Content type enum (prose, code unchanged) | VERIFIED | Unmodified — backward compatible. |
| `src/test/java/dev/alexandria/ingestion/chunking/MarkdownChunkerTest.java` | TDD tests for parent-child chunking behavior | VERIFIED | 1114 lines, 47 `@Test`. 10 dedicated parent-child scenario tests at lines 852-1114. Existing tests updated for new output structure. |

### Plan 14-02 Artifacts

| Artifact | Provides | Status | Details |
|----------|----------|--------|---------|
| `src/main/java/dev/alexandria/ingestion/IngestionService.java` | Stores both parent and child chunks with embeddings | VERIFIED | `storeChunks()` processes all `DocumentChunkData` records (parents and children) identically via `embeddingStore.addAll()`. `enrichChunks()` preserves `chunkType` and `parentId` at lines 128-129. |
| `src/main/java/dev/alexandria/search/SearchService.java` | Parent context retrieval when child matches | VERIFIED | 276 lines. Injects `DocumentChunkRepository`. Pipeline: `deduplicateByParent()` -> `resolveParentTexts()` -> `rerankerService.rerank()` -> `substituteParentText()`. `parent_id` metadata key read at lines 130 and 165. |
| `src/main/java/dev/alexandria/search/RerankerService.java` | Reranker works with parent-child aware results | VERIFIED | No changes needed — reranker scores child text for precision; parent substitution happens in SearchService after reranking. Confirmed by SearchServiceTest test `searchDelegatesToRerankerService`. |
| `src/main/java/dev/alexandria/document/DocumentChunkRepository.java` | Batch native query for parent text lookup | VERIFIED | `findParentTextsByKeys(@Param("parentKeys") String[] parentKeys)` at lines 155-165. Native SQL joins `source_url#section_path` to match `parentId` format. |
| `src/test/java/dev/alexandria/search/SearchServiceTest.java` | Tests for parent substitution and deduplication | VERIFIED | 416 lines, 17 `@Test`. Parent-child tests at lines 252-415: `searchSubstitutesParentTextForChildMatch`, `searchDeduplicatesMultipleChildrenOfSameParent`, `searchPassesThroughParentMatchDirectly`, `searchHandlesLegacyChunksWithoutChunkType`, `searchKeepsHighestScoringChildPerParent`. |

### Plan 14-03 Artifacts

| Artifact | Provides | Status | Details |
|----------|----------|--------|---------|
| `gradle/libs.versions.toml` | jqwik dependency version | VERIFIED | Line 25: `jqwik = "1.9.2"`. Line 52: `jqwik = { module = "net.jqwik:jqwik", version.ref = "jqwik" }`. |
| `build.gradle.kts` | jqwik test dependency | VERIFIED | Line 56: `testImplementation(libs.jqwik)`. Note: Plan 14-03 PLAN.md says `build.gradle` but actual file is `build.gradle.kts` — no impact, dependency is correctly declared. |
| `src/test/java/dev/alexandria/ingestion/chunking/MarkdownChunkerPropertyTest.java` | Property-based tests for chunker invariants | VERIFIED | 504 lines (exceeds 150 minimum). 7 `@Property(tries=200)` tests. Calls `chunker.chunk()` at 6 invocations. Full Markdown arbitrary generator with headings (H2/H3/H4), prose paragraphs, code blocks (5 languages), GFM tables, and preamble. |

---

## Key Link Verification

| From | To | Via | Status | Evidence |
|------|----|-----|--------|---------|
| `MarkdownChunker.chunk()` | `DocumentChunkData` | Returns `List<DocumentChunkData>` with parent and child records | WIRED | All 4 `new DocumentChunkData(...)` calls in `MarkdownChunker.java` supply `chunkType` and `parentId` as the 9th and 10th constructor arguments. |
| `DocumentChunkData.toMetadata()` | langchain4j `Metadata` | Puts `chunk_type` and `parent_id` keys | WIRED | Lines 68-73 in `DocumentChunkData.java`: conditionally calls `metadata.put("chunk_type", chunkType)` and `metadata.put("parent_id", parentId)`. |
| `IngestionService.storeChunks()` | `EmbeddingStore` | Stores both parent and child `TextSegment`s | WIRED | `storeChunks()` at line 164 processes all chunks from the list (parents + children) into `TextSegment`s and calls `embeddingStore.addAll(embeddings, batch)` at line 175. |
| `SearchService.search()` | `EmbeddingStore` | Fetches child matches, looks up `parent_id` in metadata, retrieves parent text | WIRED | Lines 96-111: `embeddingStore.search()` fetches candidates, `deduplicateByParent()` reads `parent_id` at line 130, `resolveParentTexts()` reads `parent_id` at line 165 and calls `documentChunkRepository.findParentTextsByKeys()` at line 181. |
| `SearchService` | `RerankerService` | Passes parent-enriched candidates to reranker | WIRED | Line 107: `rerankerService.rerank(request.query(), deduplicated, request.maxResults(), request.minScore())`. Parent text substitution happens after reranking at line 111. |
| `MarkdownChunkerPropertyTest` | `MarkdownChunker` | Exercises `chunk()` with randomly generated Markdown | WIRED | 6 calls to `chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED)` at lines 242, 307, 335, 358, 397, 439, 463 (smallChunker at 307). |
| `build.gradle.kts` | jqwik | `testImplementation` dependency | WIRED | Line 56: `testImplementation(libs.jqwik)`. Resolves `net.jqwik:jqwik:1.9.2`. |

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|---------|
| CHUNK-01 | 14-01-PLAN.md | Le systeme produit des parent chunks (section H2/H3 complete code+prose) et des child chunks (paragraphes/blocs individuels) avec lien parent-child en metadata | SATISFIED | `MarkdownChunker.java` produces parent chunks (full section text including raw code fences) and child chunks (individual prose/code blocks) with `parentId = sourceUrl#sectionPath`. Verified by 10 TDD tests and 7 property tests. `requirements-completed: [CHUNK-01]` in 14-01-SUMMARY.md. |
| CHUNK-02 | 14-02-PLAN.md | La recherche retourne les parent chunks complets quand un child chunk matche, reunissant code et prose dans le contexte | SATISFIED | `SearchService.resolveParentTexts()` + `substituteParentText()` replaces child text with parent section content. `DocumentChunkRepository.findParentTextsByKeys()` batch-fetches parent text from DB. Verified by `searchSubstitutesParentTextForChildMatch` test. `requirements-completed: [CHUNK-02]` in 14-02-SUMMARY.md. |
| QUAL-04 | 14-03-PLAN.md | jqwik teste les invariants structurels du MarkdownChunker (conservation contenu, bornes taille, code blocks equilibres, tables completes) | SATISFIED | `MarkdownChunkerPropertyTest.java` implements all 5 required invariants (content conservation, size bounds, code block balance, table completeness, parent-child integrity) plus 2 additional invariants (type consistency, slugification). `requirements-completed: [QUAL-04]` in 14-03-SUMMARY.md. |

**Orphaned requirements check:** REQUIREMENTS.md maps CHUNK-01, CHUNK-02, and QUAL-04 to Phase 14. All three are claimed by the plans and verified. No orphaned requirements.

---

## Anti-Patterns Found

No anti-patterns detected in production files. Scan of `MarkdownChunker.java`, `DocumentChunkData.java`, `SearchService.java`, `DocumentChunkRepository.java`, and `IngestionService.java` returned zero TODO/FIXME/PLACEHOLDER comments, zero stub returns, and zero empty handler implementations.

---

## Human Verification Required

### 1. End-to-End Retrieval Quality

**Test:** Ingest a documentation page that has prose and a code block in the same section, then search for a query that matches a prose phrase in that section. Verify the search result text contains the code block alongside the prose (parent text returned, not just the narrow prose match).

**Expected:** `SearchResult.text` contains both the prose and the ```` ```java ... ``` ```` fenced code block from the same section.

**Why human:** Requires a live PostgreSQL + pgvector instance with real ONNX embeddings running. Cannot be exercised by unit tests alone.

### 2. Deduplication in Practice

**Test:** Ingest a section with multiple paragraphs. Issue a query that is broad enough to semantically match several paragraphs from the same parent. Verify that only one result is returned per parent section, not one per matching paragraph.

**Expected:** Single search result per parent section, even when multiple child chunks match the query.

**Why human:** Requires live embedding model to produce similar vectors for related paragraphs, and a running PostgreSQL instance to store and retrieve chunks.

---

## Verification Summary

Phase 14 goal is fully achieved. The three success criteria from ROADMAP.md are all satisfied:

1. **Parent-child data model (CHUNK-01):** `MarkdownChunker` produces a two-level hierarchy — parent chunks contain the full H2/H3 section as raw markdown (heading + prose + code fences), child chunks are individual blocks linked via `parentId = sourceUrl#sectionPath`. The hierarchy rules (H3 sub-parents, H2 direct parent only without H3 children, H4+ in H3 parent, preamble root parent) are implemented and verified by 10 TDD scenario tests and 7 property-based tests across 200 random inputs each.

2. **Search context resolution (CHUNK-02):** `SearchService` implements the full small-to-big retrieval pipeline. Child matches are deduplicated by `parent_id` (keeping the highest-scoring child per parent), parent text is batch-fetched from PostgreSQL via `DocumentChunkRepository.findParentTextsByKeys()`, and child text is substituted with parent text in the final `SearchResult`. Legacy chunks without `chunk_type` metadata pass through unchanged. All 5 targeted unit tests pass.

3. **Property-based invariant tests (QUAL-04):** jqwik 1.9.2 is wired into the Gradle build. `MarkdownChunkerPropertyTest` exercises 7 structural invariants across 200 random Markdown inputs each (1400 total runs): content conservation, size bounds, code block balance, table completeness, parent-child structural integrity, chunk type consistency, and sectionPath slugification.

All commits verified against git log: `ea181cc`, `486652a`, `0695d3b`, `e847cda` (Plan 01); `cb36bf7`, `1f5b210`, `8ebed0a` (Plans 02+03). No blocking anti-patterns found. Two items require human verification with a live database for end-to-end confidence.

---

_Verified: 2026-02-22T16:00:00Z_
_Verifier: Claude (gsd-verifier)_
