# Documentation RAG (Alexandria)

## What This Is

A personal RAG (Retrieval-Augmented Generation) system using PostgreSQL with pgvector and Apache AGE to index and search technical documentation. Built with Java 21 and LangChain4j 1.0-beta3, exposed via MCP server for Claude Code integration and CLI for maintenance.

## Core Value

Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.

## Current State

**Latest shipped:** v0.2 Full Docker (2026-01-22)

**Delivered:** Docker packaging with HTTP/SSE MCP transport, CLI wrapper, and automatic GitHub Container Registry publishing.

## Current Milestone: v0.3 Better DX and Quality Gate

**Goal:** Improve test coverage visibility and quality validation tools for AI-generated code.

**Target features:**
- JaCoCo coverage reports (local HTML + CI artifacts)
- PIT mutation testing (incremental local, reflection tool)
- Integration tests in CI (Testcontainers on every PR + push master)
- Local scripts for easy quality analysis

## Requirements

### Validated

- ✓ Indexer des fichiers markdown (documentation, conventions, bonnes pratiques) — v0.1
- ✓ Pipeline RAG avec LangChain4j 1.0+ — v0.1
- ✓ Embeddings locaux ONNX (all-MiniLM-L6-v2, 384 dimensions) — v0.1
- ✓ Stockage vectoriel PostgreSQL 17 + pgvector 0.8.1 (index HNSW) — v0.1
- ✓ Graph de documents avec Apache AGE 1.6.0 — v0.1
- ✓ Chunking hierarchique (parent 1000 tokens, child 200 tokens) — v0.1
- ✓ Exposer la recherche via serveur MCP (Java SDK) — v0.1
- ✓ Recherche semantique sur la documentation indexee — v0.1
- ✓ Commande CLI pour indexer/reindexer manuellement — v0.1
- ✓ Retourner les documents pertinents avec leur contexte — v0.1
- ✓ Recherche hybride vector + full-text — v0.1
- ✓ Traversee graph pour documents lies — v0.1
- ✓ Dockerfile multi-stage pour build + runtime — v0.2
- ✓ Service app dans docker-compose.yml — v0.2
- ✓ MCP transport HTTP/SSE configurable (+ STDIO existant) — v0.2
- ✓ Script wrapper CLI (`alexandria` command) — v0.2
- ✓ GitHub Actions pour build et push image — v0.2
- ✓ Configuration externalisee (env vars, .env, volumes) — v0.2

### Active

- [ ] JaCoCo coverage reports avec rapport HTML local
- [ ] JaCoCo coverage reports dans artifacts CI
- [ ] PIT mutation testing en mode incremental local
- [ ] Tests d'integration Testcontainers en CI (PR + push master)
- [ ] Scripts/commandes pour lancer l'analyse qualite facilement

### Deferred (v0.4+)

- Scoring pondere configurable (vector vs keyword)
- Reranking des resultats
- Support fichiers RST, AsciiDoc
- Tool `similar_docs` pour documents similaires

### Out of Scope

- Indexation automatique (watch mode) — commande manuelle suffit
- Interface web — usage via Claude Code uniquement
- Multi-utilisateur — usage personnel uniquement
- Knowledge graph complet (Microsoft GraphRAG) — overkill pour quelques milliers de docs
- Extraction d'entites NLP — complexite excessive
- API REST — MCP suffit pour l'integration Claude Code
- Embeddings via API externe — doit fonctionner offline

## Context

**Current State (v0.2 shipped):**
- 53 Java files, 5,751 lines of code
- Tech stack: Java 21, Spring Boot 3.4.7, LangChain4j 1.2.0, PostgreSQL 17, pgvector 0.8.1, Apache AGE 1.6.0
- MCP server: 4 tools (search_docs, index_docs, list_categories, get_doc) with HTTP/SSE + STDIO transports
- CLI: 4 commands (index, search, status, clear) with Spring Shell 3.4.1 + Docker wrapper
- Docker: Multi-stage build, auto-publishing to ghcr.io/sebc-dev/alexandria
- Hexagonal architecture enforced by ArchUnit tests

**Architecture:**
```
┌─────────────────────────────────────────────────────────────────┐
│                    API LAYER                                     │
├─────────────────────────────────────────────────────────────────┤
│  MCP Server (Spring AI MCP 1.0.0, STDIO transport)              │
│  CLI (Spring Shell 3.4.1, non-interactive mode)                  │
├─────────────────────────────────────────────────────────────────┤
│                    CORE LAYER                                    │
├─────────────────────────────────────────────────────────────────┤
│  Services: IngestionService, SearchService                       │
│  Ports: DocumentRepository, ChunkRepository, GraphRepository,    │
│         SearchRepository, EmbeddingGenerator, MarkdownParserPort │
├─────────────────────────────────────────────────────────────────┤
│                    INFRA LAYER                                   │
├─────────────────────────────────────────────────────────────────┤
│  JDBC Repositories (pgvector, AGE via cypher())                 │
│  LangChain4j (AllMiniLmL6V2EmbeddingModel, ONNX in-process)     │
│  CommonMark (markdown parsing, YAML frontmatter)                 │
└─────────────────────────────────────────────────────────────────┘
```

## Constraints

- **Stack**: Java 21 + LangChain4j 1.0+ — framework RAG mature
- **Database**: PostgreSQL 17 + pgvector 0.8.1 + Apache AGE 1.6.0
- **Embeddings**: all-MiniLM-L6-v2 via ONNX (in-process, ~100MB RAM)
- **Interface**: MCP Java SDK — integration Claude Code native
- **Usage**: Personnel — pas besoin de gestion multi-utilisateur

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| LangChain4j over Spring AI | Meilleure integration graph, plus leger | ✓ Good |
| PostgreSQL unified (pgvector + AGE) | Un seul service, pas de Neo4j separe | ✓ Good |
| HNSW over IVFFlat | Meilleur speed-recall, pas de reindex | ✓ Good |
| all-MiniLM-L6-v2 local | Zero config, ~100MB RAM, 200-400 emb/sec | ✓ Good |
| Hierarchical chunking | Child pour matching, parent pour contexte | ✓ Good |
| Spring AI MCP SDK | STDIO transport, wiring simple | ✓ Good |
| Character-based token approximation | ~4 chars/token for LangChain4j splitters | ✓ Good |
| RRF k=60 default | Balanced rank sensitivity for hybrid search | ✓ Good |
| websearch_to_tsquery | User-friendly full-text query syntax | ✓ Good |
| Spring Shell 3.4.1 | @Command annotation style, non-interactive mode | ✓ Good |
| Hexagonal architecture | api -> core <- infra, enforced by ArchUnit | ✓ Good |
| Profile-based MCP transport | STDIO + HTTP/SSE via Spring profiles | ✓ Good |
| 120s health start_period | ONNX model loading on first run | ✓ Good |
| wget over curl | JRE image has no curl, wget is lighter | ✓ Good |
| POSIX shell CLI wrapper | Maximum portability across systems | ✓ Good |
| Official Docker GH Actions | Reliable GHCR publishing with gha cache | ✓ Good |

---
*Last updated: 2026-01-22 after starting v0.3 milestone*
