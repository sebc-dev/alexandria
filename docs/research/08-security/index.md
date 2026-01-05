# 08 - Security

Research documents for MCP server security.

## Documents

| File | Description |
|------|-------------|
| [mcp-server-security.md](mcp-server-security.md) | Complete guide for securing single-user MCP servers |
| [mcp-security-spring-boot.md](mcp-security-spring-boot.md) | MCP Server Security compatibility with Spring Boot 3.5.9 |
| [mcp-server-security-library.md](mcp-server-security-library.md) | MCP-Server-Security library status |
| [spring-security-api-key.md](spring-security-api-key.md) | Spring Security API key authentication |

## Key Findings

- Single-user scenario simplifies security requirements
- API key authentication is sufficient for local usage
- Never expose filesystem paths to Claude (use logical URIs)
- Avoid hardcoded secrets, use environment variables
