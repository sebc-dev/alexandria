package fr.kalifazzia.alexandria.core.search;

import java.util.List;

/**
 * Domain record for search filter criteria.
 * Encapsulates all filtering options for semantic search operations.
 */
public record SearchFilters(
        int maxResults,
        Double minSimilarity,    // null = no minimum filter (e.g., 0.3)
        String category,         // null = no category filter
        List<String> tags        // null = no tags filter
) {
    /**
     * Compact constructor with validation.
     */
    public SearchFilters {
        if (maxResults < 1 || maxResults > 100) {
            throw new IllegalArgumentException("maxResults must be 1-100");
        }
        if (minSimilarity != null && (minSimilarity < 0.0 || minSimilarity > 1.0)) {
            throw new IllegalArgumentException("minSimilarity must be 0.0-1.0");
        }
        tags = tags != null ? List.copyOf(tags) : null;
    }

    /**
     * Factory method for simple search with default filters.
     *
     * @param maxResults Maximum number of results to return
     * @return SearchFilters with no category, tags, or similarity filter
     */
    public static SearchFilters simple(int maxResults) {
        return new SearchFilters(maxResults, null, null, null);
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
