# Testing Best Practices for RAG Spring Boot Servers

**Spring Boot 3.5.9 + Langchain4j 1.10.0 + Spring AI MCP 1.1.2** demands a specialized testing strategy that addresses the unique challenges of retrieval-augmented generation systems: non-deterministic LLM outputs, embedding quality validation, and complex pipeline orchestration. This guide provides concrete patterns, complete code examples, and practical fixtures tailored to your specific tech stack.

The fundamental shift in RAG testing versus traditional API testing lies in separating **retrieval quality** from **generation quality**—poor retrieval guarantees poor generation, making this distinction critical for debugging. Your test pyramid should allocate **70% unit tests** for deterministic components (document splitters, transformers, repository logic), **20% integration tests** with Testcontainers for vector search and retry behavior, and **10% E2E/simulation tests** for MCP flows and complete RAG pipelines.

## Test package structure and naming conventions

Organize tests by mirroring your main source structure while clearly separating test types:

```
src/test/java/com/yourcompany/ragserver/
├── unit/
│   ├── splitter/
│   │   └── MarkdownDocumentSplitterTest.java
│   ├── embedding/
│   │   └── EmbeddingServiceTest.java
│   └── retrieval/
│       └── RetrievalServiceTest.java
├── integration/
│   ├── repository/
│   │   └── VectorStoreRepositoryIT.java
│   ├── retry/
│   │   └── EmbeddingClientRetryIT.java
│   └── mcp/
│       └── McpServerIT.java
├── e2e/
│   └── RagPipelineE2ETest.java
├── config/
│   ├── PgVectorTestConfiguration.java
│   ├── WireMockTestConfiguration.java
│   └── McpTestConfiguration.java
├── fixtures/
│   ├── MarkdownFixtures.java
│   ├── EmbeddingFixtures.java
│   └── WireMockStubLoader.java
└── support/
    ├── BaseIntegrationTest.java
    └── TestDataFactory.java
```

Use **`*Test.java`** for unit tests (Maven Surefire), **`*IT.java`** for integration tests (Maven Failsafe), and **`*E2ETest.java`** for end-to-end tests. Method naming should follow the `should_expectedBehavior_when_condition` pattern for maximum readability.

## Testcontainers configuration with pgvector 0.8.1

The official `pgvector/pgvector:0.8.1-pg18` image comes pre-configured with the vector extension. Create a reusable test configuration:

```java
@TestConfiguration(proxyBeanMethods = false)
public class PgVectorTestConfiguration {
    
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:0.8.1-pg18")
                .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("rag_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("db/init-pgvector.sql")
            .withCommand("postgres", 
                "-c", "max_connections=100",
                "-c", "shared_buffers=256MB",
                "-c", "maintenance_work_mem=512MB")
            .withReuse(true);
    }
}
```

**Database initialization script** (`src/test/resources/db/init-pgvector.sql`):

```sql
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS vector_store (
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text NOT NULL,
    metadata jsonb,
    embedding vector(1024)  -- Match your Infinity model dimensions
);

CREATE INDEX IF NOT EXISTS vector_store_hnsw_idx 
    ON vector_store USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

**Integration test base class:**

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(PgVectorTestConfiguration.class)
@ActiveProfiles("test")
public abstract class BaseVectorStoreIT {
    
    @Autowired
    protected JdbcTemplate jdbcTemplate;
    
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE vector_store RESTART IDENTITY");
    }
    
    @Test
    void shouldPerformSimilaritySearch() {
        // Seed test vectors
        jdbcTemplate.update(
            "INSERT INTO vector_store (content, embedding) VALUES (?, ?::vector)",
            "Spring Boot testing guide", generateNormalizedVector(1024, 42L));
        
        jdbcTemplate.execute("SET hnsw.ef_search = 100");
        
        List<Map<String, Object>> results = jdbcTemplate.queryForList("""
            SELECT content, 1 - (embedding <=> ?::vector) AS similarity
            FROM vector_store 
            ORDER BY embedding <=> ?::vector 
            LIMIT 5
        """, generateNormalizedVector(1024, 42L), generateNormalizedVector(1024, 42L));
        
        assertThat(results).isNotEmpty();
        assertThat((Double) results.get(0).get("similarity")).isGreaterThan(0.9);
    }
    
    protected String generateNormalizedVector(int dimensions, long seed) {
        Random random = new Random(seed);
        double[] values = new double[dimensions];
        double sumSquares = 0;
        for (int i = 0; i < dimensions; i++) {
            values[i] = random.nextGaussian() * 0.02;
            sumSquares += values[i] * values[i];
        }
        double magnitude = Math.sqrt(sumSquares);
        return "[" + Arrays.stream(values)
            .map(v -> String.format("%.8f", v / magnitude))
            .collect(Collectors.joining(",")) + "]";
    }
}
```

