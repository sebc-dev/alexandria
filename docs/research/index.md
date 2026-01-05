# Alexandria Research Documentation

Technical research for the Alexandria RAG Server project (January 2026).

## Directory Structure

| Directory | Topic | Files |
|-----------|-------|-------|
| [01-stack-validation](01-stack-validation/) | Java 25, Spring Boot 3.5.9, dependency validation | 5 |
| [02-mcp](02-mcp/) | Model Context Protocol, Spring AI SDK, tool patterns | 13 |
| [03-rag-pipeline](03-rag-pipeline/) | Embeddings, reranking, chunking, retrieval | 9 |
| [04-database](04-database/) | PostgreSQL 18, pgvector 0.8.1, schema design | 6 |
| [05-ingestion](05-ingestion/) | Document formats, llms.txt, update strategies | 5 |
| [06-resilience](06-resilience/) | Resilience4j, retry patterns, timeouts | 6 |
| [07-testing](07-testing/) | Testcontainers, WireMock, E2E testing | 4 |
| [08-security](08-security/) | MCP security, API key authentication | 4 |
| [09-deployment](09-deployment/) | Docker, configuration, logging, health checks | 7 |

**Total: 59 research documents**

## Technology Stack Summary

| Component | Version | Status |
|-----------|---------|--------|
| Java | 25 LTS (25.0.1) | GA |
| Spring Boot | 3.5.9 | GA |
| Spring AI MCP SDK | 1.1.2 | GA |
| Langchain4j | 1.10.0 | GA |
| Resilience4j | 2.3.0 | GA |
| PostgreSQL | 18.1 | GA |
| pgvector | 0.8.1 | GA |

## Quick Links

- **Start here**: [01-stack-validation/comprehensive-stack-validation.md](01-stack-validation/comprehensive-stack-validation.md)
- **MCP Implementation**: [02-mcp/spring-ai-mcp-sdk-1.1.2-guide.md](02-mcp/spring-ai-mcp-sdk-1.1.2-guide.md)
- **RAG Pipeline**: [03-rag-pipeline/rag-pipeline-parameters.md](03-rag-pipeline/rag-pipeline-parameters.md)
- **Database Schema**: [04-database/postgresql-pgvector-schema.md](04-database/postgresql-pgvector-schema.md)
