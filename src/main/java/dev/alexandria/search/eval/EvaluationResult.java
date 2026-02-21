package dev.alexandria.search.eval;

import java.util.List;

/**
 * Per-query evaluation result containing retrieved chunks and computed IR metrics.
 *
 * @param query the original search query
 * @param queryType the classification of the query intent
 * @param chunkResults the list of retrieved chunks with scores and relevance grades
 * @param recallAt5 recall at depth 5
 * @param recallAt10 recall at depth 10
 * @param recallAt20 recall at depth 20
 * @param precisionAt5 precision at depth 5
 * @param precisionAt10 precision at depth 10
 * @param precisionAt20 precision at depth 20
 * @param mrr mean reciprocal rank
 * @param ndcgAt5 normalized discounted cumulative gain at depth 5
 * @param ndcgAt10 normalized discounted cumulative gain at depth 10
 * @param ndcgAt20 normalized discounted cumulative gain at depth 20
 * @param averagePrecision average precision for this query
 * @param hitRateAt5 hit rate at depth 5 (1.0 if any relevant in top-5, else 0.0)
 * @param hitRateAt10 hit rate at depth 10
 * @param hitRateAt20 hit rate at depth 20
 */
public record EvaluationResult(
    String query,
    QueryType queryType,
    List<ChunkResult> chunkResults,
    double recallAt5,
    double recallAt10,
    double recallAt20,
    double precisionAt5,
    double precisionAt10,
    double precisionAt20,
    double mrr,
    double ndcgAt5,
    double ndcgAt10,
    double ndcgAt20,
    double averagePrecision,
    double hitRateAt5,
    double hitRateAt10,
    double hitRateAt20) {

  /**
   * A single retrieved chunk with its search score, rank position, and ground-truth relevance.
   *
   * @param chunkId the identifier of the retrieved chunk
   * @param score the retrieval score assigned by the search system
   * @param rank the 1-based rank position in the result list
   * @param relevanceGrade the ground-truth relevance grade (0, 1, or 2)
   */
  public record ChunkResult(String chunkId, double score, int rank, int relevanceGrade) {}
}
