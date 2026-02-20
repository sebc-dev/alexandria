package dev.alexandria.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SearchRequestTest {

  @Test
  void defaultMaxResultsIsTen() {
    SearchRequest request = new SearchRequest("query");
    assertThat(request.maxResults()).isEqualTo(10);
  }

  @Test
  void customMaxResultsIsRespected() {
    SearchRequest request = new SearchRequest("query", 5);
    assertThat(request.maxResults()).isEqualTo(5);
  }

  @Test
  void nullQueryThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new SearchRequest(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Query must not be blank");
  }

  @Test
  void blankQueryThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new SearchRequest("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Query must not be blank");
  }

  @Test
  void maxResultsLessThanOneThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new SearchRequest("query", 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxResults must be at least 1");
  }

  @Test
  void filterFieldsDefaultToNull() {
    SearchRequest request = new SearchRequest("query", 10);

    assertThat(request.source()).isNull();
    assertThat(request.sectionPath()).isNull();
    assertThat(request.version()).isNull();
    assertThat(request.contentType()).isNull();
    assertThat(request.minScore()).isNull();
    assertThat(request.rrfK()).isNull();
  }

  @Test
  void allArgsConstructorSetsAllFilterFields() {
    SearchRequest request =
        new SearchRequest(
            "query", 5, "spring-boot", "getting-started/quick-start", "3.2.0", "CODE", 0.5, 42);

    assertThat(request.query()).isEqualTo("query");
    assertThat(request.maxResults()).isEqualTo(5);
    assertThat(request.source()).isEqualTo("spring-boot");
    assertThat(request.sectionPath()).isEqualTo("getting-started/quick-start");
    assertThat(request.version()).isEqualTo("3.2.0");
    assertThat(request.contentType()).isEqualTo("CODE");
    assertThat(request.minScore()).isEqualTo(0.5);
    assertThat(request.rrfK()).isEqualTo(42);
  }

  @Test
  void queryStillValidatedInFullConstructor() {
    assertThatThrownBy(() -> new SearchRequest("", 5, "source", null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Query must not be blank");
  }
}
