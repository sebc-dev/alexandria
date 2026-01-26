package fr.kalifazzia.alexandria.core.evaluation;

import java.util.List;
import java.util.UUID;

/**
 * Per-query evaluation result containing all computed retrieval metrics.
 *
 * <p>Captures the detailed evaluation outcome for a single query from the
 * golden dataset, including all standard IR metrics and the actual
 * retrieved vs expected document IDs for debugging.
 *
 * @param query the original query text
 * @param questionType classification of the query complexity
 * @param precisionAt5 precision at K=5
 * @param precisionAt10 precision at K=10
 * @param recallAt10 recall at K=10
 * @param recallAt20 recall at K=20
 * @param mrr mean reciprocal rank
 * @param ndcgAt10 normalized discounted cumulative gain at K=10
 * @param retrievedDocIds document IDs returned by search (for debugging)
 * @param expectedDocIds expected document IDs from golden dataset
 */
public record QueryEvaluation(
        String query,
        QuestionType questionType,
        double precisionAt5,
        double precisionAt10,
        double recallAt10,
        double recallAt20,
        double mrr,
        double ndcgAt10,
        List<UUID> retrievedDocIds,
        List<UUID> expectedDocIds
) {

    /**
     * Compact constructor with defensive copies for collections.
     */
    public QueryEvaluation {
        retrievedDocIds = retrievedDocIds != null ? List.copyOf(retrievedDocIds) : List.of();
        expectedDocIds = expectedDocIds != null ? List.copyOf(expectedDocIds) : List.of();
    }
}
