package dev.alexandria.core;

import dev.alexandria.core.exception.AlexandriaException;
import dev.alexandria.core.exception.ErrorCategory;
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
   * Creates a successful search response containing the provided results.
   *
   * @param results the list of search results to include in the response
   * @param metadata the metadata associated with the search
   * @return a McpSearchResponse instance with OK status and no message
   */
  public static McpSearchResponse success(
      final List<SearchResult> results, final SearchMetadata metadata) {
    return new McpSearchResponse(SearchStatus.OK, results, metadata, null);
  }

  /**
   * Creates a partial response indicating some results were filtered or limited.
   *
   * @param results the list of search results to include in the response
   * @param metadata the metadata associated with the search
   * @param message explanation of why results are partial
   * @return a McpSearchResponse instance with PARTIAL status and the provided message
   */
  public static McpSearchResponse partial(
      final List<SearchResult> results, final SearchMetadata metadata, final String message) {
    return new McpSearchResponse(SearchStatus.PARTIAL, results, metadata, message);
  }

  /**
   * Creates a response indicating no results were found for the search.
   *
   * @param metadata search metadata (query and counters)
   * @return a McpSearchResponse instance with NO_RESULTS status, empty results list, and no message
   */
  public static McpSearchResponse noResults(final SearchMetadata metadata) {
    return new McpSearchResponse(SearchStatus.NO_RESULTS, List.of(), metadata, null);
  }

  /**
   * Creates a search response indicating an error with a contextual message.
   *
   * @param message the error message to include in the response
   * @return a McpSearchResponse instance with ERROR status and the provided message
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
     * Determines the relevance level corresponding to a score (scale 0.0–1.0).
     *
     * @param score the relevance score, must be between 0.0 and 1.0 inclusive
     * @return HIGH if score >= 0.8, MEDIUM if score >= 0.6 and < 0.8, LOW otherwise
     * @throws AlexandriaException if score is outside the valid range [0.0, 1.0]
     */
    @SuppressWarnings("PMD.OnlyOneReturn")
    public static RelevanceLevel fromScore(final double score) {
      if (score < 0.0 || score > 1.0) {
        throw new AlexandriaException(
            ErrorCategory.VALIDATION, "Score must be between 0.0 and 1.0, but was: " + score);
      }
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
        throw new AlexandriaException(
            ErrorCategory.VALIDATION, "chunkIndex must be >= 0, but was: " + chunkIndex);
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
