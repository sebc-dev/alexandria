package dev.alexandria.core;

import java.util.Set;
import java.util.regex.Pattern;

/** Validates search queries to ensure they are meaningful and well-formed. */
public class QueryValidator {

  private static final int MIN_CHARS = 3;
  private static final int MIN_MEANINGFUL_TOKENS = 2;

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  /** Common stopwords in English and French that don't contribute to semantic search. */
  private static final Set<String> STOPWORDS =
      Set.of(
          // English
          "a",
          "an",
          "the",
          "and",
          "or",
          "but",
          "in",
          "on",
          "at",
          "to",
          "for",
          "of",
          "with",
          "by",
          "from",
          "as",
          "is",
          "was",
          "are",
          "were",
          "been",
          "be",
          "have",
          "has",
          "had",
          "do",
          "does",
          "did",
          "will",
          "would",
          "could",
          "should",
          "may",
          "might",
          "must",
          "shall",
          "can",
          "this",
          "that",
          "these",
          "those",
          "it",
          "its",
          // French (excluding duplicates with English)
          "le",
          "la",
          "les",
          "un",
          "une",
          "des",
          "du",
          "de",
          "et",
          "ou",
          "mais",
          "dans",
          "sur",
          "en",
          "pour",
          "par",
          "avec",
          "sans",
          "sous",
          "entre",
          "vers",
          "chez",
          "est",
          "sont",
          "ont",
          "avoir",
          "être",
          "fait",
          "faire",
          "ce",
          "cette",
          "ces",
          "il",
          "elle",
          "ils",
          "elles",
          "nous",
          "vous",
          "je",
          "tu",
          "qui",
          "que",
          "quoi",
          "dont",
          "où");

  /**
   * Validates a search query.
   *
   * @param query the query to validate
   * @return validation result indicating if valid or the problem found
   */
  public ValidationResult validate(final String query) {
    if (query == null) {
      return ValidationResult.invalid(QueryProblem.TOO_SHORT);
    }

    String trimmed = query.trim();
    if (trimmed.length() < MIN_CHARS) {
      return ValidationResult.invalid(QueryProblem.TOO_SHORT);
    }

    String[] tokens = WHITESPACE.split(trimmed.toLowerCase());
    long meaningfulTokens = countMeaningfulTokens(tokens);

    if (meaningfulTokens < MIN_MEANINGFUL_TOKENS) {
      return ValidationResult.invalid(QueryProblem.TOO_VAGUE);
    }

    return ValidationResult.valid();
  }

  private long countMeaningfulTokens(final String[] tokens) {
    long count = 0;
    for (String token : tokens) {
      if (!token.isEmpty() && !STOPWORDS.contains(token)) {
        count++;
      }
    }
    return count;
  }

  /** Problems that can be detected during query validation. */
  public enum QueryProblem {
    /** Query is too short (less than minimum characters). */
    TOO_SHORT,
    /** Query contains only stopwords and lacks meaningful content. */
    TOO_VAGUE
  }

  /**
   * Result of query validation.
   *
   * @param isValid true if the query is valid
   * @param problem the problem found, or null if valid
   */
  public record ValidationResult(boolean isValid, QueryProblem problem) {

    /** Creates a valid result. */
    public static ValidationResult valid() {
      return new ValidationResult(true, null);
    }

    /** Creates an invalid result with the specified problem. */
    public static ValidationResult invalid(final QueryProblem problem) {
      return new ValidationResult(false, problem);
    }
  }
}
