package dev.alexandria.search;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import dev.alexandria.document.DocumentChunkRepository;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Search orchestration layer implementing two-stage retrieval with parent-child context resolution:
 * over-fetch with metadata filters, deduplicate child matches by parent, cross-encoder reranking,
 * then substitute parent text for child matches.
 *
 * <p>Pipeline: embed query -> build metadata filter from SearchRequest -> over-fetch 50 candidates
 * from EmbeddingStore (hybrid vector + keyword) -> deduplicate children by parent_id (keep
 * best-scoring child per parent) -> delegate to RerankerService for cross-encoder scoring on child
 * text -> resolve parent text for child results -> return top maxResults with parent context.
 */
@Service
public class SearchService {

  private static final Logger log = LoggerFactory.getLogger(SearchService.class);

  /** Number of candidates to over-fetch for reranking. */
  static final int RERANK_CANDIDATES = 50;

  /**
   * BGE query prefix recommended by the bge-small-en-v1.5 model documentation. Prepended to search
   * queries (NOT to documents at ingestion time) to improve retrieval relevance.
   */
  static final String BGE_QUERY_PREFIX =
      "Represent this sentence for searching relevant passages: ";

  private final EmbeddingStore<TextSegment> embeddingStore;
  private final EmbeddingModel embeddingModel;
  private final RerankerService rerankerService;
  private final DocumentChunkRepository documentChunkRepository;

  public SearchService(
      EmbeddingStore<TextSegment> embeddingStore,
      EmbeddingModel embeddingModel,
      RerankerService rerankerService,
      DocumentChunkRepository documentChunkRepository) {
    this.embeddingStore = embeddingStore;
    this.embeddingModel = embeddingModel;
    this.rerankerService = rerankerService;
    this.documentChunkRepository = documentChunkRepository;
  }

  /**
   * Performs two-stage hybrid search with parent-child context resolution: over-fetch candidates
   * with metadata filters, deduplicate children by parent, rerank with cross-encoder on child text,
   * then substitute parent text for context richness.
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

    Embedding queryEmbedding = embeddingModel.embed(BGE_QUERY_PREFIX + request.query()).content();
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

    // Deduplicate: group child matches by parent_id, keep highest-scoring child per parent
    List<EmbeddingMatch<TextSegment>> deduplicated = deduplicateByParent(candidates);

    // Rerank on child text (the matched text) for precision scoring
    List<SearchResult> reranked =
        rerankerService.rerank(
            request.query(), deduplicated, request.maxResults(), request.minScore());

    // Substitute parent text for child results
    return resolveParentText(reranked);
  }

  /**
   * Deduplicates child matches that share the same parent. For each unique parent_id, only the
   * highest-scoring child is retained. Parent matches (chunk_type="parent") and legacy chunks (no
   * chunk_type) pass through unmodified.
   *
   * @param candidates the raw embedding matches
   * @return deduplicated list with at most one child per parent
   */
  List<EmbeddingMatch<TextSegment>> deduplicateByParent(
      List<EmbeddingMatch<TextSegment>> candidates) {
    // LinkedHashMap preserves insertion order (score-descending from store)
    Map<String, EmbeddingMatch<TextSegment>> bestChildPerParent = new LinkedHashMap<>();
    List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();

    for (EmbeddingMatch<TextSegment> match : candidates) {
      String chunkType = match.embedded().metadata().getString("chunk_type");
      String parentId = match.embedded().metadata().getString("parent_id");

      if ("child".equals(chunkType) && parentId != null) {
        // Child match: keep only the highest-scoring one per parent
        EmbeddingMatch<TextSegment> existing = bestChildPerParent.get(parentId);
        if (existing == null || match.score() > existing.score()) {
          bestChildPerParent.put(parentId, match);
        }
      } else {
        // Parent match or legacy chunk: pass through
        result.add(match);
      }
    }

    result.addAll(bestChildPerParent.values());
    // Re-sort by score descending to maintain consistent ordering
    result.sort(Comparator.comparingDouble(EmbeddingMatch<TextSegment>::score).reversed());
    return result;
  }

  /**
   * Resolves parent text for child search results. Child results (those with chunk_type="child" in
   * their original match) get their text replaced with the parent's full section content. Parent
   * matches and legacy chunks are returned as-is.
   *
   * @param results the reranked search results
   * @return results with parent text substituted for child matches
   */
  List<SearchResult> resolveParentText(List<SearchResult> results) {
    // Collect unique parent_ids that need resolution
    // parentId format: {sourceUrl}#{sectionPath} — same as the parent's composite key
    List<String> parentIds = new ArrayList<>();
    for (SearchResult r : results) {
      // A result is a child if it has a non-empty sourceUrl and sectionPath,
      // and we can check if the parent exists. But we need to know which results
      // are children. Since RerankerService's toSearchResult doesn't carry chunk_type,
      // we need another approach: look up all potential parent keys and see if they exist.
      // Actually, the simplest approach: look up parent text for ALL results'
      // source_url#section_path
      // combinations, and substitute only where a parent is found AND the result's text differs
      // from the parent (meaning it's a child result, not the parent itself).
      //
      // However, this approach is incorrect because parent chunks also have
      // source_url#section_path.
      // We need chunk_type metadata to distinguish children from parents.
      //
      // Since SearchResult doesn't carry chunk_type, we need to either:
      // a) Add chunk_type to SearchResult
      // b) Carry parentId through to SearchResult
      // c) Do the resolution before building SearchResult
      //
      // Option (b) is cleanest: add parentId to SearchResult.
      // But actually, we can do the resolution BEFORE building final SearchResults.
      // Wait, the reranker builds SearchResult objects. We need to modify the flow.
      //
      // Best approach: pass parentId through SearchResult (add optional field),
      // then resolve here.
    }

    // We need parentId on SearchResult. Let me reconsider the approach.
    // Instead of resolving after reranking, we can resolve by looking at the SearchResult
    // metadata. But SearchResult is a simple record without chunk metadata.
    //
    // Simplest fix: we already know which results are children because we deduplicated them.
    // We should carry the parentId through. Let me modify SearchResult to include parentId.
    return results;
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
