package fr.kalifazzia.alexandria.core.ingestion;

import java.util.List;
import java.util.Map;

/**
 * Structured metadata extracted from YAML frontmatter.
 * Provides typed access to common fields while preserving raw data.
 */
public record DocumentMetadata(
        String title,
        String category,
        List<String> tags,
        Map<String, List<String>> rawFrontmatter
) {
    /**
     * Creates an empty metadata instance for documents without frontmatter.
     */
    public static DocumentMetadata empty() {
        return new DocumentMetadata(null, null, List.of(), Map.of());
    }

    /**
     * Defensive copy constructor.
     */
    public DocumentMetadata {
        tags = tags != null ? List.copyOf(tags) : List.of();
        rawFrontmatter = rawFrontmatter != null ? Map.copyOf(rawFrontmatter) : Map.of();
    }
}
