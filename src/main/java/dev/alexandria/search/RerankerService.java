package dev.alexandria.search;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Cross-encoder reranking service that re-scores search candidates using an ONNX-based scoring
 * model (ms-marco-MiniLM-L-6-v2).
 *
 * <p>Takes RRF hybrid search candidates and a query, scores each query-passage pair via the
 * cross-encoder, then returns results sorted by reranking score descending. Respects {@code
 * maxResults} and optional {@code minScore} threshold.
 *
 * <p>On ONNX model failure, exceptions are propagated to the caller (no silent fallback).
 *
 * @see dev.langchain4j.model.scoring.ScoringModel
 */
@Service
public class RerankerService {

  private final ScoringModel scoringModel;

  public RerankerService(ScoringModel scoringModel) {
    this.scoringModel = scoringModel;
  }

  /**
   * Reranks search candidates using the cross-encoder scoring model.
   *
   * @param query the original search query text
   * @param candidates RRF hybrid search candidates to rerank
   * @param maxResults maximum number of results to return after reranking
   * @param minScore minimum reranking score threshold (nullable; null means no threshold)
   * @return search results sorted by reranking score descending, limited to maxResults
   */
  public List<SearchResult> rerank(
      String query,
      List<EmbeddingMatch<TextSegment>> candidates,
      int maxResults,
      @Nullable Double minScore) {
    if (candidates.isEmpty()) {
      return List.of();
    }

    List<TextSegment> segments = candidates.stream().map(EmbeddingMatch::embedded).toList();

    Response<List<Double>> scores = scoringModel.scoreAll(segments, query);

    return IntStream.range(0, candidates.size())
        .mapToObj(i -> toSearchResult(candidates.get(i), scores.content().get(i)))
        .filter(r -> minScore == null || r.rerankScore() >= minScore)
        .sorted(Comparator.comparingDouble(SearchResult::rerankScore).reversed())
        .limit(maxResults)
        .toList();
  }

  private SearchResult toSearchResult(EmbeddingMatch<TextSegment> match, double rerankScore) {
    TextSegment segment = match.embedded();
    return new SearchResult(
        segment.text(),
        match.score(),
        Objects.requireNonNullElse(segment.metadata().getString("source_url"), ""),
        Objects.requireNonNullElse(segment.metadata().getString("section_path"), ""),
        rerankScore);
  }
}
