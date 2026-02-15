# Phase 2: Core Search - Research

**Researched:** 2026-02-15
**Domain:** Hybrid search (vector + full-text + RRF) with LangChain4j PgVectorEmbeddingStore on PostgreSQL
**Confidence:** HIGH

## Summary

Phase 2 implements hybrid search combining semantic vector search (pgvector HNSW, cosine similarity) with PostgreSQL full-text search (tsvector/tsquery), merged via Reciprocal Rank Fusion (RRF). The excellent news is that LangChain4j 1.11.0-beta19 (already in the project's dependency catalog) ships with built-in hybrid search support for PgVectorEmbeddingStore, merged via PR #4288 on Feb 3, 2026. This means the core search functionality -- vector search, keyword search, RRF fusion, configurable result count -- is available out of the box through `SearchMode.HYBRID` on the existing `PgVectorEmbeddingStore` builder.

The primary implementation work is: (1) reconfigure the existing `EmbeddingStore` bean with `searchMode(SearchMode.HYBRID)` and `textSearchConfig("english")`, (2) fix the GIN index expression mismatch between the Flyway migration and LangChain4j's expected SQL (critical), (3) build a `SearchService` that wraps the `EmbeddingStore.search()` call and maps results to a domain DTO with citation metadata (source URL, section path), and (4) write integration tests with manually seeded test data proving all three search modes work.

**Primary recommendation:** Leverage LangChain4j's built-in `SearchMode.HYBRID` on `PgVectorEmbeddingStore` -- do NOT hand-roll RRF or full-text search SQL. Focus implementation effort on the service layer, the GIN index fix, and thorough integration testing.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| LangChain4j PgVector | 1.11.0-beta19 | Embedding store with hybrid search | Built-in SearchMode.HYBRID with RRF, already a project dependency |
| LangChain4j Core | 1.11.0 | EmbeddingSearchRequest, Filter API, TextSegment/Metadata | Core abstractions for search requests and results |
| LangChain4j BGE Embeddings | 1.11.0-beta19 | ONNX embedding model (bge-small-en-v1.5-q) | Already configured in Phase 1 |
| PostgreSQL + pgvector | pg16 (pgvector/pgvector:pg16) | Vector storage, HNSW index, GIN full-text index | Already running in Docker Compose |
| Flyway | 11.8.2 | Schema migrations (GIN index fix) | Already managing schema |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Boot Starter Web | 3.5.7 | REST controller for search endpoint | Expose search API for integration tests and future MCP |
| Spring Boot Starter Test | 3.5.7 | Integration test framework | @SpringBootTest with Testcontainers |
| Testcontainers PostgreSQL | 1.21.1 | PostgreSQL container for tests | Integration tests with real pgvector |
| AssertJ | (via spring-boot-starter-test) | Fluent test assertions | Asserting search result quality |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| LangChain4j built-in RRF | Custom RRF SQL | More control but unnecessary complexity; LangChain4j's SQL CTE approach is well-tested |
| PostgreSQL ts_rank | pg_textsearch (BM25) | True BM25 ranking is better but requires a PostgreSQL extension not in standard pgvector image |
| textSearchConfig "english" | textSearchConfig "simple" | "simple" does no stemming; "english" stems words (configure -> configur) improving recall |

**Dependencies (Gradle):**
```groovy
// Already in libs.versions.toml - no new dependencies needed for Phase 2
// All search functionality is provided by the existing langchain4j-pgvector dependency
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/dev/alexandria/
  config/
    EmbeddingConfig.java       # MODIFY: add searchMode, textSearchConfig, rrfK
  search/
    SearchService.java         # NEW: orchestrates search, maps results to DTOs
    SearchResult.java          # NEW: domain DTO with text, score, sourceUrl, sectionPath
    SearchRequest.java         # NEW: domain request with query, maxResults
  document/
    DocumentChunk.java         # EXISTS: JPA entity (unchanged)
    DocumentChunkRepository.java # EXISTS: Spring Data repo (unchanged)

src/main/resources/db/migration/
  V2__fix_gin_index_expression.sql  # NEW: fix GIN index to match LangChain4j SQL

src/integrationTest/java/dev/alexandria/
  search/
    SemanticSearchIT.java      # NEW: vector search tests
    KeywordSearchIT.java       # NEW: full-text search tests
    HybridSearchIT.java        # NEW: hybrid search + RRF tests
    SearchTestFixtures.java    # NEW: test data factory
```

### Pattern 1: SearchService Wrapping EmbeddingStore
**What:** A Spring `@Service` that accepts a domain-level `SearchRequest`, delegates to `EmbeddingStore.search()`, and maps `EmbeddingMatch<TextSegment>` results to domain `SearchResult` DTOs with citation metadata extracted from `TextSegment.metadata()`.
**When to use:** Always -- the MCP layer (Phase 5) will call this service, not the raw EmbeddingStore.
**Example:**
```java
// Source: LangChain4j PgVector docs + verified EmbeddingSearchRequest API
@Service
public class SearchService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public SearchService(EmbeddingStore<TextSegment> embeddingStore,
                         EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    public List<SearchResult> search(String query, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .query(query)           // text query for full-text search in HYBRID mode
                .maxResults(maxResults)
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

        return result.matches().stream()
                .map(this::toSearchResult)
                .toList();
    }

    private SearchResult toSearchResult(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        Metadata metadata = segment.metadata();
        return new SearchResult(
                segment.text(),
                match.score(),
                metadata.getString("source_url"),
                metadata.getString("section_path")
        );
    }
}
```

### Pattern 2: Test Data Seeding with Metadata
**What:** Integration tests create TextSegments with structured metadata (source_url, section_path) and store them via EmbeddingStore, then search and verify results include citation data.
**When to use:** All search integration tests -- Phase 2 validates search with manually inserted test data before any crawling exists.
**Example:**
```java
// Source: LangChain4j TextSegment/Metadata API docs
private void seedTestData() {
    Metadata metadata = Metadata.from("source_url", "https://docs.spring.io/routing")
            .put("section_path", "Configuration > Routing > Basic Setup");

    TextSegment segment = TextSegment.from(
            "To configure routing in Spring Boot, use the @RequestMapping annotation...",
            metadata
    );

    Embedding embedding = embeddingModel.embed(segment).content();
    embeddingStore.add(embedding, segment);
}
```

### Pattern 3: Separate EmbeddingStore Beans for Different Search Modes
**What:** If both pure vector search and hybrid search are needed simultaneously, configure a single `PgVectorEmbeddingStore` with `SearchMode.HYBRID` and use the `query` field presence/absence to control behavior.
**When to use:** The `SearchMode` is set at store construction time. With `HYBRID` mode, when `EmbeddingSearchRequest.query()` is null, the store falls back to vector-only search. When the `query` field is populated, it performs hybrid search.
**Important:** Do NOT create multiple EmbeddingStore beans. A single HYBRID-configured store handles both modes.

### Anti-Patterns to Avoid
- **Hand-rolling RRF SQL:** LangChain4j already implements RRF via a well-tested CTE-based SQL query. Writing custom SQL duplicates effort and risks bugs in score normalization.
- **Using MetadataStorageConfig.COLUMN_PER_KEY:** The project uses a JSONB `metadata` column managed by Flyway. Using COLUMN_PER_KEY would require separate columns for each metadata key, conflicting with the existing schema.
- **Creating a separate tsvector column:** LangChain4j's hybrid search computes tsvector on-the-fly from the `text` column. Adding a stored tsvector column would diverge from LangChain4j's expectations.
- **Using textSearchConfig "simple":** The "simple" config does no stemming. For English documentation search, "english" provides better recall by normalizing word forms (e.g., "configuring" matches "configure").

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Vector similarity search | Custom SQL with `<=>` operator | `EmbeddingStore.search()` with `SearchMode.VECTOR` | LangChain4j handles score normalization `(2-dist)/2`, min-score filtering, metadata column resolution |
| Full-text search | Custom tsvector/tsquery queries | `EmbeddingStore.search()` with `SearchMode.HYBRID` | LangChain4j builds correct `plainto_tsquery`, handles NULL coalescing, uses configurable text search config |
| Reciprocal Rank Fusion | Custom merge/sort of two result lists | `SearchMode.HYBRID` on PgVectorEmbeddingStore | LangChain4j implements RRF in a single SQL CTE with FULL OUTER JOIN, avoiding multiple round-trips |
| Embedding generation | Custom ONNX runtime calls | `EmbeddingModel.embed()` (already configured) | Already working from Phase 1 |
| Connection pooling | Custom DataSource | Spring Boot auto-configured HikariCP (already shared) | Phase 1 decision: datasourceBuilder() shares pool with JPA |

**Key insight:** LangChain4j 1.11.0-beta19 provides the ENTIRE search pipeline -- vector search, keyword search, and RRF fusion -- in a single `embeddingStore.search()` call. The only code we write is the service layer mapping results to domain DTOs with citation metadata.

## Common Pitfalls

### Pitfall 1: GIN Index Expression Mismatch
**What goes wrong:** PostgreSQL GIN index is not used for full-text search queries, causing sequential scan on every keyword search.
**Why it happens:** The existing Flyway migration V1 creates a GIN index on `to_tsvector('english', text)` but LangChain4j's hybrid search SQL uses `to_tsvector('english', coalesce(text, ''))`. These are different expressions to PostgreSQL's query planner even though `text` is NOT NULL.
**How to avoid:** Add a V2 migration that drops the old index and creates a new one matching LangChain4j's expression: `CREATE INDEX ... USING gin (to_tsvector('english', coalesce(text, '')))`.
**Warning signs:** EXPLAIN ANALYZE on a hybrid search query shows "Seq Scan" instead of "Bitmap Index Scan" on the document_chunks table.

### Pitfall 2: textSearchConfig Mismatch Between Index and Query
**What goes wrong:** GIN index built with one text search config (e.g., 'english') but queries use another (e.g., 'simple' which is LangChain4j's default).
**Why it happens:** LangChain4j defaults `textSearchConfig` to "simple". The GIN index must be built with the same config.
**How to avoid:** Explicitly set `textSearchConfig("english")` on the PgVectorEmbeddingStore builder AND ensure the GIN index uses `'english'` in its expression.
**Warning signs:** Full-text search returns no results or wrong results because stemming rules differ between 'simple' (no stemming) and 'english' (Porter stemmer).

### Pitfall 3: Missing query Field in EmbeddingSearchRequest
**What goes wrong:** Hybrid search silently falls back to vector-only search.
**Why it happens:** `EmbeddingSearchRequest` has both `queryEmbedding` (Embedding) and `query` (String). If `query` is null/empty, the keyword search CTE in hybrid mode may return no results, making RRF degenerate to vector-only ranking.
**How to avoid:** Always pass both `queryEmbedding` AND `query` (the raw text string) when calling `embeddingStore.search()`.
**Warning signs:** Hybrid search results are identical to vector-only search results.

### Pitfall 4: SearchMode Set at Store Construction
**What goes wrong:** Attempting to dynamically switch between VECTOR and HYBRID modes per query.
**Why it happens:** `SearchMode` is a builder parameter on PgVectorEmbeddingStore, not a per-request parameter.
**How to avoid:** Configure the store with `SearchMode.HYBRID`. When only vector search is needed, simply omit the `query` field from `EmbeddingSearchRequest`.
**Warning signs:** Creating multiple EmbeddingStore beans for different search modes, wasting connection pool resources.

### Pitfall 5: Metadata Keys Not Matching Between Store and Retrieve
**What goes wrong:** `metadata.getString("source_url")` returns null even though data was stored.
**Why it happens:** LangChain4j serializes Metadata to JSONB. If the key used at store time ("sourceUrl") differs from retrieve time ("source_url"), the value is null.
**How to avoid:** Define metadata key constants and use them consistently. Test the full round-trip (store with metadata, search, extract metadata from result).
**Warning signs:** Search results return text correctly but citation URLs are null.

### Pitfall 6: ts_rank vs BM25 Ranking Quality
**What goes wrong:** Keyword search ranking is mediocre for documentation search.
**Why it happens:** The requirement mentions "BM25" but PostgreSQL's native `ts_rank()` is NOT BM25 -- it only uses term frequency within a document, without inverse document frequency (IDF) or term saturation. LangChain4j's hybrid search uses `ts_rank()`.
**How to avoid:** Accept this limitation for v1. The RRF fusion with vector search compensates significantly for ts_rank's limitations. True BM25 would require a PostgreSQL extension (pg_textsearch, pg_search) not available in the standard pgvector Docker image.
**Warning signs:** Keyword-only search ranking seems arbitrary for common terms. This is expected and acceptable since hybrid search is the primary mode.

## Code Examples

Verified patterns from official sources:

### Configuring PgVectorEmbeddingStore for Hybrid Search
```java
// Source: LangChain4j PgVector docs (https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/)
// Verified against PR #4288 and raw source code on GitHub main branch

import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.SearchMode;

@Bean
public EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
    return PgVectorEmbeddingStore.datasourceBuilder()
            .datasource(dataSource)
            .table("document_chunks")
            .dimension(384)
            .createTable(false)       // Schema managed by Flyway
            .useIndex(false)          // HNSW index managed by Flyway
            .searchMode(SearchMode.HYBRID)
            .textSearchConfig("english")
            .rrfK(60)                 // RRF constant (default is 60)
            .build();
}
```

### Performing Hybrid Search
```java
// Source: LangChain4j EmbeddingSearchRequest API (verified from source code)

Embedding queryEmbedding = embeddingModel.embed("how to configure routing").content();

EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .query("how to configure routing")  // REQUIRED for hybrid search keyword matching
        .maxResults(10)
        .minScore(0.0)                      // optional, default 0.0
        .build();

EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

for (EmbeddingMatch<TextSegment> match : result.matches()) {
    double score = match.score();                      // RRF-combined score
    String text = match.embedded().text();             // chunk text
    String sourceUrl = match.embedded().metadata().getString("source_url");
    String sectionPath = match.embedded().metadata().getString("section_path");
}
```

### Storing Test Data with Citation Metadata
```java
// Source: LangChain4j TextSegment/Metadata API docs

Metadata metadata = Metadata.from("source_url", "https://angular.dev/guide/routing")
        .put("section_path", "Guide > Routing > Basic Configuration")
        .put("content_type", "prose");

TextSegment segment = TextSegment.from(
        "Angular uses the RouterModule to configure navigation routes. " +
        "Import RouterModule.forRoot(routes) in your AppModule to set up routing.",
        metadata
);

Embedding embedding = embeddingModel.embed(segment).content();
String id = embeddingStore.add(embedding, segment);
```

### V2 Flyway Migration - Fix GIN Index
```sql
-- V2__fix_gin_index_for_hybrid_search.sql
-- Fix GIN index expression to match LangChain4j's hybrid search SQL
-- LangChain4j uses: to_tsvector(config, coalesce(text, ''))
-- Old index used:   to_tsvector('english', text)

DROP INDEX IF EXISTS idx_document_chunks_text_fts;

CREATE INDEX idx_document_chunks_text_fts
    ON document_chunks
    USING gin (to_tsvector('english', coalesce(text, '')));
```

### SearchResult Domain DTO
```java
// Domain DTO for search results with citation metadata

public record SearchResult(
        String text,
        double score,
        String sourceUrl,
        String sectionPath
) {}
```

### SearchRequest Domain DTO
```java
// Domain request DTO with configurable result count (SRCH-06)

public record SearchRequest(
        String query,
        int maxResults
) {
    public SearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be at least 1");
        }
    }

    public SearchRequest(String query) {
        this(query, 10);  // Default to 10 results (SRCH-06)
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Custom RRF in application code | `SearchMode.HYBRID` on PgVectorEmbeddingStore | LangChain4j 1.11.0-beta19 (Feb 2026) | No need to hand-roll RRF SQL |
| Separate ContentRetriever for full-text | Unified `EmbeddingStore.search()` with query field | LangChain4j 1.11.0-beta19 (Feb 2026) | Single search call handles all modes |
| PgVectorEmbeddingStore vector-only | `SearchMode` enum (VECTOR, HYBRID) | PR #4288 merged Feb 3, 2026 | Builder-level config for search strategy |
| `EmbeddingSearchRequest` without `query` field | `query(String)` field on builder | LangChain4j 1.11.0 | Text query passed alongside embedding for hybrid |
| Custom metadata column management | `MetadataStorageConfig` (JSON/JSONB/COLUMN_PER_KEY) | LangChain4j 1.x | Configurable metadata storage modes |

**Deprecated/outdated:**
- `findRelevant()` method on EmbeddingStore: Replaced by `search(EmbeddingSearchRequest)` which supports filtering, query text, and more options.
- `SearchMode.VECTOR` naming: The docs show both "VECTOR" and "EMBEDDING_ONLY" in different places. The actual enum in source code has `VECTOR` and `HYBRID` (verified from raw GitHub source).

## Open Questions

1. **Does `SearchMode.HYBRID` with null `query` gracefully fall back to vector-only?**
   - What we know: The `search()` method dispatches to `hybridSearch()` when mode is HYBRID. The keyword_search CTE uses `plainto_tsquery(config, ?)` with the query parameter.
   - What's unclear: If query is null, does the SQL fail, return empty keyword results, or does LangChain4j short-circuit to vector-only?
   - Recommendation: Test this explicitly in integration tests. If it fails, the SearchService should check for null/empty query and either skip the query field or handle the error.

2. **Does the existing `metadataStorageConfig` default (COMBINED_JSON) work with the Flyway-managed `metadata JSONB` column?**
   - What we know: The default is COMBINED_JSON (not COMBINED_JSONB). The column is JSONB in the schema.
   - What's unclear: Whether COMBINED_JSON mode writes JSON text to a JSONB column (PostgreSQL auto-casts) or whether COMBINED_JSONB is required.
   - Recommendation: Test with the default first. If metadata round-trip fails, explicitly set `metadataStorageConfig(MetadataStorageConfig.combinedJsonb())`. PostgreSQL does auto-cast JSON text to JSONB, so the default should work.

3. **RRF k=60: Is this optimal for documentation search?**
   - What we know: k=60 is the standard default from the original RRF paper. Lower k (20-40) emphasizes top-ranked results; higher k (80-100) gives more balanced weight.
   - What's unclear: Whether documentation search benefits from a different k value.
   - Recommendation: Use k=60 (default) for v1. This is a tuning parameter that can be adjusted in Phase 8 (Advanced Search & Quality) with real usage data.

## Sources

### Primary (HIGH confidence)
- [LangChain4j PgVector raw source code](https://raw.githubusercontent.com/langchain4j/langchain4j/main/langchain4j-pgvector/src/main/java/dev/langchain4j/store/embedding/pgvector/PgVectorEmbeddingStore.java) - SearchMode enum, hybrid search SQL, builder methods, RRF implementation
- [LangChain4j PgVector official docs](https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/) - Configuration parameters, hybrid search usage, metadata storage modes
- [LangChain4j EmbeddingSearchRequest source](https://raw.githubusercontent.com/langchain4j/langchain4j/main/langchain4j-core/src/main/java/dev/langchain4j/store/embedding/EmbeddingSearchRequest.java) - query field, filter field, builder API
- [PR #4288: PgVector hybrid search](https://github.com/langchain4j/langchain4j/pull/4288) - Implementation details, merged Feb 3, 2026, targeting 1.11.0
- [Maven Central: langchain4j-pgvector](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-pgvector) - Version 1.11.0-beta19 is latest, published ~Feb 4, 2026
- [PostgreSQL Full-Text Search docs](https://www.postgresql.org/docs/current/textsearch-tables.html) - GIN index expression matching requirements
- Existing codebase: V1__initial_schema.sql, EmbeddingConfig.java, EmbeddingStoreIT.java

### Secondary (MEDIUM confidence)
- [Reciprocal Rank Fusion explained](https://medium.com/@devalshah1619/mathematical-intuition-behind-reciprocal-rank-fusion-rrf-explained-in-2-mins-002df0cc5e2a) - RRF algorithm formula and k parameter semantics
- [pgvector HNSW performance tuning](https://cloud.google.com/blog/products/databases/faster-similarity-search-performance-with-pgvector-indexes) - hnsw.ef_search tuning, m parameter impact
- [PostgreSQL ts_rank vs BM25 comparison](https://emschwartz.me/comparing-full-text-search-algorithms-bm25-tf-idf-and-postgres/) - ts_rank limitations vs true BM25

### Tertiary (LOW confidence)
- SearchMode enum values: Verified as `VECTOR` and `HYBRID` from raw source code. Earlier web search results mentioned "EMBEDDING_ONLY" and "FULL_TEXT_ONLY" which appear to be from an earlier PR iteration (PR #1633, closed) and do not exist in the merged code (PR #4288).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries already in project, hybrid search API verified from source code
- Architecture: HIGH - Service layer pattern is straightforward, LangChain4j API verified
- Pitfalls: HIGH - GIN index mismatch verified by reading both Flyway migration and LangChain4j SQL
- Search API: HIGH - EmbeddingSearchRequest.query field, SearchMode enum, builder methods all verified from raw GitHub source

**Research date:** 2026-02-15
**Valid until:** 2026-03-15 (LangChain4j pgvector is still beta, API could change in next beta release)
