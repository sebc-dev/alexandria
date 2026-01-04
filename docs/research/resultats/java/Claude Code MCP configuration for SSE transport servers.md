# Claude Code MCP configuration for SSE transport servers

Configuring Claude Code to connect to a custom MCP server via SSE transport requires specific file locations, JSON formats, and understanding of the protocol handshake. **SSE transport is officially deprecated** as of MCP specification version 2025-03-26 in favor of "Streamable HTTP," but remains fully supported for backward compatibility. Your Alexandria server on localhost:8080 will work, though consider migrating to Streamable HTTP for future-proofing.

## Configuration file locations and precedence

Claude Code stores MCP server configurations in **`~/.claude.json`** for user-scoped settings or **`.mcp.json`** in the project root for project-scoped configurations. A common misconception places configs in `~/.claude/settings.json`—this file exists but handles permissions, not MCP servers.

| Scope | File Location | Purpose |
|-------|---------------|---------|
| **User** | `~/.claude.json` | Available across all projects |
| **Project** | `.mcp.json` (project root) | Shared via version control |
| **Local** | `~/.claude.json` under `projects` key | Private to you, current project only |
| **Enterprise** | `/Library/Application Support/ClaudeCode/managed-mcp.json` (macOS) | Admin-deployed, highest precedence |

The precedence hierarchy follows: enterprise managed → local → project → user. For connecting to your Alexandria server during development, `~/.claude.json` with user scope works best.

## Exact JSON format for SSE versus stdio transport

The configuration schema differs significantly between transport types. For your Alexandria SSE server at localhost:8080:

**Complete SSE configuration for Alexandria:**
```json
{
  "mcpServers": {
    "alexandria": {
      "type": "sse",
      "url": "http://localhost:8080/sse",
      "headers": {
        "Authorization": "Bearer ${ALEXANDRIA_TOKEN}",
        "X-API-Key": "${ALEXANDRIA_API_KEY:-default-key}"
      }
    }
  }
}
```

**Stdio transport comparison** (for local process-based servers):
```json
{
  "mcpServers": {
    "filesystem": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/dir"],
      "env": {
        "DEBUG": "true"
      }
    }
  }
}
```

**Streamable HTTP transport** (recommended modern approach):
```json
{
  "mcpServers": {
    "alexandria-modern": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Bearer ${ALEXANDRIA_TOKEN}"
      }
    }
  }
}
```

Key differences: SSE requires the `/sse` endpoint path and uses a dual-endpoint architecture (GET `/sse` for the event stream, POST `/messages` for client requests). Streamable HTTP uses a single endpoint supporting both methods.

## Tool discovery happens automatically via protocol

Tools are **auto-discovered**, not manually specified on the client side. During the initialization handshake, Claude Code queries the server using the `tools/list` JSON-RPC method:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "tools/list",
  "params": {}
}
```

The server responds with its available tools including JSON Schema definitions for each tool's parameters:

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "search_documents",
        "description": "Search Alexandria document store",
        "inputSchema": {
          "type": "object",
          "properties": {
            "query": {"type": "string", "description": "Search query"},
            "limit": {"type": "integer", "default": 10}
          },
          "required": ["query"]
        }
      }
    ]
  }
}
```

When tools change dynamically, compliant servers emit a `notifications/tools/list_changed` notification, prompting clients to refresh their tool list. Your Spring AI Alexandria server should declare tool capability in its initialization response: `"capabilities": {"tools": {"listChanged": true}}`.

## Authentication methods supported through headers

Claude Code supports **custom HTTP headers** for authentication, accommodating Bearer tokens, API keys, and custom schemes. Environment variable expansion works in all header values:

```json
{
  "mcpServers": {
    "alexandria": {
      "type": "sse",
      "url": "http://localhost:8080/sse",
      "headers": {
        "Authorization": "Bearer ${AUTH_TOKEN}",
        "X-API-Key": "${API_KEY:-fallback-key}",
        "X-Workspace-ID": "workspace-123"
      }
    }
  }
}
```

**Supported expansion syntax:**
- `${VAR}` — expands to environment variable value
- `${VAR:-default}` — uses default if variable unset

