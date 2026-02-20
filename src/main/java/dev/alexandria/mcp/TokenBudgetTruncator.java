package dev.alexandria.mcp;

import dev.alexandria.search.SearchResult;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Truncates search results to fit within a configurable token budget.
 *
 * <p>Uses character-based token estimation (chars / 4) which is the industry-standard approximation
 * for English text. Results are formatted as readable text blocks with source URLs and section
 * paths for citation, then accumulated until the token budget is reached.
 *
 * <p>If even the first result exceeds the budget, it is included but truncated at the character
 * level to ensure at least one result is always returned.
 *
 * @see dev.alexandria.search.SearchResult
 */
@Component
public class TokenBudgetTruncator {

  private static final double CHARS_PER_TOKEN = 4.0;

  private final int tokenBudget;

  public TokenBudgetTruncator(@Value("${alexandria.mcp.token-budget:5000}") int tokenBudget) {
    this.tokenBudget = tokenBudget;
  }

  /**
   * Formats and truncates search results to fit within the configured token budget.
   *
   * @param results the search results to format and truncate
   * @return formatted text containing as many results as fit within the token budget
   */
  public String truncate(@Nullable List<SearchResult> results) {
    if (results == null || results.isEmpty()) {
      return "";
    }

    StringBuilder output = new StringBuilder();
    int estimatedTokens = 0;

    for (int i = 0; i < results.size(); i++) {
      String formatted = formatResult(i + 1, results.get(i));
      int resultTokens = estimateTokens(formatted);

      if (i == 0 && resultTokens > tokenBudget) {
        // First result exceeds budget: truncate at character level
        int maxChars = (int) (tokenBudget * CHARS_PER_TOKEN);
        output.append(formatted, 0, Math.min(maxChars, formatted.length()));
        break;
      }

      if (estimatedTokens + resultTokens > tokenBudget) {
        break;
      }

      output.append(formatted);
      estimatedTokens += resultTokens;
    }

    return output.toString();
  }

  /**
   * Returns the configured token budget.
   *
   * @return the maximum number of estimated tokens for search results
   */
  public int getTokenBudget() {
    return tokenBudget;
  }

  int estimateTokens(String text) {
    return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
  }

  private String formatResult(int index, SearchResult result) {
    return "## [%d] Source: %s\nSection: %s\nScore: %.3f\n\n%s\n\n---\n"
        .formatted(
            index, result.sourceUrl(), result.sectionPath(), result.rerankScore(), result.text());
  }
}
