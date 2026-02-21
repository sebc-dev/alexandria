---
phase: 08-advanced-search-quality
verified: 2026-02-20T14:00:00Z
status: passed
score: 20/20 must-haves verified
re_verification: null
gaps: []
human_verification:
  - test: "Run ./quality.sh test to confirm all 265 unit tests pass"
    expected: "All tests pass with 0 failures"
    why_human: "Tests require the full Gradle toolchain and cannot be confirmed statically"
  - test: "Docker build to confirm ms-marco-MiniLM-L-6-v2 ONNX model downloads successfully"
    expected: "docker build succeeds, model files present at /app/models/ms-marco-MiniLM-L-6-v2/"
    why_human: "Requires Docker daemon and internet access to HuggingFace"
  - test: "End-to-end: call search_docs MCP tool with version='React 19' filter after indexing a versioned source"
    expected: "Results are filtered to only React 19 chunks, reranking scores appear in output"
    why_human: "Requires live PostgreSQL+pgvector database and ONNX model loaded at runtime"
---

# Phase 8: Advanced Search Quality Verification Report

**Phase Goal:** Search results are more precise through cross-encoder reranking and richer filtering options -- the quality multiplier layer
**Verified:** 2026-02-20T14:00:00Z
**Status:** PASSED
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Source entity has a version field that persists to the database | VERIFIED | `Source.java` L75-76 `@Column(name = "version") private String version;` + `getVersion()`/`setVersion()` L190-196; V3 migration `ALTER TABLE sources ADD COLUMN version TEXT` confirmed |
| 2 | Version label is denormalized into chunk metadata at ingestion time as 'version' key | VERIFIED | `DocumentChunkData.java` L53-55 `if (version != null) { metadata.put("version", version); }` in `toMetadata()` |
| 3 | Source name is denormalized into chunk metadata at ingestion time as 'source_name' key | VERIFIED | `DocumentChunkData.java` L56-58 `if (sourceName != null) { metadata.put("source_name", sourceName); }` in `toMetadata()` |
| 4 | Batch update of chunk metadata version works for existing chunks when version changes | VERIFIED | `DocumentChunkRepository.java` L24-31 `updateVersionMetadata()` native JSONB query using `jsonb_set(COALESCE(metadata, '{}'::jsonb), '{version}', to_jsonb(:version))` |
| 5 | ContentType.fromValue() handles case-insensitive input including 'MIXED' | VERIFIED | `ContentType.java` L27 `type.value.equalsIgnoreCase(value)`; `parseSearchFilter()` L41-46 returns null for MIXED |
| 6 | ScoringModel bean loads the ms-marco-MiniLM-L-6-v2 ONNX model at startup | VERIFIED | `EmbeddingConfig.java` L48-53 `@Bean public ScoringModel scoringModel(...)` with `new OnnxScoringModel(modelPath, tokenizerPath)` |
| 7 | RerankerService scores query-passage pairs and returns results sorted by reranking score | VERIFIED | `RerankerService.java` L57-62: zips candidates with scores, sorts by `rerankScore` descending, limits by `maxResults` |
| 8 | RerankerService respects maxResults limit after reranking | VERIFIED | `RerankerService.java` L61 `.limit(maxResults)` + `RerankerServiceTest.java` `respectsMaxResultsLimit()` test |
| 9 | RerankerService respects minScore threshold, excluding low-confidence results | VERIFIED | `RerankerService.java` L59 `.filter(r -> minScore == null \|\| r.rerankScore() >= minScore)` + `respectsMinScoreThreshold()` test |
| 10 | On ONNX model failure, RerankerService throws exception (not silent fallback) | VERIFIED | No try/catch in `rerank()` method; `propagatesScoringModelException()` test confirms exception propagates |
| 11 | SearchRequest accepts optional filter parameters: source, sectionPath, version, contentType, rrfK | VERIFIED | `SearchRequest.java` L25-34: record with all 8 fields including source, sectionPath, version, contentType, minScore, rrfK |
| 12 | SearchService builds composable LangChain4j Filter from SearchRequest filter params (AND logic) | VERIFIED | `SearchService.java` `buildFilter()` L87-112: source_name isEqualTo, version isEqualTo, section_path containsString, content_type isEqualTo, combined via `reduce((a, b) -> a.and(b))` |
| 13 | SearchService over-fetches 50 candidates for reranking, then returns top maxResults after reranking | VERIFIED | `SearchService.java` L68 `.maxResults(RERANK_CANDIDATES)` (RERANK_CANDIDATES=50, L35); L77 delegates to rerankerService with `request.maxResults()` |
| 14 | Section path filter uses prefix matching via containsString on slugified input | VERIFIED | `SearchService.java` L99 `metadataKey("section_path").containsString(slugify(request.sectionPath()))`; `slugify()` L118-122 lowercases and replaces non-alnum with hyphens |
| 15 | Content type MIXED (or null) means no content_type filter | VERIFIED | `SearchService.java` L103-106: calls `ContentType.parseSearchFilter()` and only adds filter when parsed != null; `searchWithContentTypeMixedSkipsFilter()` test confirms |
| 16 | search_docs MCP tool accepts filter params: source, sectionPath, version, contentType, minScore, rrfK | VERIFIED | `McpToolService.java` L81-109: `searchDocs()` has 8 `@ToolParam` parameters including all filters; constructs `SearchRequest` with all params at L99 |
| 17 | search_docs returns reranking score in each result for Claude Code confidence assessment | VERIFIED | `TokenBudgetTruncator.java` L83-89: `formatResult()` outputs `"Score: %.3f"` using `result.rerankScore()` |
| 18 | add_source MCP tool accepts optional version parameter that persists on Source entity | VERIFIED | `McpToolService.java` L153: `@ToolParam(...) String version`; L164 `source.setVersion(version)` before save; `addSourceSetsVersionOnEntity()` test confirms |
| 19 | recrawl_source MCP tool accepts optional version parameter and batch-updates chunk metadata on version change | VERIFIED | `McpToolService.java` L241: version param; L262-266: `if (version != null && !version.equals(source.getVersion()))` -> setVersion + save + `updateVersionMetadata()`; all 3 recrawl version tests confirm |
| 20 | CrawlService passes version and sourceName to IngestionService during ingestion | VERIFIED | `CrawlService.java` L80-88: loads Source once per crawl; L94-95 (llms-full.txt call) and L247-248 (incremental call) both use 5-arg `ingestPage(...)` passing `sourceVersion, sourceName` |

