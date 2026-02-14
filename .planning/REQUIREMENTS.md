# Requirements: Alexandria

**Defined:** 2026-02-14
**Core Value:** Claude Code peut trouver et retourner des extraits de documentation technique pertinents et precis pour n'importe quel framework ou librairie indexe, a la demande.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Source Management

- [ ] **SRC-01**: User can add a documentation URL as a source via MCP tool `add_source`
- [ ] **SRC-02**: User can list all configured sources with status, last crawl time, and chunk count via MCP tool `list_sources`
- [ ] **SRC-03**: User can remove a source and all its indexed data (cascade delete) via MCP tool `remove_source`
- [ ] **SRC-04**: User can see freshness status of each source (time since last crawl, staleness indicator)
- [ ] **SRC-05**: User can view index statistics (total chunks, total sources, storage size, embedding dimensions) via MCP tool

### Crawling & Ingestion

- [ ] **CRWL-01**: System crawls a documentation site recursively from a root URL via Crawl4AI sidecar
- [ ] **CRWL-02**: System converts crawled HTML to Markdown preserving headings, code blocks with language tags, tables, and lists
- [ ] **CRWL-03**: User can control crawl scope (URL pattern allowlist/blocklist, max depth, max pages)
- [ ] **CRWL-04**: System handles JavaScript-rendered pages via Crawl4AI's Chromium headless browser
- [ ] **CRWL-05**: System removes boilerplate content (navigation, footers, sidebars) from crawled pages
- [ ] **CRWL-06**: System performs incremental/delta crawls — only re-processes pages whose content hash (SHA-256) has changed
- [ ] **CRWL-07**: User can schedule periodic recrawls for a source (interval-based)
- [ ] **CRWL-08**: System discovers pages via sitemap.xml when available, falls back to recursive crawl
- [ ] **CRWL-09**: User can check crawl progress (pages crawled, pages remaining, errors) via MCP tool `crawl_status`
- [ ] **CRWL-10**: User can trigger a recrawl of an existing source via MCP tool `recrawl_source`
- [ ] **CRWL-11**: System can ingest llms.txt and llms-full.txt files as documentation sources and use them as page discovery mechanism (like sitemap.xml) for further crawling

### Chunking & Embedding

- [ ] **CHUNK-01**: System chunks Markdown at heading boundaries (H1/H2/H3), never splitting mid-code-block or mid-table
- [ ] **CHUNK-02**: System applies configurable chunk overlap (default 50-100 tokens)
- [ ] **CHUNK-03**: Each chunk carries metadata: source URL, section path (breadcrumb), heading hierarchy, content type, last updated timestamp
- [ ] **CHUNK-04**: System generates embeddings via ONNX in-process (bge-small-en-v1.5-q, 384 dimensions)
- [ ] **CHUNK-05**: System extracts code examples as separate chunks tagged with language and content_type="code"
- [ ] **CHUNK-06**: User can tag each source with a version label (e.g., "React 19", "Spring Boot 3.5")
- [ ] **CHUNK-07**: User can optionally provide pre-chunked content (from external tooling or LLM-assisted chunking) instead of relying on built-in automatic chunking

### Search & Retrieval

- [ ] **SRCH-01**: User can search indexed documentation via semantic vector search (cosine similarity, pgvector HNSW)
- [ ] **SRCH-02**: User can search indexed documentation via keyword search (PostgreSQL tsvector/tsquery BM25)
- [ ] **SRCH-03**: System combines vector and keyword results via Reciprocal Rank Fusion (RRF) for hybrid search
- [ ] **SRCH-04**: User can filter search results by source name
- [ ] **SRCH-05**: Every search result includes source URL and section path for citation
- [ ] **SRCH-06**: User can configure number of results returned (default 10)
- [ ] **SRCH-07**: System re-ranks top candidates via cross-encoder model for improved precision
- [ ] **SRCH-08**: User can filter search results by section path (e.g., "API Reference" only)
- [ ] **SRCH-09**: User can filter search results by version tag
- [ ] **SRCH-10**: User can filter search results by content type (code vs prose vs all)

### MCP Server

