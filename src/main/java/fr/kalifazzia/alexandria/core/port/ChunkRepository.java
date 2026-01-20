package fr.kalifazzia.alexandria.core.port;

import fr.kalifazzia.alexandria.core.model.Chunk;
import fr.kalifazzia.alexandria.core.model.ChunkType;

import java.util.List;
import java.util.UUID;

/**
 * Port interface for chunk persistence.
 * Follows hexagonal architecture - core defines the contract, infra implements it.
 *
 * <p>Chunks are text segments with embeddings used for vector similarity search.
 * Supports hierarchical parent-child relationships for context retrieval.
 */
public interface ChunkRepository {

    /**
     * Saves a chunk with its embedding vector.
     *
     * @param documentId ID of the parent document
     * @param parentChunkId ID of the parent chunk (null for parent chunks)
     * @param type Type of chunk (PARENT or CHILD)
     * @param content Text content of the chunk
     * @param embedding Embedding vector for the chunk
     * @param position Position of this chunk in the document/parent
     * @return Generated UUID of the saved chunk
     */
    UUID saveChunk(UUID documentId, UUID parentChunkId, ChunkType type,
                   String content, float[] embedding, int position);

    /**
     * Deletes all chunks associated with a document.
     * Used for re-indexing when document content changes.
     *
     * @param documentId ID of the document
     */
    void deleteByDocumentId(UUID documentId);

    /**
     * Finds all chunks belonging to a document.
     *
     * @param documentId ID of the document
     * @return List of chunks, ordered by position and type
     */
    List<Chunk> findByDocumentId(UUID documentId);
}
