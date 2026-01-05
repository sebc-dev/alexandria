# Spring AI 1.1.2 tool exception handling explained

**Yes, `spring.ai.tools.throw-exception-on-error` exists in Spring AI 1.1.2 with a default value of `false`**, meaning tool execution errors are converted to messages sent back to the AI model rather than propagated as exceptions. However, for MCP server-side tools using `@McpTool` annotations, a separate mcp-annotations library mechanism handles exceptions independently of this property.

## The property controls how the AI model receives tool errors

The `spring.ai.tools.throw-exception-on-error` property controls the behavior of `DefaultToolExecutionExceptionProcessor`, which is autoconfigured by Spring Boot starters:

| Setting | Behavior |
|---------|----------|
| `false` (default) | RuntimeException messages are converted to text and sent back to the AI model, allowing it to process and respond to the error |
| `true` | Exceptions are thrown and propagated to the caller for explicit handling |

**Critical caveat**: Checked exceptions (like `IOException`) and Errors (like `OutOfMemoryError`) are **always thrown** regardless of this setting. Only `RuntimeException` subclasses are affected by the property.

The key classes involved are `ToolExecutionExceptionProcessor` (the functional interface), `DefaultToolExecutionExceptionProcessor` (the autoconfigured implementation), and `DefaultToolCallingManager` (which catches `ToolExecutionException` and delegates to the processor).

## Configuration examples for your Alexandria RAG Server

**application.properties:**
```properties
# Default behavior - errors sent to AI model
spring.ai.tools.throw-exception-on-error=false

# Alternative - throw exceptions for caller handling  
spring.ai.tools.throw-exception-on-error=true
```

**Programmatic configuration:**
```java
@Bean
ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
    return new DefaultToolExecutionExceptionProcessor(true);
}
```

## MCP tools have their own exception handling mechanism

For your setup using `@McpTool` annotations with HTTP Streamable transport, the mcp-annotations library provides a **separate exception handling system** that operates independently of `spring.ai.tools.throw-exception-on-error`:

When an `@McpTool` method throws an exception, the mcp-annotations framework converts it to a `CallToolResult` with `isError(true)`:

```java
CallToolResult.builder()
    .isError(true)
    .addTextContent("Error invoking method: " + exception.getMessage())
    .build()
```

The JSON response sent to Claude Code looks like:
```json
{
  "isError": true,
  "content": [{ "type": "text", "text": "Error invoking method: file not found" }]
}
```

By default, the mcp-annotations library catches `Exception.class` (all exceptions) and converts them to error responses. You can customize this per-tool via the `toolCallExceptionClass` configuration or by overriding `AbstractMcpToolProvider#doGetToolCallException()`.

## A critical bug affecting MCP tools was fixed in 1.1.0-M1

Issues #2857 and #3023 documented a significant problem where MCP tool errors would **terminate the entire execution flow** instead of allowing the AI to recover:

**The bug**: `SyncMcpToolCallback` threw `IllegalStateException` when an MCP tool returned an error, but `DefaultToolCallingManager` only caught `ToolExecutionException`. This caused MCP errors to bypass `ToolExecutionExceptionProcessor` entirely and interrupt the agentic loop.

**The fix (1.1.0-M1)**: MCP tool callbacks were updated to throw `ToolExecutionException` instead of `IllegalStateException`, ensuring proper integration with the exception processor. Since you're using **Spring AI 1.1.2 GA**, this fix is included.

## How the two mechanisms interact

| Component | Controls | Affected by property? |
|-----------|----------|----------------------|
| `@McpTool` server-side methods | mcp-annotations library converts exceptions to `CallToolResult` | No - uses its own mechanism |
| MCP client callbacks (`SyncMcpToolCallback`) | `ToolExecutionExceptionProcessor` processes `ToolExecutionException` | Yes - after fix in 1.1.0-M1 |
| Regular `@Tool` functions | `ToolExecutionExceptionProcessor` | Yes |

For your Alexandria RAG Server acting as an MCP server, the **mcp-annotations exception handling applies first** (server-side), converting exceptions to MCP error responses. The `spring.ai.tools.throw-exception-on-error` property would only matter if your server also acts as an MCP client calling other tools.

## Recommended configuration for Claude Code integration

For optimal behavior with Claude Code as an MCP client:

1. **Keep the default** `spring.ai.tools.throw-exception-on-error=false` - this allows error recovery when your server calls other tools

2. **Return descriptive error messages** from your `@McpTool` methods - Claude Code can understand and work around errors if the messages are clear:

```java
@McpTool(name = "search-documents", description = "Search the document index")
public String searchDocuments(
        @McpToolParam(description = "Search query", required = true) String query) {
    try {
        return documentService.search(query);
    } catch (IndexNotFoundException e) {
        // Descriptive error that Claude Code can act on
        throw new RuntimeException("Search index unavailable. Try rebuilding with 'rebuild-index' tool.", e);
    }
}
```

3. **For explicit error responses**, return `CallToolResult` directly:

```java
@McpTool(name = "process-file", description = "Process a file")
public CallToolResult processFile(CallToolRequest request) {
    try {
        // processing logic
        return CallToolResult.builder()
            .addTextContent("Processed successfully")
            .build();
    } catch (FileNotFoundException e) {
        return CallToolResult.builder()
            .isError(true)
            .addTextContent("File not found: " + e.getMessage())
            .build();
    }
}
```

## Related MCP server configuration properties

Your Spring Boot 3.5.9 application can use these MCP-specific properties:

```yaml
spring:
  ai:
    mcp:
      server:
        type: SYNC              # or ASYNC for reactive
        name: "alexandria-rag"
        version: "1.0.0"
        protocol: STREAMABLE    # For HTTP Streamable via /mcp
        annotation-scanner:
          enabled: true         # Enable @McpTool annotation scanning
```

## Conclusion

The `spring.ai.tools.throw-exception-on-error` property exists in Spring AI 1.1.2 with a default of `false`, but its relevance to your MCP server is limited. Your `@McpTool` methods use the mcp-annotations library's own exception handling, which converts thrown exceptions to MCP-compliant error responses (`CallToolResult` with `isError: true`). This allows Claude Code to receive structured error information and potentially recover or inform the user. Keep the default setting and focus on returning descriptive error messages from your tools for the best Claude Code experience.