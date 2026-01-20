package fr.kalifazzia.alexandria.core.search;

import java.util.List;

/**
 * Combined result from hybrid search with graph traversal.
 * Contains semantic search results (vector + full-text) and
 * related documents discovered via graph edges.
 */
public record HybridSearchResult(
    List<SearchResult> searchResults,      // Vector + full-text combined via RRF
    List<RelatedDocument> relatedDocuments // Documents from graph traversal
) {
    /**
     * Compact constructor with defensive copying.
     */
    public HybridSearchResult {
        searchResults = searchResults != null ? List.copyOf(searchResults) : List.of();
        relatedDocuments = relatedDocuments != null ? List.copyOf(relatedDocuments) : List.of();
    }

    /**
     * Factory method for results without graph traversal.
     *
     * @param searchResults Semantic search results
     * @return HybridSearchResult with empty relatedDocuments
     */
    public static HybridSearchResult withoutGraph(List<SearchResult> searchResults) {
        return new HybridSearchResult(searchResults, List.of());
    }
}
