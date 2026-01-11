package dev.alexandria.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.alexandria.core.QueryValidator.QueryProblem;
import dev.alexandria.core.QueryValidator.ValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueryValidatorTest {

  private final QueryValidator validator = new QueryValidator();

  @ParameterizedTest
  @ValueSource(strings = {"ab", "a", "", "   ", "  "})
  void shouldRejectTooShortQuery(String query) {
    ValidationResult result = validator.validate(query);

    assertThat(result.isValid()).isFalse();
    assertThat(result.problem()).isEqualTo(QueryProblem.TOO_SHORT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"the a an", "a the an", "an a"})
  void shouldRejectStopwordsOnlyEnglish(String query) {
    ValidationResult result = validator.validate(query);

    assertThat(result.isValid()).isFalse();
    assertThat(result.problem()).isEqualTo(QueryProblem.TOO_VAGUE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"le la les", "un une des", "du de la"})
  void shouldRejectStopwordsOnlyFrench(String query) {
    ValidationResult result = validator.validate(query);

    assertThat(result.isValid()).isFalse();
    assertThat(result.problem()).isEqualTo(QueryProblem.TOO_VAGUE);
  }

  @Test
  void shouldAcceptValidQuery() {
    ValidationResult result = validator.validate("how to configure PostgreSQL");

    assertThat(result.isValid()).isTrue();
    assertThat(result.problem()).isNull();
  }

  @Test
  void shouldAcceptMinimalValidQuery() {
    ValidationResult result = validator.validate("PostgreSQL config");

    assertThat(result.isValid()).isTrue();
    assertThat(result.problem()).isNull();
  }

  @Test
  void shouldRejectNullQuery() {
    ValidationResult result = validator.validate(null);

    assertThat(result.isValid()).isFalse();
    assertThat(result.problem()).isEqualTo(QueryProblem.TOO_SHORT);
  }

  @Test
  void shouldAcceptQueryWithMixedStopwordsAndMeaningfulWords() {
    ValidationResult result = validator.validate("the PostgreSQL configuration guide");

    assertThat(result.isValid()).isTrue();
    assertThat(result.problem()).isNull();
  }
}