## WireMock stubs for Infinity embeddings and rerank

Create comprehensive stubs for your OpenAI-compatible Infinity server and Cohere-format reranking:

**WireMock configuration class:**

```java
@TestConfiguration
public class WireMockTestConfiguration {
    
    @Bean(destroyMethod = "stop")
    public WireMockServer wireMockServer() {
        WireMockServer server = new WireMockServer(options()
            .port(8089)
            .globalTemplating(true)
            .withRootDirectory("src/test/resources/wiremock"));
        server.start();
        return server;
    }
}
```

**Embeddings stub** (`src/test/resources/wiremock/mappings/infinity-embeddings.json`):

```json
{
  "request": {
    "method": "POST",
    "urlPath": "/v1/embeddings",
    "headers": {
      "Authorization": { "matches": "Bearer .*" }
    },
    "bodyPatterns": [
      { "matchesJsonPath": "$.model" },
      { "matchesJsonPath": "$.input" }
    ]
  },
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "bodyFileName": "embeddings/success-response.json",
    "transformers": ["response-template"]
  }
}
```

**Dynamic embedding response** (`src/test/resources/wiremock/__files/embeddings/success-response.json`):

```json
{
  "object": "list",
  "data": [
    {
      "object": "embedding",
      "index": 0,
      "embedding": {{parseJson parameters.embedding1024}}
    }
  ],
  "model": "{{jsonPath request.body '$.model'}}",
  "usage": {
    "prompt_tokens": {{math (size (jsonPath request.body '$.input')) '*' 25}},
    "total_tokens": {{math (size (jsonPath request.body '$.input')) '*' 25}}
  }
}
```

**Cohere rerank stub** (`src/test/resources/wiremock/mappings/infinity-rerank.json`):

```json
{
  "request": {
    "method": "POST",
    "urlPath": "/v1/rerank",
    "bodyPatterns": [
      { "matchesJsonPath": "$.query" },
      { "matchesJsonPath": "$.documents" }
    ]
  },
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "jsonBody": {
      "id": "{{randomValue type='UUID'}}",
      "results": [
        {"index": 0, "relevance_score": 0.95},
        {"index": 1, "relevance_score": 0.72},
        {"index": 2, "relevance_score": 0.31}
      ],
      "meta": {
        "api_version": {"version": "2"},
        "billed_units": {"search_units": 1}
      }
    },
    "transformers": ["response-template"]
  }
}
```

**Error and latency simulation stubs:**

```java
@WireMockTest(httpPort = 8089)
class InfinityClientErrorHandlingTest {
    
    @Test
    void shouldHandleRateLimiting() {
        stubFor(post(urlEqualTo("/v1/embeddings"))
            .willReturn(aResponse()
                .withStatus(429)
                .withHeader("Retry-After", "60")
                .withHeader("x-ratelimit-remaining-tokens", "0")
                .withBody("""
                    {"error": {
                        "message": "Rate limit exceeded",
                        "type": "rate_limit_error",
                        "code": "rate_limit_exceeded"
                    }}
                """)));
        
        assertThrows(RateLimitException.class, 
            () -> embeddingClient.embed("test"));
    }
    
    @Test
    void shouldSimulateColdStart() {
        stubFor(post(urlEqualTo("/v1/embeddings"))
            .inScenario("Cold Start")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse()
                .withStatus(200)
                .withBodyFile("embeddings/success-response.json")
                .withLogNormalRandomDelay(2000, 0.5))  // 2s median cold start
            .willSetStateTo("WARM"));
        
        stubFor(post(urlEqualTo("/v1/embeddings"))
            .inScenario("Cold Start")
            .whenScenarioStateIs("WARM")
            .willReturn(aResponse()
                .withStatus(200)
                .withBodyFile("embeddings/success-response.json")
                .withUniformRandomDelay(50, 150)));  // 50-150ms warm
    }
    
    @Test
    void shouldHandleNetworkFailure() {
        stubFor(post(urlEqualTo("/v1/embeddings"))
            .willReturn(aResponse()
                .withFault(Fault.CONNECTION_RESET_BY_PEER)));
        
        assertThrows(ConnectionException.class, 
            () -> embeddingClient.embed("test"));
    }
}
```