- [ ] **MCP-01**: Server communicates via stdio transport for Claude Code integration
- [ ] **MCP-02**: Tools have clear, front-loaded descriptions optimized for LLM tool selection
- [ ] **MCP-03**: Tool errors return structured, actionable messages (not stack traces)
- [ ] **MCP-04**: Search results respect a configurable token budget (default 5000 tokens)
- [ ] **MCP-05**: Server exposes maximum 6 tools: `search_docs`, `list_sources`, `add_source`, `remove_source`, `crawl_status`, `recrawl_source`

### Infrastructure

- [ ] **INFRA-01**: System runs via single `docker compose up` command (Java app + Crawl4AI + PostgreSQL+pgvector)
- [ ] **INFRA-02**: System works with sensible defaults, zero mandatory configuration beyond Docker
- [ ] **INFRA-03**: Project includes Claude Code integration guide (.mcp.json configuration)
- [ ] **INFRA-04**: System fits within 14 GB RAM budget (on 24 GB machine)

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Quality Enhancements

- **QUAL-01**: System supports multiple embedding providers (Voyage, Cohere, local models)
- **QUAL-02**: System supports upgrading embedding model with automated reindexation

### Operational

- **OPS-01**: Cron-expression based scheduling for recrawls
- **OPS-02**: Bulk source import via YAML/JSON config file
- **OPS-03**: Source freshness alerting (proactive notifications)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Built-in LLM answer generation | Alexandria is a retrieval system. Claude generates answers from retrieved context |
| Multi-tenant user management | Single-tenant by design. One instance per developer/team |
| Web UI dashboard | Claude Code via MCP tools IS the interface |
| PDF/Office document ingestion | Fundamentally different parsing pipeline. Focus on web documentation |
| Knowledge graph (GraphRAG) | Extreme complexity. Hierarchical metadata provides 80% of structural benefit |
| Custom embedding model hosting | Adds GPU requirements. ONNX in-process suffices for v1 |
| Real-time streaming of crawl output | Poll-based status via MCP tool is simpler and sufficient |
| OpenAPI/Swagger spec parsing | General-purpose crawling handles API docs as regular pages |
| Multi-langue support | English-only simplifies embedding model choice |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SRC-01 | Phase 6 | Pending |
| SRC-02 | Phase 6 | Pending |
| SRC-03 | Phase 6 | Pending |
| SRC-04 | Phase 6 | Pending |
| SRC-05 | Phase 6 | Pending |
| CRWL-01 | Phase 3 | Pending |
| CRWL-02 | Phase 3 | Pending |
| CRWL-03 | Phase 7 | Pending |
| CRWL-04 | Phase 3 | Pending |
| CRWL-05 | Phase 3 | Pending |
| CRWL-06 | Phase 7 | Pending |
| CRWL-07 | Phase 7 | Pending |
| CRWL-08 | Phase 3 | Pending |
| CRWL-09 | Phase 7 | Pending |
| CRWL-10 | Phase 7 | Pending |
| CRWL-11 | Phase 7 | Pending |
| CHUNK-01 | Phase 4 | Pending |
| CHUNK-02 | Phase 4 | Pending |
| CHUNK-03 | Phase 4 | Pending |
| CHUNK-04 | Phase 1 | Pending |
| CHUNK-05 | Phase 4 | Pending |
| CHUNK-06 | Phase 8 | Pending |
| CHUNK-07 | Phase 4 | Pending |
| SRCH-01 | Phase 2 | Pending |
| SRCH-02 | Phase 2 | Pending |
| SRCH-03 | Phase 2 | Pending |
| SRCH-04 | Phase 8 | Pending |
| SRCH-05 | Phase 2 | Pending |
| SRCH-06 | Phase 2 | Pending |
| SRCH-07 | Phase 8 | Pending |
| SRCH-08 | Phase 8 | Pending |
| SRCH-09 | Phase 8 | Pending |
| SRCH-10 | Phase 8 | Pending |
| MCP-01 | Phase 5 | Pending |
| MCP-02 | Phase 5 | Pending |
| MCP-03 | Phase 5 | Pending |
| MCP-04 | Phase 5 | Pending |
| MCP-05 | Phase 5 | Pending |
| INFRA-01 | Phase 1 | Pending |
| INFRA-02 | Phase 1 | Pending |
| INFRA-03 | Phase 5 | Pending |
| INFRA-04 | Phase 1 | Pending |

**Coverage:**
- v1 requirements: 40 total
- Mapped to phases: 40
- Unmapped: 0

---
*Requirements defined: 2026-02-14*
*Last updated: 2026-02-14 after roadmap creation*
