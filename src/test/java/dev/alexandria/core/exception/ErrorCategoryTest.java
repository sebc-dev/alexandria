package dev.alexandria.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCategoryTest {

  @Test
  void shouldHaveSixCategories() {
    assertThat(ErrorCategory.values()).hasSize(6);
  }

  @Test
  void shouldHaveTitleForEachCategory() {
    for (ErrorCategory category : ErrorCategory.values()) {
      assertThat(category.title())
          .as("Category %s should have a non-null title", category)
          .isNotNull()
          .isNotBlank();
    }
  }

  @Test
  void shouldHaveSuggestedActionForEachCategory() {
    for (ErrorCategory category : ErrorCategory.values()) {
      assertThat(category.suggestedAction())
          .as("Category %s should have a non-null suggested action", category)
          .isNotNull()
          .isNotBlank();
    }
  }
}
