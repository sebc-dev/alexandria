package fr.kalifazzia.alexandria.core.port;

import java.util.List;

/**
 * Port interface for generating embeddings from text.
 * Follows hexagonal architecture - core defines the contract, infra implements it.
 *
 * <p>Embeddings are dense vector representations of text used for semantic similarity search.
 * This interface abstracts the embedding model implementation (e.g., LangChain4j, OpenAI).
 */
public interface EmbeddingGenerator {

    /**
     * Generates an embedding vector for a single text.
     *
     * @param text Text to embed
     * @return Embedding vector as float array
     */
    float[] embed(String text);

    /**
     * Generates embeddings for multiple texts in a batch.
     * More efficient than calling {@link #embed(String)} repeatedly.
     *
     * @param texts List of texts to embed
     * @return List of embedding vectors, in same order as input texts
     */
    List<float[]> embedAll(List<String> texts);

    /**
     * Returns the dimension of embedding vectors produced by this generator.
     *
     * @return Number of dimensions (e.g., 384 for all-MiniLM-L6-v2)
     */
    int dimension();
}
