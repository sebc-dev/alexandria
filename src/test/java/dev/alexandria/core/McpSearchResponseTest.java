package dev.alexandria.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.alexandria.core.McpSearchResponse.RelevanceLevel;
import dev.alexandria.core.McpSearchResponse.SearchMetadata;
import dev.alexandria.core.McpSearchResponse.SearchResult;
import dev.alexandria.core.McpSearchResponse.SearchStatus;
import dev.alexandria.core.exception.AlexandriaException;
import dev.alexandria.core.exception.ErrorCategory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class McpSearchResponseTest {

  @Test
  void successShouldHaveOkStatus() {
    var result = new SearchResult("content", "uri", 0, 0.95, RelevanceLevel.HIGH, null);
    var metadata = new SearchMetadata("test query", 100, 1);

    var response = McpSearchResponse.success(List.of(result), metadata);

    assertThat(response.status()).isEqualTo(SearchStatus.OK);
    assertThat(response.results()).hasSize(1);
    assertThat(response.metadata()).isEqualTo(metadata);
  }

  @Test
  void partialShouldHavePartialStatus() {
    var result = new SearchResult("content", "uri", 0, 0.7, RelevanceLevel.MEDIUM, null);
    var metadata = new SearchMetadata("test query", 100, 1);

    var response = McpSearchResponse.partial(List.of(result), metadata, "Some results filtered");

    assertThat(response.status()).isEqualTo(SearchStatus.PARTIAL);
    assertThat(response.results()).hasSize(1);
    assertThat(response.message()).isEqualTo("Some results filtered");
  }

  @Test
  void noResultsShouldHaveNoResultsStatus() {
    var metadata = new SearchMetadata("obscure query", 0, 0);

    var response = McpSearchResponse.noResults(metadata);

    assertThat(response.status()).isEqualTo(SearchStatus.NO_RESULTS);
    assertThat(response.results()).isEmpty();
  }

  @Test
  void errorShouldHaveErrorStatus() {
    var response = McpSearchResponse.error("Database connection failed");

    assertThat(response.status()).isEqualTo(SearchStatus.ERROR);
    assertThat(response.results()).isEmpty();
    assertThat(response.message()).isEqualTo("Database connection failed");
  }

  @ParameterizedTest
  @CsvSource({
    "0.85, HIGH",
    "0.80, HIGH",
    "0.79, MEDIUM",
    "0.60, MEDIUM",
    "0.59, LOW",
    "0.30, LOW",
    "0.0, LOW"
  })
  void shouldCalculateRelevanceLevelFromScore(double score, RelevanceLevel expected) {
    RelevanceLevel level = RelevanceLevel.fromScore(score);

    assertThat(level).isEqualTo(expected);
  }

  @Test
  void searchResultShouldContainAllFields() {
    var result =
        new SearchResult(
            "This is the content",
            "file:///docs/readme.md",
            3,
            0.92,
            RelevanceLevel.HIGH,
            "Docs > Introduction");

    assertThat(result.content()).isEqualTo("This is the content");
    assertThat(result.sourceUri()).isEqualTo("file:///docs/readme.md");
    assertThat(result.chunkIndex()).isEqualTo(3);
    assertThat(result.score()).isEqualTo(0.92);
    assertThat(result.relevance()).isEqualTo(RelevanceLevel.HIGH);
    assertThat(result.breadcrumbs()).isEqualTo("Docs > Introduction");
  }

  @Test
  void searchMetadataShouldContainAllFields() {
    var metadata = new SearchMetadata("postgresql config", 150, 5);

    assertThat(metadata.query()).isEqualTo("postgresql config");
    assertThat(metadata.totalMatches()).isEqualTo(150);
    assertThat(metadata.returnedCount()).isEqualTo(5);
  }

  @Test
  void resultsShouldBeImmutableDefensiveCopy() {
    List<SearchResult> mutableList = new ArrayList<>();
    mutableList.add(new SearchResult("content", "uri", 0, 0.9, RelevanceLevel.HIGH, null));
    var metadata = new SearchMetadata("query", 1, 1);

    var response = McpSearchResponse.success(mutableList, metadata);

    // Mutating original list should not affect response
    mutableList.add(new SearchResult("new", "uri2", 1, 0.5, RelevanceLevel.LOW, null));

    assertThat(response.results()).hasSize(1);
  }

  @Test
  void searchResultShouldRejectNegativeChunkIndex() {
    assertThatThrownBy(() -> new SearchResult("content", "uri", -1, 0.9, RelevanceLevel.HIGH, null))
        .isInstanceOf(AlexandriaException.class)
        .hasMessageContaining("chunkIndex must be >= 0")
        .satisfies(
            ex ->
                assertThat(((AlexandriaException) ex).getCategory())
                    .isEqualTo(ErrorCategory.VALIDATION));
  }

  @ParameterizedTest
  @ValueSource(doubles = {-0.1, -1.0, 1.1, 2.0, Double.MAX_VALUE, Double.NEGATIVE_INFINITY})
  void fromScoreShouldRejectInvalidScores(double invalidScore) {
    assertThatThrownBy(() -> RelevanceLevel.fromScore(invalidScore))
        .isInstanceOf(AlexandriaException.class)
        .hasMessageContaining("Score must be between 0.0 and 1.0")
        .satisfies(
            ex ->
                assertThat(((AlexandriaException) ex).getCategory())
                    .isEqualTo(ErrorCategory.VALIDATION));
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 0.5, 1.0})
  void fromScoreShouldAcceptValidBoundaryScores(double validScore) {
    RelevanceLevel level = RelevanceLevel.fromScore(validScore);
    assertThat(level).isNotNull();
  }
}
