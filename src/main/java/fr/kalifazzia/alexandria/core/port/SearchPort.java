package fr.kalifazzia.alexandria.core.port;

import fr.kalifazzia.alexandria.core.search.SearchResult;

import java.util.List;

/**
 * Port interface for search operations.
 *
 * <p>Abstracts the search capability for use in evaluation and other
 * services that need to execute searches without coupling to
 * the concrete SearchService implementation.
 */
public interface SearchPort {

    /**
     * Executes a hybrid search (vector + full-text) with default parameters.
     *
     * @param query the search query text
     * @param maxResults maximum number of results to return
     * @return list of search results ordered by relevance
     */
    List<SearchResult> hybridSearch(String query, int maxResults);
}
