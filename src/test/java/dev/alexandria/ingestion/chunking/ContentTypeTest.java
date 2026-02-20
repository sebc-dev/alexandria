package dev.alexandria.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContentTypeTest {

  // --- fromValue: case-insensitive ---

  @ParameterizedTest
  @ValueSource(strings = {"prose", "PROSE", "Prose", "pRoSe"})
  void fromValueHandlesCaseInsensitiveProseInput(String input) {
    ContentType result = ContentType.fromValue(input);

    assertThat(result).isEqualTo(ContentType.PROSE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"code", "CODE", "Code", "cOdE"})
  void fromValueHandlesCaseInsensitiveCodeInput(String input) {
    ContentType result = ContentType.fromValue(input);

    assertThat(result).isEqualTo(ContentType.CODE);
  }

  @Test
  void fromValueRejectsUnknownValue() {
    assertThatThrownBy(() -> ContentType.fromValue("garbage"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("garbage");
  }

  @Test
  void fromValueRejectsNull() {
    assertThatThrownBy(() -> ContentType.fromValue(null)).isInstanceOf(Exception.class);
  }

  // --- parseSearchFilter ---

  @Test
  void parseSearchFilterReturnsProse() {
    ContentType result = ContentType.parseSearchFilter("prose");

    assertThat(result).isEqualTo(ContentType.PROSE);
  }

  @Test
  void parseSearchFilterReturnsProseForUpperCase() {
    ContentType result = ContentType.parseSearchFilter("PROSE");

    assertThat(result).isEqualTo(ContentType.PROSE);
  }

  @Test
  void parseSearchFilterReturnsCode() {
    ContentType result = ContentType.parseSearchFilter("code");

    assertThat(result).isEqualTo(ContentType.CODE);
  }

  @Test
  void parseSearchFilterReturnsNullForMixed() {
    ContentType result = ContentType.parseSearchFilter("mixed");

    assertThat(result).isNull();
  }

  @Test
  void parseSearchFilterReturnsNullForMixedUpperCase() {
    ContentType result = ContentType.parseSearchFilter("MIXED");

    assertThat(result).isNull();
  }

  @Test
  void parseSearchFilterReturnsNullForNull() {
    ContentType result = ContentType.parseSearchFilter(null);

    assertThat(result).isNull();
  }

  @Test
  void parseSearchFilterRejectsUnknownValue() {
    assertThatThrownBy(() -> ContentType.parseSearchFilter("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unknown");
  }
}