**Embedding fixture generator:**

```java
public class EmbeddingFixtures {
    
    public static String generate1024DimensionalEmbedding(long seed) {
        Random random = new Random(seed);
        double[] embedding = new double[1024];
        double sumSquares = 0;
        
        for (int i = 0; i < 1024; i++) {
            embedding[i] = random.nextGaussian() * 0.02;
            sumSquares += embedding[i] * embedding[i];
        }
        
        double magnitude = Math.sqrt(sumSquares);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 1024; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%.8f", embedding[i] / magnitude));
        }
        sb.append("]");
        return sb.toString();
    }
    
    public static void generateFixtureFiles(Path outputDir, int count) throws IOException {
        for (int i = 0; i < count; i++) {
            String embedding = generate1024DimensionalEmbedding(i);
            Files.writeString(outputDir.resolve("embedding-" + i + ".json"), embedding);
        }
    }
}
```

## Markdown DocumentSplitter testing patterns

Test your custom markdown splitter with comprehensive edge cases for **code blocks, header hierarchy, and token limits**:

```java
class MarkdownDocumentSplitterTest {
    
    private DocumentSplitter splitter;
    private TokenCountEstimator tokenEstimator;
    
    @BeforeEach
    void setUp() {
        tokenEstimator = new OpenAiTokenCountEstimator("gpt-4o-mini");
        splitter = DocumentSplitters.recursive(450, 50, tokenEstimator);
    }
    
    // ======== Code Block Preservation ========
    
    @Test
    void should_preserve_fenced_code_blocks_without_splitting_inside() {
        String markdown = """
            # API Documentation
            
            Use the following code:
            
            ```java
            // # This comment looks like a header but isn't
            public class DocumentProcessor {
                public void process(Document doc) {
                    // ## Another fake header
                    System.out.println("Processing...");
                }
            }
            ```
            
            ## Next Section
            More content here.
            """;
        
        Document doc = Document.from(markdown);
        List<TextSegment> segments = splitter.split(doc);
        
        // Code block should remain intact
        boolean codeBlockIntact = segments.stream()
            .anyMatch(s -> s.text().contains("```java") && 
                          s.text().contains("```") &&
                          s.text().contains("DocumentProcessor"));
        assertThat(codeBlockIntact).isTrue();
        
        // Fake headers inside code shouldn't create new sections
        assertThat(segments.stream()
            .noneMatch(s -> s.text().startsWith("// #"))).isTrue();
    }
    
    @Test
    void should_handle_multiple_code_block_languages() {
        String markdown = """
            # Examples
            
            ```python
            def hello():
                return "Hello"
            ```
            
            ```sql
            SELECT * FROM users WHERE active = true;
            ```
            
            ```yaml
            spring:
              datasource:
                url: jdbc:postgresql://localhost/db
            ```
            """;
        
        List<TextSegment> segments = splitter.split(Document.from(markdown));
        
        String allText = segments.stream()
            .map(TextSegment::text)
            .collect(Collectors.joining());
        
        assertThat(allText)
            .contains("```python")
            .contains("```sql")
            .contains("```yaml");
    }
    
    // ======== Header Hierarchy (Breadcrumbs) ========
    
    @Test
    void should_track_header_hierarchy_in_metadata() {
        String markdown = """
            # Main Title
            Introduction.
            
            ## Chapter One
            Chapter content.
            
            ### Section 1.1
            Section content.
            
            ### Section 1.2
            More section content.
            
            ## Chapter Two
            Another chapter.
            """;
        
        List<TextSegment> segments = splitter.split(Document.from(markdown));
        
        // Verify breadcrumb metadata exists
        TextSegment section11 = segments.stream()
            .filter(s -> s.text().contains("Section 1.1"))
            .findFirst()
            .orElseThrow();
        
        assertThat(section11.metadata().getString("md-section-header"))
            .isEqualTo("Section 1.1");
        assertThat(section11.metadata().getString("md-parent-header"))
            .isEqualTo("Chapter One");
    }
    
    // ======== Token Size Limits ========
    
    @Test
    void segments_should_not_exceed_450_tokens() {
        Document doc = Document.from(MarkdownFixtures.VERY_LONG_DOCUMENT);
        List<TextSegment> segments = splitter.split(doc);
        
        for (TextSegment segment : segments) {
            int tokens = tokenEstimator.estimateTokenCountInText(segment.text());
            assertThat(tokens)
                .as("Segment exceeded max tokens: %s...", 
                    segment.text().substring(0, Math.min(50, segment.text().length())))
                .isLessThanOrEqualTo(450);
        }
    }
    
    @Test
    void should_create_50_token_overlap_between_segments() {
        Document doc = Document.from(MarkdownFixtures.MULTI_PARAGRAPH_DOC);
        List<TextSegment> segments = splitter.split(doc);
        
        if (segments.size() > 1) {
            for (int i = 0; i < segments.size() - 1; i++) {
                String current = segments.get(i).text();
                String next = segments.get(i + 1).text();
                
                // Extract last ~50 tokens worth of content from current
                String[] currentWords = current.split("\\s+");
                String lastPortion = Arrays.stream(currentWords)
                    .skip(Math.max(0, currentWords.length - 60))
                    .collect(Collectors.joining(" "));
                
                // Should appear at start of next segment
                assertThat(next).containsIgnoringWhitespace(
                    lastPortion.substring(0, Math.min(100, lastPortion.length())));
            }
        }
    }
    
    // ======== Edge Cases ========
    
    @Test
    void should_handle_empty_document() {
        List<TextSegment> segments = splitter.split(Document.from(""));
        assertThat(segments).isEmpty();
    }
    
    @Test
    void should_handle_headers_only_document() {
        String headersOnly = "# Title\n## Section\n### Subsection";
        List<TextSegment> segments = splitter.split(Document.from(headersOnly));
        assertThat(segments).isNotEmpty();
    }
    
    @Test
    void should_preserve_markdown_tables() {
        String tableDoc = """
            # Data Table
            
            | Framework | Performance | Ease of Use |
            |-----------|-------------|-------------|
            | Spring    | High        | Medium      |
            | Quarkus   | Very High   | Medium      |
            """;
        
        List<TextSegment> segments = splitter.split(Document.from(tableDoc));
        assertThat(segments.get(0).text())
            .contains("| Framework |")
            .contains("| Spring    |");
    }
    
    @Test
    void should_handle_unicode_and_special_characters() {
        String unicodeDoc = """
            # Documentation 文档
            
            Émojis work: 🚀 ✅ 📊
            
            ## العربية Section
            
            Math symbols: α + β = γ, √2 ≈ 1.414
            """;
        
        List<TextSegment> segments = splitter.split(Document.from(unicodeDoc));
        String allText = segments.stream()
            .map(TextSegment::text)
            .collect(Collectors.joining());
        
        assertThat(allText).contains("🚀", "文档", "العربية", "√2");
    }
}
```

**Markdown test fixtures class:**

```java
public final class MarkdownFixtures {
    
