# Phase 5: Recherche Avancee - Research

**Researched:** 2026-01-20
**Domain:** Hybrid search (vector + full-text), graph traversal for related documents
**Confidence:** HIGH

## Summary

Phase 5 implements two advanced search features: hybrid search combining pgvector semantic similarity with PostgreSQL tsvector full-text search, and graph traversal via Apache AGE to discover related documents. The existing infrastructure has all required components: HNSW index on embeddings, GIN index on tsvector content, and Apache AGE with REFERENCES edges between documents.

Key findings:
1. **Reciprocal Rank Fusion (RRF)** is the standard approach to combine vector and full-text search results - it's score-independent and requires no normalization
2. **The GIN index on tsvector already exists** in 002-schema.sql (`idx_chunks_content_fts`)
3. **Two CTEs + FULL OUTER JOIN** is the standard SQL pattern for hybrid search
4. **Graph traversal uses existing `findRelatedDocuments`** method with variable-length path `[:REFERENCES*1..N]`
5. **`websearch_to_tsquery`** is preferred over `plainto_tsquery` for user queries (handles OR, quotes, negation)

**Primary recommendation:** Implement hybrid search as a new method on `SearchRepository` using RRF score combination via SQL CTEs. Graph-related results are fetched separately and merged in the service layer.

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| PostgreSQL tsvector | 17 | Full-text search | Built-in, no extension needed |
| pgvector | 0.8.1 | Vector similarity | Already installed and indexed |
| Apache AGE | 1.6.0 | Graph traversal | Already configured with REFERENCES edges |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring JdbcTemplate | 3.4.1 | Query execution | CTE-based hybrid queries |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| RRF | Weighted score combination | Requires score normalization, harder to tune |
| RRF | Cross-encoder reranking | Much slower (ML inference), better quality |
| `websearch_to_tsquery` | `plainto_tsquery` | Less flexible (AND only), no phrase support |
| GIN index on content | Stored tsvector column | Same index already exists, no migration needed |

**No additional dependencies needed.** All required capabilities are already available.

## Hybrid Search Implementation

### RRF Score Formula

Reciprocal Rank Fusion combines rankings from multiple search methods:

```
RRF(d) = SUM(1 / (k + rank_i(d)))
```

Where:
- `d` is a document/chunk
- `k` is a constant (default 60) that prevents top ranks from dominating
- `rank_i(d)` is the rank of document d in search method i

