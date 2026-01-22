# Project Milestones: Documentation RAG (Alexandria)

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
