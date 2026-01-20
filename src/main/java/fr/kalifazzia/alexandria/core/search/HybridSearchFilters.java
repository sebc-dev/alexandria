package fr.kalifazzia.alexandria.core.search;

import java.util.List;

/**
 * Filter criteria for hybrid search combining vector and full-text search.
 * Uses Reciprocal Rank Fusion (RRF) for score-independent result combination.
 */
public record HybridSearchFilters(
        int maxResults,
        Double minSimilarity,    // null = no minimum filter
        String category,         // null = no category filter
        List<String> tags,       // null = no tags filter
        double vectorWeight,     // Weight for vector search (default 1.0)
        double textWeight,       // Weight for full-text search (default 1.0)
        int rrfK                 // RRF constant k (default 60)
) {
    /**
     * Compact constructor with validation.
     */
    public HybridSearchFilters {
        if (maxResults < 1 || maxResults > 100) {
            throw new IllegalArgumentException("maxResults must be 1-100");
        }
        if (minSimilarity != null && (minSimilarity < 0.0 || minSimilarity > 1.0)) {
            throw new IllegalArgumentException("minSimilarity must be 0.0-1.0");
        }
        if (vectorWeight < 0) {
            throw new IllegalArgumentException("vectorWeight must be non-negative");
        }
        if (textWeight < 0) {
            throw new IllegalArgumentException("textWeight must be non-negative");
        }
        if (rrfK < 1) {
            throw new IllegalArgumentException("rrfK must be positive");
        }
        tags = tags != null ? List.copyOf(tags) : null;
    }

    /**
     * Factory method for hybrid search with default RRF parameters.
     * Uses equal weights (1.0) for vector and text, k=60 for RRF.
     *
     * @param maxResults Maximum number of results to return
     * @return HybridSearchFilters with default RRF parameters
     */
    public static HybridSearchFilters defaults(int maxResults) {
        return new HybridSearchFilters(maxResults, null, null, null, 1.0, 1.0, 60);
    }

    /**
     * Factory method with category and tags filters.
     *
     * @param maxResults Maximum number of results
     * @param category Category filter (null for no filter)
     * @param tags Tags filter (null for no filter)
     * @return HybridSearchFilters with filters and default RRF parameters
     */
    public static HybridSearchFilters withFilters(int maxResults, String category, List<String> tags) {
        return new HybridSearchFilters(maxResults, null, category, tags, 1.0, 1.0, 60);
    }

    /**
     * Utility for SQL parameter binding.
     *
     * @return Tags as String array, or null if no tags filter
     */
    public String[] tagsArray() {
        return tags != null ? tags.toArray(String[]::new) : null;
    }
}
