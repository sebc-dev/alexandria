package fr.kalifazzia.alexandria.core.search;

import fr.kalifazzia.alexandria.core.port.DocumentRepository;
import fr.kalifazzia.alexandria.core.port.EmbeddingGenerator;
import fr.kalifazzia.alexandria.core.port.GraphRepository;
import fr.kalifazzia.alexandria.core.port.SearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service orchestrating semantic search operations.
 * Converts text queries to embeddings, then searches by similarity.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final EmbeddingGenerator embeddingGenerator;
    private final SearchRepository searchRepository;
    private final GraphRepository graphRepository;
    private final DocumentRepository documentRepository;

    public SearchService(
            EmbeddingGenerator embeddingGenerator,
            SearchRepository searchRepository,
            GraphRepository graphRepository,
            DocumentRepository documentRepository) {
        this.embeddingGenerator = embeddingGenerator;
        this.searchRepository = searchRepository;
        this.graphRepository = graphRepository;
        this.documentRepository = documentRepository;
    }

    /**
     * Searches for documentation chunks similar to the query text.
     *
     * @param query Text query to search for
     * @param filters Search filters (maxResults, minSimilarity, category, tags)
     * @return List of search results with parent context, ordered by similarity
     * @throws IllegalArgumentException if query is null or blank
     */
    public List<SearchResult> search(String query, SearchFilters filters) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be null or blank");
        }

        log.debug("Searching for: '{}' with filters: {}", query, filters);

        // 1. Generate embedding for query text
        float[] queryEmbedding = embeddingGenerator.embed(query);

        // 2. Execute similarity search
        List<SearchResult> results = searchRepository.searchSimilar(queryEmbedding, filters);

        log.info("Search for '{}' returned {} results", truncate(query, 50), results.size());

        return results;
    }

    /**
     * Convenience method for simple search without filters.
     *
     * @param query Text query to search for
     * @param maxResults Maximum number of results to return
     * @return List of search results
     */
    public List<SearchResult> search(String query, int maxResults) {
        return search(query, SearchFilters.simple(maxResults));
    }

    /**
     * Searches using hybrid vector + full-text search with RRF scoring.
     *
     * @param query Text query (used for both embedding generation and full-text search)
     * @param filters Hybrid search filters including RRF parameters
     * @return List of search results ordered by combined RRF score
     * @throws IllegalArgumentException if query is null or blank
     */
    public List<SearchResult> hybridSearch(String query, HybridSearchFilters filters) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Query cannot be null or blank");
        }

        log.debug("Hybrid searching for: '{}' with filters: {}", query, filters);

        // 1. Generate embedding for vector search
        float[] queryEmbedding = embeddingGenerator.embed(query);

        // 2. Execute hybrid search (vector + full-text via RRF)
        List<SearchResult> results = searchRepository.hybridSearch(queryEmbedding, query, filters);

        log.info("Hybrid search for '{}' returned {} results", truncate(query, 50), results.size());

        return results;
    }

    /**
     * Convenience method for hybrid search with default RRF parameters.
     *
     * @param query Text query
     * @param maxResults Maximum number of results
     * @return List of search results
     */
    public List<SearchResult> hybridSearch(String query, int maxResults) {
        return hybridSearch(query, HybridSearchFilters.defaults(maxResults));
    }

    /**
     * Performs hybrid search with graph traversal for related documents.
     *
     * <p>Steps:
     * <ol>
     *   <li>Execute hybrid search (vector + full-text via RRF)</li>
     *   <li>Extract unique document IDs from search results</li>
     *   <li>Traverse graph to find related documents within maxHops</li>
     *   <li>Exclude documents already in search results</li>
     *   <li>Load metadata for related documents</li>
     * </ol>
     *
     * @param query Text query for search
     * @param filters Hybrid search filters
     * @param maxHops Maximum graph hops (1-10, recommended 2)
     * @return HybridSearchResult with semantic results and graph-related documents
     * @throws IllegalArgumentException if query is null/blank or maxHops invalid
     */
    public HybridSearchResult hybridSearchWithGraph(String query, HybridSearchFilters filters, int maxHops) {
        if (maxHops < 1 || maxHops > 10) {
            throw new IllegalArgumentException("maxHops must be between 1 and 10");
        }

        // 1. Execute hybrid search
        List<SearchResult> searchResults = hybridSearch(query, filters);

        if (searchResults.isEmpty()) {
            return HybridSearchResult.withoutGraph(searchResults);
        }

        // 2. Extract unique document IDs from search results
        Set<UUID> resultDocIds = searchResults.stream()
                .map(SearchResult::documentId)
                .collect(Collectors.toSet());

        // 3. Find graph-related documents
        Set<UUID> relatedIds = new HashSet<>();
        for (UUID docId : resultDocIds) {
            try {
                relatedIds.addAll(graphRepository.findRelatedDocuments(docId, maxHops));
            } catch (Exception e) {
                // Graph query may fail if document not in graph - log and continue
                log.warn("Graph traversal failed for document {}: {}", docId, e.getMessage());
            }
        }

        // 4. Exclude documents already in search results
        relatedIds.removeAll(resultDocIds);

        if (relatedIds.isEmpty()) {
            return HybridSearchResult.withoutGraph(searchResults);
        }

        // 5. Load document metadata for related documents
        List<RelatedDocument> relatedDocs = documentRepository.findByIds(relatedIds).stream()
                .map(doc -> new RelatedDocument(
                        doc.id(),
                        doc.title(),
                        doc.path(),
                        doc.category()
                ))
                .toList();

        log.info("Hybrid search for '{}' returned {} results + {} related documents",
                truncate(query, 50), searchResults.size(), relatedDocs.size());

        return new HybridSearchResult(searchResults, relatedDocs);
    }

    /**
     * Convenience method with default maxHops=2.
     *
     * @param query Text query
     * @param filters Hybrid search filters
     * @return HybridSearchResult with graph traversal (2 hops)
     */
    public HybridSearchResult hybridSearchWithGraph(String query, HybridSearchFilters filters) {
        return hybridSearchWithGraph(query, filters, 2);
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
