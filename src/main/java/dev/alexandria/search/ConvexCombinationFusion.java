package dev.alexandria.search;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure static utility for fusing vector and full-text search results using Convex Combination.
 *
 * <p>Applies min-max normalisation to each source's scores independently, then combines them using
 * a weighted formula: {@code combined = alpha * normVector + (1 - alpha) * normFTS}.
 *
 * <p>This class has no Spring dependencies and no state -- all methods are pure functions.
 */
public final class ConvexCombinationFusion {

  /** Zero-length embedding placeholder for FTS-only results (reranker does not use embeddings). */
  private static final Embedding EMPTY_EMBEDDING = Embedding.from(new float[0]);

  private ConvexCombinationFusion() {}

  /**
   * Fuses vector and FTS search results using convex combination of normalised scores.
   *
   * <p>Algorithm:
   *
   * <ol>
   *   <li>Min-max normalise vector scores to [0, 1] (if max == min, all normalise to 1.0)
   *   <li>Min-max normalise FTS scores to [0, 1] (same logic)
   *   <li>Combine by embeddingId: {@code combined = alpha * normVector + (1 - alpha) * normFTS}
   *   <li>Results in only one source get 0.0 for the missing source
   *   <li>Sort by combined score descending, limit to maxResults
   * </ol>
   *
   * @param vectorResults candidates from vector (embedding) search
   * @param ftsResults candidates from full-text search
   * @param alpha weight for vector scores (0.0 = FTS only, 1.0 = vector only)
   * @param maxResults maximum number of results to return
   * @return fused results sorted by combined score descending, as {@link EmbeddingMatch} for
   *     compatibility with the existing reranker pipeline
   */
  static List<EmbeddingMatch<TextSegment>> fuse(
      List<ScoredCandidate> vectorResults,
      List<ScoredCandidate> ftsResults,
      double alpha,
      int maxResults) {
    if (vectorResults.isEmpty() && ftsResults.isEmpty()) {
      return List.of();
    }

    // Step 1-2: Compute min-max normalisation bounds
    double vectorMin = minScore(vectorResults);
    double vectorMax = maxScore(vectorResults);
    double ftsMin = minScore(ftsResults);
    double ftsMax = maxScore(ftsResults);

    // Step 3: Build combined map by embeddingId (preserves insertion order for determinism)
    Map<String, FusedEntry> fusedMap = new LinkedHashMap<>();

    for (ScoredCandidate vc : vectorResults) {
      double normScore = normalise(vc.score(), vectorMin, vectorMax);
      Embedding vectorEmb = vc.embedding() != null ? vc.embedding() : EMPTY_EMBEDDING;
      fusedMap.put(
          vc.embeddingId(),
          new FusedEntry(vc.embeddingId(), vc.segment(), vectorEmb, alpha * normScore));
    }

    for (ScoredCandidate fc : ftsResults) {
      double normScore = normalise(fc.score(), ftsMin, ftsMax);
      double ftsContribution = (1.0 - alpha) * normScore;

      FusedEntry existing = fusedMap.get(fc.embeddingId());
      if (existing != null) {
        // Overlapping result: add FTS contribution; keep vector embedding
        fusedMap.put(
            fc.embeddingId(),
            new FusedEntry(
                existing.embeddingId,
                existing.segment,
                existing.embedding,
                existing.combinedScore + ftsContribution));
      } else {
        // FTS-only result: use empty embedding placeholder
        Embedding embedding = fc.embedding() != null ? fc.embedding() : EMPTY_EMBEDDING;
        fusedMap.put(
            fc.embeddingId(),
            new FusedEntry(fc.embeddingId(), fc.segment(), embedding, ftsContribution));
      }
    }

    // Step 4-5: Sort by combined score descending, limit to maxResults
    return fusedMap.values().stream()
        .sorted(Comparator.comparingDouble(FusedEntry::combinedScore).reversed())
        .limit(maxResults)
        .map(FusedEntry::toEmbeddingMatch)
        .toList();
  }

  /**
   * Min-max normalises a score to [0, 1]. If max == min (all scores identical), returns 1.0.
   *
   * @param score the raw score to normalise
   * @param min the minimum score in the source
   * @param max the maximum score in the source
   * @return normalised score in [0, 1]
   */
  private static double normalise(double score, double min, double max) {
    if (max == min) {
      return 1.0;
    }
    return (score - min) / (max - min);
  }

  private static double minScore(List<ScoredCandidate> candidates) {
    return candidates.stream().mapToDouble(ScoredCandidate::score).min().orElse(0.0);
  }

  private static double maxScore(List<ScoredCandidate> candidates) {
    return candidates.stream().mapToDouble(ScoredCandidate::score).max().orElse(0.0);
  }

  /** Internal holder for a fused result during combination. */
  private record FusedEntry(
      String embeddingId, TextSegment segment, Embedding embedding, double combinedScore) {

    EmbeddingMatch<TextSegment> toEmbeddingMatch() {
      return new EmbeddingMatch<>(combinedScore, embeddingId, embedding, segment);
    }
  }
}
