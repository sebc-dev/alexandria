package dev.alexandria.search.eval;

import java.util.List;

/**
 * A golden set entry representing an annotated evaluation query with graded relevance judgments.
 *
 * @param query the natural language search query
 * @param queryType the classification of the query intent
 * @param judgments the list of relevance judgments (only grade 1 and 2; absence means grade 0)
 */
public record GoldenSetEntry(String query, QueryType queryType, List<RelevanceJudgment> judgments) {
  public GoldenSetEntry {
    judgments = List.copyOf(judgments);
  }
}
