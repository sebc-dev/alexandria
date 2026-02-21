package dev.alexandria;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EmbeddingStoreIT extends BaseIntegrationTest {

  @Autowired EmbeddingModel embeddingModel;

  @Test
  void embeddingModelGenerates384DimensionVector() {
    Response<Embedding> response = embeddingModel.embed("How to configure Spring Boot");
    Embedding embedding = response.content();

    assertThat(embedding.vector()).hasSize(384);
    assertThat(embedding.vector()).isNotEqualTo(new float[384]);
  }

  @Test
  void embedStoreRetrieveRoundtrip() {
    String text = "Spring Boot auto-configuration simplifies application setup";
    Embedding embedding = embeddingModel.embed(text).content();
    TextSegment segment = TextSegment.from(text);

    String id = embeddingStore.add(embedding, segment);
    assertThat(id).isNotNull().isNotBlank();

    EmbeddingSearchRequest searchRequest =
        EmbeddingSearchRequest.builder()
            .queryEmbedding(embeddingModel.embed("Spring Boot configuration").content())
            .query("Spring Boot configuration") // Required for HYBRID search mode
            .maxResults(1)
            .build();

    EmbeddingSearchResult<TextSegment> results = embeddingStore.search(searchRequest);

    assertThat(results.matches()).hasSize(1);
    assertThat(results.matches().getFirst().embedded().text()).isEqualTo(text);
    // In HYBRID mode, scores use RRF formula (not raw cosine similarity), so values are lower
    assertThat(results.matches().getFirst().score()).isGreaterThan(0);
  }

  @Test
  void springContextLoadsWithEmbeddingBeans() {
    assertThat(embeddingModel).isNotNull();
    assertThat(embeddingStore).isNotNull();
  }
}
