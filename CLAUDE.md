# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Alexandria is a self-hosted RAG system that crawls, chunks, and indexes technical documentation, then exposes it to Claude Code via MCP (Model Context Protocol). Stack: Java 21, Spring Boot 3.5, LangChain4j, PostgreSQL+pgvector, Crawl4AI sidecar.

## Build & Test Commands

```bash
# Quality gate script (outputs concise summaries)
./quality.sh test                                          # unit tests
./quality.sh integration                                   # integration tests
./quality.sh coverage                                      # tests + JaCoCo report
./quality.sh mutation --package dev.alexandria.search       # PIT on specific package
./quality.sh spotbugs                                      # SpotBugs analysis
./quality.sh arch                                          # ArchUnit architecture tests
./quality.sh all --with-integration                        # everything
```

## Architecture

**Hybrid pragmatic**: feature-based packages, minimal abstractions, Spring Boot idioms. NOT clean/hexagonal architecture. See `docs/architecture.md` for full rationale.

### Package structure (`dev.alexandria`)

```
config/          Spring @Configuration beans (EmbeddingConfig, GlobalExceptionHandler)
crawl/           Web crawling via Crawl4AI REST sidecar (CrawlService orchestrates)
ingestion/       Pipeline: crawled pages → chunks → embeddings → pgvector store
  chunking/      Markdown AST-based chunker (CommonMark/flexmark), ContentType enum
  prechunked/    Import pre-chunked data bypass
search/          Hybrid search: vector + PostgreSQL FTS merged via RRF
document/        JPA entities and repository for pgvector storage (DocumentChunk)
source/          Documentation source management (Source entity, status tracking)
```

### Dependency flow

Adapter packages (`mcp/`, `api/`) → feature services (`ingestion/`, `search/`, `source/`) → data layer (`document/`). Feature packages must NOT depend on adapter packages (enforced by ArchUnit).

### Key conventions

- **Constructor injection only** — no `@Autowired` on fields
- **Records for DTOs** — all immutable data transfer objects are Java records
- **No ServiceImpl anti-pattern** — interfaces only at real integration boundaries (LangChain4j provides `EmbeddingModel`, `EmbeddingStore`)
- **No unnecessary abstractions** — single implementations used directly, no wrapping of LangChain4j interfaces
- **Sequential pipeline orchestration** — no generic pipeline framework; simple method calls in `IngestionService`
- **80% unit / 20% integration** test ratio — unit tests must run without Spring context
- **Virtual threads enabled** (`spring.threads.virtual.enabled: true`)

## Infrastructure

- **Database**: PostgreSQL 16 + pgvector 0.8 (Flyway migrations in `src/main/resources/db/migration/`)
- **Embeddings**: ONNX in-process (bge-small-en-v1.5-q, 384 dimensions) — no external API
- **Crawling**: Crawl4AI Python sidecar on port 11235
- **Docker**: `docker-compose up -d` starts postgres + crawl4ai + app
- **Profiles**: `web` (REST + MCP SSE on port 8080), `stdio` (MCP stdio, no web server)
- **Testcontainers**: Integration tests use `pgvector/pgvector:pg17` image with `@ServiceConnection`
- **Dependency versions**: centralized in `gradle/libs.versions.toml`

## Quality Gates

JaCoCo (coverage), PIT (mutation testing), SpotBugs (bug detection), ArchUnit (architecture rules), SonarCloud. CI pipeline in `.github/workflows/ci.yml`.

## Planning & Roadmap

Project planning docs live in `.planning/` (PROJECT.md, ROADMAP.md, REQUIREMENTS.md, STATE.md). Architecture decisions in `docs/`.
