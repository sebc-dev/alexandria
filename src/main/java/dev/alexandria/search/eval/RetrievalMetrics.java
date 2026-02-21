package dev.alexandria.search.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Computes standard Information Retrieval evaluation metrics.
 *
 * <p>All methods are pure functions operating on retrieved chunk IDs and relevance judgments. A
 * chunk is considered "relevant" if its grade in the judgments list is >= 1.
 */
public final class RetrievalMetrics {

  private RetrievalMetrics() {}

  /** Result record containing all six IR metrics computed at once. */
  public record MetricsResult(
      double recallAtK,
      double precisionAtK,
      double mrr,
      double ndcgAtK,
      double averagePrecision,
      double hitRate) {}

  /** Recall@k: fraction of relevant documents found in top-k results. */
  public static double recallAtK(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    Map<String, Integer> gradeMap = toGradeMap(judgments);
    Set<String> relevantIds = relevantIds(gradeMap);
    if (relevantIds.isEmpty()) {
      return 0.0;
    }
    List<String> topK = truncate(retrievedIds, k);
    long found = topK.stream().filter(relevantIds::contains).count();
    return (double) found / relevantIds.size();
  }

  /** Precision@k: fraction of top-k results that are relevant. */
  public static double precisionAtK(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    Map<String, Integer> gradeMap = toGradeMap(judgments);
    Set<String> relevantIds = relevantIds(gradeMap);
    List<String> topK = truncate(retrievedIds, k);
    if (topK.isEmpty()) {
      return 0.0;
    }
    long found = topK.stream().filter(relevantIds::contains).count();
    return (double) found / topK.size();
  }

  /** MRR: reciprocal rank of the first relevant result (for a single query). */
  public static double mrr(List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    Map<String, Integer> gradeMap = toGradeMap(judgments);
    Set<String> relevantIds = relevantIds(gradeMap);
    List<String> topK = truncate(retrievedIds, k);
    for (int i = 0; i < topK.size(); i++) {
      if (relevantIds.contains(topK.get(i))) {
        return 1.0 / (i + 1);
      }
    }
    return 0.0;
  }

  /** NDCG@k: Normalized Discounted Cumulative Gain with graded relevance. */
  public static double ndcgAtK(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    Map<String, Integer> gradeMap = toGradeMap(judgments);
    List<String> topK = truncate(retrievedIds, k);

    double dcg = computeDcg(topK, gradeMap);
    double idcg = computeIdcg(gradeMap, topK.size());

    if (idcg == 0.0) {
      return 0.0;
    }
    return dcg / idcg;
  }

  /** Average Precision for a single query (component of MAP). */
  public static double averagePrecision(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    Map<String, Integer> gradeMap = toGradeMap(judgments);
    Set<String> relevantIds = relevantIds(gradeMap);
    if (relevantIds.isEmpty()) {
      return 0.0;
    }
    List<String> topK = truncate(retrievedIds, k);
    double sumPrecision = 0.0;
    int relevantFound = 0;
    for (int i = 0; i < topK.size(); i++) {
      if (relevantIds.contains(topK.get(i))) {
        relevantFound++;
        sumPrecision += (double) relevantFound / (i + 1);
      }
    }
    return relevantFound == 0 ? 0.0 : sumPrecision / relevantIds.size();
  }

  /** Hit Rate: 1.0 if at least one relevant document in top-k, else 0.0. */
  public static double hitRate(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    Map<String, Integer> gradeMap = toGradeMap(judgments);
    Set<String> relevantIds = relevantIds(gradeMap);
    List<String> topK = truncate(retrievedIds, k);
    for (String id : topK) {
      if (relevantIds.contains(id)) {
        return 1.0;
      }
    }
    return 0.0;
  }

  /** Computes all six metrics at once, avoiding redundant relevance lookups. */
  public static MetricsResult computeAll(
      List<String> retrievedIds, List<RelevanceJudgment> judgments, int k) {
    Map<String, Integer> gradeMap = toGradeMap(judgments);
    Set<String> relevantIds = relevantIds(gradeMap);
    List<String> topK = truncate(retrievedIds, k);

    // Recall@k
    double recall =
        relevantIds.isEmpty()
            ? 0.0
            : (double) topK.stream().filter(relevantIds::contains).count() / relevantIds.size();

    // Precision@k
    double precision =
        topK.isEmpty()
            ? 0.0
            : (double) topK.stream().filter(relevantIds::contains).count() / topK.size();

    // MRR
    double mrrValue = 0.0;
    for (int i = 0; i < topK.size(); i++) {
      if (relevantIds.contains(topK.get(i))) {
        mrrValue = 1.0 / (i + 1);
        break;
      }
    }

    // NDCG@k
    double dcg = computeDcg(topK, gradeMap);
    double idcg = computeIdcg(gradeMap, topK.size());
    double ndcg = idcg == 0.0 ? 0.0 : dcg / idcg;

    // Average Precision
    double sumPrecision = 0.0;
    int relevantFound = 0;
    for (int i = 0; i < topK.size(); i++) {
      if (relevantIds.contains(topK.get(i))) {
        relevantFound++;
        sumPrecision += (double) relevantFound / (i + 1);
      }
    }
    double ap =
        (relevantIds.isEmpty() || relevantFound == 0) ? 0.0 : sumPrecision / relevantIds.size();

    // Hit Rate
    double hit = topK.stream().anyMatch(relevantIds::contains) ? 1.0 : 0.0;

    return new MetricsResult(recall, precision, mrrValue, ndcg, ap, hit);
  }

  // --- Internal helpers ---

  private static Map<String, Integer> toGradeMap(List<RelevanceJudgment> judgments) {
    return judgments.stream()
        .collect(Collectors.toMap(RelevanceJudgment::chunkId, RelevanceJudgment::grade));
  }

  private static Set<String> relevantIds(Map<String, Integer> gradeMap) {
    return gradeMap.entrySet().stream()
        .filter(e -> e.getValue() >= 1)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  private static List<String> truncate(List<String> ids, int k) {
    return ids.subList(0, Math.min(k, ids.size()));
  }

  private static double computeDcg(List<String> topK, Map<String, Integer> gradeMap) {
    double dcg = 0.0;
    for (int i = 0; i < topK.size(); i++) {
      int grade = gradeMap.getOrDefault(topK.get(i), 0);
      dcg += grade / log2(i + 2); // log2(i+2) because rank is 1-based: log2(1+1), log2(2+1), ...
    }
    return dcg;
  }

  private static double computeIdcg(Map<String, Integer> gradeMap, int k) {
    // Sort all grades descending, take top-k
    List<Integer> sortedGrades = new ArrayList<>(gradeMap.values());
    sortedGrades.sort(Comparator.reverseOrder());
    double idcg = 0.0;
    int limit = Math.min(k, sortedGrades.size());
    for (int i = 0; i < limit; i++) {
      idcg += sortedGrades.get(i) / log2(i + 2);
    }
    return idcg;
  }

  private static double log2(double x) {
    return Math.log(x) / Math.log(2);
  }
}
