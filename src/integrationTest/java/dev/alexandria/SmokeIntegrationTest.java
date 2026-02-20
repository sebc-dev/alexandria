package dev.alexandria;

import org.junit.jupiter.api.Test;

class SmokeIntegrationTest extends BaseIntegrationTest {

  @Test
  void contextLoads() {
    // Spring context loaded successfully with:
    // - Flyway migrations applied
    // - EmbeddingModel bean created (ONNX model loaded)
    // - EmbeddingStore bean created (pgvector connection)
    // - JpaSchemaDriftIT validated against schema
  }
}
