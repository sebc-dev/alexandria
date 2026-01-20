package fr.kalifazzia.alexandria.core.ingestion;

import fr.kalifazzia.alexandria.core.model.ChunkType;

import java.util.List;
import java.util.UUID;

/**
 * Event published after a document has been successfully ingested into PostgreSQL.
 * Contains all the information needed to create graph vertices and edges in Apache AGE.
 *
 * <p>This event is processed by a {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * to ensure graph operations only occur after PostgreSQL changes are committed.
 * This prevents orphaned graph data when PostgreSQL transactions fail.
 */
public record DocumentIngestedEvent(
        UUID documentId,
        String documentPath,
        List<ChunkGraphOperation> chunkOperations,
        List<ReferenceEdgeOperation> referenceOperations
) {
    /**
     * Represents a chunk vertex creation with optional parent-child edge.
     */
    public record ChunkGraphOperation(
            UUID chunkId,
            ChunkType chunkType,
            UUID documentId,
            UUID parentChunkId // null for parent chunks
    ) {}

    /**
     * Represents a REFERENCES edge between documents.
     */
    public record ReferenceEdgeOperation(
            UUID sourceDocId,
            UUID targetDocId,
            String linkText
    ) {}
}
