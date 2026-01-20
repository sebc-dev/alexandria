package fr.kalifazzia.alexandria.core.ingestion;

/**
 * Result of parsing a markdown file.
 * Contains extracted metadata and content without frontmatter.
 */
public record ParsedDocument(
        DocumentMetadata metadata,
        String content
) {
    /**
     * Creates a parsed document with empty metadata.
     */
    public static ParsedDocument withoutMetadata(String content) {
        return new ParsedDocument(DocumentMetadata.empty(), content);
    }

    /**
     * Convenience accessor for title.
     */
    public String title() {
        return metadata.title();
    }

    /**
     * Convenience accessor for category.
     */
    public String category() {
        return metadata.category();
    }
}