**Score:** 20/20 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V3__source_version_column.sql` | Version column on sources table | VERIFIED | Contains `ALTER TABLE sources ADD COLUMN version TEXT` |
| `src/main/java/dev/alexandria/source/Source.java` | version getter/setter on Source entity | VERIFIED | L75-76 field, L190-196 getter/setter |
| `src/main/java/dev/alexandria/ingestion/chunking/DocumentChunkData.java` | version and sourceName fields in chunk metadata | VERIFIED | L29-31 fields, L53-58 conditional metadata.put |
| `src/main/java/dev/alexandria/document/DocumentChunkRepository.java` | updateVersionMetadata and findDistinctVersions queries | VERIFIED | All 4 native queries present: updateVersionMetadata, updateSourceNameMetadata, findDistinctVersions, findDistinctSourceNames |
| `src/main/java/dev/alexandria/ingestion/chunking/ContentType.java` | parseSearchFilter() method | VERIFIED | L41-46 parseSearchFilter returning null for null/MIXED |
| `src/main/java/dev/alexandria/ingestion/IngestionService.java` | 5-param ingestPage() overload | VERIFIED | L74-82 5-param overload with enrichChunks() helper |
| `src/main/java/dev/alexandria/search/RerankerService.java` | Cross-encoder reranking logic | VERIFIED | 75 lines, full implementation: scoreAll, zip, sort, filter, limit |
| `src/main/java/dev/alexandria/config/EmbeddingConfig.java` | ScoringModel bean from OnnxScoringModel | VERIFIED | L48-53 @Bean with @Value injection, `new OnnxScoringModel(modelPath, tokenizerPath)` |
| `src/test/java/dev/alexandria/search/RerankerServiceTest.java` | Unit tests for reranking behavior | VERIFIED | 150 lines, 7 tests covering all required behaviors |
| `src/main/java/dev/alexandria/search/SearchResult.java` | rerankScore field | VERIFIED | 5-field record with rerankScore; 4-arg backward-compat constructor |
| `src/main/java/dev/alexandria/search/SearchRequest.java` | Extended search request with filter fields | VERIFIED | 8-field record: query, maxResults, source, sectionPath, version, contentType, minScore, rrfK |
| `src/main/java/dev/alexandria/search/SearchService.java` | Filter composition + reranking pipeline | VERIFIED | 123 lines; buildFilter(), slugify(), 50-candidate over-fetch, rerankerService.rerank() delegation |
| `src/test/java/dev/alexandria/search/SearchServiceTest.java` | Unit tests for filter composition and reranking | VERIFIED | 248 lines, 11 tests covering all filter types and reranker delegation |
| `src/main/java/dev/alexandria/mcp/McpToolService.java` | Extended MCP tools with filter and version params | VERIFIED | 402 lines; search_docs (8 params), add_source (version), recrawl_source (version+batch-update), empty result messages |
| `src/main/java/dev/alexandria/mcp/TokenBudgetTruncator.java` | Search result formatting with reranking score | VERIFIED | L83-89 `formatResult()` with `Score: %.3f` using rerankScore |
| `src/test/java/dev/alexandria/mcp/McpToolServiceTest.java` | Unit tests for all new MCP parameters | VERIFIED | 665 lines; 9 new Phase 8 tests covering filter forwarding, empty results, addSource/recrawlSource version |
| `src/main/java/dev/alexandria/crawl/CrawlService.java` | version/sourceName passthrough at both ingestPage call sites | VERIFIED | L94-95 (llms-full.txt) and L247-248 (incremental) both pass sourceVersion/sourceName |
| `src/main/resources/application.yml` | reranker model paths + rrf-k config | VERIFIED | L29-33: `rrf-k: 60`, `model-path` and `tokenizer-path` with env var defaults |
| `gradle/libs.versions.toml` + `build.gradle.kts` | langchain4j-onnx-scoring dependency | VERIFIED | libs.versions.toml L30 and build.gradle.kts L32 both confirmed |
| `Dockerfile` | ms-marco-MiniLM-L-6-v2 model download during build | VERIFIED | L22-27: curl downloads model.onnx and tokenizer.json from HuggingFace |
| `docker-compose.yml` | RERANKER_MODEL_PATH and RERANKER_TOKENIZER_PATH env vars | VERIFIED | L51-52 confirmed |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `EmbeddingConfig.java` | `OnnxScoringModel` | Spring @Bean constructor with model/tokenizer paths | WIRED | L48-53: `new OnnxScoringModel(modelPath, tokenizerPath)` with @Value injection |
| `RerankerService.java` | `ScoringModel` | constructor injection, `scoreAll()` for batch scoring | WIRED | L30 constructor injection; L55 `scoringModel.scoreAll(segments, query)` |
| `SearchService.java` | `RerankerService` | `search()` calls `rerankerService.rerank()` after EmbeddingStore retrieval | WIRED | L39 constructor injection; L77 `rerankerService.rerank(request.query(), candidates, request.maxResults(), request.minScore())` |
| `SearchService.java` | `MetadataFilterBuilder` | `buildFilter()` creates composable Filter from SearchRequest fields | WIRED | L87-112: `metadataKey("source_name").isEqualTo()`, `.containsString()`, `.isEqualTo()`, combined with `reduce((a, b) -> a.and(b))` |
| `SearchService.java` | `EmbeddingSearchRequest` | filter() passed to search request, maxResults=50 for over-fetch | WIRED | L65-74: builder with `maxResults(RERANK_CANDIDATES)` (50) and conditional `.filter(filter)` |
| `McpToolService.java` | `SearchService` | `searchDocs` passes filter params via `SearchRequest` | WIRED | L98-99: `new SearchRequest(query, max, source, sectionPath, version, contentType, minScore, rrfK)` |
| `McpToolService.java` | `Source.setVersion` | `addSource` and `recrawlSource` set version on Source entity | WIRED | L164 (`addSource`) and L263 (`recrawlSource`) both call `source.setVersion(version)` |
| `McpToolService.java` | `DocumentChunkRepository` | `recrawlSource` calls batch update metadata on version change | WIRED | L265: `documentChunkRepository.updateVersionMetadata(source.getUrl(), version)` when version differs |
| `CrawlService.java` | `IngestionService.ingestPage` | passes version and sourceName from Source to ingestPage | WIRED | L94-95 (llms-full.txt) and L247-248 (incremental crawl) both use 5-arg signature |
| `IngestionService.java` | `DocumentChunkData` | passes version and sourceName from caller to chunk metadata | WIRED | L77-79: `enrichChunks()` creates new DocumentChunkData instances with version/sourceName |
| `DocumentChunkData.java` | `Metadata` | `toMetadata()` includes version and source_name keys | WIRED | L53-58: conditional `metadata.put("version", ...)` and `metadata.put("source_name", ...)` |

### Requirements Coverage

| Requirement | Status | Notes |
|-------------|--------|-------|
| CHUNK-06: User can tag each source with a version label | SATISFIED | Source.version field (V3 migration), setVersion() in add_source and recrawl_source MCP tools, denormalized to chunk metadata |
| SRCH-04: User can filter search results by source name | SATISFIED | SearchService.buildFilter() builds `metadataKey("source_name").isEqualTo(source)`, wired through SearchRequest -> McpToolService |
| SRCH-07: System re-ranks top candidates via cross-encoder model | SATISFIED | RerankerService with OnnxScoringModel (ms-marco-MiniLM-L-6-v2), wired into SearchService two-stage pipeline |
| SRCH-08: User can filter search results by section path | SATISFIED | SearchService.buildFilter() builds `metadataKey("section_path").containsString(slugify(sectionPath))`, exposed via search_docs sectionPath param |
| SRCH-09: User can filter search results by version tag | SATISFIED | SearchService.buildFilter() builds `metadataKey("version").isEqualTo(version)`, exposed via search_docs version param |
| SRCH-10: User can filter search results by content type | SATISFIED | SearchService.buildFilter() uses ContentType.parseSearchFilter() -> `metadataKey("content_type").isEqualTo(...)`, MIXED/null skips filter |

All 6 required requirements (CHUNK-06, SRCH-04, SRCH-07, SRCH-08, SRCH-09, SRCH-10) are SATISFIED.

### Anti-Patterns Found

No anti-patterns found in Phase 8 code:
- No TODO/FIXME/HACK/PLACEHOLDER comments in any Phase 8 source files
- No stub return patterns (return null, return {}, return []) in service implementations
- No empty handlers or uncaught silent failures
- All key links verified as fully wired with substantive implementations

### Human Verification Required

#### 1. Unit Test Suite Execution

**Test:** Run `./quality.sh test` from project root
**Expected:** All 265 tests pass with 0 failures (per 08-04 self-check)
**Why human:** Requires Gradle toolchain, cannot confirm statically

#### 2. Docker Build Model Download

**Test:** Run `docker build .` and verify model files present
**Expected:** `docker build` completes, `/app/models/ms-marco-MiniLM-L-6-v2/model.onnx` and `tokenizer.json` exist in image
**Why human:** Requires Docker daemon and internet access to HuggingFace CDN

#### 3. Live Reranking End-to-End

**Test:** Add source with version="React 19", crawl, then search_docs with version="React 19" filter and observe reranking scores in output
**Expected:** Results only contain React 19 chunks; each result shows "Score: X.XXX" where X differs by result (confirming reranking actually ran)
**Why human:** Requires live database, ONNX model loaded, and actual indexed content

### Gaps Summary

No gaps. All 20 observable truths verified, all artifacts exist and are substantively implemented, all key links are wired, and all 6 requirement IDs are satisfied.

The phase delivered exactly what the goal promised: cross-encoder reranking (RerankerService + OnnxScoringModel) and richer filtering options (source, sectionPath, version, contentType filters) composable through the full stack from MCP tools through SearchService to EmbeddingStore. Version tagging infrastructure (Source.version, chunk metadata denormalization, batch update queries) provides the data foundation for version filtering.

---

_Verified: 2026-02-20T14:00:00Z_
_Verifier: Claude (gsd-verifier)_
