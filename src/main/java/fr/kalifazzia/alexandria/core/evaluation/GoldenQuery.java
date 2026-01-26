package fr.kalifazzia.alexandria.core.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Golden dataset entry for retrieval evaluation.
 *
 * <p>Represents a query with its expected documents and metadata
 * for measuring retrieval quality metrics (precision, recall, MRR, nDCG).
 *
 * @param query the search query text
 * @param expectedDocIds expected document IDs that should be retrieved
 * @param requiresKg whether this query requires knowledge graph for optimal retrieval
 * @param reasoningHops number of reasoning steps needed to answer
 * @param questionType classification of the question complexity
 */
public record GoldenQuery(
        String query,
        @JsonProperty("expected_doc_ids") List<UUID> expectedDocIds,
        @JsonProperty("requires_kg") boolean requiresKg,
        @JsonProperty("reasoning_hops") int reasoningHops,
        @JsonProperty("question_type") QuestionType questionType
) {

    /**
     * Compact constructor with validation.
     */
    public GoldenQuery {
        if (query == null) {
            throw new IllegalArgumentException("query cannot be null");
        }
        if (expectedDocIds == null || expectedDocIds.isEmpty()) {
            throw new IllegalArgumentException("expectedDocIds cannot be null or empty");
        }
        if (questionType == null) {
            throw new IllegalArgumentException("questionType cannot be null");
        }
        if (reasoningHops < 0) {
            throw new IllegalArgumentException("reasoningHops cannot be negative");
        }
        expectedDocIds = List.copyOf(expectedDocIds);
    }
}
