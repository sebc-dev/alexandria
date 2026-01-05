# Spring AI MCP SDK 1.1.2 progress notification API

The progress notification API in Spring AI MCP SDK 1.1.2 GA uses **`McpSyncServerExchange.progressNotification(ProgressNotification)`** for sending progress updates, though this interface is now deprecated in favor of **`McpSyncRequestContext`**. Both APIs are stable and fully supported, with the newer context-based approach offering a unified interface for stateful and stateless operations. The progress token is obtained via the **`@McpProgressToken`** annotation on a String parameter.

## Exact method signatures for progress notifications

The primary method on `McpSyncServerExchange` for sending progress notifications is:

```java
void progressNotification(ProgressNotification notification)
```

For async servers using `McpAsyncServerExchange`, the signature returns a reactive type:

```java
Mono<Void> progressNotification(ProgressNotification notification)
```

The **`ProgressNotification`** class (located in `io.modelcontextprotocol.spec.McpSchema`) accepts these parameters via its builder or constructor:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `progressToken` | `String` (ProgressToken) | **Yes** | Opaque token from the request's `_meta` field |
| `progress` | `double` | **Yes** | Current progress value (must increase monotonically) |
| `total` | `Double` | No | Total progress value or item count if known |
| `message` | `String` | No | Human-readable status description (added in MCP spec 2025-03-26) |

Construction uses either the builder pattern or direct instantiation:

```java
// Builder pattern
ProgressNotification.builder()
    .progressToken("abc123")
    .progress(0.5)
    .total(1.0)
    .message("Processing...")
    .build();

// Direct constructor
new ProgressNotification(progressToken, 0.5, 1.0, "Processing...");
```

## Obtaining McpSyncServerExchange in @McpTool methods

The exchange object is **automatically injected** when declared as a parameter in an `@McpTool` annotated method. Spring AI's annotation processor recognizes this special parameter type and excludes it from the generated JSON schema. The progress token is obtained separately via the `@McpProgressToken` annotation:

```java
@McpTool(name = "long-operation", description = "Long-running operation with progress")
public String performLongOperation(
        @McpProgressToken String progressToken,           // Auto-injected, can be null
        @McpToolParam(description = "Task name") String task,
        McpSyncServerExchange exchange) {                 // Auto-injected, never null
    
    if (progressToken != null) {
        exchange.progressNotification(new ProgressNotification(
            progressToken, 0.0, 1.0, "Starting " + task));
        
        // Perform work with progress updates...
        for (int i = 1; i <= 10; i++) {
            Thread.sleep(1000);
            exchange.progressNotification(new ProgressNotification(
                progressToken, (double) i / 10, 1.0, 
                String.format("Processing... %d%%", i * 10)));
        }
    }
    return "Completed: " + task;
}
```

**Critical note**: The `@McpProgressToken` parameter **can be null** if the client did not provide a progress token in the request. Always perform a null check before sending notifications.

## API stability and deprecation warnings

The `McpSyncServerExchange` interface is **deprecated but still fully supported** in Spring AI 1.1.2. The official documentation states:

> **(Deprecated and replaced by McpSyncRequestContext)** `McpSyncServerExchange` - Legacy parameter type for stateful synchronous operations. Use `McpSyncRequestContext` instead for a unified interface that works with both stateful and stateless operations.

The recommended replacement is **`McpSyncRequestContext`**, which provides a cleaner API with convenience methods:

```java
@McpTool(name = "modern-progress", description = "Using recommended context API")
public String modernProgress(
        McpSyncRequestContext context,
        @McpToolParam(description = "Task") String task) {
    
    String progressToken = context.request().progressToken();
    
    if (progressToken != null) {
        // Convenient builder-based progress method
        context.progress(p -> p
            .progress(0.5)
            .total(1.0)
            .message("Halfway done"));
        
        // Simple percentage shorthand
        context.progress(75);  // 75% complete
    }
    
    // Additional convenience methods
    context.info("Processing task: " + task);  // Logging notification
    context.ping();                             // Client ping
    
    return "Done: " + task;
}
```

**Important limitation**: Methods using `McpSyncServerExchange` or `McpSyncRequestContext` are **filtered out in stateless server mode**. These bidirectional features only work with stateful servers configured via `spring.ai.mcp.server.type: SYNC` and `spring.ai.mcp.server.protocol: STREAMABLE`.

## MCP specification compliance

Spring AI's implementation aligns with the MCP specification for progress notifications. The MCP spec defines the JSON-RPC notification format:

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/progress",
  "params": {
    "progressToken": "abc123",
    "progress": 50,
    "total": 100,
    "message": "Processing items..."
  }
}
```

Key compliance requirements that Spring AI implements:

- **Progress values must increase monotonically** with each notification
- **Progress and total may be floating-point numbers**
- **No server capability declaration required** for progress support
- **Token uniqueness** must be maintained by the client across active requests
- Notifications **must stop after operation completion**

The `message` field was added in MCP specification version **2025-03-26**, and Spring AI 1.1.2 fully supports this field.

## Complete HTTP Streamable transport configuration

For the Streamable HTTP transport (not deprecated SSE), configure your Spring Boot application:

```yaml
spring:
  ai:
    mcp:
      server:
        name: progress-demo-server
        version: 1.0.0
        type: SYNC                    # Required for McpSyncServerExchange
        protocol: STREAMABLE          # HTTP Streamable transport
        capabilities:
          tool: true
          resource: true
          prompt: true
        streamable-http:
          mcp-endpoint: /mcp          # Default endpoint
          keep-alive-interval: 30s    # Optional
```

**Maven dependency** for WebMVC-based servers:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

## Auto-injected special parameters reference

Spring AI recognizes these parameter types and auto-injects them (excluded from JSON schema):

| Parameter Type | Null Safety | Purpose |
|----------------|-------------|---------|
| `McpSyncRequestContext` | Never null | **Recommended**: Unified context for all operations |
| `McpSyncServerExchange` | Never null | **Deprecated**: Legacy stateful exchange |
| `@McpProgressToken String` | **Can be null** | Progress token from client request |
| `McpMeta` | Never null (empty if no metadata) | Request metadata access |
| `CallToolRequest` | Never null | Full request for dynamic schemas |
| `McpTransportContext` | Never null | Lightweight stateless context |

## Conclusion

The Spring AI MCP SDK 1.1.2 provides a **stable, GA-quality** progress notification API. While `McpSyncServerExchange.progressNotification()` remains functional, new implementations should use `McpSyncRequestContext` for its cleaner API and forward compatibility. The `@McpProgressToken` annotation is the standard mechanism for obtaining the client-provided token, and null-checking is essential since clients may not request progress tracking. The implementation fully complies with MCP specification requirements including the 2025-03-26 `message` field addition.