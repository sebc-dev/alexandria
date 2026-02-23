package dev.alexandria.search;

import org.jspecify.annotations.Nullable;

/**
 * Domain request DTO for search queries with configurable result count and optional filters.
 *
 * <p>Filter fields narrow search results via metadata matching:
 *
 * <ul>
 *   <li>{@code source} - exact match on source_name metadata
 *   <li>{@code sectionPath} - prefix match on section_path metadata (slugified)
 *   <li>{@code version} - exact match on version metadata
 *   <li>{@code contentType} - exact match on content_type metadata (MIXED/null skips filter)
 *   <li>{@code minScore} - minimum reranking score threshold
 * </ul>
 *
 * @param query the search query text (must not be null or blank)
 * @param maxResults the maximum number of results to return (must be >= 1)
 * @param source optional source name filter (exact match)
 * @param sectionPath optional section path filter (prefix match, slugified)
 * @param version optional version filter (exact match)
 * @param contentType optional content type filter ("prose", "code", "mixed"/null means no filter)
 * @param minScore optional minimum reranking score threshold
 */
public record SearchRequest(
    String query,
    int maxResults,
    @Nullable String source,
    @Nullable String sectionPath,
    @Nullable String version,
    @Nullable String contentType,
    @Nullable Double minScore) {

  /** Default number of results when not specified. */
  private static final int DEFAULT_MAX_RESULTS = 10;

  /** Compact constructor validating input. */
  public SearchRequest {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("Query must not be blank");
    }
    if (maxResults < 1) {
      throw new IllegalArgumentException("maxResults must be at least 1");
    }
  }

  /** Convenience constructor defaulting maxResults to 10 and all filters to null. */
  public SearchRequest(String query) {
    this(query, DEFAULT_MAX_RESULTS, null, null, null, null, null);
  }

  /** Convenience constructor with query and maxResults, all filters null. */
  public SearchRequest(String query, int maxResults) {
    this(query, maxResults, null, null, null, null, null);
  }
}
