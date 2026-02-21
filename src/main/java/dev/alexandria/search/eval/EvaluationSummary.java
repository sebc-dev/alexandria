package dev.alexandria.search.eval;

import java.util.List;
import java.util.Map;

/**
 * Aggregate evaluation summary with global averages, per-type breakdowns, and pass/fail status.
 *
 * @param globalRecallAt10 average Recall@10 across all queries
 * @param globalMrr average MRR across all queries
 * @param globalNdcgAt10 average NDCG@10 across all queries
 * @param globalMap average MAP (mean average precision) across all queries
 * @param globalHitRateAt10 average Hit Rate@10 across all queries
 * @param byType per-query-type metric averages
 * @param passed true if all threshold constraints are met
 * @param failedQueries list of query texts that fell below configured thresholds
 */
public record EvaluationSummary(
    double globalRecallAt10,
    double globalMrr,
    double globalNdcgAt10,
    double globalMap,
    double globalHitRateAt10,
    Map<QueryType, TypeMetrics> byType,
    boolean passed,
    List<String> failedQueries) {

  /**
   * Per-type metric averages for a subset of evaluation queries.
   *
   * @param count number of queries in this type
   * @param recallAt10 average Recall@10 for this type
   * @param mrr average MRR for this type
   * @param ndcgAt10 average NDCG@10 for this type
   */
  public record TypeMetrics(int count, double recallAt10, double mrr, double ndcgAt10) {}
}
