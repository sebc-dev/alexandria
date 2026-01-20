# Phase 4: Recherche Base - Research

**Researched:** 2026-01-20
**Domain:** pgvector semantic search with metadata filtering, parent-child retrieval
**Confidence:** HIGH

## Summary

This phase implements semantic search using pgvector's cosine similarity with metadata filtering (category, tags). The existing codebase already has HNSW indexes, parent-child chunk relationships, and GIN indexes on both tags and tsvector content.

Key findings:
1. **pgvector cosine operator `<=>`** returns distance (0-2 range), convert to similarity via `1 - distance`
2. **Post-filtering** is pgvector's behavior - WHERE clause filters AFTER similarity search
3. **Parent-child retrieval** requires JOINing child results back to parent chunks via `parent_chunk_id`
4. **GIN index on tags** uses containment operator `@>` for optimal index utilization

**Primary recommendation:** Implement a `SearchRepository` port with native SQL queries using JdbcTemplate, not LangChain4j's EmbeddingStore abstraction. This gives full control over JOIN patterns for parent context and leverages existing indexes.

## pgvector Semantic Search

### Operators Reference

| Operator | Distance Type | Return Range | Use Case |
|----------|---------------|--------------|----------|
| `<=>` | Cosine distance | 0-2 | Text similarity (use this) |
| `<->` | L2 (Euclidean) | 0-inf | Spatial data |
| `<#>` | Negative inner product | -inf to inf | Normalized vectors only |

