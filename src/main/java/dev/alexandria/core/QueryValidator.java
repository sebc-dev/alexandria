package dev.alexandria.core;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Validates search queries to ensure they are meaningful and well-formed. */
public final class QueryValidator {

  private static final int MIN_CHARS = 3;
  private static final int MIN_TOKENS = 2;
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
   * Empêche l'instanciation de cette classe utilitaire.
   */
  private QueryValidator() {
    // Utility class
  }

  /**
   * Vérifie qu'une requête de recherche contient au moins le nombre minimal de caractères et suffisamment de tokens significatifs.
   *
   * @param query la requête à valider ; `null` est considéré comme trop courte et produit le problème `TOO_SHORT`
   * @return un ValidationResult indiquant si la requête est valide ; si invalide, le champ `problem` vaut `TOO_SHORT` ou `TOO_VAGUE`
   */
  @SuppressWarnings("PMD.OnlyOneReturn")
  public static ValidationResult validate(final String query) {
    if (query == null) {
      return ValidationResult.invalid(QueryProblem.TOO_SHORT);
    }

    final String trimmed = query.trim();
    if (trimmed.length() < MIN_CHARS) {
      return ValidationResult.invalid(QueryProblem.TOO_SHORT);
    }

    final String[] tokens = WHITESPACE.split(trimmed.toLowerCase(Locale.ROOT));
    final long meaningfulTokens = countMeaningfulTokens(tokens);

    if (meaningfulTokens < MIN_TOKENS) {
      return ValidationResult.invalid(QueryProblem.TOO_VAGUE);
    }

    return ValidationResult.valid();
  }

  /**
   * Compte le nombre de tokens significatifs dans le tableau fourni.
   *
   * @param tokens le tableau de tokens à évaluer
   * @return le nombre de tokens qui sont non vides et qui ne figurent pas dans {@code STOPWORDS}
   */
  private static long countMeaningfulTokens(final String... tokens) {
    long count = 0;
    for (final String token : tokens) {
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

    /**
     * Crée un objet ValidationResult représentant une validation réussie.
     *
     * @return une ValidationResult indiquant que la requête est valide (isValid = true, problem = null)
     */
    public static ValidationResult valid() {
      return new ValidationResult(true, null);
    }

    /**
     * Construit un ValidationResult indiquant que la requête est invalide pour le problème fourni.
     *
     * @param problem le motif d'invalidité associé au résultat
     * @return un ValidationResult dont `isValid` vaut `false` et `problem` vaut la valeur fournie
     */
    public static ValidationResult invalid(final QueryProblem problem) {
      return new ValidationResult(false, problem);
    }
  }
}