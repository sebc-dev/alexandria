# Project Milestones: Documentation RAG (Alexandria)

## v0.3 Better DX and Quality Gate (Shipped: 2026-01-23)

**Delivered:** Test quality visibility through JaCoCo code coverage, PIT mutation testing, CI integration with coverage badges, and convenience scripts for quick quality analysis.

**Phases completed:** 11-14 (5 plans total)

**Key accomplishments:**

- JaCoCo code coverage with HTML/XML/CSV reports (44% LINE, 41% BRANCH baseline)
- Integration test coverage with separate unit/IT/merged reports
- PIT mutation testing with incremental analysis (34% mutation score, 5s incremental runs)
- GitHub Actions pipeline: Testcontainers integration tests on every PR + push master
- Dynamic coverage badges auto-generated and committed to README
- Consolidated quality scripts: `./coverage`, `./mutation`, `./quality --full`

**Stats:**

- 58 files modified
- 9,707 lines added
- 4 phases, 5 plans, 13 requirements
- 2 days (2026-01-22 → 2026-01-23)

**Git range:** docs(11)..feat(14) (46 commits)

**What's next:** v0.4 planned features - configurable search weights, reranking, additional file format support

---

## v0.2 Full Docker (Shipped: 2026-01-22)

**Delivered:** Docker packaging with HTTP/SSE MCP transport, CLI wrapper, and automatic GitHub Container Registry publishing.

**Phases completed:** 8-10 (6 plans total)

**Key accomplishments:**

- Multi-stage Dockerfile with layered JAR extraction for optimal Docker layer caching
- HTTP/SSE MCP transport alongside existing STDIO (profile-based selection)
- Complete Docker Compose stack with health checks and service dependencies (120s start period for ONNX)
- CLI wrapper script (`./alexandria`) hiding Docker complexity from users
- GitHub Actions CI/CD for automatic Docker image publishing to GHCR on semver tags
- Developer configuration: .env.example template, .dockerignore for fast builds

**Stats:**

- 49 files modified
- 6,640 lines added
- 3 phases, 6 plans
- 2 days (2026-01-21 → 2026-01-22)

**Git range:** v0.1..f79e738 (50 commits)

**What's next:** v0.3 planned features - configurable search weights, reranking, additional file format support

---

## v0.1 MVP (Shipped: 2026-01-20)

**Delivered:** Personal RAG system for technical documentation with MCP server for Claude Code integration and CLI for maintenance.

**Phases completed:** 1-7 (15 plans total)

**Key accomplishments:**

- PostgreSQL infrastructure with pgvector 0.8.1 + Apache AGE 1.6.0 (Docker image built from source)
- Hierarchical chunking pipeline: markdown parsing, YAML frontmatter, parent (1000 tokens) / child (200 tokens) splitting
- ONNX embeddings with all-MiniLM-L6-v2 (384 dimensions, in-process, ~200-400 emb/sec)
- Document graph in AGE: HAS_CHILD edges for chunk hierarchy, REFERENCES edges for cross-document links
- Hybrid search: vector similarity (pgvector <=> operator) + full-text (tsvector) with RRF scoring
- Graph traversal: discover related documents via REFERENCES edges (1-2 hops)
- MCP server: 4 tools (search_docs, index_docs, list_categories, get_doc) for Claude Code integration
- CLI commands: index, search, status, clear with Spring Shell 3.4.1

**Stats:**

- 53 Java files created
- 5,732 lines of Java
- 7 phases, 15 plans
- 2 days from start to ship (2026-01-19 → 2026-01-20)

**Git range:** `feat(01-01)` → `feat(07-02)` (117 commits)

**What's next:** v0.2 planned features - configurable search weights, reranking, additional file format support

---
