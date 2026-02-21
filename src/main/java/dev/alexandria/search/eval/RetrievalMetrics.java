package dev.alexandria.search.eval;

import java.util.List;

/**
 * Computes standard Information Retrieval evaluation metrics.
 *
 * <p>All methods are pure functions operating on retrieved chunk IDs and relevance judgments. A
 * chunk is considered "relevant" if its grade in the judgments list is >= 1.
 */
public final class RetrievalMetrics {

  private RetrievalMetrics() {}

  /** Result record containing all six IR metrics computed at once. */
  public record MetricsResult(
      double recallAtK,
      double precisionAtK,
      double mrr,
      double ndcgAtK,
      double averagePrecision,
      double hitRate) {}

  /** Recall@k: fraction of relevant documents found in top-k results. */
  public static double recallAtK(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  /** Precision@k: fraction of top-k results that are relevant. */
  public static double precisionAtK(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  /** MRR: reciprocal rank of the first relevant result (for a single query). */
  public static double mrr(List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  /** NDCG@k: Normalized Discounted Cumulative Gain with graded relevance. */
  public static double ndcgAtK(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  /** Average Precision for a single query (component of MAP). */
  public static double averagePrecision(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  /** Hit Rate: 1.0 if at least one relevant document in top-k, else 0.0. */
  public static double hitRate(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  /** Computes all six metrics at once, avoiding redundant relevance lookups. */
  public static MetricsResult computeAll(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    throw new UnsupportedOperationException("Not yet implemented");
  }
}
