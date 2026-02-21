package dev.alexandria;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base class for all integration tests.
 *
 * <p>Provides a Testcontainers-managed PostgreSQL instance with pgvector and automatically cleans
 * the embedding store before each test. The {@code @Autowired(required = false)} on {@link
 * EmbeddingStore} ensures subclasses that do not inject the store (e.g. {@code
 * SmokeIntegrationTest}, {@code Crawl4AiClientIT}) are unaffected.
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  static {
    postgres.start();
  }

  @Autowired(required = false)
  protected EmbeddingStore<TextSegment> embeddingStore;

  @BeforeEach
  void cleanStore() {
    if (embeddingStore != null) {
      embeddingStore.removeAll();
    }
  }
}
