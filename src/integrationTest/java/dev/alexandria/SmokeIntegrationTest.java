package dev.alexandria;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class SmokeIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Test
    void contextLoads() {
        // If we get here, Spring context loaded successfully with:
        // - Flyway migrations applied
        // - EmbeddingModel bean created (ONNX model loaded)
        // - EmbeddingStore bean created (pgvector connection)
        // - JPA entities validated against schema
        assertThat(true).isTrue();
    }
}
