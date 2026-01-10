package dev.alexandria.test;

import java.time.Duration;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.Implementation;

/**
 * Support pour les tests du serveur MCP.
 *
 * <p>Fournit des helpers pour créer des clients MCP synchrones connectés au serveur de test.
 */
public final class McpTestSupport {

  /** Endpoint MCP (HTTP Streamable transport). */
  public static final String MCP_ENDPOINT = "/mcp";

  /** Timeout par défaut pour les requêtes MCP (30s). */
  public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  /** Client info pour les tests. */
  private static final Implementation CLIENT_INFO =
      new Implementation("alexandria-test-client", "1.0.0");

  private McpTestSupport() {}

  /**
   * Crée et initialise un client MCP synchrone connecté au serveur de test.
   *
   * @param port port du serveur de test
   * @return client MCP synchrone configuré et initialisé
   */
  public static McpSyncClient createClient(int port) {
    String baseUrl = String.format("http://localhost:%d%s", port, MCP_ENDPOINT);

    HttpClientStreamableHttpTransport transport =
        HttpClientStreamableHttpTransport.builder(baseUrl).build();

    McpSyncClient client =
        McpClient.sync(transport).clientInfo(CLIENT_INFO).requestTimeout(REQUEST_TIMEOUT).build();

    client.initialize();
    return client;
  }

  /**
   * Ferme proprement un client MCP.
   *
   * @param client client à fermer
   */
  public static void closeClient(McpSyncClient client) {
    if (client != null) {
      client.closeGracefully();
    }
  }
}
