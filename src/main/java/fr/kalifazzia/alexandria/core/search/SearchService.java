package fr.kalifazzia.alexandria.core.search;

import fr.kalifazzia.alexandria.core.port.EmbeddingGenerator;
import fr.kalifazzia.alexandria.core.port.SearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service orchestrating semantic search operations.
 * Converts text queries to embeddings, then searches by similarity.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final EmbeddingGenerator embeddingGenerator;
    private final SearchRepository searchRepository;

    public SearchService(EmbeddingGenerator embeddingGenerator, SearchRepository searchRepository) {
        this.embeddingGenerator = embeddingGenerator;
        this.searchRepository = searchRepository;
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

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
