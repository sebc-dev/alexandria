package dev.alexandria.search;

/**
 * Domain DTO for search results with citation metadata.
 *
 * @param text        the matched text segment content
 * @param score       the relevance score (RRF-combined score in hybrid mode)
 * @param sourceUrl   the URL of the documentation source (from TextSegment metadata "source_url")
 * @param sectionPath the section path within the document (from TextSegment metadata "section_path")
 */
public record SearchResult(
        String text,
        double score,
        String sourceUrl,
        String sectionPath
) {
}
