package fr.kalifazzia.alexandria.core.search;

import java.util.UUID;

/**
 * Domain record for documents discovered via graph traversal.
 * Contains document metadata for display without full content.
 */
public record RelatedDocument(
    UUID documentId,
    String title,
    String path,
    String category
) {
    /**
     * Compact constructor - no validation needed, all fields are metadata.
     */
    public RelatedDocument {
        // All fields can be null/empty for edge cases
    }
}
