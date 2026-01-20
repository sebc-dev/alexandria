package fr.kalifazzia.alexandria.core.port;

import fr.kalifazzia.alexandria.core.ingestion.ChunkPair;

import java.util.List;

/**
 * Port interface for document chunking.
 * Follows hexagonal architecture - core defines the contract, infra implements it.
 */
public interface ChunkerPort {

    /**
     * Chunks the given content into a hierarchical parent-child structure.
     *
     * @param content the text content to chunk
     * @return list of chunk pairs, each containing a parent and its children;
     *         empty list if content is null or blank
     */
    List<ChunkPair> chunk(String content);
}
