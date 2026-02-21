package dev.alexandria.search.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RetrievalMetricsTest {

  // Shared test data — judgments with graded relevance
  private static final List<RelevanceJudgment> JUDGMENTS_ABD =
      List.of(
          new RelevanceJudgment("a", 2),
          new RelevanceJudgment("b", 1),
          new RelevanceJudgment("d", 2));

  private static final List<RelevanceJudgment> JUDGMENTS_AB =
      List.of(new RelevanceJudgment("a", 2), new RelevanceJudgment("b", 1));

  private static final List<RelevanceJudgment> JUDGMENTS_A = List.of(new RelevanceJudgment("a", 2));

  private static final List<RelevanceJudgment> JUDGMENTS_EMPTY = List.of();

  private static final double TOLERANCE = 0.001;

  @Nested
  class RecallAtK {

    @Test
    void two_of_three_relevant_found_in_top_k() {
      var retrieved = List.of("a", "b", "c");

      double result = RetrievalMetrics.recallAtK(retrieved, JUDGMENTS_ABD, 3);

      assertThat(result).isCloseTo(2.0 / 3.0, within(TOLERANCE));
    }

    @Test
    void no_relevant_found_returns_zero() {
      var retrieved = List.of("x", "y");

      double result = RetrievalMetrics.recallAtK(retrieved, JUDGMENTS_A, 2);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void empty_retrieved_returns_zero() {
      double result = RetrievalMetrics.recallAtK(List.of(), JUDGMENTS_A, 5);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void no_relevant_documents_returns_zero() {
      var retrieved = List.of("a");

      double result = RetrievalMetrics.recallAtK(retrieved, JUDGMENTS_EMPTY, 1);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void all_relevant_found_returns_one() {
      var retrieved = List.of("a", "b");

      double result = RetrievalMetrics.recallAtK(retrieved, JUDGMENTS_AB, 2);

      assertThat(result).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void k_smaller_than_retrieved_truncates() {
      var retrieved = List.of("a", "b", "c");

      double result = RetrievalMetrics.recallAtK(retrieved, JUDGMENTS_AB, 1);

      // Only "a" in top-1 out of 2 relevant -> 1/2
      assertThat(result).isCloseTo(0.5, within(TOLERANCE));
    }
  }

  @Nested
  class PrecisionAtK {

    @Test
    void two_relevant_out_of_three_retrieved() {
      var retrieved = List.of("a", "b", "c");

      double result = RetrievalMetrics.precisionAtK(retrieved, JUDGMENTS_AB, 3);

      assertThat(result).isCloseTo(2.0 / 3.0, within(TOLERANCE));
    }

    @Test
    void all_relevant_in_top_k() {
      var retrieved = List.of("a", "b", "c");

      double result = RetrievalMetrics.precisionAtK(retrieved, JUDGMENTS_AB, 2);

      assertThat(result).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void empty_retrieved_returns_zero() {
      double result = RetrievalMetrics.precisionAtK(List.of(), JUDGMENTS_A, 5);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void no_relevant_documents_returns_zero() {
      var retrieved = List.of("a", "b");

      double result = RetrievalMetrics.precisionAtK(retrieved, JUDGMENTS_EMPTY, 2);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void k_larger_than_retrieved_uses_actual_size() {
      var retrieved = List.of("a");

      double result = RetrievalMetrics.precisionAtK(retrieved, JUDGMENTS_AB, 10);

      // 1 relevant out of 1 actual retrieved (not out of 10)
      assertThat(result).isCloseTo(1.0, within(TOLERANCE));
    }
  }

  @Nested
  class Mrr {

    @Test
    void first_relevant_at_position_two() {
      var retrieved = List.of("x", "a", "b");

      double result = RetrievalMetrics.mrr(retrieved, JUDGMENTS_AB, 3);

      assertThat(result).isCloseTo(0.5, within(TOLERANCE));
    }

    @Test
    void first_relevant_at_position_one() {
      var retrieved = List.of("a", "b");

      double result = RetrievalMetrics.mrr(retrieved, JUDGMENTS_A, 2);

      assertThat(result).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void no_relevant_found_returns_zero() {
      var retrieved = List.of("x", "y", "z");

      double result = RetrievalMetrics.mrr(retrieved, JUDGMENTS_A, 3);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void empty_retrieved_returns_zero() {
      double result = RetrievalMetrics.mrr(List.of(), JUDGMENTS_A, 5);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void no_relevant_documents_returns_zero() {
      var retrieved = List.of("a");

      double result = RetrievalMetrics.mrr(retrieved, JUDGMENTS_EMPTY, 1);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }
  }

  @Nested
  class NdcgAtK {

    @Test
    void graded_relevance_with_known_test_vector() {
      // retrievedIds=["a","c","b"], judgments=[a:2, b:1, c:0], k=3
      // DCG = 2/log2(2) + 0/log2(3) + 1/log2(4) = 2/1 + 0/1.585 + 1/2 = 2.5
      // IDCG = ideal order [a:2, b:1] -> 2/log2(2) + 1/log2(3) = 2 + 0.631 = 2.631
      // NDCG = 2.5 / 2.631 = 0.950
      var retrieved = List.of("a", "c", "b");
      var judgments =
          List.of(
              new RelevanceJudgment("a", 2),
              new RelevanceJudgment("b", 1),
              new RelevanceJudgment("c", 0));

      double result = RetrievalMetrics.ndcgAtK(retrieved, judgments, 3);

      assertThat(result).isCloseTo(0.950, within(TOLERANCE));
    }

    @Test
    void perfect_ranking_returns_one() {
      // Ideal order: a:2 then b:1
      var retrieved = List.of("a", "b");

      double result = RetrievalMetrics.ndcgAtK(retrieved, JUDGMENTS_AB, 2);

      assertThat(result).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void no_relevant_documents_returns_zero() {
      var retrieved = List.of("a", "b");

      double result = RetrievalMetrics.ndcgAtK(retrieved, JUDGMENTS_EMPTY, 2);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void empty_retrieved_returns_zero() {
      double result = RetrievalMetrics.ndcgAtK(List.of(), JUDGMENTS_A, 5);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void all_zero_grades_returns_zero() {
      var retrieved = List.of("x", "y");
      var judgments = List.of(new RelevanceJudgment("x", 0), new RelevanceJudgment("y", 0));

      double result = RetrievalMetrics.ndcgAtK(retrieved, judgments, 2);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }
  }

  @Nested
  class AveragePrecision {

    @Test
    void interleaved_relevant_and_irrelevant() {
      // retrievedIds=["a","x","b","y"], judgments=[a:2, b:1], k=4
      // Relevant at positions 1 and 3
      // P@1 = 1/1 = 1.0, P@3 = 2/3 = 0.667
      // AP = (1.0 + 0.667) / 2 = 0.833
      var retrieved = List.of("a", "x", "b", "y");

      double result = RetrievalMetrics.averagePrecision(retrieved, JUDGMENTS_AB, 4);

      assertThat(result).isCloseTo(0.833, within(TOLERANCE));
    }

    @Test
    void no_relevant_found_returns_zero() {
      var retrieved = List.of("x", "y");

      double result = RetrievalMetrics.averagePrecision(retrieved, JUDGMENTS_A, 2);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void all_relevant_at_top_returns_one() {
      var retrieved = List.of("a", "b");

      double result = RetrievalMetrics.averagePrecision(retrieved, JUDGMENTS_AB, 2);

      // P@1 = 1/1, P@2 = 2/2 => AP = (1 + 1) / 2 = 1.0
      assertThat(result).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void empty_retrieved_returns_zero() {
      double result = RetrievalMetrics.averagePrecision(List.of(), JUDGMENTS_A, 5);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void no_relevant_documents_returns_zero() {
      var retrieved = List.of("a");

      double result = RetrievalMetrics.averagePrecision(retrieved, JUDGMENTS_EMPTY, 1);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }
  }

  @Nested
  class HitRate {

    @Test
    void at_least_one_relevant_returns_one() {
      var retrieved = List.of("x", "a");

      double result = RetrievalMetrics.hitRate(retrieved, JUDGMENTS_A, 2);

      assertThat(result).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void no_relevant_returns_zero() {
      var retrieved = List.of("x", "y");

      double result = RetrievalMetrics.hitRate(retrieved, JUDGMENTS_A, 2);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void empty_retrieved_returns_zero() {
      double result = RetrievalMetrics.hitRate(List.of(), JUDGMENTS_A, 5);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void no_relevant_documents_returns_zero() {
      var retrieved = List.of("a");

      double result = RetrievalMetrics.hitRate(retrieved, JUDGMENTS_EMPTY, 1);

      assertThat(result).isCloseTo(0.0, within(TOLERANCE));
    }
  }

  @Nested
  class ComputeAll {

    @Test
    void returns_all_metrics_consistently() {
      var retrieved = List.of("a", "x", "b", "y");

      RetrievalMetrics.MetricsResult result =
          RetrievalMetrics.computeAll(retrieved, JUDGMENTS_AB, 4);

      // recallAtK: 2 found / 2 total relevant = 1.0
      assertThat(result.recallAtK()).isCloseTo(1.0, within(TOLERANCE));
      // precisionAtK: 2 relevant / 4 retrieved = 0.5
      assertThat(result.precisionAtK()).isCloseTo(0.5, within(TOLERANCE));
      // mrr: first relevant at position 1 -> 1/1 = 1.0
      assertThat(result.mrr()).isCloseTo(1.0, within(TOLERANCE));
      // averagePrecision: P@1=1/1, P@3=2/3 -> (1 + 0.667)/2 = 0.833
      assertThat(result.averagePrecision()).isCloseTo(0.833, within(TOLERANCE));
      // hitRate: at least one relevant -> 1.0
      assertThat(result.hitRate()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void empty_results_returns_all_zeros() {
      RetrievalMetrics.MetricsResult result =
          RetrievalMetrics.computeAll(List.of(), JUDGMENTS_A, 5);

      assertThat(result.recallAtK()).isCloseTo(0.0, within(TOLERANCE));
      assertThat(result.precisionAtK()).isCloseTo(0.0, within(TOLERANCE));
      assertThat(result.mrr()).isCloseTo(0.0, within(TOLERANCE));
      assertThat(result.ndcgAtK()).isCloseTo(0.0, within(TOLERANCE));
      assertThat(result.averagePrecision()).isCloseTo(0.0, within(TOLERANCE));
      assertThat(result.hitRate()).isCloseTo(0.0, within(TOLERANCE));
    }
  }

  @Nested
  class RelevanceJudgmentValidation {

    @Test
    void valid_grades_accepted() {
      assertThat(new RelevanceJudgment("a", 0).grade()).isEqualTo(0);
      assertThat(new RelevanceJudgment("a", 1).grade()).isEqualTo(1);
      assertThat(new RelevanceJudgment("a", 2).grade()).isEqualTo(2);
    }

    @Test
    void negative_grade_rejected() {
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RelevanceJudgment("a", -1))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void grade_above_two_rejected() {
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RelevanceJudgment("a", 3))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
