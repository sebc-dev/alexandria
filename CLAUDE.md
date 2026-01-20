# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Alexandria is a RAG (Retrieval-Augmented Generation) system for personal technical documentation. It indexes markdown files and provides semantic search via an MCP server for Claude Code integration.

**Tech Stack:** Java 21, Spring Boot 3.4, LangChain4j 1.0-beta3, PostgreSQL 17, pgvector 0.8.1, Apache AGE 1.6.0

## Build Commands

```bash
# Run unit tests only
mvn test

# Run single unit test
mvn test -Dtest=MarkdownParserTest

# Run integration tests (requires Docker)
mvn verify

# Run single integration test
mvn verify -Dit.test=IngestionIT

# Full build with all tests
mvn clean verify

# Build without tests
mvn package -DskipTests
```

## Database Setup

```bash
# Start PostgreSQL with pgvector + Apache AGE
docker compose up -d

# View logs
docker logs -f rag-postgres

# Stop database
docker compose down

# Reset database (delete data)
docker compose down && rm -rf data/ && docker compose up -d
```

## Architecture

Three-layer architecture enforced by ArchUnit tests (`ArchitectureTest.java`):

```
api → core ← infra
```

- **api** (`fr.kalifazzia.alexandria.api`): Entry points - MCP handlers, REST controllers, CLI
- **core** (`fr.kalifazzia.alexandria.core`): Business logic, domain models, port interfaces
- **infra** (`fr.kalifazzia.alexandria.infra`): Repository implementations, external integrations

**Dependencies flow inward only:** infra/api depend on core, never the reverse. Core has no Spring dependencies except annotations.

### Key Patterns

- **Ports and Adapters**: Core defines interfaces (ports) in `core/port/`, infra implements them
- **Hierarchical Chunking**: Parent chunks (1000 tokens) contain child chunks (200 tokens) for context retrieval
- **Event-driven graph updates**: `DocumentIngestedEvent` triggers Apache AGE operations after PostgreSQL commit

## Testing Conventions

- Unit tests: `*Test.java` - run with `mvn test`
- Integration tests: `*IT.java` - run with `mvn verify`, require Docker
- Integration tests use Testcontainers with `pgvector/pgvector:pg17` image (no AGE extension)
- Use `@ActiveProfiles("test")` for integration tests

## Database Notes

- **Apache AGE search_path**: Every connection must run `LOAD 'age'; SET search_path = ag_catalog, "$user", public` (configured in HikariCP `connection-init-sql`)
- **HNSW index**: 384-dimension vectors with `vector_cosine_ops`, m=16, ef_construction=100
- **Test vs Production**: Tests use pgvector-only image; production Docker image includes AGE

## Code Style

- Java 21 features: records, pattern matching, virtual threads enabled
- Constructor injection (no `@Autowired` on constructors)
- Self-injection pattern for `@Transactional` proxy calls within same class (see `IngestionService`)
