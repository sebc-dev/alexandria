# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Alexandria is a RAG (Retrieval-Augmented Generation) server exposed via MCP (Model Context Protocol) for semantic documentation search. Stack: Java 25 LTS, Spring Boot 3.5.9, Langchain4j 1.10.0, PostgreSQL 18 + pgvector 0.8.1.

## Essential Commands

```bash
# Development
make dev              # Quick compile
make compile          # Compile with Error Prone
make test             # Unit tests only
make test-it          # Integration tests (Testcontainers)
make test-all         # All tests (mvn verify)

# Code Quality
make format           # Apply Spotless formatting
make analyse          # PMD + SpotBugs + Checkstyle
make quick-check      # Full analysis without PIT (~2-5 min)
make full-check       # Complete analysis with PIT (~10-15 min)
make pit              # Mutation testing only

# Single test
mvn test -Dtest=MyClassTest
mvn test -Dtest=MyClassTest#specificMethod

# Git hooks
make install-hooks    # Install pre-commit hook
```

## Architecture

Flat, YAGNI architecture - no hexagonal, no abstract interfaces for HTTP clients.

```text
dev.alexandria/
├── core/           # Business logic, entities, services
├── adapters/       # HTTP clients (Infinity), MCP tools, formatters
├── config/         # @Configuration, @ConfigurationProperties
├── filter/         # HTTP filters (CorrelationId)
├── health/         # Custom HealthIndicators
└── cli/            # Picocli commands for bulk ingestion
```

## Critical Technical Rules

### Resilience4j
- **DO NOT** combine `enableExponentialBackoff` + `enableRandomizedWait` in YAML - causes `IllegalStateException`
- Use `RetryConfigCustomizer` bean for exponential backoff with jitter
- `@Retry(name = "infinityApi")` on all HTTP clients
- Fallback signature: same parameters + `Exception ex` as last parameter

### pgvector
- Use `vector(1024)` - Langchain4j does NOT support `halfvec`
- Create HNSW index manually - Langchain4j creates IVFFlat by default
- Runtime settings via HikariCP `connection-init-sql` (not schema.sql - won't persist)

### Langchain4j Versions
- Core: 1.10.0 (via BOM)
- Spring starters: 1.10.0-beta18 (explicit version required - not in GA BOM)
- pgvector: 1.10.0-beta18 (explicit version required)

### MCP Server
- Endpoint: `/mcp` (HTTP Streamable transport)
- Dual response format: JSON structured + Markdown readable (max 8000 tokens)
- Use `CallToolResult.builder().isError(true)` for error responses

### Testing
- TDD: Red-Green-Refactor mandatory
- Testcontainers 2.x: artifact is `testcontainers-postgresql` (not `postgresql`)
- Image: `pgvector/pgvector:0.8.1-pg18`
- WireMock for Infinity API stubs

## Configuration

Virtual Threads enabled via `spring.threads.virtual.enabled=true` - no manual configuration needed.

Key timeouts in `application.yml`:
- Cold start: 90s global, 30s embedding/reranking
- Warm: 30s global, 5s embedding/reranking

## Claude Code Hooks (Active)

- **PostToolUse**: Spotless auto-format on .java files after Write/Edit
- **Stop**: Quality reminder displayed at end of responses
- **SessionStart**: Welcome message with available commands

## Quality Commands (Custom Skills)

```text
/quality-check       # PMD + SpotBugs + Checkstyle (~2-5 min)
/full-analysis       # Complete analysis + PIT (~10-15 min)
/pit-test            # Mutation testing only (~8-15 min)
```

## Anti-Patterns to Avoid

- No abstract interfaces for HTTP clients (direct adapters)
- No circuit breaker (retry alone suffices)
- No H2 for pgvector tests (doesn't support vector extension)
- No OpenAI format for `/rerank` endpoint (Infinity uses Cohere format)
