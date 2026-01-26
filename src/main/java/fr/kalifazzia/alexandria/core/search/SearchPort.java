package fr.kalifazzia.alexandria.core.search;

import java.util.List;

/**
 * Internal interface for search operations used within core services.
 *
 * <p>This interface exists for testability (Mockito on Java 25 cannot mock
 * concrete classes). Unlike infrastructure ports in {@code core/port/},
 * this is an internal abstraction within the search domain.
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