    public static final String STANDARD_DOC = """
        # Spring Boot RAG Server Guide
        
        This document covers testing best practices.
        
        ## Getting Started
        
        First, set up your dependencies:
        
        ```xml
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>1.10.0</version>
        </dependency>
        ```
        
        ### Configuration
        
        Configure your application.yml:
        
        ```yaml
        spring:
          ai:
            embedding:
              base-url: http://localhost:8080
        ```
        
        ## Testing
        
        Use Testcontainers for integration tests.
        
        ### Unit Tests
        
        Focus on document splitters and transformers.
        
        ### Integration Tests
        
        Test vector store operations with real PostgreSQL.
        """;
    
    public static final String MULTI_PARAGRAPH_DOC = createMultiParagraphDoc();
    public static final String VERY_LONG_DOCUMENT = createVeryLongDocument(100);
    
    private static String createMultiParagraphDoc() {
        StringBuilder sb = new StringBuilder("# Multi-Paragraph Document\n\n");
        for (int i = 0; i < 20; i++) {
            sb.append("## Section ").append(i).append("\n\n");
            sb.append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ");
            sb.append("Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. ");
            sb.append("Ut enim ad minim veniam, quis nostrud exercitation ullamco.\n\n");
        }
        return sb.toString();
    }
    
    private static String createVeryLongDocument(int sections) {
        StringBuilder sb = new StringBuilder("# Very Long Document\n\n");
        for (int i = 0; i < sections; i++) {
            sb.append("## Section ").append(i).append("\n\n");
            sb.append("This is detailed content for section ").append(i).append(". ");
            sb.append("Lorem ipsum ".repeat(50)).append("\n\n");
        }
        return sb.toString();
    }
}
```

## Spring AI MCP SSE testing patterns

Test your MCP server endpoints with WebTestClient and full client integration:

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({PgVectorTestConfiguration.class, WireMockTestConfiguration.class})
class McpServerIT {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private WebTestClient webTestClient;
    
    // ======== SSE Endpoint Tests ========
    
    @Test
    void should_establish_sse_connection() {
        Flux<ServerSentEvent<String>> sseStream = webTestClient.get()
            .uri("/mcp/sse")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk()
            .returnResult(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .getResponseBody();
        
        StepVerifier.create(sseStream.take(Duration.ofSeconds(5)))
            .expectNextCount(1)
            .thenCancel()
            .verify(Duration.ofSeconds(10));
    }
    
    @Test
    void should_handle_mcp_message_endpoint() {
        String toolListRequest = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "tools/list",
                "params": {}
            }
            """;
        
        webTestClient.post()
            .uri("/mcp/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(toolListRequest)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.result.tools").isArray()
            .jsonPath("$.result.tools[0].name").isNotEmpty();
    }
    
    // ======== Full MCP Client Integration ========
    
    @Test
    void should_complete_full_mcp_flow() {
        WebClient webclient = WebClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
        
        WebFluxSseClientTransport transport = WebFluxSseClientTransport
            .builder(webclient).build();
        
        McpSyncClient client = McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(30))
            .build();
        
        try {
            // Initialize connection
            InitializeResult initResult = client.initialize();
            assertThat(initResult.serverInfo().name()).isNotBlank();
            
            // Discover tools
            List<Tool> tools = client.listTools();
            assertThat(tools).isNotEmpty();
            assertThat(tools.stream().map(Tool::name))
                .contains("search_documents", "get_document");
            
            // Call a tool
            CallToolResult result = client.callTool(
                new CallToolRequest("search_documents", 
                    Map.of("query", "spring boot testing")));
            
            assertThat(result.isError()).isFalse();
            assertThat(result.content()).isNotEmpty();
            
        } finally {
            client.closeGracefully();
        }
    }
    
    // ======== @McpTool Unit Tests ========
    
    @Test
    void should_discover_annotated_tools() {
        // Direct bean testing
        assertThat(ragTools.searchDocuments("test query"))
            .isNotNull();
    }
    
    @Test
    void should_handle_concurrent_connections() throws Exception {
        int numConnections = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numConnections);
        CountDownLatch latch = new CountDownLatch(numConnections);
        AtomicInteger successCount = new AtomicInteger();
        
        for (int i = 0; i < numConnections; i++) {
            executor.submit(() -> {
                try {
                    WebClient webclient = WebClient.builder()
                        .baseUrl("http://localhost:" + port).build();
                    McpSyncClient client = McpClient.sync(
                        WebFluxSseClientTransport.builder(webclient).build()
                    ).build();
                    
                    client.initialize();
                    client.listTools();
                    successCount.incrementAndGet();
                    client.closeGracefully();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(60, TimeUnit.SECONDS);
        assertThat(successCount.get()).isEqualTo(numConnections);
    }
}
```

