package dev.alexandria;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingStoreIT extends BaseIntegrationTest {

    @Autowired
    EmbeddingModel embeddingModel;

    @Autowired
    EmbeddingStore<TextSegment> embeddingStore;

    @BeforeEach
    void cleanStore() {
        embeddingStore.removeAll();
    }

    @Test
    void embedding_model_generates_384_dimension_vector() {
        Response<Embedding> response = embeddingModel.embed("How to configure Spring Boot");
        Embedding embedding = response.content();

        assertThat(embedding.vector()).hasSize(384);
        assertThat(embedding.vector()).isNotEqualTo(new float[384]);
    }

    @Test
    void embed_store_retrieve_roundtrip() {
        String text = "Spring Boot auto-configuration simplifies application setup";
        Embedding embedding = embeddingModel.embed(text).content();
        TextSegment segment = TextSegment.from(text);

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull().isNotBlank();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Spring Boot configuration").content())
                .query("Spring Boot configuration")  // Required for HYBRID search mode
                .maxResults(1)
                .build();

        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(searchRequest);

        assertThat(results.matches()).hasSize(1);
        assertThat(results.matches().getFirst().embedded().text())
                .isEqualTo(text);
        // In HYBRID mode, scores use RRF formula (not raw cosine similarity), so values are lower
        assertThat(results.matches().getFirst().score()).isGreaterThan(0);
    }

    @Test
    void spring_context_loads_with_embedding_beans() {
        assertThat(embeddingModel).isNotNull();
        assertThat(embeddingStore).isNotNull();
    }
}
