package fr.kalifazzia.alexandria.core.ingestion;

import java.util.List;
import java.util.Objects;

/**
 * Links a parent chunk to its child chunks.
 * Used in hierarchical chunking where documents are split into parent chunks (~1000 tokens)
 * and each parent is further split into child chunks (~200 tokens).
 *
 * <p>Child chunks are used for retrieval (embedded), parent chunks provide context.
 *
 * @param parentContent the text content of the parent chunk
 * @param childContents list of child chunk text contents derived from this parent
 * @param position the position of this parent in the original document (0-indexed)
 */
public record ChunkPair(
        String parentContent,
        List<String> childContents,
        int position
) {
    /**
     * Creates a ChunkPair with defensive copying of child contents list.
     */
    public ChunkPair {
        Objects.requireNonNull(parentContent, "parentContent must not be null");
        Objects.requireNonNull(childContents, "childContents must not be null");
        if (position < 0) {
            throw new IllegalArgumentException("position must be non-negative");
        }
        // Defensive copy
        childContents = List.copyOf(childContents);
    }
}
