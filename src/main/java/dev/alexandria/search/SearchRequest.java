package dev.alexandria.search;

/**
 * Domain request DTO for search queries with configurable result count.
 *
 * @param query      the search query text (must not be null or blank)
 * @param maxResults the maximum number of results to return (must be >= 1)
 */
public record SearchRequest(String query, int maxResults) {

    /** Default number of results when not specified. */
    private static final int DEFAULT_MAX_RESULTS = 10;

    /**
     * Compact constructor validating input.
     */
    public SearchRequest {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query must not be blank");
        }
        if (maxResults < 1) {
            throw new IllegalArgumentException("maxResults must be at least 1");
        }
    }

    /**
     * Convenience constructor defaulting maxResults to 10.
     */
    public SearchRequest(String query) {
        this(query, DEFAULT_MAX_RESULTS);
    }
}
