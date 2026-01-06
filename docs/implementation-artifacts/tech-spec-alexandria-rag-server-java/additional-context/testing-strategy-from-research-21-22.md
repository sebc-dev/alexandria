# Testing Strategy (from research #21-22)

**Pyramide de tests:** 70% unitaires / 20% intégration / 10% E2E

| Niveau | Outils | Couverture |
|--------|--------|------------|
| **Unit** | JUnit 5, Mockito | Services, splitter, parsers |
| **Integration** | Testcontainers, WireMock | pgvector, Infinity API |
| **E2E** | McpSyncClient | Flow MCP complet |

**PgVectorTestConfiguration:**

```java
package dev.alexandria.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
// Testcontainers 2.x: artifact "testcontainers-postgresql" (pas "postgresql")
// Package 2.x: org.testcontainers.postgresql (pas org.testcontainers.containers)
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Configuration réutilisable pour tests avec pgvector.
 */
@TestConfiguration
public class PgVectorTestConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("pgvector/pgvector:0.8.1-pg18")
            .withDatabaseName("alexandria_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres",
                "-c", "shared_preload_libraries=vector",
                "-c", "max_connections=20");
    }
}
```

**WireMock stubs Infinity (embeddings + rerank):**

```java
package dev.alexandria.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Stubs WireMock pour Infinity API avec simulation cold start.
 */
public class InfinityStubs {

    /**
     * Stub embeddings avec réponse normale.
     */
    public static void stubEmbeddings(WireMockServer server, float[] embedding) {
        server.stubFor(post(urlPathEqualTo("/v1/embeddings"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(embeddingsResponse(embedding))));
    }

    /**
     * Stub cold start avec délai log-normal (simule wake-up RunPod).
     */
    public static void stubColdStart(WireMockServer server, float[] embedding) {
        server.stubFor(post(urlPathEqualTo("/v1/embeddings"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withLogNormalRandomDelay(2000, 0.5)  // ~2s median, variance 0.5
                .withBody(embeddingsResponse(embedding))));
    }

    /**
     * Stub rerank.
     */
    public static void stubRerank(WireMockServer server, double... scores) {
        server.stubFor(post(urlPathEqualTo("/rerank"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(rerankResponse(scores))));
    }

    /**
     * Stub erreur 503 puis success (pour tester retry).
     */
    public static void stubRetryScenario(WireMockServer server, float[] embedding) {
        // Premier appel: 503
        server.stubFor(post(urlPathEqualTo("/v1/embeddings"))
            .inScenario("retry")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("failed_once"));

        // Deuxième appel: success
        server.stubFor(post(urlPathEqualTo("/v1/embeddings"))
            .inScenario("retry")
            .whenScenarioStateIs("failed_once")
            .willReturn(aResponse()
                .withStatus(200)
                .withBody(embeddingsResponse(embedding))));
    }

    private static String embeddingsResponse(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");

        return String.format("""
            {
              "data": [{"embedding": %s, "index": 0}],
              "model": "BAAI/bge-m3",
              "usage": {"prompt_tokens": 10, "total_tokens": 10}
            }
            """, sb);
    }

    private static String rerankResponse(double... scores) {
        StringBuilder results = new StringBuilder();
        for (int i = 0; i < scores.length; i++) {
            if (i > 0) results.append(",");
            results.append(String.format(
                "{\"index\": %d, \"relevance_score\": %f}", i, scores[i]));
        }
        return String.format("{\"results\": [%s]}", results);
    }
}
```

**EmbeddingFixtures (vecteurs normalisés 1024D):**

```java
/**
 * Génère des embeddings 1024D déterministes pour tests.
 */
public class EmbeddingFixtures {

    public static float[] generate(long seed) {
        Random random = new Random(seed);
        float[] embedding = new float[1024];

        double sumSquares = 0;
        for (int i = 0; i < 1024; i++) {
            embedding[i] = (float) random.nextGaussian();
            sumSquares += embedding[i] * embedding[i];
        }

        // Normaliser (L2 norm = 1)
        float norm = (float) Math.sqrt(sumSquares);
        for (int i = 0; i < 1024; i++) {
            embedding[i] /= norm;
        }

        return embedding;
    }

    public static float[] similar(float[] base, float similarity) {
        // Génère un vecteur avec cosine similarity ~= similarity
        float[] result = base.clone();
        Random random = new Random();

        float noise = 1 - similarity;
        for (int i = 0; i < result.length; i++) {
            result[i] += (float) (random.nextGaussian() * noise);
        }

        // Re-normaliser
        return normalize(result);
    }
}
```

**Test Resilience4j retry:**

```java
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.embedding.EmbeddingModel;

@SpringBootTest
@AutoConfigureWireMock(port = 0)
class InfinityClientRetryTest {

    @Autowired
    private EmbeddingModel embeddingModel;  // OpenAiEmbeddingModel configured with Infinity baseUrl

    @Autowired
    private WireMockServer wireMock;

    @Test
    @DisplayName("Should retry on 503 and succeed on second attempt")
    void shouldRetryOnServiceUnavailable() {
        float[] embedding = EmbeddingFixtures.generate(42L);
        InfinityStubs.stubRetryScenario(wireMock, embedding);

        // embed() retourne Response<Embedding>, pas un tableau directement
        Response<Embedding> response = embeddingModel.embed("test query");

        assertThat(response.content().vector()).hasSize(1024);
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/v1/embeddings")));
    }
}
```

**Test McpSyncClient E2E:**

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class McpToolsE2ETest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should search documents via MCP")
    void shouldSearchViaMcp() {
        McpSyncClient client = McpTestSupport.createClient(port);

        try {
            client.initialize();

            var tools = client.listTools();
            assertThat(tools.tools())
                .extracting(Tool::name)
                .contains("search_documents", "ingest_document");

            var result = client.callTool(new CallToolRequest(
                "search_documents",
                Map.of("query", "how to configure PostgreSQL")));

            assertThat(result.isError()).isFalse();
            assertThat(result.content()).isNotEmpty();

        } finally {
            client.closeGracefully();
        }
    }
}

/**
 * Helper pour créer un client MCP de test (HTTP Streamable).
 * Utilise MCP Java SDK 0.17.0 (inclus via Spring AI 1.1.2).
 */
public class McpTestSupport {

    public static McpSyncClient createClient(int port) {
        // HTTP Streamable transport - classe du MCP Java SDK
        // Package: io.modelcontextprotocol.client.transport
        var transport = HttpClientStreamableHttpTransport
            .builder("http://localhost:" + port)
            .endpoint("/mcp")
            .build();

        return McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(30))
            .clientInfo(new McpSchema.Implementation("alexandria-test", "1.0.0"))
            .build();
    }
}
```

**Reporté à v2:**
- Retrieval quality metrics CI/CD (P@5, R@10, MRR)
- Claude Code headless E2E tests
- MCP Inspector validation
