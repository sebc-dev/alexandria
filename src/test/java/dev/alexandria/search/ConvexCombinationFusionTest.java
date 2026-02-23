package dev.alexandria.search;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConvexCombinationFusionTest {

  private static final double ALPHA = 0.7;

  // --- Helper factory methods ---

  private static ScoredCandidate vectorCandidate(String id, String text, double score) {
    return new ScoredCandidate(
        id, TextSegment.from(text), Embedding.from(new float[] {1.0f, 0.0f}), score);
  }

  private static ScoredCandidate ftsCandidate(String id, String text, double score) {
    return new ScoredCandidate(id, TextSegment.from(text), null, score);
  }

  // --- Test cases ---

  @Test
  void empty_inputs_return_empty_result() {
    List<EmbeddingMatch<TextSegment>> result =
        ConvexCombinationFusion.fuse(List.of(), List.of(), ALPHA, 10);

    assertThat(result).isEmpty();
  }

  @Test
  void single_vector_result_no_fts_scores_alpha_times_one() {
    var vector = List.of(vectorCandidate("id1", "text1", 0.85));

    List<EmbeddingMatch<TextSegment>> result =
        ConvexCombinationFusion.fuse(vector, List.of(), ALPHA, 10);

    assertThat(result).hasSize(1);
    // Single result normalises to 1.0; combined = alpha * 1.0 + 0 = alpha
    assertThat(result.getFirst().score()).isEqualTo(ALPHA);
    assertThat(result.getFirst().embeddingId()).isEqualTo("id1");
  }

  @Test
  void single_fts_result_no_vector_scores_one_minus_alpha_times_one() {
    var fts = List.of(ftsCandidate("id2", "text2", 3.5));

    List<EmbeddingMatch<TextSegment>> result =
        ConvexCombinationFusion.fuse(List.of(), fts, ALPHA, 10);

    assertThat(result).hasSize(1);
    // Single FTS result normalises to 1.0; combined = 0 + (1 - alpha) * 1.0
    assertThat(result.getFirst().score()).isEqualTo(1.0 - ALPHA);
  }

  @Test
  void overlapping_results_combine_both_scores() {
    var vector = List.of(vectorCandidate("overlap", "shared text", 0.9));
    var fts = List.of(ftsCandidate("overlap", "shared text", 4.0));

    List<EmbeddingMatch<TextSegment>> result = ConvexCombinationFusion.fuse(vector, fts, ALPHA, 10);

    assertThat(result).hasSize(1);
    // Both single-entry lists normalise to 1.0
    // combined = alpha * 1.0 + (1 - alpha) * 1.0 = 1.0
    assertThat(result.getFirst().score()).isEqualTo(1.0);
    assertThat(result.getFirst().embeddingId()).isEqualTo("overlap");
  }

  @Test
  void multiple_results_normalisation_is_correct() {
    // Vector: scores 0.9 and 0.7 → normalised to 1.0 and 0.0
    var vector =
        List.of(vectorCandidate("v1", "text-v1", 0.9), vectorCandidate("v2", "text-v2", 0.7));
    // FTS: scores 5.0 and 3.0 → normalised to 1.0 and 0.0
    var fts = List.of(ftsCandidate("f1", "text-f1", 5.0), ftsCandidate("f2", "text-f2", 3.0));

    List<EmbeddingMatch<TextSegment>> result = ConvexCombinationFusion.fuse(vector, fts, ALPHA, 10);

    assertThat(result).hasSize(4);

    // v1: alpha * 1.0 + 0 = 0.7
    assertThat(findById(result, "v1").score()).isEqualTo(ALPHA);
    // v2: alpha * 0.0 + 0 = 0.0
    assertThat(findById(result, "v2").score()).isEqualTo(0.0);
    // f1: 0 + (1 - alpha) * 1.0 = 0.3
    assertThat(findById(result, "f1").score()).isEqualTo(1.0 - ALPHA);
    // f2: 0 + (1 - alpha) * 0.0 = 0.0
    assertThat(findById(result, "f2").score()).isEqualTo(0.0);
  }

  @Test
  void max_equals_min_normalises_all_scores_to_one() {
    // All vector scores identical → max == min → all normalise to 1.0
    var vector = List.of(vectorCandidate("a", "text-a", 0.5), vectorCandidate("b", "text-b", 0.5));

    List<EmbeddingMatch<TextSegment>> result =
        ConvexCombinationFusion.fuse(vector, List.of(), ALPHA, 10);

    assertThat(result).hasSize(2);
    // Both normalised to 1.0 → combined = alpha * 1.0 = alpha
    assertThat(result.get(0).score()).isEqualTo(ALPHA);
    assertThat(result.get(1).score()).isEqualTo(ALPHA);
  }

  @Test
  void alpha_zero_only_fts_contributes() {
    var vector = List.of(vectorCandidate("id1", "text1", 0.95));
    var fts = List.of(ftsCandidate("id2", "text2", 4.0));

    List<EmbeddingMatch<TextSegment>> result = ConvexCombinationFusion.fuse(vector, fts, 0.0, 10);

    // alpha=0 → vector contribution = 0; FTS contribution = (1-0)*1.0 = 1.0
    assertThat(findById(result, "id1").score()).isEqualTo(0.0);
    assertThat(findById(result, "id2").score()).isEqualTo(1.0);
  }

  @Test
  void alpha_one_only_vector_contributes() {
    var vector = List.of(vectorCandidate("id1", "text1", 0.95));
    var fts = List.of(ftsCandidate("id2", "text2", 4.0));

    List<EmbeddingMatch<TextSegment>> result = ConvexCombinationFusion.fuse(vector, fts, 1.0, 10);

    // alpha=1 → vector contribution = 1*1.0 = 1.0; FTS contribution = 0
    assertThat(findById(result, "id1").score()).isEqualTo(1.0);
    assertThat(findById(result, "id2").score()).isEqualTo(0.0);
  }

  @Test
  void default_alpha_weights_seventy_percent_vector() {
    // Verify the default 0.7 alpha gives expected weights
    var vector = List.of(vectorCandidate("shared", "text", 0.8));
    var fts = List.of(ftsCandidate("shared", "text", 3.0));

    List<EmbeddingMatch<TextSegment>> result = ConvexCombinationFusion.fuse(vector, fts, 0.7, 10);

    // Single entries each normalise to 1.0
    // combined = 0.7 * 1.0 + 0.3 * 1.0 = 1.0
    assertThat(result).hasSize(1);
    assertThat(result.getFirst().score()).isEqualTo(1.0);
  }

  @Test
  void max_results_limits_output_size() {
    var vector =
        List.of(
            vectorCandidate("v1", "t1", 0.9),
            vectorCandidate("v2", "t2", 0.8),
            vectorCandidate("v3", "t3", 0.7));

    List<EmbeddingMatch<TextSegment>> result =
        ConvexCombinationFusion.fuse(vector, List.of(), ALPHA, 2);

    assertThat(result).hasSize(2);
  }

  @Test
  void results_sorted_by_combined_score_descending() {
    // Vector: 0.9 and 0.7 → normalised 1.0 and 0.0
    var vector = List.of(vectorCandidate("v1", "t1", 0.9), vectorCandidate("v2", "t2", 0.7));
    // FTS: 5.0 and 3.0 → normalised 1.0 and 0.0
    var fts = List.of(ftsCandidate("f1", "t-f1", 5.0), ftsCandidate("f2", "t-f2", 3.0));

    List<EmbeddingMatch<TextSegment>> result = ConvexCombinationFusion.fuse(vector, fts, ALPHA, 10);

    // Expected order: v1 (0.7), f1 (0.3), v2 (0.0), f2 (0.0)
    assertThat(result.get(0).embeddingId()).isEqualTo("v1");
    assertThat(result.get(1).embeddingId()).isEqualTo("f1");
    // v2 and f2 both score 0.0 — order among ties doesn't matter, just verify they're last
    assertThat(result.get(0).score()).isGreaterThan(result.get(1).score());
    assertThat(result.get(1).score()).isGreaterThan(result.get(2).score());
  }

  @Test
  void fts_only_result_gets_zero_vector_placeholder_embedding() {
    var fts = List.of(ftsCandidate("fts1", "fts-text", 2.5));

    List<EmbeddingMatch<TextSegment>> result =
        ConvexCombinationFusion.fuse(List.of(), fts, ALPHA, 10);

    assertThat(result).hasSize(1);
    // FTS-only results should have a zero-vector placeholder
    assertThat(result.getFirst().embedding()).isNotNull();
    assertThat(result.getFirst().embedding().vector()).isEmpty();
  }

  @Test
  void overlapping_result_uses_vector_embedding_not_null() {
    var vector = List.of(vectorCandidate("shared", "text", 0.8));
    var fts = List.of(ftsCandidate("shared", "text", 3.0));

    List<EmbeddingMatch<TextSegment>> result = ConvexCombinationFusion.fuse(vector, fts, ALPHA, 10);

    // Overlapping result should use the vector's embedding (not null from FTS)
    assertThat(result.getFirst().embedding().vector()).isNotEmpty();
  }

  // --- Helper ---

  private static EmbeddingMatch<TextSegment> findById(
      List<EmbeddingMatch<TextSegment>> results, String embeddingId) {
    return results.stream()
        .filter(m -> embeddingId.equals(m.embeddingId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No result with embeddingId: " + embeddingId));
  }
}
