package dev.alexandria.search.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.alexandria.search.SearchRequest;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Orchestrates retrieval evaluation: loads the golden set, executes queries against the live index,
 * computes IR metrics at multiple depths, and exports results to CSV.
 *
 * <p>This service ties together {@link SearchService} for query execution, {@link RetrievalMetrics}
 * for metric computation, and {@link EvaluationExporter} for CSV output.
 */
@Service
public class RetrievalEvaluationService {

  private static final Logger log = LoggerFactory.getLogger(RetrievalEvaluationService.class);

  /** Maximum search depth needed (k=20 is the deepest metric we compute). */
  private static final int MAX_SEARCH_DEPTH = 20;

  private static final String GOLDEN_SET_PATH = "eval/golden-set.json";

  private final SearchService searchService;
  private final EvaluationExporter evaluationExporter;
  private final ObjectMapper objectMapper;
  private final double recallThreshold;
  private final double mrrThreshold;

  public RetrievalEvaluationService(
      SearchService searchService,
      EvaluationExporter evaluationExporter,
      ObjectMapper objectMapper,
      @Value("${alexandria.eval.thresholds.recall-at-10:0.70}") double recallThreshold,
      @Value("${alexandria.eval.thresholds.mrr:0.60}") double mrrThreshold) {
    this.searchService = searchService;
    this.evaluationExporter = evaluationExporter;
    this.objectMapper = objectMapper;
    this.recallThreshold = recallThreshold;
    this.mrrThreshold = mrrThreshold;
  }

  /**
   * Runs evaluation against the live search index using the golden set.
   *
   * @param label a descriptive label for this evaluation run (e.g. "baseline", "post-reranking")
   * @return summary with global/per-type metrics and pass/fail status
   * @throws IOException if golden set loading or CSV export fails
   */
  public EvaluationSummary evaluate(String label) throws IOException {
    List<GoldenSetEntry> goldenSet = loadGoldenSet();
    log.info("Loaded golden set with {} queries", goldenSet.size());

    List<EvaluationResult> results = new ArrayList<>(goldenSet.size());

    for (GoldenSetEntry entry : goldenSet) {
      EvaluationResult result = evaluateQuery(entry);
      results.add(result);
    }

    evaluationExporter.export(results, label);
    log.info("Exported evaluation results for label '{}'", label);

    return buildSummary(results);
  }

  private List<GoldenSetEntry> loadGoldenSet() throws IOException {
    ClassPathResource resource = new ClassPathResource(GOLDEN_SET_PATH);
    try (InputStream is = resource.getInputStream()) {
      return objectMapper.readValue(is, new TypeReference<List<GoldenSetEntry>>() {});
    }
  }

  private EvaluationResult evaluateQuery(GoldenSetEntry entry) {
    List<SearchResult> searchResults =
        searchService.search(new SearchRequest(entry.query(), MAX_SEARCH_DEPTH));

    List<String> retrievedIds = new ArrayList<>(searchResults.size());
    List<EvaluationResult.ChunkResult> chunkResults = new ArrayList<>(searchResults.size());

    for (int i = 0; i < searchResults.size(); i++) {
      SearchResult sr = searchResults.get(i);
      String chunkId = buildChunkId(sr);
      retrievedIds.add(chunkId);

      int relevanceGrade = findRelevanceGrade(chunkId, entry.judgments());
      chunkResults.add(
          new EvaluationResult.ChunkResult(chunkId, sr.score(), i + 1, relevanceGrade));
    }

    RetrievalMetrics.MetricsResult at5 =
        RetrievalMetrics.computeAll(retrievedIds, entry.judgments(), 5);
    RetrievalMetrics.MetricsResult at10 =
        RetrievalMetrics.computeAll(retrievedIds, entry.judgments(), 10);
    RetrievalMetrics.MetricsResult at20 =
        RetrievalMetrics.computeAll(retrievedIds, entry.judgments(), 20);

    return new EvaluationResult(
        entry.query(),
        entry.queryType(),
        chunkResults,
        at5.recallAtK(),
        at10.recallAtK(),
        at20.recallAtK(),
        at5.precisionAtK(),
        at10.precisionAtK(),
        at20.precisionAtK(),
        at10.mrr(),
        at5.ndcgAtK(),
        at10.ndcgAtK(),
        at20.ndcgAtK(),
        at10.averagePrecision(),
        at5.hitRate(),
        at10.hitRate(),
        at20.hitRate());
  }

