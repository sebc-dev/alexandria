package dev.alexandria.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import dev.alexandria.search.SearchResult;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenBudgetTruncatorTest {

  @Test
  void singleResultWithinBudgetIsIncluded() {
    var truncator = new TokenBudgetTruncator(5000);
    var result =
        new SearchResult("Short content", 0.9, "https://docs.example.com", "Getting Started");

    String output = truncator.truncate(List.of(result));

    assertThat(output).contains("Short content");
    assertThat(output).contains("https://docs.example.com");
    assertThat(output).contains("Getting Started");
  }

  @Test
  void multipleResultsTruncatedWhenBudgetExceeded() {
    // Token budget of 50 tokens = ~200 chars. Each formatted result has overhead.
    var truncator = new TokenBudgetTruncator(50);
    var result1 = new SearchResult("First result", 0.9, "https://a.com", "Section A");
    var result2 = new SearchResult("Second result", 0.8, "https://b.com", "Section B");
    var result3 = new SearchResult("Third result", 0.7, "https://c.com", "Section C");

    String output = truncator.truncate(List.of(result1, result2, result3));

    // With a budget of 50 tokens (~200 chars), not all three formatted results should fit
    // At minimum, result1 is included; result3 should be excluded
    assertThat(output).contains("First result");
    assertThat(output).doesNotContain("Third result");
  }

  @Test
  void emptyResultListReturnsEmptyString() {
    var truncator = new TokenBudgetTruncator(5000);

    String output = truncator.truncate(Collections.emptyList());

    assertThat(output).isEmpty();
  }

  @Test
  void firstResultAlwaysIncludedEvenIfExceedsBudget() {
    // Budget of 10 tokens = ~40 chars. A formatted result will far exceed this.
    var truncator = new TokenBudgetTruncator(10);
    var result =
        new SearchResult(
            "This is a long content that definitely exceeds the tiny token budget",
            0.9,
            "https://docs.example.com",
            "Section");

    String output = truncator.truncate(List.of(result));

    // First result is always included (truncated at char level)
    assertThat(output).isNotEmpty();
    // Truncated to budget * 4 chars = 40 chars
    assertThat(output.length()).isLessThanOrEqualTo(40);
  }

  @Test
  void outputFormattingIncludesSourceUrlAndSectionPath() {
    var truncator = new TokenBudgetTruncator(5000);
    var result =
        new SearchResult("Content here", 0.85, "https://spring.io/docs", "Web > Controllers");

    String output = truncator.truncate(List.of(result));

    assertThat(output).contains("Source: https://spring.io/docs");
    assertThat(output).contains("Section: Web > Controllers");
    assertThat(output).contains("Content here");
    assertThat(output).contains("---");
  }

  @Test
  void tokenEstimationUsesCharsDiv4() {
    var truncator = new TokenBudgetTruncator(5000);
    // 400 chars should estimate to 100 tokens (400 / 4 = 100)
    String text = "a".repeat(400);

    int tokens = truncator.estimateTokens(text);

    assertThat(tokens).isEqualTo(100);
  }

  @Test
  void nullResultListReturnsEmptyString() {
    var truncator = new TokenBudgetTruncator(5000);

    String output = truncator.truncate(null);

    assertThat(output).isEmpty();
  }

  @Test
  void formatResultIncludesRerankScore() {
    var truncator = new TokenBudgetTruncator(5000);
    var result = new SearchResult("Content text", 0.9, "https://docs.example.com", "Section", 0.85);

    String output = truncator.truncate(List.of(result));

    assertThat(output).contains("Score: 0.850");
  }

  @Test
  void formatResultWithZeroRerankScoreShowsZero() {
    var truncator = new TokenBudgetTruncator(5000);
    var result = new SearchResult("Content text", 0.9, "https://docs.example.com", "Section", 0.0);

    String output = truncator.truncate(List.of(result));

    assertThat(output).contains("Score: 0.000");
  }

  @Test
  void formatResultWithBackwardCompatConstructorShowsZeroScore() {
    var truncator = new TokenBudgetTruncator(5000);
    // 4-arg convenience constructor defaults rerankScore to 0.0
    var result = new SearchResult("Content text", 0.9, "https://docs.example.com", "Section");

    String output = truncator.truncate(List.of(result));

    assertThat(output).contains("Score: 0.000");
  }
}
