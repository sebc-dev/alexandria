package fr.kalifazzia.alexandria.api.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.kalifazzia.alexandria.core.evaluation.*;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * CLI commands for retrieval evaluation against golden datasets.
 */
@Command(group = "Evaluation")
@Component
public class EvaluationCommands {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final EvaluationService evaluationService;
    private final ObjectMapper objectMapper;

    public EvaluationCommands(EvaluationService evaluationService, ObjectMapper objectMapper) {
        this.evaluationService = evaluationService;
        // Configure ObjectMapper for pretty-printing and Java 8 time support
        this.objectMapper = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Evaluate retrieval quality against a golden dataset.
     *
     * @param datasetPath Path to golden dataset JSONL file
     * @param outputFormat Output format: table or json
     * @return Formatted evaluation report
     */
    @Command(command = "evaluate", description = "Evaluate retrieval against golden dataset")
    public String evaluate(
            @Option(longNames = "dataset", shortNames = 'd', required = true,
                    description = "Path to golden dataset JSONL file")
            String datasetPath,
            @Option(longNames = "output", shortNames = 'o', defaultValue = "table",
                    description = "Output format: table or json")
            String outputFormat
    ) {
        Path path = Path.of(datasetPath).toAbsolutePath();

        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Dataset file does not exist: " + path);
        }
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Dataset path is not a regular file: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("Dataset file is not readable: " + path);
        }

        EvaluationReport report = evaluationService.evaluate(path);

        if (report.details().isEmpty()) {
            return "No queries in dataset";
        }

        return switch (outputFormat.toLowerCase()) {
            case "json" -> formatJson(report);
            case "table" -> formatTable(report);
            default -> throw new IllegalArgumentException(
                    "Invalid output format: " + outputFormat + ". Use 'table' or 'json'");
        };
    }

    private String formatJson(EvaluationReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize report to JSON", e);
        }
    }

    private String formatTable(EvaluationReport report) {
        StringBuilder sb = new StringBuilder();
        OverallMetrics overall = report.overall();

        sb.append("Evaluation Report\n");
        sb.append("=================\n");
        sb.append(String.format("Evaluated at: %s\n", DATE_FORMAT.format(report.evaluatedAt())));
        sb.append(String.format("Queries: %d\n\n", overall.queryCount()));

        // Overall metrics
        sb.append("Overall Metrics:\n");
        sb.append(String.format("  Precision@5:  %.3f  %s\n",
                overall.precisionAt5(), passIndicator(overall.precisionAt5() >= 0.5)));
        sb.append(String.format("  Precision@10: %.3f\n", overall.precisionAt10()));
        sb.append(String.format("  Recall@10:    %.3f\n", overall.recallAt10()));
        sb.append(String.format("  Recall@20:    %.3f\n", overall.recallAt20()));
        sb.append(String.format("  MRR:          %.3f\n", overall.mrr()));
        sb.append(String.format("  NDCG@10:      %.3f  %s\n",
                overall.ndcgAt10(), passIndicator(overall.ndcgAt10() >= 0.5)));

        // By question type
        if (!report.byQuestionType().isEmpty()) {
            sb.append("\nBy Question Type:\n");

            // Process in consistent order
            for (QuestionType type : QuestionType.values()) {
                OverallMetrics metrics = report.byQuestionType().get(type);
                if (metrics != null) {
                    sb.append(String.format("  %s (%d queries):\n",
                            formatQuestionType(type), metrics.queryCount()));
                    sb.append(String.format("    P@5: %.3f  R@10: %.3f  MRR: %.3f  NDCG@10: %.3f\n",
                            metrics.precisionAt5(),
                            metrics.recallAt10(),
                            metrics.mrr(),
                            metrics.ndcgAt10()));
                }
            }
        }

        return sb.toString();
    }

    private String passIndicator(boolean pass) {
        return pass ? "[PASS]" : "[FAIL]";
    }

    private String formatQuestionType(QuestionType type) {
        return switch (type) {
            case FACTUAL -> "FACTUAL";
            case MULTI_HOP -> "MULTI_HOP";
            case GRAPH_TRAVERSAL -> "GRAPH_TRAVERSAL";
        };
    }
}
