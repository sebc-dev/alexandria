# 02 - MCP (Model Context Protocol)

Research documents for MCP server implementation with Spring AI SDK.

## Documents

| File | Description |
|------|-------------|
| [spring-ai-mcp-tool-annotations.md](spring-ai-mcp-tool-annotations.md) | Complete @McpTool annotation guide |
| [spring-ai-mcp-sdk-1.1.2-guide.md](spring-ai-mcp-sdk-1.1.2-guide.md) | Spring AI MCP SDK 1.1.2 configuration |
| [spring-ai-mcp-webmvc-sse.md](spring-ai-mcp-webmvc-sse.md) | Spring AI MCP Server WebMVC SSE |
| [claude-code-mcp-configuration.md](claude-code-mcp-configuration.md) | Claude Code MCP client configuration |
| [mcp-error-handling.md](mcp-error-handling.md) | MCP error handling patterns |
| [mcp-tool-patterns.md](mcp-tool-patterns.md) | MCP tool patterns for RAG servers |
| [mcp-response-formats.md](mcp-response-formats.md) | Optimal response formats for Claude Code |
| [mcp-timeout-configuration.md](mcp-timeout-configuration.md) | MCP timeout environment variables |
| [spring-ai-tool-exception-handling.md](spring-ai-tool-exception-handling.md) | Spring AI 1.1.2 exception handling |
| [spring-ai-mcp-package-imports.md](spring-ai-mcp-package-imports.md) | Correct package imports for SDK |
| [spring-ai-mcp-progress-notification.md](spring-ai-mcp-progress-notification.md) | Progress notification API |
| [spring-ai-mcp-request-context.md](spring-ai-mcp-request-context.md) | Request context class reference |
| [mcp-http-streamable-transport.md](mcp-http-streamable-transport.md) | HTTP Streamable transport implementation |

## Key Findings

- HTTP Streamable transport is recommended (single `/mcp` endpoint)
- Spring AI 1.1.2 GA provides stable @McpTool annotations
- Dual-format responses (JSON + Markdown) optimize Claude Code integration
- isError: true pattern for business error differentiation
