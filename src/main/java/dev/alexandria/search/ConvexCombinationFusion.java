package dev.alexandria.search;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.List;

/**
 * Pure static utility for fusing vector and full-text search results using Convex Combination.
 *
 * <p>Applies min-max normalisation to each source's scores independently, then combines them using
 * a weighted formula: {@code combined = alpha * normVector + (1 - alpha) * normFTS}.
 */
public final class ConvexCombinationFusion {

  private ConvexCombinationFusion() {}

  /**
   * Fuses vector and FTS search results using convex combination of normalised scores.
   *
   * @param vectorResults candidates from vector (embedding) search
   * @param ftsResults candidates from full-text search
   * @param alpha weight for vector scores (0.0 = FTS only, 1.0 = vector only)
   * @param maxResults maximum number of results to return
   * @return fused results sorted by combined score descending
   */
  static List<EmbeddingMatch<TextSegment>> fuse(
      List<ScoredCandidate> vectorResults,
      List<ScoredCandidate> ftsResults,
      double alpha,
      int maxResults) {
    return List.of(); // Stub — will fail all non-empty tests
  }
}
