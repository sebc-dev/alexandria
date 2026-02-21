package dev.alexandria.search.eval;

/**
 * A graded relevance judgment mapping a chunk identifier to a relevance grade.
 *
 * @param chunkId logical identifier for the document chunk (semantic path-style)
 * @param grade relevance grade: 0 = not relevant, 1 = partially relevant, 2 = highly relevant
 */
public record RelevanceJudgment(String chunkId, int grade) {

  public RelevanceJudgment {
    if (grade < 0 || grade > 2) {
      throw new IllegalArgumentException("Grade must be 0, 1, or 2 but was " + grade);
    }
  }
}