## Testing @Retryable with spring-retry 2.0.11

Verify retry behavior, exponential backoff, and recovery methods:

```java
@SpringBootTest
@EnableRetry
@Import({WireMockTestConfiguration.class})
class EmbeddingClientRetryIT {
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Autowired
    private WireMockServer wireMockServer;
    
    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
        wireMockServer.resetScenarios();
    }
    
    @Test
    void should_retry_on_transient_failure_and_succeed() {
        // First two calls fail, third succeeds
        stubFor(post(urlEqualTo("/v1/embeddings"))
            .inScenario("Retry Flow")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("RETRY_1"));
        
        stubFor(post(urlEqualTo("/v1/embeddings"))
            .inScenario("Retry Flow")
            .whenScenarioStateIs("RETRY_1")
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("RETRY_2"));
        
        stubFor(post(urlEqualTo("/v1/embeddings"))
            .inScenario("Retry Flow")
            .whenScenarioStateIs("RETRY_2")
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBodyFile("embeddings/success-response.json")));
        
        // Should succeed after retries
        float[] embedding = embeddingService.embed("test text");
        
        assertThat(embedding).isNotNull();
        verify(exactly(3), postRequestedFor(urlEqualTo("/v1/embeddings")));
    }
    
    @Test
    void should_use_exponential_backoff() {
        List<Long> timestamps = Collections.synchronizedList(new ArrayList<>());
        
        stubFor(post(urlEqualTo("/v1/embeddings"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("{\"error\": \"Service unavailable\"}")));
        
        // Capture timestamps via custom callback
        wireMockServer.addMockServiceRequestListener((request, response) -> {
            if (request.getUrl().equals("/v1/embeddings")) {
                timestamps.add(System.currentTimeMillis());
            }
        });
        
        try {
            embeddingService.embed("test");
        } catch (Exception ignored) {}
        
        // Verify exponential delays: ~1000ms, ~2000ms, ~4000ms
        if (timestamps.size() >= 3) {
            long delay1 = timestamps.get(1) - timestamps.get(0);
            long delay2 = timestamps.get(2) - timestamps.get(1);
            
            assertThat(delay1).isGreaterThanOrEqualTo(900);   // ~1000ms
            assertThat(delay2).isGreaterThanOrEqualTo(1800);  // ~2000ms
            assertThat(delay2).isGreaterThan(delay1);         // Increasing
        }
    }
    
    @Test
    void should_invoke_recovery_after_max_attempts() {
        stubFor(post(urlEqualTo("/v1/embeddings"))
            .willReturn(aResponse().withStatus(503)));
        
        // Recovery method should return fallback
        float[] result = embeddingService.embed("test");
        
        // Assuming @Recover returns empty array as fallback
        assertThat(result).isEmpty();
        verify(exactly(3), postRequestedFor(urlEqualTo("/v1/embeddings")));
    }
    
    @Test
    void should_not_retry_on_4xx_client_errors() {
        stubFor(post(urlEqualTo("/v1/embeddings"))
            .willReturn(aResponse()
                .withStatus(400)
                .withBody("{\"error\": \"Bad request\"}")));
        
        assertThrows(BadRequestException.class, 
            () -> embeddingService.embed(""));
        
        verify(exactly(1), postRequestedFor(urlEqualTo("/v1/embeddings")));
    }
}
```

