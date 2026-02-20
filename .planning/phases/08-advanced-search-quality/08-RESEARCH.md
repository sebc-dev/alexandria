# Phase 8: Advanced Search & Quality - Research

**Researched:** 2026-02-20
**Domain:** Cross-encoder reranking (ONNX in-process), metadata filtering (pgvector JSONB), version tagging
**Confidence:** MEDIUM

## Summary

Phase 8 enhances the existing search pipeline with cross-encoder reranking and metadata-based filtering. The core technical challenge is integrating an ONNX cross-encoder model for in-process reranking via LangChain4j's `OnnxScoringModel`, while the filtering work leverages LangChain4j's existing `Filter` API with `MetadataFilterBuilder` against pgvector's JSONB metadata column.

The cross-encoder reranking requires `langchain4j-onnx-scoring` (version 1.11.0-beta19, matching the project's existing langchain4j-beta version) with an externally-provided ONNX model file. The recommended model is `cross-encoder/ms-marco-MiniLM-L-6-v2` (22.7M params, 74.30 NDCG@10), which offers an excellent balance of accuracy and speed for CPU-only inference. Unlike the embedding model (bundled in a Maven JAR), the scoring model requires downloading ONNX model and tokenizer files from HuggingFace, then providing file paths to `OnnxScoringModel`.

Metadata filtering is well-supported: LangChain4j's `EmbeddingSearchRequest.builder().filter()` accepts composable `Filter` objects, and pgvector's store translates them to JSONB SQL queries. The existing `metadata` column is already JSONB. Adding `version` and `source_name` to chunk metadata at ingestion time enables direct filtering without JOINs. For section path prefix matching, `ContainsString` is supported by pgvector in LangChain4j, though true prefix matching may require a custom SQL approach or using `ContainsString` as a pragmatic approximation.

**Primary recommendation:** Use `langchain4j-onnx-scoring` 1.11.0-beta19 with `ms-marco-MiniLM-L-6-v2` ONNX model (downloaded to a configurable file path), and build all filters using LangChain4j's `MetadataFilterBuilder` composable `Filter` API against the existing JSONB metadata column.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Cross-encoder reranking
- Always-on -- every search goes through reranking automatically, no opt-in parameter
- Pipeline: RRF hybrid retrieves top 50 candidates -> cross-encoder re-ranks -> return top K (maxResults, default 10)
- ONNX in-process only -- no external sidecar or API call, consistent with bge-small embedding approach
- On failure (model missing, ONNX error): return explicit error in MCP response, do NOT silently fall back to RRF-only results
- Include reranking score in each search result so Claude Code can judge confidence

#### Filter experience
- Flat parameters on search_docs: query, source, sectionPath, version, contentType, maxResults, rrfK, minScore
- AND logic when combining multiple filters -- all specified filters must match
- Empty result + explanatory message when filters match nothing (e.g., "No results for version 'React 19'. Available versions: ...")
- sectionPath filter uses prefix matching (hierarchical) -- "API Reference" matches "API Reference > Authentication"
- contentType accepts existing enum values (PROSE, CODE, MIXED) case-insensitive

#### Version tagging
- Version label lives on the Source entity -- one version per source
- Assigned via optional `version` parameter on `add_source`, modifiable via `recrawl_source`
- Free-form string format -- no semver constraint ("3.5", "React 19", "latest" all valid)
- Denormalized into chunk metadata at ingestion time (key: `version`) for direct pgvector filtering without JOIN
- On version change: batch update all chunk metadata for that source

#### Search defaults
- maxResults default stays at 10 (reranking improves quality, not quantity)
- RRF k becomes configurable via optional `rrfK` parameter (default 60)
- Cross-encoder minScore as optional parameter -- results below threshold excluded, may return fewer than maxResults

### Claude's Discretion
- Choice of cross-encoder ONNX model (research best available for documentation/code)
- Exact SQL query construction for metadata filters
- Default value for minScore if not provided (or no threshold by default)
- Error message formatting for empty filter results

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| SRCH-07 | System re-ranks top candidates via cross-encoder model for improved precision | `langchain4j-onnx-scoring` 1.11.0-beta19 with `OnnxScoringModel`; ms-marco-MiniLM-L-6-v2 ONNX model; pipeline: RRF top-50 -> cross-encoder rerank -> top-K |
| SRCH-08 | User can filter search results by section path (e.g., "API Reference" only) | `MetadataFilterBuilder.metadataKey("section_path").containsString()` for prefix matching on existing JSONB metadata |
| SRCH-09 | User can filter search results by version tag | New `version` key in chunk metadata (denormalized from Source), filtered via `metadataKey("version").isEqualTo()` |
| SRCH-10 | User can filter search results by content type (code vs prose vs all) | Filter via `metadataKey("content_type").isEqualTo()` on existing JSONB metadata; add MIXED enum value |
| SRCH-04 | User can filter search results by source name | New `source_name` key in chunk metadata (denormalized from Source), filtered via `metadataKey("source_name").isEqualTo()` |
| CHUNK-06 | User can tag each source with a version label | New `version` column on `sources` table; propagated to chunk metadata at ingestion time |
</phase_requirements>

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| langchain4j-onnx-scoring | 1.11.0-beta19 | In-process ONNX cross-encoder scoring | Same ecosystem as existing embedding model; `ScoringModel` interface integrates with LangChain4j patterns |
| OnnxScoringModel | (in langchain4j-onnx-scoring) | Cross-encoder reranking inference | Runs CPU-only, no external API, constructor takes model+tokenizer paths |
| MetadataFilterBuilder | (in langchain4j-core) | Composable metadata filters for search | Already used in codebase (`IngestionService`, `PreChunkedImporter`); pgvector supports it natively |
| ms-marco-MiniLM-L-6-v2 | ONNX | Cross-encoder model for passage reranking | 22.7M params, 74.30 NDCG@10, 1800 docs/sec on GPU (fast on CPU too); best accuracy/size tradeoff |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| onnxruntime (transitive) | (managed by langchain4j) | ONNX Runtime Java bindings | Pulled in transitively by langchain4j-onnx-scoring; CPU-only, no CUDA needed |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| ms-marco-MiniLM-L-6-v2 (22.7M) | ms-marco-MiniLM-L-12-v2 (33M) | L-12 has marginally better NDCG (74.31 vs 74.30) but is ~2x slower; difference negligible |
| ms-marco-MiniLM-L-6-v2 | bge-reranker-base (278M) | 12x larger, known ClassCastException bug (#3112) with LangChain4j ONNX scorer; NOT recommended |
| ms-marco-MiniLM-L-6-v2 | ms-marco-TinyBERT-L2-v2 (4.4M) | Much faster (9000 docs/sec) but significantly worse accuracy (69.84 NDCG); too much quality loss |

**Installation (Gradle):**
```kotlin
// In libs.versions.toml:
langchain4j-onnx-scoring = { module = "dev.langchain4j:langchain4j-onnx-scoring", version.ref = "langchain4j-beta" }

// In build.gradle.kts:
implementation(libs.langchain4j.onnx.scoring)
```

**Model files (must be downloaded separately):**
- `model.onnx` from `https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/onnx/model.onnx`
- `tokenizer.json` from `https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2/resolve/main/tokenizer.json`

## Architecture Patterns

### Recommended Changes to Existing Structure
```
dev.alexandria/
├── config/
│   └── EmbeddingConfig.java        # ADD: ScoringModel bean (OnnxScoringModel)
│                                    # MODIFY: EmbeddingStore builder to add rrfK as configurable
├── search/
│   ├── SearchService.java          # MODIFY: add reranking step, accept filters
│   ├── SearchRequest.java          # MODIFY: add filter fields (source, sectionPath, version, contentType, rrfK, minScore)
│   ├── SearchResult.java           # MODIFY: add rerankScore field
│   └── RerankerService.java        # NEW: wraps OnnxScoringModel, scores query-passage pairs
├── source/
│   └── Source.java                 # MODIFY: add version column
├── ingestion/
│   ├── IngestionService.java       # MODIFY: accept version + sourceName metadata
│   └── chunking/
│       ├── DocumentChunkData.java  # MODIFY: add version + sourceName fields to metadata
│       └── ContentType.java        # MODIFY: add MIXED enum value
├── mcp/
│   ├── McpToolService.java         # MODIFY: add filter params to searchDocs, version to addSource/recrawlSource
│   └── TokenBudgetTruncator.java   # MODIFY: include rerankScore in output format
└── document/
    └── (no changes)                # JSONB metadata column already supports arbitrary keys
```

### Pattern 1: Reranking Pipeline (SearchService)
**What:** Two-stage retrieval: RRF hybrid retrieves top-N candidates, cross-encoder re-scores them
**When to use:** Every search call (always-on per decision)
**Example:**
```java
// SearchService.search() - modified flow
public List<SearchResult> search(SearchRequest request) {
    Embedding queryEmbedding = embeddingModel.embed(request.query()).content();

    // Stage 1: Retrieve top-50 candidates from hybrid search (RRF)
    EmbeddingSearchRequest.Builder searchBuilder = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .query(request.query())
            .maxResults(50);  // Over-fetch for reranking

    // Apply metadata filters if present
    Filter filter = buildFilter(request);
    if (filter != null) {
        searchBuilder.filter(filter);
    }

    EmbeddingSearchResult<TextSegment> candidates = embeddingStore.search(searchBuilder.build());

    // Stage 2: Cross-encoder reranking
    List<SearchResult> reranked = rerankerService.rerank(
            request.query(), candidates.matches(), request.maxResults(), request.minScore());

    return reranked;
}
```

### Pattern 2: Filter Composition (AND logic)
**What:** Build composable Filter objects from optional search parameters
**When to use:** When any filter parameter is non-null
**Example:**
```java
// Source: LangChain4j MetadataFilterBuilder API
private Filter buildFilter(SearchRequest request) {
    List<Filter> filters = new ArrayList<>();

    if (request.source() != null) {
        filters.add(metadataKey("source_name").isEqualTo(request.source()));
    }
    if (request.version() != null) {
        filters.add(metadataKey("version").isEqualTo(request.version()));
    }
    if (request.contentType() != null) {
        filters.add(metadataKey("content_type").isEqualTo(request.contentType().toLowerCase()));
    }
    if (request.sectionPath() != null) {
        // Prefix matching: "api-reference" matches "api-reference/authentication"
        filters.add(metadataKey("section_path").containsString(slugify(request.sectionPath())));
    }

    if (filters.isEmpty()) return null;
    return filters.stream().reduce(Filter::and).orElse(null);
}
```

### Pattern 3: Version Denormalization at Ingestion
**What:** Copy version from Source entity into each chunk's JSONB metadata
**When to use:** During ingestion (ingestPage) and batch update on version change
**Example:**
```java
// In DocumentChunkData.toMetadata() - extended
public Metadata toMetadata() {
    Metadata metadata = Metadata.from("source_url", sourceUrl)
            .put("section_path", sectionPath)
            .put("content_type", contentType.value())
            .put("last_updated", lastUpdated);
    if (language != null) metadata.put("language", language);
    if (version != null) metadata.put("version", version);
    if (sourceName != null) metadata.put("source_name", sourceName);
    return metadata;
}
```

### Pattern 4: Configurable RRF k via EmbeddingConfig
**What:** Make RRF k configurable as a Spring property, optionally overridable per search
**When to use:** EmbeddingStore bean configuration
**Consideration:** The current `EmbeddingConfig` hardcodes `.rrfK(60)`. To make it configurable per-search, there are two approaches:
1. **Store-level default** (simpler): Change EmbeddingConfig to read from property, keep as global default
2. **Per-request override**: Would require changes to how PgVectorEmbeddingStore handles rrfK -- this may not be supported by the LangChain4j API (rrfK is a store-level config, not a request-level param)

**Recommendation:** Make rrfK configurable at the store level via Spring property (`alexandria.search.rrf-k`). The `rrfK` MCP parameter can override by creating a second store instance or by documenting this limitation. Given complexity, a store-level config with application.yml override is pragmatic.

### Anti-Patterns to Avoid
- **Querying Source table during search:** Never JOIN sources during search queries; denormalize needed metadata into chunks at ingestion time (per decision: no JOIN for filtering)
- **Falling back silently on reranker failure:** User decision explicitly requires error propagation, not silent degradation
- **Loading ONNX model per request:** The `OnnxScoringModel` must be a singleton Spring bean, loaded once at startup
- **Filtering after retrieval in Java:** Apply filters in the pgvector query (via LangChain4j Filter), not by post-filtering results in Java (loses pagination/scoring semantics)

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Cross-encoder scoring | Custom ONNX Runtime inference code | `OnnxScoringModel` from langchain4j-onnx-scoring | Handles tokenization, session management, output parsing |
| Metadata filter SQL | Raw SQL with JSONB operators | `MetadataFilterBuilder` + `EmbeddingSearchRequest.filter()` | pgvector store translates to correct SQL; composable, type-safe |
| ONNX model file management | Classpath bundling or download-at-startup | Docker volume mount with configurable path | Model files are 80MB+; don't bundle in JAR, don't download at runtime |
| ContentType case normalization | Manual toLowerCase comparison | Enum `fromValue()` with case-insensitive matching | Already have `ContentType.fromValue()`; extend it |

**Key insight:** LangChain4j provides both the `ScoringModel` interface and the `Filter` API -- use them both rather than building custom inference or SQL query logic.

## Common Pitfalls

### Pitfall 1: ONNX Model ClassCastException with bge-reranker Models
**What goes wrong:** LangChain4j's `OnnxScoringBertCrossEncoder` casts ONNX output as `float[][]` but some models (notably bge-reranker-base) output `float[][][]`, causing `ClassCastException`
**Why it happens:** Different cross-encoder architectures produce different output tensor shapes; LangChain4j only handles the 2D case
**How to avoid:** Use `ms-marco-MiniLM-L-6-v2` which produces the expected 2D output shape; avoid bge-reranker models with LangChain4j ONNX scorer
**Warning signs:** `ClassCastException: class [[[F cannot be cast to class [[F` at runtime
**Reference:** https://github.com/langchain4j/langchain4j/issues/3112

### Pitfall 2: ONNX Model Path at Runtime in Docker
**What goes wrong:** Model file path is valid on host but not inside Docker container
**Why it happens:** ONNX model files must be accessible at the path provided to `OnnxScoringModel`; Docker containers have different filesystems
**How to avoid:** Use Docker volume mount for model files; configure path via Spring property with sensible default (e.g., `/app/models/ms-marco-MiniLM-L-6-v2/`)
**Warning signs:** `FileNotFoundException` or ONNX session creation failure at startup

### Pitfall 3: Section Path Format Mismatch Between Ingestion and Search
**What goes wrong:** Search filter doesn't match any chunks because section_path format differs between what was stored and what user provides
**Why it happens:** Ingestion slugifies section paths (e.g., "API Reference" -> "api-reference"), but user might search with original human-readable text
**How to avoid:** Slugify the sectionPath filter parameter before building the filter; document that sectionPath uses slugified format. Use `containsString` rather than `isEqualTo` for prefix matching.
**Warning signs:** Empty results when filtering by section path that visually appears in search results

### Pitfall 4: Missing Version Metadata on Pre-existing Chunks
**What goes wrong:** Chunks ingested before version tagging have no `version` key in metadata; version filter excludes them
**Why it happens:** Version denormalization only applies to newly ingested chunks; existing chunks lack the key
**How to avoid:** When a version is first set on a Source, batch-update metadata for all existing chunks of that source. Consider: what happens when version filter is applied but some sources have no version? Should they be included or excluded?
**Warning signs:** Version filter returns far fewer results than expected

### Pitfall 5: RRF k Not Being Per-Request Configurable
**What goes wrong:** Implementing rrfK as a search_docs parameter but PgVectorEmbeddingStore only accepts rrfK at construction time
**Why it happens:** LangChain4j's PgVectorEmbeddingStore configures rrfK in the builder, not in EmbeddingSearchRequest
**How to avoid:** Make rrfK a store-level configuration via Spring property (not per-request). If per-request is truly needed, would require custom store wrapper or upstream contribution.
**Warning signs:** rrfK parameter is accepted but has no effect on search results

### Pitfall 6: Over-fetching for Reranking with Filters
**What goes wrong:** Requesting 50 candidates for reranking but metadata filters reduce the candidate set to < 10
**Why it happens:** The 50-candidate over-fetch is applied after filters, so filtered searches may have few candidates
**How to avoid:** This is acceptable behavior -- the cross-encoder simply reranks whatever the filter returns. Document that filtered searches with few matches may return all matches reranked.

## Code Examples

### OnnxScoringModel Bean Configuration
```java
// Source: LangChain4j docs - https://docs.langchain4j.dev/integrations/scoring-reranking-models/in-process/
@Bean
public ScoringModel scoringModel(
        @Value("${alexandria.reranker.model-path}") String modelPath,
        @Value("${alexandria.reranker.tokenizer-path}") String tokenizerPath) {
    return new OnnxScoringModel(modelPath, tokenizerPath);
}
```

### Scoring a Single Query-Passage Pair
```java
// Source: LangChain4j docs
Response<Double> scoreResponse = scoringModel.score("What is Spring Boot?", passageText);
double relevanceScore = scoreResponse.content();
```

### Scoring Multiple Passages (Batch)
```java
// Source: LangChain4j ScoringModel interface
List<TextSegment> segments = candidates.stream()
        .map(match -> match.embedded())
        .toList();
Response<List<Double>> scores = scoringModel.scoreAll(segments, query);
```

### Building Composable Filters
```java
// Source: LangChain4j MetadataFilterBuilder - used in existing codebase
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

Filter sourceFilter = metadataKey("source_name").isEqualTo("Spring Boot Docs");
Filter versionFilter = metadataKey("version").isEqualTo("3.5");
Filter combined = sourceFilter.and(versionFilter);

EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .query(queryText)
        .filter(combined)
        .maxResults(50)
        .build();
```

### Flyway Migration for Version Column
```sql
-- V3__source_version_column.sql
ALTER TABLE sources ADD COLUMN version TEXT;
```

### Batch Update Chunk Metadata (Version Denormalization)
```java
// Custom SQL approach for updating JSONB metadata on existing chunks
// Must use native query since LangChain4j EmbeddingStore doesn't expose metadata update
@Query(value = """
    UPDATE document_chunks
    SET metadata = jsonb_set(COALESCE(metadata, '{}'::jsonb), '{version}', to_jsonb(:version))
    WHERE source_id = :sourceId
    """, nativeQuery = true)
@Modifying
void updateVersionMetadata(@Param("sourceId") UUID sourceId, @Param("version") String version);
```

### Empty Filter Result with Available Values
```java
// Query available values for helpful error messages
@Query(value = """
    SELECT DISTINCT metadata->>'version' as version
    FROM document_chunks
    WHERE metadata->>'version' IS NOT NULL
    """, nativeQuery = true)
List<String> findDistinctVersions();
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| RRF-only hybrid search | RRF + cross-encoder reranking | 2023-2024 widespread adoption | 10-28% NDCG improvement depending on model |
| Remote reranking APIs (Cohere, Jina) | In-process ONNX reranking | 2024 (LangChain4j 0.35+) | No external API dependency, lower latency, free |
| Separate filter queries | Composable Filter API in search request | LangChain4j 0.30+ | Single-query filter+search, database-level filtering |
| Column-based metadata | JSONB metadata in pgvector | LangChain4j 0.35+ | Flexible schema, no migration for new metadata keys |

**Deprecated/outdated:**
- Cohere reranking API: Still works but unnecessary overhead for self-hosted; ONNX in-process is preferred
- Custom SQL for metadata filtering: LangChain4j now handles this via Filter API; only use native SQL for batch metadata updates

## Open Questions

1. **MIXED ContentType value**
   - What we know: User decision says `contentType accepts PROSE, CODE, MIXED`. Current code only has PROSE and CODE enum values. No chunks are tagged MIXED.
   - What's unclear: Should MIXED be a real enum value stored on chunks, or a search-only filter meaning "return both PROSE and CODE"? If it's a filter-only concept, it means "no content_type filter" rather than a metadata value.
   - Recommendation: Implement MIXED as "no filter" behavior in search (return all content types). Do NOT add it as an enum value stored in metadata. When contentType param is null or "MIXED", simply skip the content_type filter. This avoids a metadata schema change with no real benefit.

2. **Per-request RRF k override**
   - What we know: User decision says rrfK should be a configurable parameter on search_docs. LangChain4j's PgVectorEmbeddingStore accepts rrfK only at construction time.
   - What's unclear: Whether this can be overridden per-request without creating a new store instance
   - Recommendation: Implement as store-level Spring property (`alexandria.search.rrf-k`, default 60). Accept the parameter in MCP but log a warning that per-request override is not supported by the underlying store. Alternatively, document that rrfK changes require application restart.

3. **OnnxScoringModel `scoreAll` batch behavior**
   - What we know: The `score(query, passage)` method returns a single Double. `scoreAll(segments, query)` returns `Response<List<Double>>`.
   - What's unclear: Whether `scoreAll` processes in a single ONNX inference call or loops internally; performance characteristics for 50 candidates
   - Recommendation: Test with 50 candidates during implementation. Cross-encoder inference on 50 pairs with a 22M-param model should complete in <500ms on CPU. If slower, consider reducing to 30 candidates.

4. **Section path prefix matching implementation**
   - What we know: `ContainsString` is supported by pgvector in LangChain4j. Section paths are slugified during ingestion (e.g., "api-reference/authentication").
   - What's unclear: Whether `containsString("api-reference")` correctly matches "api-reference/authentication/oauth" without false positives (e.g., matching "some-api-reference-note")
   - Recommendation: Use `containsString` as initial implementation. Since section paths use slash-separated slugs, matching "api-reference" will catch "api-reference/..." and also "parent/api-reference" (false positive on mid-path match). For true prefix matching, consider appending "/" to the filter: `containsString("api-reference/")` or using a native SQL approach with `LIKE 'api-reference%'`. Test with real data during implementation.

5. **Model file distribution in Docker**
   - What we know: ONNX model + tokenizer = ~90MB total. Can't bundle in JAR (too large). Docker volume mount is standard.
   - What's unclear: Best practice for first-time setup (user must download model files)
   - Recommendation: Add model files to Docker image build (download during `docker build`), or provide a startup script that downloads if missing. Configure path via `alexandria.reranker.model-path` property with default pointing to `/app/models/` inside container.

## Sources

### Primary (HIGH confidence)
- LangChain4j Official Docs: https://docs.langchain4j.dev/integrations/scoring-reranking-models/in-process/ - OnnxScoringModel API, constructor, Maven coordinates
- LangChain4j Official Docs: https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/ - pgvector metadata filtering, JSONB storage config
- Sentence Transformers Pretrained Models: https://sbert.net/docs/cross_encoder/pretrained_models.html - NDCG benchmarks for all MS MARCO cross-encoder models
- Maven Central: langchain4j-onnx-scoring 1.11.0-beta19 available at https://repo1.maven.org/maven2/dev/langchain4j/langchain4j-onnx-scoring/

### Secondary (MEDIUM confidence)
- LangChain4j PR #1769 (merged): https://github.com/langchain4j/langchain4j/pull/1769 - OnnxScoringModel implementation details
- LangChain4j Issue #3112 (open bug): https://github.com/langchain4j/langchain4j/issues/3112 - ClassCastException with bge-reranker-base (confirms ms-marco models are safer choice)
- HuggingFace Xenova/ms-marco-MiniLM-L-6-v2: https://huggingface.co/Xenova/ms-marco-MiniLM-L-6-v2 - Pre-converted ONNX model files
- LangChain4j Filter API source: https://github.com/langchain4j/langchain4j/blob/main/langchain4j-core/src/main/java/dev/langchain4j/store/embedding/filter/Filter.java - Available filter operations (isEqualTo, isIn, containsString, and/or/not)
- LangChain4j examples (metadata filtering): https://github.com/langchain4j/langchain4j-examples/blob/main/rag-examples/src/main/java/_3_advanced/_05_Advanced_RAG_with_Metadata_Filtering_Examples.java

### Tertiary (LOW confidence)
- Cross-encoder/ms-marco-MiniLM-L6-v2 HuggingFace card: https://huggingface.co/cross-encoder/ms-marco-MiniLM-L6-v2 - Model trained on MS MARCO passage ranking dataset
- ZeroEntropy reranking guide 2026: https://www.zeroentropy.dev/articles/ultimate-guide-to-choosing-the-best-reranking-model-in-2025 - Industry context on cross-encoder reranking adoption

## Metadata

**Confidence breakdown:**
- Standard stack: MEDIUM - langchain4j-onnx-scoring verified on Maven Central; OnnxScoringModel API verified from official docs; ms-marco-MiniLM model benchmarks verified from sbert.net. However, open ClassCastException bug (#3112) on some models is concerning (mitigated by choosing ms-marco which uses expected output shape). `scoreAll` batch performance unverified.
- Architecture: HIGH - Filter API verified from LangChain4j source code and examples; pattern of over-fetch + rerank is standard in industry; metadata denormalization pattern is straightforward.
- Pitfalls: HIGH - ClassCastException bug verified from GitHub issue; Docker path issues are standard operational concern; section path format mismatch identified from codebase analysis.

**Research date:** 2026-02-20
**Valid until:** 2026-03-20 (30 days -- LangChain4j releases frequently but ms-marco model is stable)
