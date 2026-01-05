# MCP HTTP Streamable Client Transport in Spring AI 1.1.2

The hypothetical `StreamableHttpMcpTransport` class referenced in the Alexandria tech-spec **does not exist**. The actual class is `HttpClientStreamableHttpTransport` from the MCP Java SDK, which Spring AI 1.1.2 uses as its underlying transport layer.

## Exact class names and package paths

**JDK HttpClient-based Transport (recommended for tests):**
```java
io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
```

**WebFlux-based Transport (for reactive applications):**
```java
io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport
```

The MCP Java SDK (`io.modelcontextprotocol.sdk:mcp`) provides both transports. Spring AI 1.1.2 wraps this SDK through its boot starters but uses these exact transport classes under the hood.

## Complete McpTestSupport.java implementation

```java
package com.example.alexandria.test;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Test support class for MCP integration tests using Streamable HTTP transport.
 * Uses Spring AI MCP SDK 1.1.2 compatible APIs.
 */
public class McpTestSupport {

    private McpSyncClient mcpClient;
    private HttpClientStreamableHttpTransport transport;

    /**
     * Creates an MCP client connected to the specified server URL.
     * 
     * @param serverUrl base URL of the MCP server (e.g., "http://localhost:8080")
     * @param endpoint  MCP endpoint path (default is "/mcp")
     */
    public void connect(String serverUrl, String endpoint) {
        // Create Streamable HTTP transport
        transport = HttpClientStreamableHttpTransport
            .builder(serverUrl)
            .endpoint(endpoint)  // Default is "/mcp"
            .clientBuilder(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30)))
            .build();

        // Create synchronous MCP client
        mcpClient = McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(20))
            .initializationTimeout(Duration.ofSeconds(20))
            .clientInfo(new McpSchema.Implementation("alexandria-test-client", "1.0.0"))
            .capabilities(McpSchema.ClientCapabilities.builder()
                .sampling()
                .build())
            .build();

        // Initialize the connection
        mcpClient.initialize();
    }

    /**
     * Connects to localhost:8080/mcp (default Alexandria RAG server endpoint).
     */
    public void connect() {
        connect("http://localhost:8080", "/mcp");
    }

    /**
     * Lists all available tools from the MCP server.
     */
    public ListToolsResult listTools() {
        return mcpClient.listTools();
    }

    /**
     * Calls a tool by name with the provided arguments.
     */
    public CallToolResult callTool(String toolName, Map<String, Object> arguments) {
        return mcpClient.callTool(new CallToolRequest(toolName, arguments));
    }

    /**
     * Gets the underlying MCP client for advanced operations.
     */
    public McpSyncClient getClient() {
        return mcpClient;
    }

    /**
     * Gracefully closes the MCP connection.
     */
    public void disconnect() {
        if (mcpClient != null) {
            mcpClient.closeGracefully();
        }
    }
}
```

## Required Maven dependencies

Add these to your `pom.xml`:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Spring AI MCP Client (includes MCP Java SDK) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-client</artifactId>
    </dependency>
    
    <!-- OR for direct MCP SDK usage without Spring Boot auto-config -->
    <dependency>
        <groupId>io.modelcontextprotocol.sdk</groupId>
        <artifactId>mcp</artifactId>
        <version>0.17.0</version>
    </dependency>
</dependencies>
```

## Integration test example

```java
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AlexandriaRagServerIntegrationTest {

    private McpTestSupport mcp;

    @BeforeEach
    void setUp() {
        mcp = new McpTestSupport();
        mcp.connect("http://localhost:8080", "/mcp");
    }

    @AfterEach
    void tearDown() {
        mcp.disconnect();
    }

    @Test
    void shouldListAvailableTools() {
        var tools = mcp.listTools();
        
        assertThat(tools.tools()).isNotEmpty();
        assertThat(tools.tools())
            .extracting("name")
            .contains("search_documents", "retrieve_context");
    }

    @Test
    void shouldSearchDocuments() {
        var result = mcp.callTool("search_documents", Map.of(
            "query", "machine learning fundamentals",
            "limit", 10
        ));
        
        assertThat(result.content()).isNotEmpty();
    }
}
```

## Key corrections to the tech-spec

| Tech-spec (hypothetical) | Actual class name |
|-------------------------|-------------------|
| `StreamableHttpMcpTransport.builder()` | `HttpClientStreamableHttpTransport.builder()` |
| Package unknown | `io.modelcontextprotocol.client.transport` |

## Alternative: WebFlux-based transport

For reactive applications, use `WebClientStreamableHttpTransport`:

```java
import io.modelcontextprotocol.client.transport.WebClientStreamableHttpTransport;
import org.springframework.web.reactive.function.client.WebClient;

WebClient.Builder webClientBuilder = WebClient.builder()
    .baseUrl("http://localhost:8080");

var transport = WebClientStreamableHttpTransport
    .builder(webClientBuilder)
    .build();

McpSyncClient client = McpClient.sync(transport)
    .requestTimeout(Duration.ofSeconds(20))
    .build();
```

This requires the `spring-ai-starter-mcp-client-webflux` or `io.modelcontextprotocol.sdk:mcp-spring-webflux` dependency.

## Summary

The **correct class name** for creating an MCP HTTP Streamable client transport is `HttpClientStreamableHttpTransport` from package `io.modelcontextprotocol.client.transport`. The builder pattern is `HttpClientStreamableHttpTransport.builder("http://localhost:8080").build()`. Spring AI 1.1.2 uses MCP Java SDK version **0.17.0** which provides this transport class.