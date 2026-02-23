package dev.alexandria.search;

/**
 * Domain DTO for search results with citation metadata and optional reranking score.
 *
 * @param text the matched text segment content
 * @param score the relevance score (Convex Combination fused score from vector + FTS)
 * @param sourceUrl the URL of the documentation source (from TextSegment metadata "source_url")
 * @param sectionPath the section path within the document (from TextSegment metadata
 *     "section_path")
 * @param rerankScore the cross-encoder reranking score (0.0 if not reranked)
 */
public record SearchResult(
    String text, double score, String sourceUrl, String sectionPath, double rerankScore) {

  /** Convenience constructor for results without reranking (rerankScore defaults to 0.0). */
  public SearchResult(String text, double score, String sourceUrl, String sectionPath) {
    this(text, score, sourceUrl, sectionPath, 0.0);
  }
}
