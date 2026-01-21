# Alexandria

A RAG (Retrieval-Augmented Generation) system for personal technical documentation. Index your markdown files and query them semantically via MCP server for Claude Code integration.

## Features

- **Semantic Search**: Find relevant documentation using natural language queries
- **Hybrid Search**: Combines vector similarity with full-text search (RRF fusion)
- **Hierarchical Chunking**: Child chunks for matching, parent chunks for context
- **Document Graph**: Apache AGE tracks document relationships via cross-references
- **MCP Integration**: Native Claude Code integration via STDIO transport
- **CLI Interface**: Command-line tools for maintenance and manual operations
- **Local Embeddings**: all-MiniLM-L6-v2 via ONNX, no external API required

## Tech Stack

- Java 21, Spring Boot 3.4
- LangChain4j 1.0-beta3
- PostgreSQL 17 with pgvector 0.8.1 and Apache AGE 1.6.0
- Spring AI MCP Server 1.0.0
- Spring Shell 3.4.1

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- Docker & Docker Compose

### 1. Start the Database

```bash
docker compose up -d
```

This starts PostgreSQL with pgvector and Apache AGE extensions.

### 2. Build the Application

```bash
mvn clean package -DskipTests
```

### 3. Index Documentation

```bash
java -jar target/alexandria-0.1.0-SNAPSHOT.jar index --path /path/to/docs
```

### 4. Configure Claude Code

Copy `.mcp.json` to your project or merge with existing configuration:

```json
{
  "mcpServers": {
    "alexandria": {
      "type": "stdio",
      "command": "java",
      "args": [
        "--enable-native-access=ALL-UNNAMED",
        "-jar",
        "/path/to/alexandria-0.1.0-SNAPSHOT.jar",
        "--spring.profiles.active=mcp"
      ],
      "env": {
        "SPRING_DATASOURCE_URL": "jdbc:postgresql://localhost:5432/alexandria?sslmode=disable",
        "SPRING_DATASOURCE_USERNAME": "alexandria",
        "SPRING_DATASOURCE_PASSWORD": "alexandria"
      }
    }
  }
}
```

## MCP Tools

When integrated with Claude Code, the following tools become available:

| Tool | Description |
|------|-------------|
| `search_docs` | Semantic search with optional category/tag filters |
| `index_docs` | Index markdown files from a directory |
| `list_categories` | List available documentation categories |
| `get_doc` | Get full document details by ID |

## CLI Commands

```bash
# Index markdown files
java -jar target/alexandria-*.jar index --path /path/to/docs

# Search documentation
java -jar target/alexandria-*.jar search --query "how to configure logging" --limit 10

# Show database status
java -jar target/alexandria-*.jar status

# Clear all indexed data
java -jar target/alexandria-*.jar clear --force
```

## Document Format

Alexandria indexes markdown files with optional YAML frontmatter:

```markdown
---
title: My Document Title
category: conventions
tags:
  - java
  - architecture
---

# Document Content

Your documentation content here...
```

Supported frontmatter fields:
- `title`: Document title (falls back to first H1 or filename)
- `category`: Classification for filtering
- `tags`: List of tags for filtering

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         API LAYER                               │
├─────────────────────────────────────────────────────────────────┤
│  MCP Server (STDIO transport)    CLI (Spring Shell)            │
├─────────────────────────────────────────────────────────────────┤
│                         CORE LAYER                              │
├─────────────────────────────────────────────────────────────────┤
│  IngestionService                SearchService                  │
│  Ports: DocumentRepository, ChunkRepository, GraphRepository    │
├─────────────────────────────────────────────────────────────────┤
│                        INFRA LAYER                              │
├─────────────────────────────────────────────────────────────────┤
│  JDBC Repositories (pgvector, Apache AGE)                       │
│  LangChain4j Embeddings (ONNX)   CommonMark Parser              │
└─────────────────────────────────────────────────────────────────┘
```

Hexagonal architecture enforced by ArchUnit tests. Dependencies flow inward: API and Infra depend on Core, never the reverse.

## Development

```bash
# Run unit tests
mvn test

# Run integration tests (requires Docker)
mvn verify

# Full build with all tests
mvn clean verify
```

## Configuration

Key application properties:

| Property | Default | Description |
|----------|---------|-------------|
| `alexandria.mcp.allowed-paths` | `${user.home}` | Directories allowed for indexing via MCP |
| `spring.datasource.url` | - | PostgreSQL connection URL |

## License

MIT
