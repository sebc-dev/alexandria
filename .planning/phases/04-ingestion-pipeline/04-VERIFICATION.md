---
phase: 04-ingestion-pipeline
verified: 2026-02-18T05:28:43Z
status: passed
score: 14/14 must-haves verified
re_verification: false
---

# Phase 04: Ingestion Pipeline Verification Report

**Phase Goal:** Crawled Markdown is transformed into richly-annotated, searchable chunks that preserve code block integrity and heading hierarchy -- the quality-critical transformation layer
**Verified:** 2026-02-18T05:28:43Z
**Status:** passed
**Re-verification:** No -- initial verification

---

## Goal Achievement

### Observable Truths

| #  | Truth                                                                                      | Status     | Evidence                                                                                                              |
|----|--------------------------------------------------------------------------------------------|------------|-----------------------------------------------------------------------------------------------------------------------|
| 1  | Markdown is split into chunks at H1/H2/H3 boundaries only                                  | VERIFIED   | `splitsAtH1H2H3Boundaries()` passes; AST walk in MarkdownChunker.java:70 checks `heading.getLevel() <= 3`            |
| 2  | H4+ headings remain inside parent H3 chunk                                                 | VERIFIED   | `h4PlusStaysInParentH3Chunk()` passes; H4 nodes fall through to `currentContentNodes` path, not heading split path   |
| 3  | Fenced code blocks are extracted as separate chunks with content_type=code                 | VERIFIED   | `extractsCodeBlockAsSeparateChunk()` passes; emitChunks() emits separate code chunk per FencedCodeBlock              |
| 4  | Prose chunks retain text minus extracted code blocks with content_type=prose               | VERIFIED   | `proseChunkExcludesCodeBlockContent()` passes; FencedCodeBlock captured to separate list, not contentNodes            |
| 5  | Every chunk carries all 5 metadata fields: source_url, section_path, content_type, last_updated, language | VERIFIED   | `everyChunkHasAllFiveMetadataFields()` passes; DocumentChunkData record has all 6 fields (text + 5 metadata)          |
| 6  | section_path uses slash separator built from heading hierarchy                             | VERIFIED   | `splitsAtH1H2H3Boundaries()` asserts "introduction/getting-started/configuration"; `buildSectionPath()` uses `"/"` joiner |
| 7  | Code language is detected from fence info string or by keyword heuristic                   | VERIFIED   | `codeBlockWithoutLanguageTagUsesAutoDetection()` passes; `detectLanguage()` at line 204 tries info string then LanguageDetector.detect() |
| 8  | Headings inside fenced code blocks do NOT trigger chunk splits                              | VERIFIED   | `headingInsideCodeBlockDoesNotTriggerSplit()` passes; AST parses fence as FencedCodeBlock node, not Heading node      |
| 9  | Crawled pages are chunked, embedded, and stored in EmbeddingStore as searchable TextSegments | VERIFIED   | `ingest_crawl_result_produces_searchable_chunks()` IT passes; full path: chunker.chunk() -> embedAll() -> addAll()     |
| 10 | Stored chunks carry all 5 metadata fields matching snake_case convention from Phase 2      | VERIFIED   | `ingested_chunks_carry_correct_metadata()` IT passes; buildMetadata() creates source_url, section_path, content_type, last_updated, language keys |
| 11 | Pre-chunked JSON can be imported with all-or-nothing validation                            | VERIFIED   | `import_rejects_invalid_chunks_entirely()` and `import_rejects_invalid_content_type()` ITs pass                       |
| 12 | Pre-chunked import replaces existing chunks for the same source_url                        | VERIFIED   | `import_replaces_existing_chunks_for_same_source_url()` IT passes; removeAll(Filter) called before addAll()           |
| 13 | Invalid pre-chunked JSON is rejected entirely (no partial import)                          | VERIFIED   | `import_rejects_invalid_chunks_entirely()` verifies valid chunk NOT stored when one invalid chunk present in batch     |
| 14 | Chunks stored via IngestionService are findable by SearchService hybrid search             | VERIFIED   | `ingest_crawl_result_produces_searchable_chunks()` asserts searchService.search() returns results with correct sourceUrl |

**Score:** 14/14 truths verified

---

## Required Artifacts

### Plan 01 Artifacts

