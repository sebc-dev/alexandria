package dev.alexandria.core;

import java.util.List;

/**
 * Response object for MCP search operations. Contains search results along with metadata and status
 * information.
 *
 * @param status the overall status of the search operation
 * @param results the list of search results
 * @param metadata information about the search query and result count
 * @param message optional message providing additional context (e.g., for partial or error status)
 */
public record McpSearchResponse(
    SearchStatus status, List<SearchResult> results, SearchMetadata metadata, String message) {

  /** Compact constructor ensuring immutability via defensive copy. */
  public McpSearchResponse {
    results = results == null ? List.of() : List.copyOf(results);
  }

  /**
   * Creates a successful response with all results.
   *
   * @param results the search results
   * @param metadata search metadata
   * @return a new response with OK status
   */
  public static McpSearchResponse success(
      final List<SearchResult> results, final SearchMetadata metadata) {
    return new McpSearchResponse(SearchStatus.OK, results, metadata, null);
  }

  /**
   * Creates a partial response when some results were filtered or limited.
   *
   * @param results the search results
   * @param metadata search metadata
   * @param message explanation of why results are partial
   * @return a new response with PARTIAL status
   */
  public static McpSearchResponse partial(
      final List<SearchResult> results, final SearchMetadata metadata, final String message) {
    return new McpSearchResponse(SearchStatus.PARTIAL, results, metadata, message);
  }

  /**
   * Creates a response indicating no results were found.
   *
   * @param metadata search metadata
   * @return a new response with NO_RESULTS status
   */
  public static McpSearchResponse noResults(final SearchMetadata metadata) {
    return new McpSearchResponse(SearchStatus.NO_RESULTS, List.of(), metadata, null);
  }

  /**
   * Creates an error response.
   *
   * @param message the error message
   * @return a new response with ERROR status
   */
  public static McpSearchResponse error(final String message) {
    return new McpSearchResponse(SearchStatus.ERROR, List.of(), null, message);
  }

  /** Status of a search operation. */
  @SuppressWarnings("PMD.ShortVariable")
  public enum SearchStatus {
    /** Search completed successfully with results. */
    OK, // NOSONAR - OK is a standard HTTP/status name
    /** Search completed but some results were filtered or limited. */
    PARTIAL,
    /** Search completed but no matching results were found. */
    NO_RESULTS,
    /** Search failed due to an error. */
    ERROR
  }

  /** Relevance level categorizing a search result's match quality. */
  public enum RelevanceLevel {
    /** High relevance (score >= 0.8). */
    HIGH,
    /** Medium relevance (0.6 <= score < 0.8). */
    MEDIUM,
    /** Low relevance (score < 0.6). */
    LOW;

    private static final double HIGH_THRESHOLD = 0.8;
    private static final double MEDIUM_THRESHOLD = 0.6;

    /**
     * Determines the relevance level based on a numeric score.
     *
     * @param score the relevance score (0.0 to 1.0)
     * @return the corresponding relevance level
     */
    @SuppressWarnings("PMD.OnlyOneReturn")
    public static RelevanceLevel fromScore(final double score) {
      if (score >= HIGH_THRESHOLD) {
        return HIGH;
      } else if (score >= MEDIUM_THRESHOLD) {
        return MEDIUM;
      } else {
        return LOW;
      }
    }
  }

  /**
   * A single search result.
   *
   * @param content the matching content snippet
   * @param sourceUri URI of the source document
   * @param chunkIndex zero-based index of this chunk within the source document
   * @param score the relevance score (0.0 to 1.0)
   * @param relevance the categorized relevance level
   * @param breadcrumbs hierarchical path within the document
   */
  public record SearchResult(
      String content,
      String sourceUri,
      int chunkIndex,
      double score,
      RelevanceLevel relevance,
      String breadcrumbs) {

    /** Compact constructor with validation. */
    public SearchResult {
      if (chunkIndex < 0) {
        throw new IllegalArgumentException("chunkIndex must be >= 0");
      }
    }
  }

  /**
   * Metadata about a search query and its results.
   *
   * @param query the original search query
   * @param totalMatches total number of matching documents found
   * @param returnedCount number of results actually returned
   */
  public record SearchMetadata(String query, int totalMatches, int returnedCount) {}
}
