package fr.kalifazzia.alexandria.core.evaluation;

import fr.kalifazzia.alexandria.core.port.GoldenDatasetLoader;
import fr.kalifazzia.alexandria.core.port.SearchPort;
import fr.kalifazzia.alexandria.core.search.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service orchestrating retrieval evaluation against a golden dataset.
 *
 * <p>Loads golden queries, executes searches, computes metrics,
 * and generates evaluation reports with breakdown by question type.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);

    // Default pass thresholds
    private static final double DEFAULT_PRECISION_AT_5_THRESHOLD = 0.5;
    private static final double DEFAULT_NDCG_AT_10_THRESHOLD = 0.5;

    private final SearchPort searchPort;
    private final GoldenDatasetLoader datasetLoader;

    private double precisionAt5Threshold = DEFAULT_PRECISION_AT_5_THRESHOLD;
    private double ndcgAt10Threshold = DEFAULT_NDCG_AT_10_THRESHOLD;

    public EvaluationService(SearchPort searchPort, GoldenDatasetLoader datasetLoader) {
        this.searchPort = searchPort;
        this.datasetLoader = datasetLoader;
    }

    /**
     * Sets the Precision@5 threshold for passing evaluation.
     *
     * @param threshold minimum Precision@5 value (0.0-1.0)
     * @throws IllegalArgumentException if threshold is not in range [0.0, 1.0]
     */
    public void setPrecisionAt5Threshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException(
                    "Precision@5 threshold must be between 0.0 and 1.0, got: " + threshold);
        }
        this.precisionAt5Threshold = threshold;
    }

    /**
     * Sets the NDCG@10 threshold for passing evaluation.
     *
     * @param threshold minimum NDCG@10 value (0.0-1.0)
     * @throws IllegalArgumentException if threshold is not in range [0.0, 1.0]
     */
    public void setNdcgAt10Threshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException(
                    "NDCG@10 threshold must be between 0.0 and 1.0, got: " + threshold);
        }
        this.ndcgAt10Threshold = threshold;
    }

    /**
     * Evaluates retrieval quality against a golden dataset.
     *
     * <p>For each query in the dataset:
     * <ol>
     *   <li>Executes hybrid search with k=20 (for Recall@20)</li>
     *   <li>Extracts unique document IDs from results</li>
     *   <li>Calculates all retrieval metrics</li>
     * </ol>
     *
     * @param datasetPath path to the golden dataset JSONL file
     * @return evaluation report with overall and per-question-type metrics
     * @throws UncheckedIOException if dataset cannot be loaded
     */
    public EvaluationReport evaluate(Path datasetPath) {
        List<GoldenQuery> goldenQueries;
        try {
            goldenQueries = datasetLoader.load(datasetPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load golden dataset: " + datasetPath, e);
        }

        log.info("Evaluating {} golden queries from {}", goldenQueries.size(), datasetPath);

        if (goldenQueries.isEmpty()) {
            return new EvaluationReport(
                    new OverallMetrics(0, 0, 0, 0, 0, 0, 0, false),
                    Map.of(),
                    List.of(),
                    Instant.now()
            );
        }

        List<QueryEvaluation> evaluations = new ArrayList<>();

        for (GoldenQuery golden : goldenQueries) {
            QueryEvaluation eval = evaluateQuery(golden);
            evaluations.add(eval);
        }

        // Aggregate overall metrics
        OverallMetrics overall = aggregateMetrics(evaluations);

        // Group by question type and aggregate each group
        Map<QuestionType, OverallMetrics> byQuestionType = evaluations.stream()
                .collect(Collectors.groupingBy(QueryEvaluation::questionType))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> aggregateMetrics(e.getValue())
                ));

        log.info("Evaluation complete. Overall P@5={}, NDCG@10={}, passed={}",
                String.format("%.3f", overall.precisionAt5()),
                String.format("%.3f", overall.ndcgAt10()),
                overall.passed());

        return new EvaluationReport(overall, byQuestionType, evaluations, Instant.now());
    }

    private QueryEvaluation evaluateQuery(GoldenQuery golden) {
        // Execute search with k=20 for Recall@20
        List<SearchResult> results = searchPort.hybridSearch(golden.query(), 20);

        // Extract unique document IDs (multiple chunks may come from same doc)
        List<UUID> retrievedDocIds = results.stream()
                .map(SearchResult::documentId)
                .distinct()
                .toList();

        Set<UUID> relevant = new HashSet<>(golden.expectedDocIds());

        // Calculate all metrics
        double precisionAt5 = RetrievalMetrics.precisionAtK(retrievedDocIds, relevant, 5);
        double precisionAt10 = RetrievalMetrics.precisionAtK(retrievedDocIds, relevant, 10);
        double recallAt10 = RetrievalMetrics.recallAtK(retrievedDocIds, relevant, 10);
        double recallAt20 = RetrievalMetrics.recallAtK(retrievedDocIds, relevant, 20);
        double mrr = RetrievalMetrics.reciprocalRank(retrievedDocIds, relevant);
        double ndcgAt10 = RetrievalMetrics.ndcgAtK(retrievedDocIds, relevant, 10);

        return new QueryEvaluation(
                golden.query(),
                golden.questionType(),
                precisionAt5,
                precisionAt10,
                recallAt10,
                recallAt20,
                mrr,
                ndcgAt10,
                retrievedDocIds,
                golden.expectedDocIds()
        );
    }

    private OverallMetrics aggregateMetrics(List<QueryEvaluation> evaluations) {
        if (evaluations.isEmpty()) {
            return new OverallMetrics(0, 0, 0, 0, 0, 0, 0, false);
        }

        int count = evaluations.size();

        double avgPrecisionAt5 = evaluations.stream()
                .mapToDouble(QueryEvaluation::precisionAt5).average().orElse(0);
        double avgPrecisionAt10 = evaluations.stream()
                .mapToDouble(QueryEvaluation::precisionAt10).average().orElse(0);
        double avgRecallAt10 = evaluations.stream()
                .mapToDouble(QueryEvaluation::recallAt10).average().orElse(0);
        double avgRecallAt20 = evaluations.stream()
                .mapToDouble(QueryEvaluation::recallAt20).average().orElse(0);
        double avgMrr = evaluations.stream()
                .mapToDouble(QueryEvaluation::mrr).average().orElse(0);
        double avgNdcgAt10 = evaluations.stream()
                .mapToDouble(QueryEvaluation::ndcgAt10).average().orElse(0);

        boolean passed = avgPrecisionAt5 >= precisionAt5Threshold
                && avgNdcgAt10 >= ndcgAt10Threshold;

        return new OverallMetrics(
                avgPrecisionAt5,
                avgPrecisionAt10,
                avgRecallAt10,
                avgRecallAt20,
                avgMrr,
                avgNdcgAt10,
                count,
                passed
        );
    }
}