| Artifact                                                                        | Expected                                        | Status     | Details                                               |
|---------------------------------------------------------------------------------|-------------------------------------------------|------------|-------------------------------------------------------|
| `src/main/java/dev/alexandria/ingestion/chunking/MarkdownChunker.java`         | AST-based heading splitter and code extractor   | VERIFIED   | 211 lines, @Component, full implementation, no stubs  |
| `src/main/java/dev/alexandria/ingestion/chunking/DocumentChunkData.java`       | Record with chunk text + 5 metadata fields      | VERIFIED   | Java record with 6 fields; `record DocumentChunkData` present |
| `src/main/java/dev/alexandria/ingestion/chunking/LanguageDetector.java`        | Keyword-based language detection heuristic      | VERIFIED   | 68 lines, `detect()` method present, 10 languages     |
| `src/test/java/dev/alexandria/ingestion/chunking/MarkdownChunkerTest.java`     | Unit tests for chunking logic                   | VERIFIED   | 357 lines, 15 tests, all pass (0 failures, 0 skipped) |
| `src/test/java/dev/alexandria/ingestion/chunking/LanguageDetectorTest.java`    | Unit tests for language detection               | VERIFIED   | 79 lines, 14 tests, all pass (0 failures, 0 skipped)  |

### Plan 02 Artifacts

| Artifact                                                                                          | Expected                                              | Status     | Details                                                                |
|---------------------------------------------------------------------------------------------------|-------------------------------------------------------|------------|------------------------------------------------------------------------|
| `src/main/java/dev/alexandria/ingestion/IngestionService.java`                                    | Pipeline orchestrator: crawl result -> chunk -> embed -> store | VERIFIED   | 95 lines, @Service, `class IngestionService` present, full implementation |
| `src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedImporter.java`                       | JSON import with validation and replacement            | VERIFIED   | 86 lines, @Service, `class PreChunkedImporter` present, removeAll + addAll wired |
| `src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedRequest.java`                        | Outer JSON record: source_url + chunks list           | VERIFIED   | `record PreChunkedRequest` with @NotBlank, @NotEmpty, @Valid           |
| `src/main/java/dev/alexandria/ingestion/prechunked/PreChunkedChunk.java`                          | Inner JSON record: text + 5 metadata fields with Bean Validation | VERIFIED   | `record PreChunkedChunk` with @NotBlank, @Pattern(regexp = "prose|code") |
| `src/integrationTest/java/dev/alexandria/ingestion/IngestionServiceIT.java`                       | Integration tests proving ingest -> search roundtrip  | VERIFIED   | 112 lines, 4 tests, all pass (0 failures, 0 skipped)                   |
| `src/integrationTest/java/dev/alexandria/ingestion/prechunked/PreChunkedImporterIT.java`          | Integration tests proving import, validation, replacement | VERIFIED   | 163 lines, 4 tests, all pass (0 failures, 0 skipped)                   |

---

## Key Link Verification

| From                         | To                                | Via                                      | Status  | Details                                                                                    |
|------------------------------|-----------------------------------|------------------------------------------|---------|--------------------------------------------------------------------------------------------|
| `MarkdownChunker.java`       | `DocumentChunkData.java`          | `returns List<DocumentChunkData>`        | WIRED   | Line 52 signature; line 58 `new ArrayList<>()` of type; returned at line 99               |
| `MarkdownChunker.java`       | `LanguageDetector.java`           | `calls LanguageDetector.detect()`        | WIRED   | Line 209: `return LanguageDetector.detect(codeBlock.getLiteral())`                         |
| `IngestionService.java`      | `MarkdownChunker.java`            | `calls chunker.chunk()` per page         | WIRED   | Line 48: `chunker.chunk(page.markdown(), page.url(), ...)` in loop; line 66: `chunker.chunk(...)` in ingestPage |
| `IngestionService.java`      | `EmbeddingStore` + `EmbeddingModel` | `embedAll() then addAll()`             | WIRED   | Line 80: `embeddingModel.embedAll(segments).content()`; line 82: `embeddingStore.addAll(embeddings, segments)` |
| `PreChunkedImporter.java`    | `EmbeddingStore`                  | `removeAll(Filter) then addAll()`        | WIRED   | Line 60: `embeddingStore.removeAll(metadataKey("source_url").isEqualTo(...))` line 71: `embeddingStore.addAll(...)` |
| `IngestionServiceIT.java`    | `SearchService.java`              | `searchService.search()` after ingest    | WIRED   | Lines 53, 65, 89, 93: multiple `searchService.search()` calls verifying roundtrip         |

---

## Build Configuration Verification

