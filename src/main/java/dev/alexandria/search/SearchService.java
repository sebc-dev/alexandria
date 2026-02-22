package dev.alexandria.search;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import dev.alexandria.document.DocumentChunkRepository;
import dev.alexandria.ingestion.chunking.ContentType;
import dev.langchain4j.data.document.Metadata;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Search orchestration layer implementing dual-query parallel retrieval with Convex Combination
 * fusion and parent-child context resolution.
 *
 * <p>Pipeline: embed query -> build metadata filter -> parallel fetch (vector + FTS) -> CC fusion
 * -> deduplicate children by parent_id -> cross-encoder reranking on child text -> resolve parent
 * text -> return top maxResults with parent context.
 *
 * <p>Vector and FTS queries execute in parallel using {@link CompletableFuture}. Results are fused
 * via {@link ConvexCombinationFusion} with a configurable alpha weight (default 0.7 = vector
 * favoured).
 */
@Service
public class SearchService {

  private static final Logger log = LoggerFactory.getLogger(SearchService.class);

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
  private final SearchProperties searchProperties;

  public SearchService(
      EmbeddingStore<TextSegment> embeddingStore,
      EmbeddingModel embeddingModel,
      RerankerService rerankerService,
      DocumentChunkRepository documentChunkRepository,
      SearchProperties searchProperties) {
    this.embeddingStore = embeddingStore;
    this.embeddingModel = embeddingModel;
    this.rerankerService = rerankerService;
    this.documentChunkRepository = documentChunkRepository;
    this.searchProperties = searchProperties;
  }

  /**
   * Performs dual-query parallel hybrid search with Convex Combination fusion and parent-child
   * context resolution.
   *
   * <p>Executes vector search (via EmbeddingStore) and full-text search (via native SQL) in
   * parallel, fuses results using alpha-weighted convex combination, deduplicates children by
   * parent, reranks with cross-encoder on child text, then substitutes parent text for context
   * richness.
   *
   * @param request the search request containing query, filters, and result limits
   * @return list of search results ordered by reranking score descending
   */
  public List<SearchResult> search(SearchRequest request) {
    Embedding queryEmbedding = embeddingModel.embed(BGE_QUERY_PREFIX + request.query()).content();
    Filter filter = buildFilter(request);
    int candidates = searchProperties.getRerankCandidates();
    double alpha = searchProperties.getAlpha();

    // Execute vector and FTS queries in parallel
    var vectorFuture =
        CompletableFuture.supplyAsync(
            () -> executeVectorSearch(queryEmbedding, filter, candidates));
    var ftsFuture =
        CompletableFuture.supplyAsync(() -> executeFullTextSearch(request.query(), candidates));
    CompletableFuture.allOf(vectorFuture, ftsFuture).join();

    List<ScoredCandidate> vectorResults = vectorFuture.join();
    List<ScoredCandidate> ftsResults = ftsFuture.join();

    if (vectorResults.isEmpty()) {
      log.debug("Vector search returned no results for query: {}", request.query());
    }
    if (ftsResults.isEmpty()) {
      log.debug("FTS search returned no results for query: {}", request.query());
    }

    // Fuse results via Convex Combination
    List<EmbeddingMatch<TextSegment>> fused =
        ConvexCombinationFusion.fuse(vectorResults, ftsResults, alpha, candidates);

    // Deduplicate: group child matches by parent_id, keep highest-scoring child per parent
    List<EmbeddingMatch<TextSegment>> deduplicated = deduplicateByParent(fused);

    // Resolve parent texts before reranking (batch DB query), building childText -> parentText map
    Map<String, String> childToParentText = resolveParentTexts(deduplicated);

    // Rerank on child text (the matched text) for precision scoring
    List<SearchResult> reranked =
        rerankerService.rerank(
            request.query(), deduplicated, request.maxResults(), request.minScore());

    // Substitute parent text for child results
    return substituteParentText(reranked, childToParentText);
  }

  /**
   * Executes vector search via the EmbeddingStore. Converts results to {@link ScoredCandidate} for
   * fusion.
   *
   * @param queryEmbedding the query embedding vector
   * @param filter metadata filter (may be null)
   * @param maxResults maximum candidates to fetch
   * @return list of scored candidates from vector search
   */
  List<ScoredCandidate> executeVectorSearch(
      Embedding queryEmbedding, @Nullable Filter filter, int maxResults) {
    EmbeddingSearchRequest.EmbeddingSearchRequestBuilder builder =
        EmbeddingSearchRequest.builder().queryEmbedding(queryEmbedding).maxResults(maxResults);

    if (filter != null) {
      builder.filter(filter);
    }

    EmbeddingSearchResult<TextSegment> result = embeddingStore.search(builder.build());
    return result.matches().stream()
        .map(
            match ->
                new ScoredCandidate(
                    match.embeddingId(), match.embedded(), match.embedding(), match.score()))
        .toList();
  }

