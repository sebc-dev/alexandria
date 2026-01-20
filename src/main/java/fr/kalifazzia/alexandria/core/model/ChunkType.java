package fr.kalifazzia.alexandria.core.model;

/**
 * Type of chunk in hierarchical chunking strategy.
 * - PARENT: Larger context chunk (1000 tokens)
 * - CHILD: Smaller search chunk (200 tokens), embedded for retrieval
 */
public enum ChunkType {
    PARENT,
    CHILD
}