| Item                              | Status   | Evidence                                                                          |
|-----------------------------------|----------|-----------------------------------------------------------------------------------|
| `commonmark = "0.27.1"` in versions | VERIFIED | `gradle/libs.versions.toml` line 16                                              |
| `commonmark` library entry         | VERIFIED | `libs.versions.toml` lines 36-37: both commonmark and commonmark-ext-gfm-tables  |
| `implementation(libs.commonmark)` in build | VERIFIED | `build.gradle.kts` lines 37-38                                           |
| `spring-boot-starter-validation`   | VERIFIED | `libs.versions.toml` line 41; `build.gradle.kts` line 27                         |

---

## Success Criteria Coverage (from ROADMAP.md)

| SC# | Requirement                                                                                              | Status     | Notes                                                                                                        |
|-----|----------------------------------------------------------------------------------------------------------|------------|--------------------------------------------------------------------------------------------------------------|
| SC1 | System chunks Markdown at heading boundaries (H1/H2/H3) and never splits mid-code-block or mid-table    | SATISFIED  | 15 unit tests covering all cases; AST-based approach prevents mid-code-block splits by design                |
| SC2 | Chunks carry configurable overlap (default 50-100 tokens) so context is not lost at boundaries          | OVERRIDDEN | User decision documented in both plans and summaries: no overlap implemented; heading path breadcrumb provides context continuity |
| SC3 | Every chunk carries metadata: source URL, section path, content type, and last updated timestamp        | SATISFIED  | All 4 required fields present + language as 5th field; metadata roundtrip verified by IngestionServiceIT     |
| SC4 | Code examples extracted as separate chunks tagged with language and content_type="code"                 | SATISFIED  | 5 test cases cover code extraction; IngestionServiceIT Test 1 verifies end-to-end with java code block       |
| SC5 | User can optionally provide pre-chunked content bypassing automatic chunking                             | SATISFIED  | PreChunkedImporter with 4 ITs: searchable, replacement, all-or-nothing rejection, invalid content_type        |
| SC6 | End-to-end pipeline: crawl a real documentation site and produce searchable results via hybrid search   | SATISFIED  | IngestionServiceIT.ingest_crawl_result_produces_searchable_chunks() proves CrawlSiteResult -> searchable     |

**Note on SC2:** The plan explicitly documents this as an intentional user decision override, not a gap. The section path breadcrumb (e.g., "introduction/getting-started/configuration") preserves heading hierarchy context as a metadata field, which partially addresses the continuity concern.

---

## Anti-Patterns Scan

No anti-patterns detected in the ingestion package:

- No TODO/FIXME/PLACEHOLDER comments in any production file
- No stub return values (return null, return {}, return [])
- No empty handler implementations
- No console.log-only implementations (Java equivalent: no `System.out.println`-only methods)
- All methods have substantive implementations

---

## Human Verification Required

None. All observable truths are mechanically verifiable:

- Chunking logic: fully covered by 15+14=29 pure unit tests (no Spring context)
- End-to-end pipeline: covered by 8 integration tests against real pgvector via Testcontainers
- Validation rejection: tested programmatically (assertThatThrownBy)
- Replacement semantics: tested programmatically (old content absent, new content present after re-import)

---

## Summary

Phase 04 goal is fully achieved. The ingestion pipeline delivers:

1. **MarkdownChunker** (211 lines): AST-based heading splitter using commonmark-java 0.27.1. Correctly handles all 11 plan test cases: heading splits at H1/H2/H3, H4+ stays in parent, code blocks extracted as separate chunks, headings inside code blocks do not trigger splits, tables preserved, empty docs return empty list, preamble before first heading becomes its own chunk.

2. **LanguageDetector** (68 lines): Keyword-scoring heuristic for 10 languages. Returns highest-scoring language when score >= 2, "unknown" otherwise. Falls back from fence info string for unlabeled code blocks.

3. **DocumentChunkData** (24 lines): Java record with 6 fields (text + 5 metadata). Clean data carrier with no validation logic.

4. **IngestionService** (95 lines): Spring @Service orchestrating CrawlSiteResult -> chunker.chunk() -> embedAll() -> addAll(). Uses batch embedding (not per-chunk) for efficiency. 4 integration tests confirm searchability, metadata correctness, multi-page ingestion, and code-only sections.

5. **PreChunkedImporter** (86 lines): Spring @Service with @Transactional. All-or-nothing Bean Validation before any storage. removeAll(Filter) by source_url for replacement, then addAll(). 4 integration tests confirm searchability, replacement semantics, and both validation rejection paths.

All 29 unit tests pass. All 8 integration tests pass (verified against stored XML results from last run against real pgvector). No stubs, no orphaned artifacts, all key links wired and verified.

---

_Verified: 2026-02-18T05:28:43Z_
_Verifier: Claude (gsd-verifier)_