## RAG retrieval quality testing with metrics

Implement precision, recall, and MRR tests for your retrieval pipeline:

```java
class RetrievalQualityTest extends BaseVectorStoreIT {
    
    @Autowired
    private RetrievalService retrievalService;
    
    private static final List<GoldenTestCase> GOLDEN_DATASET = List.of(
        new GoldenTestCase(
            "What is dependency injection?",
            List.of("doc_spring_di_1", "doc_spring_di_2"),
            List.of(1.0, 0.8)
        ),
        new GoldenTestCase(
            "How to configure PostgreSQL?",
            List.of("doc_postgres_config", "doc_datasource_setup"),
            List.of(1.0, 0.9)
        )
    );
    
    @BeforeEach
    void seedGoldenDataset() {
        // Seed documents with known embeddings
        for (GoldenTestCase testCase : GOLDEN_DATASET) {
            for (String docId : testCase.relevantDocIds()) {
                seedDocument(docId, getDocumentContent(docId));
            }
        }
    }
    
    @Test
    void should_achieve_minimum_precision_at_5() {
        double totalPrecision = 0;
        
        for (GoldenTestCase testCase : GOLDEN_DATASET) {
            List<String> retrieved = retrievalService
                .search(testCase.query(), 5)
                .stream()
                .map(Document::getId)
                .toList();
            
            double precision = calculatePrecisionAtK(
                retrieved, testCase.relevantDocIds(), 5);
            totalPrecision += precision;
        }
        
        double avgPrecision = totalPrecision / GOLDEN_DATASET.size();
        assertThat(avgPrecision)
            .as("Average Precision@5 should be at least 0.6")
            .isGreaterThanOrEqualTo(0.6);
    }
    
    @Test
    void should_achieve_minimum_recall_at_10() {
        double totalRecall = 0;
        
        for (GoldenTestCase testCase : GOLDEN_DATASET) {
            List<String> retrieved = retrievalService
                .search(testCase.query(), 10)
                .stream()
                .map(Document::getId)
                .toList();
            
            double recall = calculateRecallAtK(
                retrieved, testCase.relevantDocIds(), 10);
            totalRecall += recall;
        }
        
        double avgRecall = totalRecall / GOLDEN_DATASET.size();
        assertThat(avgRecall)
            .as("Average Recall@10 should be at least 0.8")
            .isGreaterThanOrEqualTo(0.8);
    }
    
    @Test
    void should_achieve_minimum_mrr() {
        double totalReciprocalRank = 0;
        
        for (GoldenTestCase testCase : GOLDEN_DATASET) {
            List<String> retrieved = retrievalService
                .search(testCase.query(), 10)
                .stream()
                .map(Document::getId)
                .toList();
            
            Set<String> relevantSet = new HashSet<>(testCase.relevantDocIds());
            for (int i = 0; i < retrieved.size(); i++) {
                if (relevantSet.contains(retrieved.get(i))) {
                    totalReciprocalRank += 1.0 / (i + 1);
                    break;
                }
            }
        }
        
        double mrr = totalReciprocalRank / GOLDEN_DATASET.size();
        assertThat(mrr)
            .as("MRR should be at least 0.7")
            .isGreaterThanOrEqualTo(0.7);
    }
    
    private double calculatePrecisionAtK(List<String> retrieved, 
                                         List<String> relevant, int k) {
        Set<String> relevantSet = new HashSet<>(relevant);
        long hits = retrieved.stream()
            .limit(k)
            .filter(relevantSet::contains)
            .count();
        return (double) hits / k;
    }
    
    private double calculateRecallAtK(List<String> retrieved, 
                                      List<String> relevant, int k) {
        if (relevant.isEmpty()) return 0;
        Set<String> relevantSet = new HashSet<>(relevant);
        long hits = retrieved.stream()
            .limit(k)
            .filter(relevantSet::contains)
            .count();
        return (double) hits / relevant.size();
    }
    
    record GoldenTestCase(String query, List<String> relevantDocIds, 
                          List<Double> relevanceScores) {}
}
```