**Source:** [Jonathan Katz - Hybrid Search PostgreSQL](https://jkatz05.com/post/postgres/hybrid-search-postgres-pgvector/), [Supabase Hybrid Search](https://supabase.com/docs/guides/ai/hybrid-search)

### Benefits of RRF
- **Score-independent:** Works regardless of score scales (vector 0-1, ts_rank any range)
- **No normalization needed:** Only ranks matter, not raw scores
- **Robust:** Consistently improves relevance over single-method search (8-15%)
- **Tuneable:** Adjust weights per search method if needed

### SQL Pattern: CTE-Based Hybrid Search

```sql
-- Hybrid search combining vector similarity and full-text search using RRF
WITH vector_search AS (
    SELECT
        child.id AS chunk_id,
        ROW_NUMBER() OVER (ORDER BY child.embedding <=> :query_embedding) AS rank_ix
    FROM chunks child
    WHERE child.chunk_type = 'child'
    ORDER BY child.embedding <=> :query_embedding
    LIMIT :candidate_limit
),
text_search AS (
    SELECT
        child.id AS chunk_id,
        ROW_NUMBER() OVER (ORDER BY ts_rank_cd(
            to_tsvector('simple', child.content),
            websearch_to_tsquery('simple', :query_text)
        ) DESC) AS rank_ix
    FROM chunks child
    WHERE child.chunk_type = 'child'
      AND to_tsvector('simple', child.content) @@ websearch_to_tsquery('simple', :query_text)
    ORDER BY rank_ix
    LIMIT :candidate_limit
)
SELECT
    child.id AS child_id,
    child.content AS child_content,
    child.position AS child_position,
    parent.id AS parent_id,
    parent.content AS parent_context,
    doc.id AS document_id,
    doc.title AS document_title,
    doc.path AS document_path,
    doc.category,
    doc.tags,
    -- RRF score: higher = better
    COALESCE(1.0 / (:rrf_k + vector_search.rank_ix), 0.0) * :vector_weight +
    COALESCE(1.0 / (:rrf_k + text_search.rank_ix), 0.0) * :text_weight AS rrf_score
FROM vector_search
FULL OUTER JOIN text_search ON vector_search.chunk_id = text_search.chunk_id
JOIN chunks child ON COALESCE(vector_search.chunk_id, text_search.chunk_id) = child.id
JOIN chunks parent ON child.parent_chunk_id = parent.id
JOIN documents doc ON child.document_id = doc.id
WHERE (:category IS NULL OR doc.category = :category)
  AND (:tags IS NULL OR doc.tags @> :tags::text[])
ORDER BY rrf_score DESC
LIMIT :max_results;
```

**Source:** [Supabase Hybrid Search Docs](https://supabase.com/docs/guides/ai/hybrid-search), [ParadeDB Hybrid Search](https://www.paradedb.com/blog/hybrid-search-in-postgresql-the-missing-manual)

### Default Parameters
| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `rrf_k` | 60 | Standard value, balances top-rank emphasis |
| `vector_weight` | 1.0 | Equal weighting by default |
| `text_weight` | 1.0 | Equal weighting by default |
| `candidate_limit` | `maxResults * 2` | Fetch extra candidates for better fusion |

### Full-Text Search Functions

**Source:** [PostgreSQL Documentation](https://www.postgresql.org/docs/current/textsearch-controls.html)

| Function | Use Case | Example |
|----------|----------|---------|
| `websearch_to_tsquery` | User search input | `"spring boot" -legacy` -> phrase AND NOT |
| `plainto_tsquery` | Simple keyword search | All terms ANDed |
| `ts_rank` | Frequency-based ranking | General purpose |
| `ts_rank_cd` | Cover density ranking | Rewards term proximity |

**Recommendation:** Use `websearch_to_tsquery('simple', query)` for user input because:
- Handles quoted phrases: `"spring boot"` -> `'spring' <-> 'boot'`
- Handles OR: `java OR kotlin` -> `'java' | 'kotlin'`
- Handles negation: `-deprecated` -> `!'deprecated'`
- Gracefully handles invalid syntax (doesn't fail)

### Existing tsvector Index

The schema already has a GIN index on chunk content (from 002-schema.sql):

```sql
-- Full-text search index using 'simple' config (preserves technical terms)
CREATE INDEX idx_chunks_content_fts ON chunks
    USING gin(to_tsvector('simple', content));
```

**No schema migration needed.** The index is already configured with 'simple' config (no stemming) for mixed FR/EN technical content.

## Graph Traversal Implementation

### Existing GraphRepository Methods

The `AgeGraphRepository` already has `findRelatedDocuments`:

```java
@Override
public List<UUID> findRelatedDocuments(UUID documentId, int maxHops) {
    if (maxHops < 1 || maxHops > 10) {
        throw new IllegalArgumentException("maxHops must be between 1 and 10");
    }

    String cypher = String.format("""
        MATCH (d:Document {document_id: '%s'})-[:REFERENCES*1..%d]->(related:Document)
        WHERE related.document_id <> '%s'
        RETURN DISTINCT related.document_id AS doc_id
        """, documentId, maxHops, documentId);

    // ... execution and parsing ...
}
```

**Source:** Existing codebase (`AgeGraphRepository.java`)

### Traversal Patterns for Search

| Pattern | Cypher | Use Case |
|---------|--------|----------|
| Direct references (1 hop) | `-[:REFERENCES]->` | Immediate related docs |
| Extended references (2 hops) | `-[:REFERENCES*1..2]->` | Broader discovery |
| Bidirectional | `-[:REFERENCES*1..2]-` | Include docs that reference this one |

**Recommendation for Phase 5:**
- Default to 2 hops for related document discovery
- Bidirectional traversal captures both "docs I reference" and "docs that reference me"

### Enhanced Graph Query for Bidirectional Traversal

```java
// Find documents related in either direction (1-2 hops)
public List<UUID> findRelatedDocumentsBidirectional(UUID documentId, int maxHops) {
    String cypher = String.format("""
        MATCH (d:Document {document_id: '%s'})-[:REFERENCES*1..%d]-(related:Document)
        WHERE related.document_id <> '%s'
        RETURN DISTINCT related.document_id AS doc_id
        """, documentId, maxHops, documentId);

    // Note: - instead of -> for bidirectional
    // ...
}
```

## Integration Strategy

### Service Layer Design

```java
public record HybridSearchResult(
    List<SearchResult> semanticResults,     // Vector + full-text combined via RRF
    List<RelatedDocument> graphResults      // Documents from graph traversal
) {}

public record RelatedDocument(
    UUID documentId,
    String title,
    String path,
    String category,
    int hopDistance    // 1 = direct reference, 2 = second-degree
) {}
```

### SearchService Enhancement

```java
@Service
public class SearchService {

    // Existing search method remains unchanged
    public List<SearchResult> search(String query, SearchFilters filters) { ... }

    // New hybrid search method
    public HybridSearchResult hybridSearch(String query, HybridSearchFilters filters) {
        // 1. Generate embedding for query
        float[] queryEmbedding = embeddingGenerator.embed(query);

        // 2. Execute hybrid search (vector + full-text via RRF)
        List<SearchResult> semanticResults = searchRepository.hybridSearch(
            queryEmbedding,
            query,  // raw text for tsvector
            filters
        );

        // 3. Extract unique document IDs from results
        Set<UUID> resultDocIds = semanticResults.stream()
            .map(SearchResult::documentId)
            .collect(Collectors.toSet());

        // 4. Find graph-related documents (if enabled)
        List<RelatedDocument> graphResults = List.of();
        if (filters.includeGraphRelated()) {
            graphResults = findGraphRelatedDocuments(resultDocIds, filters.maxHops());
        }

        return new HybridSearchResult(semanticResults, graphResults);
    }

    private List<RelatedDocument> findGraphRelatedDocuments(
            Set<UUID> sourceDocIds,
            int maxHops) {
        Set<UUID> relatedIds = new HashSet<>();

        for (UUID docId : sourceDocIds) {
            relatedIds.addAll(graphRepository.findRelatedDocuments(docId, maxHops));
        }

        // Remove source documents from related
        relatedIds.removeAll(sourceDocIds);

        // Fetch document metadata for related IDs
        return documentRepository.findByIds(relatedIds).stream()
            .map(doc -> new RelatedDocument(
                doc.id(), doc.title(), doc.path(), doc.category(),
                calculateHopDistance(sourceDocIds, doc.id())
            ))
            .toList();
    }
}
```

### HybridSearchFilters Record

```java
public record HybridSearchFilters(
    int maxResults,
    Double minSimilarity,
    String category,
    List<String> tags,
    double vectorWeight,      // Default 1.0
    double textWeight,        // Default 1.0
    int rrfK,                 // Default 60
    boolean includeGraphRelated,  // Default true
    int maxHops               // Default 2
) {
    public HybridSearchFilters {
        if (maxResults < 1 || maxResults > 100) {
            throw new IllegalArgumentException("maxResults must be 1-100");
        }
        if (vectorWeight < 0 || textWeight < 0) {
            throw new IllegalArgumentException("weights must be non-negative");
        }
        if (rrfK < 1) {
            throw new IllegalArgumentException("rrfK must be positive");
        }
        if (maxHops < 1 || maxHops > 10) {
            throw new IllegalArgumentException("maxHops must be 1-10");
        }
        tags = tags != null ? List.copyOf(tags) : null;
    }

    public static HybridSearchFilters defaults(int maxResults) {
        return new HybridSearchFilters(
            maxResults, null, null, null,
            1.0, 1.0, 60,  // RRF defaults
            true, 2        // Graph defaults
        );
    }
}
```

## Architecture Patterns

### Recommended Project Structure

```
src/main/java/fr/kalifazzia/alexandria/
  core/
    search/
      SearchService.java          # Add hybridSearch method
      SearchResult.java           # Unchanged
      SearchFilters.java          # Unchanged
      HybridSearchFilters.java    # NEW: filters for hybrid search
      HybridSearchResult.java     # NEW: combined result record
      RelatedDocument.java        # NEW: graph result record
    port/
      SearchRepository.java       # Add hybridSearch method
      GraphRepository.java        # Add findRelatedDocumentsBidirectional (optional)
  infra/
    persistence/
      JdbcSearchRepository.java   # Implement hybridSearch with CTE query
```

### Pattern: Keeping Existing Search Intact

**What:** Add new `hybridSearch` method without modifying existing `search` method.
**Why:** Backward compatibility; existing callers continue working.
**How:** New method on same interfaces, new filters record.

### Anti-Patterns to Avoid

- **Combining scores directly:** Vector similarity (0-1) and ts_rank (variable) have different scales
- **Single query for everything:** Graph traversal is better separated (different data source)
- **Over-fetching graph results:** Limit hops and deduplicate to avoid explosion
- **Blocking on graph queries:** Consider async if latency becomes an issue

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Score normalization | Manual min-max scaling | RRF | RRF is rank-based, no normalization needed |
| Full-text tokenization | Custom tokenizer | PostgreSQL tsvector | Handles stemming, stop words, indexing |
| Query parsing | Regex-based parser | `websearch_to_tsquery` | Handles quotes, OR, negation safely |
| Graph traversal | Recursive SQL CTEs | AGE Cypher `*1..N` | Optimized for graph patterns |

**Key insight:** PostgreSQL's full-text search and AGE's Cypher engine are production-ready. Focus on integration, not reimplementation.

## Common Pitfalls

### Pitfall 1: Forgetting the @@ Match Operator

**What goes wrong:** Full-text search CTE returns all rows, ignores search terms
**Why it happens:** Missing `WHERE ... @@ tsquery` condition
**How to avoid:** Always include the match operator in full-text CTE:
```sql
WHERE to_tsvector('simple', content) @@ websearch_to_tsquery('simple', :query)
```
**Warning signs:** Full-text results include irrelevant documents

### Pitfall 2: Using Wrong tsvector Config

**What goes wrong:** Search misses exact matches due to stemming
**Why it happens:** Using 'english' config stems "configuration" -> "configur"
**How to avoid:** Use 'simple' config for technical content (preserves exact terms)
**Warning signs:** Can't find documents with exact technical terms

### Pitfall 3: Graph Result Explosion

**What goes wrong:** Related documents query returns hundreds of results
**Why it happens:** High hop count with densely connected documents
**How to avoid:** Limit maxHops to 2, deduplicate, cap results
**Warning signs:** Slow response times, overwhelming result counts

### Pitfall 4: RRF Weight Misconfiguration

**What goes wrong:** Results dominated by one search method
**Why it happens:** Setting weight to 0 effectively disables a method
**How to avoid:** Minimum weight of 0.1 for enabled methods
**Warning signs:** Hybrid results identical to single-method results

### Pitfall 5: Missing Full-Text Index Usage

**What goes wrong:** Sequential scan on chunks table for full-text
**Why it happens:** Index not used due to expression mismatch
**How to avoid:** Use exact same expression as index:
```sql
-- Index uses: to_tsvector('simple', content)
-- Query must use: to_tsvector('simple', content), NOT content::tsvector
```
**Warning signs:** EXPLAIN shows "Seq Scan" instead of "Index Scan"

### Pitfall 6: Empty Text Search with Pure Vector Query

**What goes wrong:** User searches "what is" and gets no full-text results
**Why it happens:** Common words may not match (stop words) or too short
**How to avoid:** Handle case where text_search CTE returns 0 rows (FULL OUTER JOIN does this)
**Warning signs:** Hybrid results identical to vector-only for short queries

## Code Examples

### Complete Hybrid Search Query

```java
// Source: Verified pattern from Supabase docs + codebase schema
public List<SearchResult> hybridSearch(
        float[] queryEmbedding,
        String queryText,
        HybridSearchFilters filters) {

    PGvector queryVector = new PGvector(queryEmbedding);
    int candidateLimit = filters.maxResults() * 2;  // Fetch extra for better fusion

    return jdbcTemplate.query("""
        WITH vector_search AS (
            SELECT
                child.id AS chunk_id,
                ROW_NUMBER() OVER (ORDER BY child.embedding <=> ?) AS rank_ix
            FROM chunks child
            WHERE child.chunk_type = 'child'
            ORDER BY child.embedding <=> ?
            LIMIT ?
        ),
        text_search AS (
            SELECT
                child.id AS chunk_id,
                ROW_NUMBER() OVER (ORDER BY ts_rank_cd(
                    to_tsvector('simple', child.content),
                    websearch_to_tsquery('simple', ?)
                ) DESC) AS rank_ix
            FROM chunks child
            WHERE child.chunk_type = 'child'
              AND to_tsvector('simple', child.content) @@ websearch_to_tsquery('simple', ?)
            ORDER BY rank_ix
            LIMIT ?
        )
        SELECT
            child.id AS child_id,
            child.content AS child_content,
            child.position AS child_position,
            parent.id AS parent_id,
            parent.content AS parent_context,
            doc.id AS document_id,
            doc.title AS document_title,
            doc.path AS document_path,
            doc.category,
            doc.tags,
            COALESCE(1.0 / (? + vector_search.rank_ix), 0.0) * ? +
            COALESCE(1.0 / (? + text_search.rank_ix), 0.0) * ? AS rrf_score
        FROM vector_search
        FULL OUTER JOIN text_search ON vector_search.chunk_id = text_search.chunk_id
        JOIN chunks child ON COALESCE(vector_search.chunk_id, text_search.chunk_id) = child.id
        JOIN chunks parent ON child.parent_chunk_id = parent.id
        JOIN documents doc ON child.document_id = doc.id
        WHERE (? IS NULL OR doc.category = ?)
          AND (? IS NULL OR doc.tags @> ?::text[])
        ORDER BY rrf_score DESC
        LIMIT ?
        """,
        this::mapRow,
        // vector_search CTE params
        queryVector, queryVector, candidateLimit,
        // text_search CTE params
        queryText, queryText, candidateLimit,
        // RRF score params
        filters.rrfK(), filters.vectorWeight(),
        filters.rrfK(), filters.textWeight(),
        // Filter params
        filters.category(), filters.category(),
        filters.tagsArray(), filters.tagsArray(),
        // Limit
        filters.maxResults()
    );
}
```

### SearchRepository Port Enhancement

```java
public interface SearchRepository {

    // Existing: pure vector search
    List<SearchResult> searchSimilar(float[] queryEmbedding, SearchFilters filters);

    // New: hybrid vector + full-text search
    List<SearchResult> hybridSearch(
        float[] queryEmbedding,
        String queryText,
        HybridSearchFilters filters
    );
}
```

### Graph Related Documents Query

```java
// Already exists in AgeGraphRepository - just use it
public List<UUID> findRelatedDocuments(UUID documentId, int maxHops);

// Optional enhancement for bidirectional
public List<UUID> findRelatedDocumentsBidirectional(UUID documentId, int maxHops) {
    String cypher = String.format("""
        MATCH (d:Document {document_id: '%s'})-[:REFERENCES*1..%d]-(related:Document)
        WHERE related.document_id <> '%s'
        RETURN DISTINCT related.document_id AS doc_id
        """, documentId, maxHops, documentId);

    String sql = String.format(
        "SELECT * FROM cypher('%s', $cypher$ %s $cypher$) AS (doc_id agtype)",
        GRAPH_NAME, cypher
    );

    List<UUID> results = new ArrayList<>();
    jdbcTemplate.query(sql, rs -> {
        String agtypeValue = rs.getString("doc_id");
        String docIdStr = parseAgtypeString(agtypeValue);
        if (docIdStr != null) {
            results.add(UUID.fromString(docIdStr));
        }
    });

    return results;
}
```

## Performance Considerations

### Query Performance

| Component | Expected Latency | Index Used |
|-----------|------------------|------------|
| Vector search CTE | ~10-50ms | HNSW (`idx_chunks_embedding`) |
| Full-text search CTE | ~5-20ms | GIN (`idx_chunks_content_fts`) |
| RRF combination | ~1-5ms | N/A (in-memory) |
| Graph traversal | ~10-30ms | AGE internal indexes |

### Optimization Strategies

1. **Limit candidate fetch:** Use `maxResults * 2` not `maxResults * 10`
2. **Parallel CTE execution:** PostgreSQL may parallelize CTEs on separate indexes
3. **Cache graph results:** Graph topology changes less often than queries
4. **Async graph fetch:** If latency critical, fetch graph results in parallel

### When to Increase ef_search

```sql
-- For filtered hybrid queries with low result counts
SET LOCAL hnsw.ef_search = 100;
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Score averaging | RRF fusion | ~2023 | More robust combination |
| Separate search systems | PostgreSQL hybrid | pgvector 0.5.0 | Single DB, ACID |
| Manual BM25 | ts_rank_cd | PostgreSQL native | No extension needed |
| Single-direction graph | Bidirectional traversal | - | Better related discovery |

**Deprecated/outdated:**
- **ElasticSearch for hybrid:** PostgreSQL native is sufficient for small-medium scale
- **Score normalization:** RRF eliminates need for score scaling
- **ParadeDB pg_search:** Not needed when native tsvector sufficient

## Open Questions

1. **Weight tuning for this corpus**
   - What we know: Default 1.0/1.0 is balanced
   - What's unclear: Whether technical docs benefit from heavier vector or text weight
   - Recommendation: Start with defaults, tune based on user feedback

2. **Graph hop distance reporting**
   - What we know: We can traverse 1-2 hops
   - What's unclear: How to report exact hop distance in results
   - Recommendation: For v1, just report "related" without distance; add distance if needed

3. **Handling empty text search results**
   - What we know: FULL OUTER JOIN includes vector-only matches
   - What's unclear: UX when query has no full-text matches
   - Recommendation: Gracefully degrade to vector-only; RRF handles missing ranks

4. **Graph result deduplication with search results**
   - What we know: Graph may return docs already in semantic results
   - What's unclear: Best UX for overlap
   - Recommendation: Exclude search result doc IDs from graph results

## Sources

### Primary (HIGH confidence)
- [PostgreSQL Full-Text Search Controls](https://www.postgresql.org/docs/current/textsearch-controls.html) - ts_rank, websearch_to_tsquery
- [Jonathan Katz - Hybrid Search PostgreSQL](https://jkatz05.com/post/postgres/hybrid-search-postgres-pgvector/) - RRF implementation
- [Supabase Hybrid Search Docs](https://supabase.com/docs/guides/ai/hybrid-search) - Complete SQL pattern
- [Apache AGE MATCH Documentation](https://age.apache.org/age-manual/master/clauses/match.html) - Variable-length paths
- Codebase: `JdbcSearchRepository.java`, `AgeGraphRepository.java` - Existing patterns
- Codebase: `002-schema.sql` - Existing tsvector GIN index

### Secondary (MEDIUM confidence)
- [ParadeDB Hybrid Search Manual](https://www.paradedb.com/blog/hybrid-search-in-postgresql-the-missing-manual) - RRF formula explanation
- [DBI Services - Hybrid Search Re-ranking](https://www.dbi-services.com/blog/rag-series-hybrid-search-with-re-ranking/) - Performance comparison

### Tertiary (LOW confidence)
- LangChain4j community discussions on hybrid search (feature in development)

## Metadata

**Confidence breakdown:**
- Hybrid search pattern: HIGH - Official PostgreSQL docs + multiple verified implementations
- RRF formula: HIGH - Standard algorithm, verified across multiple sources
- Graph traversal: HIGH - Already implemented in codebase, AGE docs verified
- Integration design: MEDIUM - Based on existing patterns, not yet validated
- Performance estimates: MEDIUM - Based on similar workloads, not benchmarked

**Research date:** 2026-01-20
**Valid until:** 2026-02-20 (PostgreSQL/pgvector/AGE all stable)

---

## RESEARCH COMPLETE

**Phase:** 5 - Recherche Avancee
**Confidence:** HIGH

### Key Findings

1. **RRF is the standard hybrid search approach** - Score-independent, no normalization needed, 8-15% accuracy improvement
2. **tsvector index already exists** - `idx_chunks_content_fts` with 'simple' config is ready to use
3. **CTE + FULL OUTER JOIN pattern** - Standard SQL pattern for combining search results
4. **Use `websearch_to_tsquery`** - Handles user input gracefully (quotes, OR, negation)
5. **Graph traversal is implemented** - `findRelatedDocuments` already supports variable-length paths
6. **Bidirectional traversal recommended** - Captures both outgoing and incoming references

### File Created

`.planning/phases/05-recherche-avancee/05-RESEARCH.md`

### Confidence Assessment

| Area | Level | Reason |
|------|-------|--------|
| Hybrid Search SQL | HIGH | Official PostgreSQL docs + multiple implementations |
| RRF Algorithm | HIGH | Standard algorithm, multiple sources agree |
| Graph Traversal | HIGH | Already implemented in codebase |
| Integration Design | MEDIUM | Based on existing patterns, not validated |
| Performance | MEDIUM | Estimates based on similar workloads |

### Open Questions

- Weight tuning for technical documentation corpus (start with 1.0/1.0)
- Exact hop distance reporting in UI (defer for v1)
- Graceful degradation when no full-text matches (handled by FULL OUTER JOIN)

### Ready for Planning

Research complete. Planner can now create PLAN.md files for:
- 05-01: Hybrid search implementation (vector + full-text with RRF)
- 05-02: Graph-related document discovery (bidirectional traversal integration)
