# Spring AI MCP SDK 1.1.2 GA: Correct Package Imports

Spring AI provides **two distinct approaches** for defining MCP server tools, and they use different packages. The choice depends on whether you want the standard Spring AI abstraction or MCP-specific functionality.

## The two annotation systems explained

Spring AI 1.1.x supports MCP tools via two pathways: the **standard `@Tool` annotation** from Spring AI's core tool system (which gets auto-converted to MCP tool specifications), and the **`@McpTool` annotation** from the Spring AI Community's mcp-annotations module (providing MCP-specific features). Both work with `spring-ai-starter-mcp-server-webmvc`.

### Option 1: Standard Spring AI annotations (recommended for most use cases)

```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
```

**Maven artifact**: Already included in `spring-ai-starter-mcp-server-webmvc`

These annotations work seamlessly with the MCP Server Boot Starter—Spring AI automatically converts `@Tool` methods to MCP tool specifications. This is the approach shown in official Spring AI documentation and Microsoft/Azure tutorials.

### Option 2: MCP-specific annotations (for advanced MCP features)

```java
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
```

**Maven artifact** (add separately):
```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>mcp-annotations</artifactId>
    <version>0.8.0</version> <!-- Check Maven Central for latest -->
</dependency>
```

These annotations provide MCP-specific capabilities like `McpSyncRequestContext`, `McpTransportContext`, progress tokens via `@McpProgressToken`, and metadata access—features not available with standard `@Tool`.

## CallToolResult class location

**This class is from the MCP Java SDK**, not Spring AI:

```java
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
```

**Maven artifact**: Transitive dependency from Spring AI MCP starters (typically `io.modelcontextprotocol.sdk:mcp`)

`CallToolResult` is a nested class within `McpSchema`. Usage example:

```java
return McpSchema.CallToolResult.builder()
    .content(List.of(new McpSchema.TextContent("Result text")))
    .isError(false)
    .build();
```

## Complete import examples for both approaches

### Using standard @Tool (most common)

```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {
    
    @Tool(description = "Get current weather for a city")
    public String getWeather(
            @ToolParam(description = "City name", required = true) String city) {
        return "Weather in " + city + ": Sunny, 22°C";
    }
}
```

Register via bean configuration:
```java
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;

@Bean
public List<ToolCallback> weatherTools(WeatherService service) {
    return ToolCallbacks.from(service);
}
```

### Using @McpTool with CallToolResult

```java
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;

@Service
public class CalculatorService {
    
    @McpTool(name = "calculator", description = "Perform calculations")
    public CallToolResult calculate(
            @McpToolParam(description = "Math expression") String expression) {
        String result = evaluateExpression(expression);
        return CallToolResult.builder()
            .content(List.of(new TextContent(result)))
            .isError(false)
            .build();
    }
}
```

## Related MCP Java SDK imports you may need

| Class | Import Statement |
|-------|------------------|
| `McpSchema` | `import io.modelcontextprotocol.spec.McpSchema;` |
| `CallToolResult` | `import io.modelcontextprotocol.spec.McpSchema.CallToolResult;` |
| `TextContent` | `import io.modelcontextprotocol.spec.McpSchema.TextContent;` |
| `Tool` (MCP definition) | `import io.modelcontextprotocol.spec.McpSchema.Tool;` |

## Key configuration for annotation scanning

Enable MCP annotation scanning in `application.yml`:

```yaml
spring:
  ai:
    mcp:
      server:
        name: my-mcp-server
        version: 1.0.0
        annotation-scanner:
          enabled: true  # Required for @McpTool scanning
```

## Summary: Which approach to choose

Use **`@Tool`/`@ToolParam`** from `org.springframework.ai.tool.annotation` if you want the standard Spring AI experience with automatic MCP integration. Use **`@McpTool`/`@McpToolParam`** from `org.springaicommunity.mcp.annotation` if you need MCP-specific features like request context, progress tracking, or direct `CallToolResult` returns. Both work with the `spring-ai-starter-mcp-server-webmvc` artifact and HTTP Streamable transport.