package dev.alexandria.search;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Search orchestration layer wrapping LangChain4j's EmbeddingStore with domain DTOs.
 *
 * <p>Embeds the query text, delegates to the hybrid-configured EmbeddingStore, and maps
 * {@link EmbeddingMatch} results to {@link SearchResult} DTOs with citation metadata.</p>
 */
@Service
public class SearchService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public SearchService(EmbeddingStore<TextSegment> embeddingStore,
                         EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Performs hybrid search (vector + keyword) and returns results with citation metadata.
     *
     * @param request the search request containing query text and max results
     * @return list of search results ordered by RRF-combined relevance score
     */
    public List<SearchResult> search(SearchRequest request) {
        Embedding queryEmbedding = embeddingModel.embed(request.query()).content();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .query(request.query())  // CRITICAL: passes text query for keyword search in hybrid mode
                .maxResults(request.maxResults())
                .build();

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(searchRequest);

        return result.matches().stream()
                .map(this::toSearchResult)
                .toList();
    }

    private SearchResult toSearchResult(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        // Metadata keys MUST match the keys used when storing TextSegments during ingestion.
        // Convention: snake_case keys ("source_url", "section_path").
        return new SearchResult(
                segment.text(),
                match.score(),
                segment.metadata().getString("source_url"),
                segment.metadata().getString("section_path")
        );
    }
}
