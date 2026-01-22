# Documentation RAG (Alexandria)

## What This Is

A personal RAG (Retrieval-Augmented Generation) system using PostgreSQL with pgvector and Apache AGE to index and search technical documentation. Built with Java 21 and LangChain4j 1.0-beta3, exposed via MCP server for Claude Code integration and CLI for maintenance.

## Core Value

Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.

## Current Milestone: v0.2 Full Docker

**Goal:** Rendre l'application installable et utilisable via docker compose sans installer de dependances.

**Target features:**
- Dockerfile pour l'application Java (MCP server + CLI)
- docker-compose.yml complet (PostgreSQL + App)
- MCP server avec transport HTTP/SSE (en plus de STDIO)
- Script wrapper CLI pour simplifier les commandes
- Publication sur GitHub Container Registry
- Configuration via env vars + fichier .env

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

### Active (v0.2)

- [ ] Dockerfile multi-stage pour build + runtime
- [ ] Service app dans docker-compose.yml
- [ ] MCP transport HTTP/SSE configurable (+ STDIO existant)
- [ ] Script wrapper CLI (`alexandria` command)
- [ ] GitHub Actions pour build et push image
- [ ] Configuration externalisee (env vars, .env, volumes)

### Deferred (v1.2+)

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

**Current State (v0.1 shipped):**
- 53 Java files, 5,732 lines of code
- Tech stack: Java 21, Spring Boot 3.4, LangChain4j 1.0-beta3, PostgreSQL 17, pgvector 0.8.1, Apache AGE 1.6.0
- MCP server: 4 tools (search_docs, index_docs, list_categories, get_doc)
- CLI: 4 commands (index, search, status, clear) with Spring Shell 3.4.1
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

---
*Last updated: 2026-01-22 after v0.2 milestone start*
