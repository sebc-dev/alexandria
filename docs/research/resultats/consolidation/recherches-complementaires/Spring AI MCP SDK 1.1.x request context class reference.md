# Spring AI MCP SDK 1.1.x request context class reference

The recommended class for accessing request context in Spring AI MCP SDK 1.1.x `@McpTool` annotated methods is **`McpSyncRequestContext`** — a unified interface that supersedes the deprecated `McpSyncServerExchange`. This new API works seamlessly with both stateful and stateless server configurations and provides convenient helper methods for logging, progress notifications, sampling, and elicitation.

## Correct class name and deprecation status

For **synchronous SYNC server type** operations:
- **Recommended**: `McpSyncRequestContext` — unified interface for stateful and stateless operations
- **Deprecated**: `McpSyncServerExchange` — legacy class, still functional but should be migrated

For **asynchronous ASYNC server type** operations:
- **Recommended**: `McpAsyncRequestContext` — reactive (Mono-based) unified interface
- **Deprecated**: `McpAsyncServerExchange` — legacy class

The package for these context classes is part of the Spring AI MCP annotations module. When using Spring AI Boot Starters, these classes are automatically available through the `spring-ai-mcp-annotations` dependency.

## Import statement and dependencies

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-annotations</artifactId>
</dependency>
```

This dependency is **automatically included** when using any MCP Boot Starter:
- `spring-ai-starter-mcp-server`
- `spring-ai-starter-mcp-server-webmvc`
- `spring-ai-starter-mcp-server-webflux`

## Parameter injection in @McpTool methods

Simply add `McpSyncRequestContext` as a method parameter — the framework **automatically injects** it and excludes it from JSON schema generation:

```java
import org.springframework.ai.mcp.server.McpSyncRequestContext;
import org.springframework.ai.mcp.server.annotation.McpTool;
import org.springframework.ai.mcp.server.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class MyToolService {

    @McpTool(name = "process-data", description = "Process data with request context")
    public String processData(
            McpSyncRequestContext context,
            @McpToolParam(description = "Data to process", required = true) String data) {
        
        // Send logging notification
        context.info("Processing data: " + data);
        
        // Send progress updates (client must set progress token to receive)
        context.progress(50); // 50% complete
        
        // Ping the client
        context.ping();
        
        return "Processed: " + data.toUpperCase();
    }
}
```

## Available methods on McpSyncRequestContext

The context object provides these capabilities organized by functionality:

### Request access
| Method | Description |
|--------|-------------|
| `request()` | Access the original request object (`CallToolRequest`, etc.) |
| `requestMeta()` | Access metadata from the request (replaces separate `McpMeta` parameter) |

### Server context access
| Method | Description |
|--------|-------------|
| `exchange()` | Access underlying `McpSyncServerExchange` (stateful operations only) |
| `transportContext()` | Access `McpTransportContext` (stateless operations) |
| `isStateless()` | Check if running in stateless mode |

### Logging notifications
| Method | Description |
|--------|-------------|
| `log(Consumer<LoggingSpec>)` | Send log messages with custom configuration |
| `debug(String message)` | Send DEBUG level log |
| `info(String message)` | Send INFO level log |
| `warn(String message)` | Send WARNING level log |
| `error(String message)` | Send ERROR level log |

### Progress notifications
| Method | Description |
|--------|-------------|
| `progress(int percentage)` | Send simple percentage progress (0-100) |
| `progress(Consumer<ProgressSpec>)` | Send progress with full configuration |

### Bidirectional capabilities (stateful only)
| Method | Description |
|--------|-------------|
| `rootsEnabled()` | Check if roots capability is supported |
| `roots()` | Access root directories |
| `elicitEnabled()` | Check if elicitation is supported |
| `elicit(Class<T>)` | Request user input, returns `StructuredElicitResult<T>` |
| `elicit(Consumer<ElicitationSpec>, Class<T>)` | Request user input with custom config |
| `sampleEnabled()` | Check if sampling is supported |
| `sample(String prompt)` | Request LLM sampling |
| `sample(Consumer<SamplingSpec>)` | Request sampling with configuration |
| `ping()` | Send ping to check connection |

## Complete code example with logging and notifications

```java
import org.springframework.ai.mcp.server.McpSyncRequestContext;
import org.springframework.ai.mcp.server.annotation.McpTool;
import org.springframework.ai.mcp.server.annotation.McpToolParam;
import org.springframework.stereotype.Component;

