package fr.kalifazzia.alexandria.core.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domain entity representing an indexed documentation file.
 * Immutable record matching the database schema.
 */
public record Document(
        UUID id,
        String path,
        String title,
        String category,
        List<String> tags,
        String contentHash,
        Map<String, Object> frontmatter,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Factory method for creating a new document (before persistence).
     * ID and timestamps will be set by the repository.
     */
    public static Document create(
            String path,
            String title,
            String category,
            List<String> tags,
            String contentHash,
            Map<String, Object> frontmatter
    ) {
        return new Document(
                null,
                path,
                title,
                category,
                tags != null ? List.copyOf(tags) : List.of(),
                contentHash,
                frontmatter != null ? Map.copyOf(frontmatter) : Map.of(),
                null,
                null
        );
    }

    /**
     * Defensive copy constructor to ensure immutability.
     */
    public Document {
        tags = tags != null ? List.copyOf(tags) : List.of();
        frontmatter = frontmatter != null ? Map.copyOf(frontmatter) : Map.of();
    }
}