## Complete TestConfiguration assembly

Combine all configurations for your integration tests:

```java
@TestConfiguration(proxyBeanMethods = false)
@Import({PgVectorTestConfiguration.class, WireMockTestConfiguration.class})
public class RagServerTestConfiguration {
    
    @Bean
    public EmbeddingModel testEmbeddingModel(WireMockServer wireMockServer) {
        return OpenAiEmbeddingModel.builder()
            .apiKey("test-key")
            .baseUrl("http://localhost:" + wireMockServer.port())
            .modelName("text-embedding-3-small")
            .dimensions(1024)
            .build();
    }
    
    @Bean
    public VectorStore testVectorStore(JdbcTemplate jdbcTemplate, 
                                       EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
            .dimensions(1024)
            .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
            .indexType(PgVectorStore.PgIndexType.HNSW)
            .initializeSchema(true)
            .build();
    }
    
    @Bean
    public RetryTemplate testRetryTemplate() {
        RetryTemplate template = new RetryTemplate();
        
        ExponentialBackOffPolicy backoff = new ExponentialBackOffPolicy();
        backoff.setInitialInterval(100);  // Faster for tests
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(1000);
        template.setBackOffPolicy(backoff);
        
        SimpleRetryPolicy retry = new SimpleRetryPolicy(3);
        template.setRetryPolicy(retry);
        
        return template;
    }
}
```

## Key recommendations and test metrics

The most critical insight for RAG testing is maintaining **separate evaluation paths** for retrieval and generation. When tests fail, this separation immediately identifies whether the issue lies in document chunking, embedding quality, vector search configuration, or the generation prompt.

For your **450-500 token chunks with 50-75 overlap**, ensure chunking tests verify both boundaries are respected and that semantic coherence is maintained across chunk boundaries. The overlap should preserve enough context that retrieval of chunk N provides sufficient context to understand content that bridges chunks N and N+1.

Your **HNSW index** with pgvector should be tested with `ef_search` values between 40-100. Higher values increase recall but decrease query speed—your tests should validate the tradeoff matches your latency requirements (typically under **50ms p99** for 10,000 vectors).

For **WireMock latency simulation**, use `withLogNormalRandomDelay(median, sigma)` rather than fixed delays to realistically model cold start behavior. A median of 2000ms with sigma 0.5 accurately represents GPU model loading patterns.

Finally, integrate retrieval quality metrics into your CI/CD pipeline with **minimum thresholds**: Precision@5 ≥ 0.6, Recall@10 ≥ 0.8, MRR ≥ 0.7. These thresholds should be calibrated against your golden dataset and adjusted as your document corpus evolves.