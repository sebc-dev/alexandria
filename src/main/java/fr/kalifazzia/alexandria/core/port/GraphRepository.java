package fr.kalifazzia.alexandria.core.port;

import fr.kalifazzia.alexandria.core.model.ChunkType;

import java.util.List;
import java.util.UUID;

/**
 * Port interface for graph operations in Apache AGE.
 * Follows hexagonal architecture - core defines the contract, infra implements it.
 *
 * <p>The graph stores Document and Chunk vertices with HAS_CHILD edges
 * linking parent chunks to their child chunks, and REFERENCES edges
 * linking documents that reference each other via markdown links.
 * This enables graph traversal for context expansion during search.
 */
public interface GraphRepository {

    /**
     * Creates a Document vertex in the graph.
     *
     * @param documentId UUID of the document (links to PostgreSQL documents table)
     * @param path File path of the document
     */
    void createDocumentVertex(UUID documentId, String path);

    /**
     * Creates a Chunk vertex in the graph.
     *
     * @param chunkId UUID of the chunk (links to PostgreSQL chunks table)
     * @param type Type of chunk (PARENT or CHILD)
     * @param documentId UUID of the parent document
     */
    void createChunkVertex(UUID chunkId, ChunkType type, UUID documentId);

    /**
     * Creates a HAS_CHILD edge between a parent chunk and a child chunk.
     *
     * @param parentId UUID of the parent chunk
     * @param childId UUID of the child chunk
     */
    void createParentChildEdge(UUID parentId, UUID childId);

    /**
     * Deletes all Chunk vertices associated with a document.
     * Uses DETACH DELETE to also remove connected edges.
     *
     * @param documentId UUID of the document
     */
    void deleteChunksByDocumentId(UUID documentId);

    /**
     * Deletes a Document vertex and all connected edges.
     * Uses DETACH DELETE to cascade deletion to edges.
     *
     * @param documentId UUID of the document
     */
    void deleteDocumentGraph(UUID documentId);

    /**
     * Creates a REFERENCES edge between two documents.
     * Used to track cross-references from markdown links.
     *
     * @param sourceDocId UUID of the document containing the link
     * @param targetDocId UUID of the document being referenced
     * @param linkText The display text of the link (may be empty)
     */
    void createReferenceEdge(UUID sourceDocId, UUID targetDocId, String linkText);

    /**
     * Finds documents related to a given document via REFERENCES edges.
     * Uses variable-length path traversal to discover documents within maxHops.
     * Excludes the source document from results.
     *
     * @param documentId UUID of the starting document
     * @param maxHops Maximum number of REFERENCES edges to traverse (1-10)
     * @return List of document UUIDs that are reachable within maxHops
     */
    List<UUID> findRelatedDocuments(UUID documentId, int maxHops);

    /**
     * Clears all vertices and edges from the graph.
     * Used by CLI clear command for full re-indexation.
     * Uses DETACH DELETE to cascade edge removal.
     */
    void clearAll();
}
