package fr.kalifazzia.alexandria.core.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Classification of evaluation questions based on retrieval complexity.
 *
 * <p>Used to categorize golden dataset queries for targeted evaluation
 * of different retrieval strategies.
 */
public enum QuestionType {

    /**
     * Simple factual questions answered by single document lookup.
     */
    @JsonProperty("factual")
    FACTUAL,

    /**
     * Questions requiring information from multiple documents.
     */
    @JsonProperty("multi_hop")
    MULTI_HOP,

    /**
     * Questions requiring knowledge graph traversal for related documents.
     */
    @JsonProperty("graph_traversal")
    GRAPH_TRAVERSAL
}
