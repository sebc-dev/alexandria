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
   * Construit une réponse de recherche indiquant un succès contenant les résultats fournis.
   *
   * @param results la liste des résultats de recherche à inclure dans la réponse
   * @param metadata les métadonnées associées à la recherche
   * @return une instance de McpSearchResponse avec le statut OK et sans message
   */
  public static McpSearchResponse success(
      final List<SearchResult> results, final SearchMetadata metadata) {
    return new McpSearchResponse(SearchStatus.OK, results, metadata, null);
  }

  /**
   * Construit une réponse partielle indiquant que certains résultats ont été filtrés ou limités.
   *
   * @param results la liste des résultats de recherche à inclure dans la réponse
   * @param metadata les métadonnées associées à la recherche
   * @param message message expliquant pourquoi les résultats sont partiels
   * @return une instance de {@code McpSearchResponse} ayant le statut {@code PARTIAL} et le message fourni
   */
  public static McpSearchResponse partial(
      final List<SearchResult> results, final SearchMetadata metadata, final String message) {
    return new McpSearchResponse(SearchStatus.PARTIAL, results, metadata, message);
  }

  /**
   * Construit une réponse indiquant qu'aucun résultat n'a été trouvé pour la recherche.
   *
   * @param metadata métadonnées de la recherche (requête et compteurs)
   * @return une instance de McpSearchResponse avec le statut NO_RESULTS, une liste de résultats vide et sans message
   */
  public static McpSearchResponse noResults(final SearchMetadata metadata) {
    return new McpSearchResponse(SearchStatus.NO_RESULTS, List.of(), metadata, null);
  }

  /**
   * Crée une réponse de recherche indiquant une erreur et contenant un message contextuel.
   *
   * @param message le message d'erreur à inclure dans la réponse
   * @return une instance de McpSearchResponse avec le statut ERROR et le message fourni
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
     * Détermine le niveau de pertinence correspondant à un score (échelle 0.0–1.0).
     *
     * @param score le score de pertinence, attendu entre 0.0 et 1.0
     * @return `HIGH` si le score est >= 0.8, `MEDIUM` si le score est >= 0.6 et < 0.8, `LOW` sinon
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
      String breadcrumbs) {}

  /**
   * Metadata about a search query and its results.
   *
   * @param query the original search query
   * @param totalMatches total number of matching documents found
   * @param returnedCount number of results actually returned
   */
  public record SearchMetadata(String query, int totalMatches, int returnedCount) {}
}