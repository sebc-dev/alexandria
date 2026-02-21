package dev.alexandria.search;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import dev.alexandria.ingestion.chunking.ContentType;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Search orchestration layer implementing two-stage retrieval: over-fetch with metadata filters,
 * then cross-encoder reranking.
 *
 * <p>Pipeline: embed query -> build metadata filter from SearchRequest -> over-fetch 50 candidates
 * from EmbeddingStore (hybrid vector + keyword) -> delegate to RerankerService for cross-encoder
 * scoring -> return top maxResults.
 */
@Service
public class SearchService {

  private static final Logger log = LoggerFactory.getLogger(SearchService.class);

  /** Number of candidates to over-fetch for reranking. */
  static final int RERANK_CANDIDATES = 50;

  private final EmbeddingStore<TextSegment> embeddingStore;
  private final EmbeddingModel embeddingModel;
  private final RerankerService rerankerService;

  public SearchService(
      EmbeddingStore<TextSegment> embeddingStore,
      EmbeddingModel embeddingModel,
      RerankerService rerankerService) {
    this.embeddingStore = embeddingStore;
    this.embeddingModel = embeddingModel;
    this.rerankerService = rerankerService;
  }

  /**
   * Performs two-stage hybrid search: over-fetch candidates with metadata filters, then rerank with
   * cross-encoder.
   *
   * @param request the search request containing query, filters, and result limits
   * @return list of search results ordered by reranking score descending
   */
  public List<SearchResult> search(SearchRequest request) {
    if (request.rrfK() != null) {
      log.debug(
          "rrfK={} provided on SearchRequest but cannot be applied per-request "
              + "(store-level config in LangChain4j); using configured store value",
          request.rrfK());
    }

    Embedding queryEmbedding = embeddingModel.embed(request.query()).content();
    Filter filter = buildFilter(request);

    EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .query(request.query())
            .maxResults(RERANK_CANDIDATES);

    if (filter != null) {
      builder.filter(filter);
    }

    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(builder.build());
    List<EmbeddingMatch<TextSegment>> candidates = result.matches();

    return rerankerService.rerank(
        request.query(), candidates, request.maxResults(), request.minScore());
  }

  /**
   * Builds a composable LangChain4j Filter from SearchRequest filter parameters. Multiple filters
   * are combined with AND logic.
   *
   * @param request the search request with optional filter fields
   * @return combined Filter, or null if no filters are specified
   */
  @Nullable Filter buildFilter(SearchRequest request) {
    List<Filter> filters = new ArrayList<>();

    if (request.source() != null) {
      filters.add(metadataKey("source_name").isEqualTo(request.source()));
    }

    if (request.version() != null) {
      filters.add(metadataKey("version").isEqualTo(request.version()));
    }

    if (request.sectionPath() != null) {
      filters.add(metadataKey("section_path").containsString(slugify(request.sectionPath())));
    }

    if (request.contentType() != null) {
      ContentType parsed = ContentType.parseSearchFilter(request.contentType());
      if (parsed != null) {
        filters.add(metadataKey("content_type").isEqualTo(parsed.value()));
      }
    }

    return filters.stream().reduce((a, b) -> a.and(b)).orElse(null);
  }

  /**
   * Slugifies input text: lowercase, non-alphanumeric to hyphens, trim leading/trailing hyphens.
   * Matches the slugification pattern used by MarkdownChunker for section paths.
   */
  static String slugify(String input) {
    return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }
}
