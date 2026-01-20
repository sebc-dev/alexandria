package fr.kalifazzia.alexandria.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain entity representing a text chunk with embedding.
 * Supports hierarchical parent-child relationships.
 */
public record Chunk(
        UUID id,
        UUID documentId,
        UUID parentChunkId,
        ChunkType type,
        String content,
        int position,
        Instant createdAt
) {
    /**
     * Factory method for creating a new chunk (before persistence).
     * ID and timestamp will be set by the repository.
     */
    public static Chunk create(
            UUID documentId,
            UUID parentChunkId,
            ChunkType type,
            String content,
            int position
    ) {
        return new Chunk(null, documentId, parentChunkId, type, content, position, null);
    }

    /**
     * Factory method for creating a parent chunk.
     */
    public static Chunk createParent(UUID documentId, String content, int position) {
        return create(documentId, null, ChunkType.PARENT, content, position);
    }

    /**
     * Factory method for creating a child chunk with parent reference.
     */
    public static Chunk createChild(UUID documentId, UUID parentChunkId, String content, int position) {
        return create(documentId, parentChunkId, ChunkType.CHILD, content, position);
    }
}
