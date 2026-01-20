package fr.kalifazzia.alexandria.core.search;

import java.util.List;
import java.util.UUID;

/**
 * Domain record representing a search result with parent context.
 * Contains the matched child chunk, its parent context for LLM consumption,
 * and associated document metadata.
 */
public record SearchResult(
        UUID childChunkId,
        String childContent,
        int childPosition,
        UUID parentChunkId,
        String parentContext,    // Full parent chunk content for LLM context
        UUID documentId,
        String documentTitle,
        String documentPath,
        String category,
        List<String> tags,
        double similarity        // 0-1 score, higher = more similar
) {
    /**
     * Compact constructor for defensive copy of tags.
     */
    public SearchResult {
        tags = tags != null ? List.copyOf(tags) : List.of();
    }
}
