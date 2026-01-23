package fr.kalifazzia.alexandria.infra.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LangChain4jEmbeddingGenerator.
 * Uses package-private constructor to inject mock EmbeddingModel.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LangChain4jEmbeddingGenerator Unit Tests")
class LangChain4jEmbeddingGeneratorTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private LangChain4jEmbeddingGenerator generator;

    @BeforeEach
    void setUp() {
        // Use package-private constructor with mock model
        generator = new LangChain4jEmbeddingGenerator(embeddingModel);
    }

    // ========== embed() tests ==========

    @Test
    @DisplayName("embed() with null throws IllegalArgumentException")
    void embed_null_throwsIllegalArgument() {
        // When/Then
        assertThatThrownBy(() -> generator.embed(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    @DisplayName("embed() with blank throws IllegalArgumentException")
    void embed_blank_throwsIllegalArgument() {
        // When/Then
        assertThatThrownBy(() -> generator.embed("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    @DisplayName("embed() with valid text returns embedding vector")
    void embed_validText_returnsEmbeddingVector() {
        // Given
        float[] expectedVector = {0.1f, 0.2f, 0.3f};
        Embedding embedding = new Embedding(expectedVector);
        when(embeddingModel.embed("test text")).thenReturn(Response.from(embedding));

        // When
        float[] result = generator.embed("test text");

        // Then
        assertThat(result).isEqualTo(expectedVector);
    }

    // ========== embedAll() tests ==========

    @Test
    @DisplayName("embedAll() with null returns empty list")
    void embedAll_null_returnsEmptyList() {
        // When
        List<float[]> result = generator.embedAll(null);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("embedAll() with empty list returns empty list")
    void embedAll_empty_returnsEmptyList() {
        // When
        List<float[]> result = generator.embedAll(List.of());

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("embedAll() with multiple texts returns embeddings")
    void embedAll_multipleTexts_returnsEmbeddings() {
        // Given
        float[] vector1 = {0.1f, 0.2f};
        float[] vector2 = {0.3f, 0.4f};
        float[] vector3 = {0.5f, 0.6f};

        List<Embedding> embeddings = List.of(
                new Embedding(vector1),
                new Embedding(vector2),
                new Embedding(vector3)
        );
        when(embeddingModel.embedAll(anyList())).thenReturn(Response.from(embeddings));

        // When
        List<float[]> result = generator.embedAll(List.of("text1", "text2", "text3"));

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo(vector1);
        assertThat(result.get(1)).isEqualTo(vector2);
        assertThat(result.get(2)).isEqualTo(vector3);
    }

    // ========== dimension() tests ==========

    @Test
    @DisplayName("dimension() returns model dimension")
    void dimension_returnsModelDimension() {
        // Given
        when(embeddingModel.dimension()).thenReturn(384);

        // When
        int dimension = generator.dimension();

        // Then
        assertThat(dimension).isEqualTo(384);
    }

    @Test
    @DisplayName("dimension() returns different dimension based on model")
    void dimension_returnsDifferentDimension() {
        // Given
        when(embeddingModel.dimension()).thenReturn(768);

        // When
        int dimension = generator.dimension();

        // Then
        assertThat(dimension).isEqualTo(768);
    }
}
