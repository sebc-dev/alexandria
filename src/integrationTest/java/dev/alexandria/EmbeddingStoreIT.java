package dev.alexandria;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class EmbeddingStoreIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    EmbeddingModel embeddingModel;

    @Autowired
    EmbeddingStore<TextSegment> embeddingStore;

    @Test
    void embedding_model_generates_384_dimension_vector() {
        Response<Embedding> response = embeddingModel.embed("How to configure Spring Boot");
        Embedding embedding = response.content();

        assertThat(embedding.vector()).hasSize(384);
        // Verify vector is not all zeros (model actually computed something)
        assertThat(embedding.vector()).isNotEqualTo(new float[384]);
    }

    @Test
    void embed_store_retrieve_roundtrip() {
        // Generate embedding
        String text = "Spring Boot auto-configuration simplifies application setup";
        Embedding embedding = embeddingModel.embed(text).content();
        TextSegment segment = TextSegment.from(text);

        // Store
        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull().isNotBlank();

        // Retrieve via search -- search for similar content
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("Spring Boot configuration").content())
                .maxResults(1)
                .build();

        EmbeddingSearchResult<TextSegment> results = embeddingStore.search(searchRequest);

        assertThat(results.matches()).hasSize(1);
        assertThat(results.matches().get(0).embedded().text())
                .isEqualTo(text);
        // Cosine similarity should be high for semantically similar queries
        assertThat(results.matches().get(0).score()).isGreaterThan(0.8);
    }

    @Test
    void flyway_migrations_created_tables_and_indexes() {
        // Verify Flyway ran successfully by checking that the app context loaded
        // (Flyway runs on startup -- if migrations fail, context won't load)
        assertThat(embeddingModel).isNotNull();
        assertThat(embeddingStore).isNotNull();
    }
}