  /**
   * Executes full-text search via native SQL query. Converts raw result rows to {@link
   * ScoredCandidate} for fusion.
   *
   * @param query the search query text
   * @param maxResults maximum candidates to fetch
   * @return list of scored candidates from FTS
   */
  List<ScoredCandidate> executeFullTextSearch(String query, int maxResults) {
    List<Object[]> rows = documentChunkRepository.fullTextSearch(query, maxResults);
    List<ScoredCandidate> candidates = new ArrayList<>(rows.size());

    for (Object[] row : rows) {
      String embeddingId = (String) row[0];
      String text = (String) row[1];
      String sourceUrl = (String) row[2];
      String sectionPath = (String) row[3];
      String chunkType = (String) row[4];
      String parentId = (String) row[5];
      String contentType = (String) row[6];
      String version = (String) row[7];
      String sourceName = (String) row[8];
      double score = ((Number) row[9]).doubleValue();

      Metadata metadata = new Metadata();
      putIfNotNull(metadata, "source_url", sourceUrl);
      putIfNotNull(metadata, "section_path", sectionPath);
      putIfNotNull(metadata, "chunk_type", chunkType);
      putIfNotNull(metadata, "parent_id", parentId);
      putIfNotNull(metadata, "content_type", contentType);
      putIfNotNull(metadata, "version", version);
      putIfNotNull(metadata, "source_name", sourceName);

      TextSegment segment = TextSegment.from(Objects.requireNonNullElse(text, ""), metadata);
      candidates.add(new ScoredCandidate(embeddingId, segment, null, score));
    }

    return candidates;
  }

  private static void putIfNotNull(Metadata metadata, String key, @Nullable String value) {
    if (value != null) {
      metadata.put(key, value);
    }
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
   * Batch-fetches parent texts for all child matches in the candidate list. Builds a mapping from
   * child text to parent text, used for post-reranking substitution. Uses a single database query
   * for efficiency.
   *
   * @param candidates the deduplicated candidate list
   * @return map of child text to parent text; empty if no children in candidates
   */
  Map<String, String> resolveParentTexts(List<EmbeddingMatch<TextSegment>> candidates) {
    // Collect unique parent_ids and map child text -> parent_id
    Map<String, String> childTextToParentId = new HashMap<>();
    List<String> parentIds = new ArrayList<>();

    for (EmbeddingMatch<TextSegment> match : candidates) {
      String chunkType = match.embedded().metadata().getString("chunk_type");
      String parentId = match.embedded().metadata().getString("parent_id");
      if ("child".equals(chunkType) && parentId != null) {
        childTextToParentId.put(match.embedded().text(), parentId);
        if (!parentIds.contains(parentId)) {
          parentIds.add(parentId);
        }
      }
    }

    if (parentIds.isEmpty()) {
      return Map.of();
    }

    // Batch-fetch parent texts from DB
    Map<String, String> parentIdToText = new HashMap<>();
    List<Object[]> rows =
        documentChunkRepository.findParentTextsByKeys(parentIds.toArray(String[]::new));
    for (Object[] row : rows) {
      String parentKey = (String) row[0];
      String text = (String) row[1];
      parentIdToText.put(parentKey, text);
    }

    if (parentIdToText.size() < parentIds.size()) {
      log.warn(
          "Could not resolve all parent texts: requested={}, found={}",
          parentIds.size(),
          parentIdToText.size());
    }

    // Build final childText -> parentText map
    Map<String, String> childToParentText = new HashMap<>();
    for (Map.Entry<String, String> entry : childTextToParentId.entrySet()) {
      String parentText = parentIdToText.get(entry.getValue());
      if (parentText != null) {
        childToParentText.put(entry.getKey(), parentText);
      }
    }

    return childToParentText;
  }

  /**
   * Substitutes parent text for child search results. For each result whose text matches a known
   * child text, the text is replaced with the parent's full section content. Parent matches and
   * legacy chunks are returned as-is.
   *
   * @param results the reranked search results
   * @param childToParentText map of child text to parent text
   * @return results with parent text substituted for child matches
   */
  List<SearchResult> substituteParentText(
      List<SearchResult> results, Map<String, String> childToParentText) {
    if (childToParentText.isEmpty()) {
      return results;
    }

    List<SearchResult> resolved = new ArrayList<>(results.size());
    for (SearchResult r : results) {
      String parentText = childToParentText.get(r.text());
      if (parentText != null) {
        resolved.add(
            new SearchResult(
                parentText, r.score(), r.sourceUrl(), r.sectionPath(), r.rerankScore()));
      } else {
        resolved.add(r);
      }
    }
    return resolved;
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
