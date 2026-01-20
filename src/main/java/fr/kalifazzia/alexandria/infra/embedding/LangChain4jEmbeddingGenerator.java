package fr.kalifazzia.alexandria.infra.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import fr.kalifazzia.alexandria.core.port.EmbeddingGenerator;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LangChain4j implementation of EmbeddingGenerator using all-MiniLM-L6-v2.
 *
 * <p>Uses the in-process ONNX model which runs locally without external API calls.
 * The model produces 384-dimensional embeddings and loads on first use (~2-3 seconds, ~100MB RAM).
 *
 * <p>The model processes inputs in parallel using all available CPU cores by default.
 */
@Component
public class LangChain4jEmbeddingGenerator implements EmbeddingGenerator {

    private static final int EMBEDDING_DIMENSION = 384;

    private final EmbeddingModel model;

    /**
     * Creates an embedding generator with the all-MiniLM-L6-v2 ONNX model.
     * Model loading is deferred until first use.
     */
    public LangChain4jEmbeddingGenerator() {
        this.model = new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * Package-private constructor for testing with custom model.
     */
    LangChain4jEmbeddingGenerator(EmbeddingModel model) {
        this.model = model;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Text to embed must not be null or blank");
        }
        return model.embed(text).content().vector();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .toList();

        return model.embedAll(segments).content().stream()
                .map(Embedding::vector)
                .toList();
    }

    @Override
    public int dimension() {
        return EMBEDDING_DIMENSION;
    }
}
