package fr.kalifazzia.alexandria.core.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Complete evaluation report with overall metrics and breakdown by question type.
 *
 * <p>Captures the full evaluation outcome including:
 * <ul>
 *   <li>Overall aggregated metrics across all queries</li>
 *   <li>Breakdown by question type (FACTUAL, MULTI_HOP, GRAPH_TRAVERSAL)</li>
 *   <li>Detailed per-query results for debugging</li>
 *   <li>Timestamp of when evaluation was performed</li>
 * </ul>
 *
 * @param overall aggregated metrics across all queries
 * @param byQuestionType metrics broken down by question type
 * @param details per-query evaluation results
 * @param evaluatedAt timestamp of evaluation
 */
public record EvaluationReport(
        OverallMetrics overall,
        Map<QuestionType, OverallMetrics> byQuestionType,
        List<QueryEvaluation> details,
        Instant evaluatedAt
) {

    /**
     * Compact constructor with defensive copies for collections.
     */
    public EvaluationReport {
        byQuestionType = byQuestionType != null
                ? Map.copyOf(byQuestionType)
                : Map.of();
        details = details != null ? List.copyOf(details) : List.of();
    }
}
