package fr.kalifazzia.alexandria.core.evaluation;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure Java implementation of standard information retrieval evaluation metrics.
 *
 * <p>All methods handle edge cases gracefully by returning 0.0 instead of
 * throwing exceptions or returning NaN for invalid inputs (empty lists, k=0, etc.).
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
        // Static utility class - prevent instantiation
    }

    /**
     * Calculates Precision@K: the proportion of relevant documents in the top K results.
     *
     * <p>Formula: {@code |relevant in top K| / K}
     *
     * @param retrieved ordered list of retrieved document IDs
     * @param relevant set of relevant document IDs
     * @param k number of top results to consider
     * @return precision value between 0.0 and 1.0
     */
    public static double precisionAtK(List<UUID> retrieved, Set<UUID> relevant, int k) {
        if (k <= 0 || retrieved == null || relevant == null) {
            return 0.0;
        }

        int limit = Math.min(k, retrieved.size());
        long relevantInTopK = retrieved.stream()
                .limit(limit)
                .filter(relevant::contains)
                .count();

        // Divide by k, not by limit (matches precision@k definition)
        return (double) relevantInTopK / k;
    }

    /**
     * Calculates Recall@K: the proportion of all relevant documents found in the top K results.
     *
     * <p>Formula: {@code |relevant in top K| / |all relevant|}
     *
     * @param retrieved ordered list of retrieved document IDs
     * @param relevant set of relevant document IDs
     * @param k number of top results to consider
     * @return recall value between 0.0 and 1.0
     */
    public static double recallAtK(List<UUID> retrieved, Set<UUID> relevant, int k) {
        if (relevant == null || relevant.isEmpty() || retrieved == null || k <= 0) {
            return 0.0;
        }

        int limit = Math.min(k, retrieved.size());
        long relevantInTopK = retrieved.stream()
                .limit(limit)
                .filter(relevant::contains)
                .count();

        return (double) relevantInTopK / relevant.size();
    }

    /**
     * Calculates the Reciprocal Rank: the inverse of the rank of the first relevant result.
     *
     * <p>Formula: {@code 1 / rank of first relevant result}, where rank is 1-indexed.
     * Returns 0.0 if no relevant document is found in the results.
     *
     * @param retrieved ordered list of retrieved document IDs
     * @param relevant set of relevant document IDs
     * @return reciprocal rank value between 0.0 and 1.0
     */
    public static double reciprocalRank(List<UUID> retrieved, Set<UUID> relevant) {
        if (retrieved == null || relevant == null || relevant.isEmpty()) {
            return 0.0;
        }

        for (int i = 0; i < retrieved.size(); i++) {
            if (relevant.contains(retrieved.get(i))) {
                // Rank is 1-indexed: position 0 = rank 1
                return 1.0 / (i + 1);
            }
        }

        return 0.0;
    }

    /**
     * Calculates NDCG@K: Normalized Discounted Cumulative Gain.
     *
     * <p>Uses binary relevance (1 if relevant, 0 otherwise).
     *
     * <p>DCG formula: {@code sum of rel_i / log2(i + 2)} for i from 0 to k-1
     * <p>NDCG formula: {@code DCG@K / IDCG@K}
     *
     * @param retrieved ordered list of retrieved document IDs
     * @param relevant set of relevant document IDs
     * @param k number of top results to consider
     * @return NDCG value between 0.0 and 1.0
     */
    public static double ndcgAtK(List<UUID> retrieved, Set<UUID> relevant, int k) {
        if (k <= 0 || retrieved == null || relevant == null || relevant.isEmpty()) {
            return 0.0;
        }

        int limit = Math.min(k, retrieved.size());
        double dcg = 0.0;
        int relevantFoundCount = 0;

        for (int i = 0; i < limit; i++) {
            if (relevant.contains(retrieved.get(i))) {
                // DCG: rel_i / log2(i + 2) -- position 0 uses log2(2) = 1
                dcg += 1.0 / log2(i + 2);
                relevantFoundCount++;
            }
        }

        // No relevant documents found in top k
        if (relevantFoundCount == 0) {
            return 0.0;
        }

        // IDCG: perfect ranking with all relevant documents first
        int idealLimit = Math.min(k, relevant.size());
        double idcg = 0.0;
        for (int i = 0; i < idealLimit; i++) {
            idcg += 1.0 / log2(i + 2);
        }

        // Guard against division by zero (should not happen if idealLimit > 0)
        if (idcg == 0.0) {
            return 0.0;
        }

        return dcg / idcg;
    }

    /**
     * Calculates log base 2 of a value.
     *
     * @param x the value
     * @return log base 2 of x
     */
    private static double log2(double x) {
        return Math.log(x) / Math.log(2);
    }
}
