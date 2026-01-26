package fr.kalifazzia.alexandria.core.evaluation;

/**
 * Aggregated evaluation metrics for a set of queries.
 *
 * <p>Represents averaged metrics across multiple queries, either for the
 * entire evaluation or for a specific question type category.
 *
 * @param precisionAt5 average precision at K=5
 * @param precisionAt10 average precision at K=10
 * @param recallAt10 average recall at K=10
 * @param recallAt20 average recall at K=20
 * @param mrr average mean reciprocal rank
 * @param ndcgAt10 average normalized discounted cumulative gain at K=10
 * @param queryCount number of queries included in this aggregation
 * @param passed whether the metrics meet configured thresholds
 */
public record OverallMetrics(
        double precisionAt5,
        double precisionAt10,
        double recallAt10,
        double recallAt20,
        double mrr,
        double ndcgAt10,
        int queryCount,
        boolean passed
) {}
