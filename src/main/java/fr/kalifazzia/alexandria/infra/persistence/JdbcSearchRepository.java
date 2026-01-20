package fr.kalifazzia.alexandria.infra.persistence;

import com.pgvector.PGvector;
import fr.kalifazzia.alexandria.core.port.SearchRepository;
import fr.kalifazzia.alexandria.core.search.HybridSearchFilters;
import fr.kalifazzia.alexandria.core.search.SearchFilters;
import fr.kalifazzia.alexandria.core.search.SearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * JDBC implementation of SearchRepository using pgvector for similarity search.
 * Searches child chunks and returns results with parent context for LLM consumption.
 */
@Repository
public class JdbcSearchRepository implements SearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SearchResult> searchSimilar(float[] queryEmbedding, SearchFilters filters) {
        PGvector queryVector = new PGvector(queryEmbedding);

        // Post-filter results in Java for minSimilarity (pgvector post-filters AFTER HNSW scan)
        // Fetch extra results to compensate for filtering
        int fetchLimit = filters.minSimilarity() != null
                ? Math.min(filters.maxResults() * 3, 100)
                : filters.maxResults();

        List<SearchResult> results = jdbcTemplate.query("""
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
                queryVector,                                // For similarity calculation
                filters.category(), filters.category(),     // Category filter (null = no filter)
                filters.tagsArray(), filters.tagsArray(),   // Tags filter (null = no filter)
                queryVector,                                // For ORDER BY
                fetchLimit
        );

        // Apply minSimilarity filter and limit in Java
        Stream<SearchResult> stream = results.stream();
        if (filters.minSimilarity() != null) {
            stream = stream.filter(r -> r.similarity() >= filters.minSimilarity());
        }
        return stream.limit(filters.maxResults()).toList();
    }

    private SearchResult mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SearchResult(
                rs.getObject("child_id", UUID.class),
                rs.getString("child_content"),
                rs.getInt("child_position"),
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

    private List<String> arrayToList(java.sql.Array sqlArray) throws SQLException {
        if (sqlArray == null) return List.of();
        try {
            String[] arr = (String[]) sqlArray.getArray();
            return arr != null ? List.of(arr) : List.of();
        } finally {
            sqlArray.free();
        }
    }

    @Override
    public List<SearchResult> hybridSearch(float[] queryEmbedding, String queryText, HybridSearchFilters filters) {
        PGvector queryVector = new PGvector(queryEmbedding);
        int candidateLimit = filters.maxResults() * 2;  // Fetch extra for better fusion

        List<SearchResult> results = jdbcTemplate.query("""
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
                this::mapHybridRow,
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

        // Apply minSimilarity filter in Java if specified
        // Note: For hybrid, minSimilarity is applied to RRF score (0-2 range with equal weights)
        if (filters.minSimilarity() != null) {
            double minScore = filters.minSimilarity() * (filters.vectorWeight() + filters.textWeight());
            return results.stream()
                    .filter(r -> r.similarity() >= minScore)
                    .toList();
        }
        return results;
    }

    private SearchResult mapHybridRow(ResultSet rs, int rowNum) throws SQLException {
        return new SearchResult(
                rs.getObject("child_id", UUID.class),
                rs.getString("child_content"),
                rs.getInt("child_position"),
                rs.getObject("parent_id", UUID.class),
                rs.getString("parent_context"),
                rs.getObject("document_id", UUID.class),
                rs.getString("document_title"),
                rs.getString("document_path"),
                rs.getString("category"),
                arrayToList(rs.getArray("tags")),
                rs.getDouble("rrf_score")  // RRF combined score instead of similarity
        );
    }
}
