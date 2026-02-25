package dev.alexandria.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Round-trip integration tests for all 7 MCP tools via McpSyncClient over SSE transport.
 *
 * <p>Each test exercises the full JSON-RPC communication path: McpSyncClient -> SSE transport ->
 * Spring AI MCP server -> McpToolService -> service layer -> pgvector database -> response
 * formatting -> SSE transport -> McpSyncClient.
 *
 * <p>Tests verify the MCP transport contract, not search quality or crawl behaviour. The Crawl4AI
 * sidecar is not running; tools that trigger async crawls verify only the synchronous response.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("web")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Sql(value = "/mcp/seed-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SuppressWarnings("NullAway.Init")
class McpRoundTripIT {

  @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(
          DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

  static {
    postgres.start();
  }

  @LocalServerPort int port;

  McpSyncClient client;

  @BeforeAll
  void setupClient() {
    var transport =
        HttpClientSseClientTransport.builder("http://localhost:" + port)
            .sseEndpoint("/sse")
            .build();
    client =
        McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(30))
            .initializationTimeout(Duration.ofSeconds(30))
            .build();
    client.initialize();
  }

  @AfterAll
  void teardownClient() {
    if (client != null) {
      client.close();
    }
  }

  // ---------------------------------------------------------------------------
  // search_docs
  // ---------------------------------------------------------------------------

  @Test
  void search_docs_happy_path_returns_results() {
    CallToolResult result =
        client.callTool(new CallToolRequest("search_docs", Map.of("query", "auto-configuration")));

    String text = extractText(result);
    assertThat(text).contains("auto-configur");
    assertThat(text).contains("Spring Boot");
    assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
  }

  @Test
  void search_docs_with_source_filter_returns_filtered_results() {
    CallToolResult result =
        client.callTool(
            new CallToolRequest(
                "search_docs", Map.of("query", "embedding model", "source", "LangChain4j")));

    String text = extractText(result);
    assertThat(text).isNotBlank();
    assertThat(text).contains("LangChain4j");
    assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
  }

  @Test
  void search_docs_with_empty_query_returns_error() {
    CallToolResult result =
        client.callTool(new CallToolRequest("search_docs", Map.of("query", "")));

    String text = extractText(result);
    assertThat(text).contains("Error");
    assertThat(text).containsIgnoringCase("query");
  }

  @Test
  void search_docs_with_no_matches_returns_no_results_message() {
    CallToolResult result =
        client.callTool(
            new CallToolRequest(
                "search_docs",
                Map.of("query", "quantum computing superconductor", "minScore", 0.99)));

    String text = extractText(result);
    assertThat(text).containsIgnoringCase("no result");
  }

  // ---------------------------------------------------------------------------
  // list_sources
  // ---------------------------------------------------------------------------

  @Test
  void list_sources_returns_all_sources() {
    CallToolResult result = client.callTool(new CallToolRequest("list_sources", Map.of()));

    String text = extractText(result);
    assertThat(text).contains("Spring Boot");
    assertThat(text).contains("LangChain4j");
    assertThat(text).contains("React");
    assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
  }

  @Test
  void list_sources_includes_status_info() {
    CallToolResult result = client.callTool(new CallToolRequest("list_sources", Map.of()));

    String text = extractText(result);
    assertThat(text).contains("INDEXED");
    assertThat(text).contains("PENDING");
  }

  // ---------------------------------------------------------------------------
  // add_source
  // ---------------------------------------------------------------------------

  @Test
  void add_source_creates_new_source() {
    CallToolResult result =
        client.callTool(
            new CallToolRequest(
                "add_source",
                Map.of(
                    "url",
                    "https://docs.example.com/test-" + System.nanoTime(),
                    "name",
                    "Test Docs")));

    String text = extractText(result);
    assertThat(text).containsIgnoringCase("created");
    assertThat(text).contains("Test Docs");
    assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
  }

  @Test
  void add_source_with_empty_url_returns_error() {
    CallToolResult result =
        client.callTool(new CallToolRequest("add_source", Map.of("url", "", "name", "Bad Source")));

    String text = extractText(result);
    assertThat(text).contains("Error");
    assertThat(text).containsIgnoringCase("url");
  }

  @Test
  void add_source_with_scope_params_is_accepted() {
    CallToolResult result =
        client.callTool(
            new CallToolRequest(
                "add_source",
                Map.of(
                    "url",
                    "https://docs.example.com/scoped-" + System.nanoTime(),
                    "name",
                    "Scoped Docs",
                    "allowPatterns",
                    "/docs/**,/api/**",
                    "maxDepth",
                    3)));

    String text = extractText(result);
    assertThat(text).containsIgnoringCase("created");
    assertThat(text).contains("Scoped Docs");
    assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
  }

  // ---------------------------------------------------------------------------
  // remove_source
  // ---------------------------------------------------------------------------

  @Test
  void remove_source_deletes_existing_source() {
    // Remove the React source (PENDING, no chunks)
    CallToolResult result =
        client.callTool(
            new CallToolRequest(
                "remove_source", Map.of("sourceId", "00000000-0000-0000-0000-000000000003")));

    String text = extractText(result);
    assertThat(text).contains("React");
    assertThat(text).containsIgnoringCase("removed");
    assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
  }

  @Test
  void remove_source_with_invalid_uuid_returns_error() {
    CallToolResult result =
        client.callTool(new CallToolRequest("remove_source", Map.of("sourceId", "not-a-uuid")));

    String text = extractText(result);
    assertThat(text).contains("Error");
    assertThat(text).containsIgnoringCase("invalid");
  }

  @Test
  void remove_source_with_non_existent_id_returns_not_found() {
    CallToolResult result =
        client.callTool(
            new CallToolRequest(
                "remove_source", Map.of("sourceId", "ffffffff-ffff-ffff-ffff-ffffffffffff")));

    String text = extractText(result);
    assertThat(text).contains("Error");
    assertThat(text).containsIgnoringCase("not found");
  }

  // ---------------------------------------------------------------------------
  // crawl_status
  // ---------------------------------------------------------------------------

  @Test
  void crawl_status_for_indexed_source_shows_summary() {
    CallToolResult result =
        client.callTool(
            new CallToolRequest(
                "crawl_status", Map.of("sourceId", "00000000-0000-0000-0000-000000000001")));

    String text = extractText(result);
    assertThat(text).contains("Spring Boot");
    assertThat(text).contains("INDEXED");
    assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
  }

  @Test
  void crawl_status_with_invalid_uuid_returns_error() {
    CallToolResult result =
        client.callTool(new CallToolRequest("crawl_status", Map.of("sourceId", "bad-uuid")));

    String text = extractText(result);
    assertThat(text).contains("Error");
    assertThat(text).containsIgnoringCase("invalid");
  }

  // ---------------------------------------------------------------------------
  // recrawl_source
  // ---------------------------------------------------------------------------

  @Test
  void recrawl_source_triggers_recrawl() {
    CallToolResult result =
        client.callTool(
            new CallToolRequest(
                "recrawl_source", Map.of("sourceId", "00000000-0000-0000-0000-000000000001")));

    String text = extractText(result);
    assertThat(text).containsIgnoringCase("recrawl");
    assertThat(text).containsIgnoringCase("started");
    assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
  }

  @Test
  void recrawl_source_with_non_existent_id_returns_not_found() {
    CallToolResult result =
        client.callTool(
            new CallToolRequest(
                "recrawl_source", Map.of("sourceId", "ffffffff-ffff-ffff-ffff-ffffffffffff")));

    String text = extractText(result);
    assertThat(text).contains("Error");
    assertThat(text).containsIgnoringCase("not found");
  }

  @Test
  void recrawl_source_with_invalid_uuid_returns_error() {
    CallToolResult result =
        client.callTool(new CallToolRequest("recrawl_source", Map.of("sourceId", "invalid-uuid")));

    String text = extractText(result);
    assertThat(text).contains("Error");
    assertThat(text).containsIgnoringCase("invalid");
  }

  // ---------------------------------------------------------------------------
  // index_statistics
  // ---------------------------------------------------------------------------

  @Test
  void index_statistics_returns_global_stats() {
    CallToolResult result = client.callTool(new CallToolRequest("index_statistics", Map.of()));

    String text = extractText(result);
    assertThat(text).containsIgnoringCase("total chunks");
    assertThat(text).containsIgnoringCase("total sources");
    assertThat(text).containsIgnoringCase("storage size");
    assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
  }

  @Test
  void index_statistics_contains_embedding_dimensions() {
    CallToolResult result = client.callTool(new CallToolRequest("index_statistics", Map.of()));

    String text = extractText(result);
    assertThat(text).contains("384");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static String extractText(CallToolResult result) {
    return ((TextContent) result.content().getFirst()).text();
  }
}