**Source:** [pgvector GitHub](https://github.com/pgvector/pgvector)

### Cosine Similarity Query Pattern

```sql
-- Basic semantic search
SELECT id, content, 1 - (embedding <=> :query_embedding) AS similarity
FROM chunks
WHERE chunk_type = 'child'
ORDER BY embedding <=> :query_embedding
LIMIT :max_results;
```

**Critical notes:**
- `ORDER BY embedding <=> ?` uses HNSW index (ascending = closest)
- `1 - distance` converts cosine distance to similarity score (0-1 range)
- Always include `LIMIT` - index is only used with ORDER BY + LIMIT
- Only CHILD chunks should be searched (they are embedded for retrieval)

### HNSW Index Configuration

The codebase already has optimal HNSW index (from 002-schema.sql):
```sql
CREATE INDEX idx_chunks_embedding ON chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 100);
```

**Runtime tuning (optional):**
```sql
-- Increase for better recall (default: 40)
SET hnsw.ef_search = 100;
```

**Source:** [Neon pgvector guide](https://neon.com/blog/understanding-vector-search-and-hnsw-index-with-pgvector), [Crunchy Data HNSW](https://www.crunchydata.com/blog/hnsw-indexes-with-postgres-and-pgvector)

### Using PGvector Java Library

The codebase already uses `com.pgvector:pgvector:0.1.6`. Query pattern with JdbcTemplate:

```java
// Create query embedding
PGvector queryVector = new PGvector(embeddingGenerator.embed(queryText));

// Execute similarity search
List<SearchResult> results = jdbcTemplate.query("""
    SELECT c.id, c.content, c.parent_chunk_id, c.document_id,
           1 - (c.embedding <=> ?) AS similarity
    FROM chunks c
    WHERE c.chunk_type = 'child'
    ORDER BY c.embedding <=> ?
    LIMIT ?
    """,
    (rs, rowNum) -> mapToSearchResult(rs),
    queryVector, queryVector, maxResults);
```

**Source:** [pgvector-java GitHub](https://github.com/pgvector/pgvector-java)

## Parent-Child Retrieval

### Strategy: Child Search with Parent Context

The hierarchical chunking pattern stores:
- **PARENT chunks**: Larger context (1000 tokens) - for context expansion
- **CHILD chunks**: Smaller search units (200 tokens) - for embedding similarity

**Retrieval flow:**
1. Search CHILD chunk embeddings for similarity
2. For each result, fetch the PARENT chunk content via `parent_chunk_id`
3. Return both child content (matched) and parent content (context)

### SQL Pattern with JOIN

```sql
SELECT
    child.id AS child_id,
    child.content AS child_content,
    child.position AS child_position,
    parent.id AS parent_id,
    parent.content AS parent_content,
    doc.id AS document_id,
    doc.title,
    doc.path,
    doc.category,
    doc.tags,
    1 - (child.embedding <=> :query) AS similarity
FROM chunks child
JOIN chunks parent ON child.parent_chunk_id = parent.id
JOIN documents doc ON child.document_id = doc.id
WHERE child.chunk_type = 'child'
ORDER BY child.embedding <=> :query
LIMIT :max_results;
```

**Performance note:** The JOIN is efficient because:
- `idx_chunks_parent_chunk_id` index exists on `parent_chunk_id`
- `idx_chunks_document_id` index exists on `document_id`

### Result DTO Design

```java
public record SearchResult(
    UUID childChunkId,
    String childContent,
    UUID parentChunkId,
    String parentContext,  // Full parent content for LLM context
    UUID documentId,
    String documentTitle,
    String documentPath,
    String category,
    List<String> tags,
    double similarity      // 0-1 score, higher = more similar
) {}
```

## Filtering Implementation

### Post-Filtering Behavior

**IMPORTANT:** pgvector applies WHERE filters AFTER the HNSW index scan. With default `hnsw.ef_search = 40`:
- If filter matches 10% of rows, only ~4 results returned
- Solution: Increase `ef_search` or filter client-side for small result sets

```sql
-- May return fewer than 10 results if filter is selective!
SELECT ... FROM chunks
WHERE chunk_type = 'child'
  AND document_id IN (SELECT id FROM documents WHERE category = 'java')
ORDER BY embedding <=> :query
LIMIT 10;
```

**Mitigation strategies:**
1. Fetch more results, filter client-side
2. Increase `hnsw.ef_search` (e.g., `SET hnsw.ef_search = 200`)
3. Use iterative index scans (pgvector 0.8.0+)

**Source:** [Timescale filtered search](https://medium.com/timescale/implementing-filtered-semantic-search-using-pgvector-and-javascript-7c6eb4894c36), [Pinecone filtering](https://www.pinecone.io/learn/vector-search-filtering/)

### Category Filtering

The `documents` table has `idx_documents_category` B-tree index.

```sql
-- Filter by category via subquery (uses B-tree index)
SELECT child.*, parent.content AS parent_context,
       1 - (child.embedding <=> :query) AS similarity
FROM chunks child
JOIN chunks parent ON child.parent_chunk_id = parent.id
JOIN documents doc ON child.document_id = doc.id
WHERE child.chunk_type = 'child'
  AND doc.category = :category
ORDER BY child.embedding <=> :query
LIMIT :max_results;
```

### Tags Filtering (Array Containment)

The `documents` table has `idx_documents_tags` GIN index. Use containment operator `@>` for GIN utilization:

```sql
-- Filter by ANY tag (uses GIN index)
SELECT ...
FROM chunks child
JOIN documents doc ON child.document_id = doc.id
WHERE child.chunk_type = 'child'
  AND doc.tags @> ARRAY[:tag]::text[]  -- Contains this tag
ORDER BY child.embedding <=> :query
LIMIT :max_results;

-- Filter by ALL tags
WHERE doc.tags @> ARRAY['java', 'spring']::text[]

-- Filter by ANY of multiple tags (overlap)
WHERE doc.tags && ARRAY['java', 'kotlin']::text[]
```

**Source:** [PostgreSQL GIN docs](https://www.postgresql.org/docs/current/gin.html), [pganalyze GIN guide](https://pganalyze.com/blog/gin-index)

### Combined Filtering Pattern

```sql
SELECT child.*, parent.content AS parent_context, doc.*,
       1 - (child.embedding <=> :query) AS similarity
FROM chunks child
JOIN chunks parent ON child.parent_chunk_id = parent.id
JOIN documents doc ON child.document_id = doc.id
WHERE child.chunk_type = 'child'
  AND (:category IS NULL OR doc.category = :category)
  AND (:tags IS NULL OR doc.tags @> :tags::text[])
ORDER BY child.embedding <=> :query
LIMIT :max_results;
```

## Codebase Integration Points

### Existing Infrastructure to Leverage

| Component | Location | How to Use |
|-----------|----------|------------|
| `EmbeddingGenerator` | `core/port/EmbeddingGenerator.java` | Generate query embedding via `embed(String)` |
| `LangChain4jEmbeddingGenerator` | `infra/embedding/` | all-MiniLM-L6-v2 implementation |
| `PGvector` class | `com.pgvector:pgvector` | Wrap float[] for SQL parameters |
| `JdbcTemplate` | Spring JDBC | Execute native SQL queries |
| `Document` model | `core/model/Document.java` | Has category, tags fields |
| `Chunk` model | `core/model/Chunk.java` | Has parentChunkId for hierarchy |

### Database Schema (from 002-schema.sql)

```sql
-- Key columns for search
chunks.embedding vector(384)     -- HNSW indexed
chunks.chunk_type TEXT           -- 'parent' or 'child'
chunks.parent_chunk_id UUID      -- FK to parent chunk
documents.category TEXT          -- B-tree indexed
documents.tags TEXT[]            -- GIN indexed
```

### Port/Adapter Pattern

Following existing patterns (e.g., `ChunkRepository`), create:

1. **Port interface** in `core/port/SearchRepository.java`:
   ```java
   public interface SearchRepository {
       List<SearchResult> searchSimilar(
           float[] queryEmbedding,
           int maxResults,
           SearchFilters filters
       );
   }
   ```

2. **Adapter** in `infra/persistence/JdbcSearchRepository.java`:
   - Inject `JdbcTemplate`
   - Use native SQL with pgvector operators
   - Handle PGvector type conversion

3. **Service** in `core/search/SearchService.java`:
   - Orchestrate embedding generation + search
   - Convert user query to embedding
   - Apply business logic

## Architecture Patterns

### Recommended Project Structure

```
src/main/java/fr/kalifazzia/alexandria/
  core/
    search/
      SearchService.java          # Orchestration
      SearchResult.java           # DTO record
      SearchFilters.java          # Filter criteria record
    port/
      SearchRepository.java       # Port interface
  infra/
    persistence/
      JdbcSearchRepository.java   # pgvector implementation
```

### Service Layer Pattern

```java
@Service
public class SearchService {
    private final EmbeddingGenerator embeddingGenerator;
    private final SearchRepository searchRepository;

    public List<SearchResult> search(String query, SearchFilters filters) {
        // 1. Generate embedding for query
        float[] queryEmbedding = embeddingGenerator.embed(query);

        // 2. Execute similarity search with filters
        return searchRepository.searchSimilar(
            queryEmbedding,
            filters.maxResults(),
            filters
        );
    }
}
```

### Anti-Patterns to Avoid

- **Don't use LangChain4j EmbeddingStore for search**: It abstracts away JOIN patterns needed for parent context
- **Don't search PARENT chunks**: They're for context, not retrieval
- **Don't forget minimum score threshold**: Filter out low-similarity noise
- **Don't build dynamic SQL with string concatenation**: Use parameterized queries

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Cosine similarity | Manual calculation | pgvector `<=>` | Hardware-optimized, index-aware |
| Vector storage | Custom format | `PGvector` class | Type-safe, JDBC compatible |
| Embedding generation | Custom model | `EmbeddingGenerator` port | Already implemented, in-process |
| Result ranking | Custom scoring | `ORDER BY <=> LIMIT` | HNSW index acceleration |

## Common Pitfalls

### Pitfall 1: Forgetting to Convert Distance to Similarity

**What goes wrong:** Returning raw `<=>` result as "similarity" (0 = most similar, 2 = least)
**Why it happens:** Cosine operator returns DISTANCE, not similarity
**How to avoid:** Always use `1 - (embedding <=> query) AS similarity`
**Warning signs:** "Best" results have lowest scores

### Pitfall 2: Not Limiting Results

**What goes wrong:** Full table scan, ignores HNSW index
**Why it happens:** HNSW requires LIMIT clause for index usage
**How to avoid:** Always include `ORDER BY ... LIMIT` together
**Warning signs:** EXPLAIN shows "Seq Scan" instead of "Index Scan"

### Pitfall 3: Filtering Returns Too Few Results

**What goes wrong:** Requesting 10 results but getting 2-3
**Why it happens:** Post-filtering with selective predicates
**How to avoid:** Fetch 2-3x more results, filter client-side, or increase ef_search
**Warning signs:** Inconsistent result counts when filters applied

### Pitfall 4: Searching Parent Chunks

**What goes wrong:** Poor search quality, wrong granularity
**Why it happens:** Parent chunks are large context, not search targets
**How to avoid:** Always filter `WHERE chunk_type = 'child'`
**Warning signs:** Embeddings for large text blocks

### Pitfall 5: Missing Parent Context in Results

**What goes wrong:** Results lack surrounding context for LLM
**Why it happens:** Only fetching child chunk, not parent
**How to avoid:** JOIN on parent_chunk_id, return parent.content
**Warning signs:** Fragmented, contextless search results

## Code Examples

### Complete Search Query

```java
// Source: Verified pattern from pgvector-java + codebase schema
public List<SearchResult> searchSimilar(float[] queryEmbedding, int maxResults, SearchFilters filters) {
    PGvector queryVector = new PGvector(queryEmbedding);

    return jdbcTemplate.query("""
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
            1 - (child.embedding <=> ?) AS similarity
        FROM chunks child
        JOIN chunks parent ON child.parent_chunk_id = parent.id
        JOIN documents doc ON child.document_id = doc.id
        WHERE child.chunk_type = 'child'
          AND (? IS NULL OR doc.category = ?)
          AND (? IS NULL OR doc.tags @> ?::text[])
        ORDER BY child.embedding <=> ?
        LIMIT ?
        """,
        this::mapRow,
        queryVector,                           // For similarity calc
        filters.category(), filters.category(), // Category filter
        filters.tags(), filters.tagsArray(),   // Tags filter
        queryVector,                           // For ORDER BY
        maxResults
    );
}
```

### SearchResult RowMapper

```java
private SearchResult mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new SearchResult(
        rs.getObject("child_id", UUID.class),
        rs.getString("child_content"),
        rs.getObject("parent_id", UUID.class),
        rs.getString("parent_context"),
        rs.getObject("document_id", UUID.class),
        rs.getString("document_title"),
        rs.getString("document_path"),
        rs.getString("category"),
        arrayToList(rs.getArray("tags")),
        rs.getDouble("similarity")
    );
}

private List<String> arrayToList(Array sqlArray) throws SQLException {
    if (sqlArray == null) return List.of();
    String[] arr = (String[]) sqlArray.getArray();
    return arr != null ? List.of(arr) : List.of();
}
```

### SearchFilters Record

```java
public record SearchFilters(
    int maxResults,
    Double minSimilarity,  // e.g., 0.5 = filter out low matches
    String category,       // null = no filter
    List<String> tags      // null = no filter
) {
    public SearchFilters {
        if (maxResults < 1 || maxResults > 100) {
            throw new IllegalArgumentException("maxResults must be 1-100");
        }
    }

    public String[] tagsArray() {
        return tags != null ? tags.toArray(String[]::new) : null;
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| IVFFlat index | HNSW index | pgvector 0.5.0 (2023) | 10x faster queries |
| Post-filtering only | Iterative scans | pgvector 0.8.0 (2024) | Better filtered results |
| Spring AI VectorStore | Native JDBC | - | Full control over JOINs |

**Deprecated/outdated:**
- **IVFFlat for cosine**: HNSW is now preferred (better recall at same speed)
- **LangChain4j PgVectorEmbeddingStore**: Doesn't support parent-child JOINs

## Open Questions

1. **ef_search tuning**
   - What we know: Default is 40, higher improves recall
   - What's unclear: Optimal value for this workload
   - Recommendation: Start with 100, benchmark with real queries

2. **Minimum similarity threshold**
   - What we know: Low scores indicate poor matches
   - What's unclear: Best cutoff for this embedding model
   - Recommendation: Start with 0.3, tune based on result quality

3. **Result deduplication**
   - What we know: Multiple child chunks from same document may match
   - What's unclear: Best UX - show all matches or dedupe by document?
   - Recommendation: Return all, let UI group by document

## Sources

### Primary (HIGH confidence)
- [pgvector GitHub](https://github.com/pgvector/pgvector) - Operators, HNSW config
- [pgvector-java GitHub](https://github.com/pgvector/pgvector-java) - Java API
- [PostgreSQL GIN docs](https://www.postgresql.org/docs/current/gin.html) - Array operators
- Codebase schema: `002-schema.sql` - Existing indexes

### Secondary (MEDIUM confidence)
- [Neon pgvector guide](https://neon.com/blog/understanding-vector-search-and-hnsw-index-with-pgvector) - HNSW parameters
- [Timescale filtered search](https://medium.com/timescale/implementing-filtered-semantic-search-using-pgvector-and-javascript-7c6eb4894c36) - Filter patterns
- [LangChain4j RAG docs](https://docs.langchain4j.dev/tutorials/rag/) - Filter API reference

### Tertiary (LOW confidence)
- Various blog posts on parent document retrieval pattern (conceptual guidance)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Using existing pgvector, confirmed with official docs
- Architecture: HIGH - Following existing codebase patterns
- Pitfalls: HIGH - Documented in official pgvector guides
- Filtering: MEDIUM - Post-filtering behavior confirmed, tuning TBD

**Research date:** 2026-01-20
**Valid until:** 60 days (pgvector stable, patterns well-established)

---

## RESEARCH COMPLETE

**Phase:** 4 - Recherche Base
**Confidence:** HIGH

### Key Findings

1. **Use native SQL with JdbcTemplate** - LangChain4j EmbeddingStore abstracts away JOIN patterns needed for parent context retrieval
2. **Cosine operator `<=>` returns distance** - Convert to similarity with `1 - distance`
3. **Filtering is post-filter** - HNSW scans first, then WHERE filters; may need to fetch extra results
4. **GIN index requires `@>` operator** - Use array containment, not `ANY` for tags
5. **Parent-child JOIN is efficient** - Existing indexes on parent_chunk_id support the pattern

### File Created

`.planning/phases/04-recherche-base/04-RESEARCH.md`

### Confidence Assessment

| Area | Level | Reason |
|------|-------|--------|
| Standard Stack | HIGH | Using existing pgvector + Java library, verified with official docs |
| Architecture | HIGH | Following existing port/adapter pattern from codebase |
| Pitfalls | HIGH | Documented in pgvector GitHub and multiple verified sources |
| Filtering | MEDIUM | Post-filter behavior confirmed; optimal ef_search tuning TBD |

### Open Questions

- Optimal `hnsw.ef_search` value for filtered queries (start with 100)
- Minimum similarity threshold (start with 0.3)
- Result deduplication strategy (return all, group in UI)

### Ready for Planning

Research complete. Planner can now create PLAN.md files for:
- 04-01: Recherche semantique pgvector (core search implementation)
- 04-02: Contexte parent et filtres (parent context + filtering)
