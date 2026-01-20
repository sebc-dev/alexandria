package fr.kalifazzia.alexandria.core.port;

import fr.kalifazzia.alexandria.core.search.HybridSearchFilters;
import fr.kalifazzia.alexandria.core.search.SearchFilters;
import fr.kalifazzia.alexandria.core.search.SearchResult;

import java.util.List;

/**
 * Port interface for semantic search operations.
 * Follows hexagonal architecture - core defines the contract, infra implements it.
 *
 * <p>Searches child chunks by embedding similarity and returns results with
 * parent context for LLM consumption. Supports filtering by category and tags.
 */
public interface SearchRepository {

    /**
     * Searches for similar chunks using cosine similarity on embeddings.
     *
     * <p>Search behavior:
     * <ul>
     *   <li>Only searches CHILD chunks (smaller, more precise matches)</li>
     *   <li>Returns parent chunk content for context expansion</li>
     *   <li>Filters by category and/or tags when specified</li>
     *   <li>Orders by similarity (highest first)</li>
     * </ul>
     *
     * @param queryEmbedding Embedding vector for the search query
     * @param filters Search filters (maxResults, minSimilarity, category, tags)
     * @return List of search results with parent context, ordered by similarity descending
     */
    List<SearchResult> searchSimilar(float[] queryEmbedding, SearchFilters filters);

    /**
     * Searches using hybrid vector + full-text search with RRF scoring.
     *
     * <p>Combines semantic similarity (pgvector) with full-text matching (tsvector)
     * using Reciprocal Rank Fusion for score-independent result combination.
     *
     * @param queryEmbedding Embedding vector for semantic search
     * @param queryText Raw text for full-text search
     * @param filters Hybrid search filters including RRF parameters
     * @return List of search results ordered by combined RRF score descending
     */
    List<SearchResult> hybridSearch(float[] queryEmbedding, String queryText, HybridSearchFilters filters);
}
