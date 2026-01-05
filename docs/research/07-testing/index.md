# 07 - Testing

Research documents for testing strategies and tools.

## Documents

| File | Description |
|------|-------------|
| [testcontainers-pgvector.md](testcontainers-pgvector.md) | Testcontainers with PostgreSQL 18 and pgvector 0.8.1 |
| [testing-best-practices.md](testing-best-practices.md) | Testing best practices for RAG Spring Boot servers |
| [mcp-e2e-testing.md](mcp-e2e-testing.md) | E2E testing for MCP servers with Spring AI SDK |
| [testcontainers-postgresql-artifact.md](testcontainers-postgresql-artifact.md) | Testcontainers 2.0.3 PostgreSQL artifact naming |

## Key Findings

- Test pyramid: 70% unit / 20% integration / 10% E2E
- Testcontainers 2.0.3 with pgvector/pgvector:0.8.1-pg18 image
- WireMock 3.13.2 for Infinity API mocking
- McpSyncClient for E2E MCP testing
- Testcontainers 2.x has changed package prefixes