public record UserInfo(String name, String email, int age) {}

@Component
public class AdvancedToolService {

    @McpTool(name = "advanced-tool", description = "Tool with full server capabilities")
    public String advancedTool(
            McpSyncRequestContext context,
            @McpToolParam(description = "Input data", required = true) String input) {
        
        // === LOGGING ===
        context.debug("Debug: Starting operation");
        context.info("Processing: " + input);
        
        // === PROGRESS TRACKING ===
        // Simple percentage
        context.progress(25);
        
        // Or with full configuration
        context.progress(p -> p
            .progress(0.5)
            .total(1.0)
            .message("Processing..."));
        
        // === PING CLIENT ===
        context.ping();
        
        // === ELICITATION (if supported) ===
        if (context.elicitEnabled()) {
            StructuredElicitResult<UserInfo> result = context.elicit(
                e -> e.message("Please provide your information"),
                UserInfo.class
            );
            
            if (result.action() == ElicitResult.Action.ACCEPT) {
                UserInfo userInfo = result.structuredContent();
                return "Processed for user: " + userInfo.name();
            }
        }
        
        // === SAMPLING (if supported) ===
        if (context.sampleEnabled()) {
            CreateMessageResult samplingResult = context.sample(
                "Generate a response for: " + input
            );
            // Use sampling result...
        }
        
        return "Completed processing: " + input;
    }
}
```

## Legacy McpSyncServerExchange approach (deprecated)

For reference, the deprecated approach using `McpSyncServerExchange` directly:

```java
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;

@McpTool(name = "legacy-tool", description = "Tool using legacy exchange")
public String legacyTool(
        McpSyncServerExchange exchange,  // Deprecated - use McpSyncRequestContext
        @McpProgressToken String progressToken,
        @McpToolParam(description = "Input") String input) {
    
    // Logging with legacy API
    exchange.loggingNotification(LoggingMessageNotification.builder()
        .level(LoggingLevel.INFO)
        .data("Processing: " + input)
        .build());
    
    // Progress with legacy API
    if (progressToken != null) {
        exchange.progressNotification(new ProgressNotification(
            progressToken, 0.5, 1.0, "Processing..."));
    }
    
    return "Processed: " + input;
}
```

## Critical considerations for SYNC server with HTTP Streamable transport

When using `spring.ai.mcp.server.type=SYNC` with HTTP Streamable transport:

1. **Stateful vs stateless**: Methods using `McpSyncRequestContext` work on **stateful servers** (default). For stateless servers (`spring.ai.mcp.server.protocol=STATELESS`), these methods are filtered out with a warning logged.

2. **Bidirectional operations**: `elicit()`, `sample()`, and `roots()` require a **stateful** server and client support. Always check `elicitEnabled()` or `sampleEnabled()` before calling.

3. **Progress tokens**: The client must include a progress token in its request for the server to send progress updates. Access via `context.request().progressToken()`.

4. **Configuration**:
```yaml
spring:
  ai:
    mcp:
      server:
        type: SYNC  # or ASYNC for reactive
        protocol: STATEFUL  # or STATELESS
        annotation-scanner:
          enabled: true
```

The `McpSyncRequestContext` unified interface is the **recommended approach** for Spring AI MCP SDK 1.1.2 GA, providing cleaner code, better capabilities checking, and a consistent API across both stateful and stateless operations.