  /**
   * Constructs a chunk identifier from search result metadata for matching against golden set
   * judgments. Uses {@code sourceUrl + "#" + sectionPath} format.
   */
  private static String buildChunkId(SearchResult sr) {
    return sr.sourceUrl() + "#" + sr.sectionPath();
  }

  /**
   * Finds the relevance grade for a retrieved chunk by matching against golden set judgments.
   * Attempts exact match first, then falls back to substring matching on the chunk ID.
   */
  private static int findRelevanceGrade(
      String retrievedChunkId, List<RelevanceJudgment> judgments) {
    // Exact match first
    for (RelevanceJudgment judgment : judgments) {
      if (judgment.chunkId().equals(retrievedChunkId)) {
        return judgment.grade();
      }
    }
    // Substring match: golden set ID is substring of retrieved, or vice versa
    for (RelevanceJudgment judgment : judgments) {
      if (retrievedChunkId.contains(judgment.chunkId())
          || judgment.chunkId().contains(retrievedChunkId)) {
        log.debug(
            "Substring match: retrieved='{}' matched golden='{}'",
            retrievedChunkId,
            judgment.chunkId());
        return judgment.grade();
      }
    }
    return 0;
  }

  private EvaluationSummary buildSummary(List<EvaluationResult> results) {
    double globalRecallAt10 = avg(results, EvaluationResult::recallAt10);
    double globalMrr = avg(results, EvaluationResult::mrr);
    double globalNdcgAt10 = avg(results, EvaluationResult::ndcgAt10);
    double globalMap = avg(results, EvaluationResult::averagePrecision);
    double globalHitRateAt10 = avg(results, EvaluationResult::hitRateAt10);

    Map<QueryType, EvaluationSummary.TypeMetrics> byType = buildTypeMetrics(results);

    List<String> failedQueries = new ArrayList<>();
    for (EvaluationResult result : results) {
      if (result.recallAt10() < recallThreshold || result.mrr() < mrrThreshold) {
        failedQueries.add(result.query());
      }
    }

    boolean passed = globalRecallAt10 >= recallThreshold && globalMrr >= mrrThreshold;

    log.info(
        "Evaluation complete: recall@10={}, mrr={}, ndcg@10={}, map={}, hitRate@10={}, passed={}",
        globalRecallAt10,
        globalMrr,
        globalNdcgAt10,
        globalMap,
        globalHitRateAt10,
        passed);

    return new EvaluationSummary(
        globalRecallAt10,
        globalMrr,
        globalNdcgAt10,
        globalMap,
        globalHitRateAt10,
        byType,
        passed,
        failedQueries);
  }

  private static Map<QueryType, EvaluationSummary.TypeMetrics> buildTypeMetrics(
      List<EvaluationResult> results) {
    Map<QueryType, List<EvaluationResult>> grouped = new EnumMap<>(QueryType.class);
    for (EvaluationResult r : results) {
      grouped.computeIfAbsent(r.queryType(), k -> new ArrayList<>()).add(r);
    }

    Map<QueryType, EvaluationSummary.TypeMetrics> byType = new EnumMap<>(QueryType.class);
    for (Map.Entry<QueryType, List<EvaluationResult>> entry : grouped.entrySet()) {
      List<EvaluationResult> typeResults = entry.getValue();
      byType.put(
          entry.getKey(),
          new EvaluationSummary.TypeMetrics(
              typeResults.size(),
              avg(typeResults, EvaluationResult::recallAt10),
              avg(typeResults, EvaluationResult::mrr),
              avg(typeResults, EvaluationResult::ndcgAt10)));
    }
    return byType;
  }

  private static double avg(
      List<EvaluationResult> results, java.util.function.ToDoubleFunction<EvaluationResult> fn) {
    if (results.isEmpty()) {
      return 0.0;
    }
    return results.stream().mapToDouble(fn).average().orElse(0.0);
  }
}
