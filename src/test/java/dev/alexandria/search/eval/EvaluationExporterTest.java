package dev.alexandria.search.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway.Init")
class EvaluationExporterTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-02-21T14:30:00Z"), ZoneId.of("UTC"));

  @TempDir Path tempDir;

  @Test
  void export_creates_aggregate_csv_with_correct_format() throws IOException {
    EvaluationExporter exporter = new EvaluationExporter(tempDir.toString(), FIXED_CLOCK);
    List<EvaluationResult> results = List.of(sampleFactualResult(), sampleCodeLookupResult());

    List<Path> paths = exporter.export(results, "baseline");

    Path aggregatePath = paths.get(0);
    List<String> lines = Files.readAllLines(aggregatePath);
    assertThat(lines.get(0))
        .isEqualTo(
            "query_type,count,recall_at_5,recall_at_10,recall_at_20,"
                + "precision_at_5,precision_at_10,precision_at_20,"
                + "mrr,ndcg_at_5,ndcg_at_10,ndcg_at_20,map,"
                + "hit_rate_at_5,hit_rate_at_10,hit_rate_at_20");
    assertThat(lines).hasSize(4); // header + FACTUAL + CODE_LOOKUP + GLOBAL
    assertThat(lines.get(1)).startsWith("FACTUAL,1,");
    assertThat(lines.get(2)).startsWith("CODE_LOOKUP,1,");
    assertThat(lines.get(3)).startsWith("GLOBAL,2,");
  }

  @Test
  void export_creates_detailed_csv_with_per_query_chunks() throws IOException {
    EvaluationExporter exporter = new EvaluationExporter(tempDir.toString(), FIXED_CLOCK);
    List<EvaluationResult> results = List.of(sampleFactualResult());

    List<Path> paths = exporter.export(results, "test");

    Path detailedPath = paths.get(1);
    List<String> lines = Files.readAllLines(detailedPath);
    assertThat(lines.get(0))
        .isEqualTo(
            "query,query_type,chunk_id,score,rank,relevance_grade,recall_at_10,mrr,ndcg_at_10");
    // 2 chunk results + header
    assertThat(lines).hasSize(3);
    // First chunk row includes per-query metrics
    assertThat(lines.get(1)).contains("FACTUAL").contains("0.8000").contains("chunk-a");
    // Second chunk row has empty per-query metrics
    assertThat(lines.get(2)).endsWith(",,");
  }

  @Test
  void filenames_contain_timestamp_and_label() throws IOException {
    EvaluationExporter exporter = new EvaluationExporter(tempDir.toString(), FIXED_CLOCK);

    List<Path> paths = exporter.export(List.of(sampleFactualResult()), "my-label");

    assertThat(paths.get(0).getFileName().toString())
        .isEqualTo("eval-aggregate-2026-02-21T14-30-00-my-label.csv");
    assertThat(paths.get(1).getFileName().toString())
        .isEqualTo("eval-detailed-2026-02-21T14-30-00-my-label.csv");
  }

  @Test
  void export_creates_directory_when_it_does_not_exist() throws IOException {
    Path nestedDir = tempDir.resolve("nested").resolve("eval");
    EvaluationExporter exporter = new EvaluationExporter(nestedDir.toString(), FIXED_CLOCK);

    List<Path> paths = exporter.export(List.of(sampleFactualResult()), "test");

    assertThat(paths.get(0)).exists();
    assertThat(paths.get(1)).exists();
    assertThat(nestedDir).isDirectory();
  }

  @Test
  void aggregate_csv_values_are_formatted_to_four_decimal_places() throws IOException {
    EvaluationExporter exporter = new EvaluationExporter(tempDir.toString(), FIXED_CLOCK);

    List<Path> paths = exporter.export(List.of(sampleFactualResult()), "precision-test");

    List<String> lines = Files.readAllLines(paths.get(0));
    // FACTUAL row: count=1, all metrics should have 4 decimal places
    String factualRow = lines.get(1);
    String[] fields = factualRow.split(",");
    // Check recall_at_5 (index 2) has 4 decimal places
    assertThat(fields[2]).matches("\\d+\\.\\d{4}");
    // Check mrr (index 8) has 4 decimal places
    assertThat(fields[8]).matches("\\d+\\.\\d{4}");
  }

  @Test
  void aggregate_csv_computes_correct_averages_across_results() throws IOException {
    EvaluationExporter exporter = new EvaluationExporter(tempDir.toString(), FIXED_CLOCK);
    EvaluationResult r1 = sampleFactualResult(); // mrr=1.0
    EvaluationResult r2 =
        new EvaluationResult(
            "another factual query",
            QueryType.FACTUAL,
            List.of(new EvaluationResult.ChunkResult("chunk-x", 0.5, 1, 0)),
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0,
            0.0);

    List<Path> paths = exporter.export(List.of(r1, r2), "avg-test");

    List<String> lines = Files.readAllLines(paths.get(0));
    String factualRow = lines.get(1);
    // FACTUAL: 2 results, average of mrr(1.0, 0.0) = 0.5
    assertThat(factualRow).startsWith("FACTUAL,2,");
    String[] fields = factualRow.split(",");
    assertThat(fields[8]).isEqualTo("0.5000"); // mrr average
  }

  @Test
  void detailed_csv_escapes_queries_containing_commas() throws IOException {
    EvaluationExporter exporter = new EvaluationExporter(tempDir.toString(), FIXED_CLOCK);
    EvaluationResult result =
        new EvaluationResult(
            "Spring Boot, the framework",
            QueryType.FACTUAL,
            List.of(new EvaluationResult.ChunkResult("chunk-a", 0.9, 1, 2)),
            0.8,
            0.9,
            1.0,
            0.4,
            0.3,
            0.2,
            1.0,
            0.7,
            0.8,
            0.9,
            0.6,
            1.0,
            1.0,
            1.0);

    List<Path> paths = exporter.export(List.of(result), "escape-test");

    List<String> lines = Files.readAllLines(paths.get(1));
    assertThat(lines.get(1)).startsWith("\"Spring Boot, the framework\"");
  }

  private static EvaluationResult sampleFactualResult() {
    return new EvaluationResult(
        "What is the default server in Spring Boot",
        QueryType.FACTUAL,
        List.of(
            new EvaluationResult.ChunkResult("chunk-a", 0.95, 1, 2),
            new EvaluationResult.ChunkResult("chunk-b", 0.80, 2, 1)),
        0.8,
        0.9,
        1.0,
        0.4,
        0.3,
        0.2,
        1.0,
        0.7,
        0.8,
        0.9,
        0.6,
        1.0,
        1.0,
        1.0);
  }

  private static EvaluationResult sampleCodeLookupResult() {
    return new EvaluationResult(
        "How to create a REST controller",
        QueryType.CODE_LOOKUP,
        List.of(
            new EvaluationResult.ChunkResult("chunk-c", 0.85, 1, 2),
            new EvaluationResult.ChunkResult("chunk-d", 0.70, 2, 1),
            new EvaluationResult.ChunkResult("chunk-e", 0.50, 3, 0)),
        0.6,
        0.8,
        1.0,
        0.3,
        0.2,
        0.15,
        0.8,
        0.5,
        0.6,
        0.7,
        0.5,
        1.0,
        1.0,
        1.0);
  }
}