For **OAuth 2.1** (the MCP specification's primary authentication method for HTTP transports), clients receive `401 Unauthorized` responses with `WWW-Authenticate` headers pointing to OAuth metadata endpoints. However, Claude Code's current SSE implementation primarily uses the header-based approach. If Alexandria requires OAuth, implement it on the server side and have clients pass Bearer tokens via headers.

**Spring AI server-side authentication note:** The `mcp-security` community module for Spring AI only supports Streamable HTTP transport for OAuth2—legacy SSE transport cannot use this security layer. For SSE, implement API key validation manually or use simple header-based authentication.

## Initialization handshake and health check protocol

The MCP protocol defines a three-phase lifecycle with an explicit handshake:

**Phase 1 — Client sends initialize request:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "capabilities": {
      "roots": {"listChanged": true}
    },
    "clientInfo": {
      "name": "claude-code",
      "version": "1.0.0"
    }
  }
}
```

**Phase 2 — Server responds with capabilities:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-06-18",
    "capabilities": {
      "tools": {"listChanged": true},
      "resources": {"subscribe": true}
    },
    "serverInfo": {
      "name": "alexandria",
      "version": "1.0.0"
    }
  }
}
```

**Phase 3 — Client confirms initialization:**
```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}
```

For **health checks**, the protocol supports a utility ping mechanism. Servers can send periodic pings to verify connection liveness. Configure keep-alive in Spring AI with `spring.ai.mcp.server.keep-alive-interval: 30s`. No dedicated health endpoint exists—connection status is determined by SSE stream continuity.

## Complete functional configuration for Alexandria

Save this to `~/.claude.json` for immediate use:

```json
{
  "mcpServers": {
    "alexandria": {
      "type": "sse",
      "url": "http://localhost:8080/sse",
      "headers": {
        "Authorization": "Bearer ${ALEXANDRIA_TOKEN}"
      }
    }
  }
}
```

Or add via CLI:
```bash
claude mcp add --transport sse alexandria \
  --header "Authorization: Bearer your-token" \
  http://localhost:8080/sse
```

**Verify configuration:**
```bash
claude mcp list          # Shows configured servers
claude mcp get alexandria # Shows server details
```

## Spring AI server-side endpoint configuration

Your Alexandria Spring AI server should expose these endpoints by default when using `spring-ai-starter-mcp-server-webmvc`:

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/sse` | GET | SSE event stream connection |
| `/mcp/message` | POST | Client JSON-RPC message endpoint |

Configure in `application.yml`:
```yaml
spring:
  ai:
    mcp:
      server:
        name: alexandria
        version: 1.0.0
        type: SYNC
        sse-endpoint: /sse
        sse-message-endpoint: /mcp/message
        capabilities:
          tool: true
          resource: true
```

## Error handling and reconnection behavior

Claude Code implements automatic reconnection for SSE transport failures. The MCP specification recommends:

- Servers send `retry` field indicating reconnect delay (default **3000ms**)
- Clients should use `Last-Event-ID` header for session resumption
- Connection timeout typically **300 seconds** before automatic reconnection attempt
- Session IDs track multi-client connections; servers return `404 Not Found` for expired sessions

**Environment variables for timeout control:**
| Variable | Purpose | Default |
|----------|---------|---------|
| `MCP_TIMEOUT` | Server startup timeout (ms) | — |
| `MCP_TOOL_TIMEOUT` | Tool execution timeout (ms) | — |
| `MAX_MCP_OUTPUT_TOKENS` | Maximum output size | 25000 |

## Migration path to Streamable HTTP

Since SSE transport is deprecated, consider updating Alexandria to support Streamable HTTP alongside SSE for backward compatibility. The modern transport uses a **single endpoint** (`/mcp`) accepting both GET and POST, with responses as either JSON or SSE streams based on the `Accept` header.

Client configuration for Streamable HTTP:
```json
{
  "mcpServers": {
    "alexandria": {
      "type": "http",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

This provides better authentication support (OAuth 2.1 via Spring AI's security module), simpler endpoint architecture, and alignment with the current MCP specification.

## Conclusion

For immediate Alexandria connectivity, create `~/.claude.json` with the SSE configuration pointing to `http://localhost:8080/sse`, add authentication headers as needed, and ensure your Spring AI server exposes the dual SSE endpoints. Tools are auto-discovered through the protocol handshake. While functional, plan migration to Streamable HTTP transport for OAuth support, simplified architecture, and long-term specification compliance.