package dev.alexandria.search.eval;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Exports evaluation results to CSV files for tracking metric trends across pipeline changes.
 *
 * <p>Produces two CSV files per export: an aggregate CSV with global and per-type metric averages,
 * and a detailed CSV with per-query chunk-level results.
 */
@Service
public class EvaluationExporter {

  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");

  private static final String AGGREGATE_HEADER =
      "query_type,count,recall_at_5,recall_at_10,recall_at_20,"
          + "precision_at_5,precision_at_10,precision_at_20,"
          + "mrr,ndcg_at_5,ndcg_at_10,ndcg_at_20,map,"
          + "hit_rate_at_5,hit_rate_at_10,hit_rate_at_20";

  private static final String DETAILED_HEADER =
      "query,query_type,chunk_id,score,rank,relevance_grade," + "recall_at_10,mrr,ndcg_at_10";

  private final Path outputDir;
  private final Clock clock;

  public EvaluationExporter(
      @Value("${alexandria.eval.output-dir:${user.home}/.alexandria/eval}") String outputDir,
      Clock clock) {
    this.outputDir = Path.of(outputDir);
    this.clock = clock;
  }

  /**
   * Exports evaluation results to aggregate and detailed CSV files.
   *
   * @param results the per-query evaluation results
   * @param label a descriptive label included in the filename (e.g., "baseline", "post-chunking")
   * @return the paths to the two generated CSV files (aggregate first, detailed second)
   * @throws IOException if file writing fails
   */
  public List<Path> export(List<EvaluationResult> results, String label) throws IOException {
    Files.createDirectories(outputDir);

    String timestamp = LocalDateTime.now(clock).format(TIMESTAMP_FORMAT);
    Path aggregatePath = outputDir.resolve("eval-aggregate-%s-%s.csv".formatted(timestamp, label));
    Path detailedPath = outputDir.resolve("eval-detailed-%s-%s.csv".formatted(timestamp, label));

    writeAggregateCsv(results, aggregatePath);
    writeDetailedCsv(results, detailedPath);

    return List.of(aggregatePath, detailedPath);
  }

  private void writeAggregateCsv(List<EvaluationResult> results, Path path) throws IOException {
    Map<QueryType, List<EvaluationResult>> byType = groupByType(results);

    try (BufferedWriter writer = Files.newBufferedWriter(path)) {
      writer.write(AGGREGATE_HEADER);
      writer.newLine();

      for (QueryType type : QueryType.values()) {
        List<EvaluationResult> typeResults = byType.getOrDefault(type, List.of());
        if (!typeResults.isEmpty()) {
          writeAggregateRow(writer, type.name(), typeResults);
        }
      }

      writeAggregateRow(writer, "GLOBAL", results);
    }
  }

  private void writeAggregateRow(
      BufferedWriter writer, String label, List<EvaluationResult> results) throws IOException {
    int count = results.size();
    writer.write(
        String.format(
            Locale.US,
            "%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f",
            label,
            count,
            avg(results, EvaluationResult::recallAt5),
            avg(results, EvaluationResult::recallAt10),
            avg(results, EvaluationResult::recallAt20),
            avg(results, EvaluationResult::precisionAt5),
            avg(results, EvaluationResult::precisionAt10),
            avg(results, EvaluationResult::precisionAt20),
            avg(results, EvaluationResult::mrr),
            avg(results, EvaluationResult::ndcgAt5),
            avg(results, EvaluationResult::ndcgAt10),
            avg(results, EvaluationResult::ndcgAt20),
            avg(results, EvaluationResult::averagePrecision),
            avg(results, EvaluationResult::hitRateAt5),
            avg(results, EvaluationResult::hitRateAt10),
            avg(results, EvaluationResult::hitRateAt20)));
    writer.newLine();
  }

  private void writeDetailedCsv(List<EvaluationResult> results, Path path) throws IOException {
    try (BufferedWriter writer = Files.newBufferedWriter(path)) {
      writer.write(DETAILED_HEADER);
      writer.newLine();

      for (EvaluationResult result : results) {
        List<EvaluationResult.ChunkResult> chunks = result.chunkResults();
        boolean firstChunk = true;
        for (EvaluationResult.ChunkResult chunk : chunks) {
          writer.write(
              String.format(
                  Locale.US,
                  "%s,%s,%s,%.4f,%d,%d,%s,%s,%s",
                  escapeCsv(result.query()),
                  result.queryType().name(),
                  escapeCsv(chunk.chunkId()),
                  chunk.score(),
                  chunk.rank(),
                  chunk.relevanceGrade(),
                  firstChunk ? String.format(Locale.US, "%.4f", result.recallAt10()) : "",
                  firstChunk ? String.format(Locale.US, "%.4f", result.mrr()) : "",
                  firstChunk ? String.format(Locale.US, "%.4f", result.ndcgAt10()) : ""));
          writer.newLine();
          firstChunk = false;
        }
      }
    }
  }

  private static Map<QueryType, List<EvaluationResult>> groupByType(
      List<EvaluationResult> results) {
    Map<QueryType, List<EvaluationResult>> map = new EnumMap<>(QueryType.class);
    for (EvaluationResult r : results) {
      map.computeIfAbsent(r.queryType(), k -> new java.util.ArrayList<>()).add(r);
    }
    return map;
  }

  private static double avg(
      List<EvaluationResult> results, java.util.function.ToDoubleFunction<EvaluationResult> fn) {
    if (results.isEmpty()) {
      return 0.0;
    }
    return results.stream().mapToDouble(fn).average().orElse(0.0);
  }

  private static String escapeCsv(String value) {
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
