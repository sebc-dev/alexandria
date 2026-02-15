# Roadmap: Alexandria

## Overview

Alexandria delivers a self-hosted RAG system that crawls, indexes, and exposes technical documentation to Claude Code via MCP. The build order follows component dependencies: database and search infrastructure first (verifiable with test data), then crawling, then the ingestion pipeline that connects them, then MCP exposure to Claude Code, then source management CRUD, then operational crawl features, and finally advanced search quality enhancements. Phases 1-5 form the critical path to a working end-to-end system; Phases 6-8 add operational completeness and search precision.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 0: CI & Quality Gate** - Local and GitHub CI with unit tests, integration tests, mutation testing, dead code detection, and architecture tests
- [ ] **Phase 1: Foundation & Infrastructure** - PostgreSQL+pgvector schema, Spring Boot skeleton, ONNX embeddings, Docker Compose
- [x] **Phase 2: Core Search** - Hybrid search (vector + keyword + RRF) verifiable with test data
- [ ] **Phase 3: Web Crawling** - Crawl4AI sidecar integration for recursive JS-capable crawling
- [ ] **Phase 4: Ingestion Pipeline** - Markdown-aware chunking, metadata enrichment, code extraction
- [ ] **Phase 5: MCP Server** - stdio transport exposing search and management tools to Claude Code
- [ ] **Phase 6: Source Management** - CRUD operations for documentation sources with status tracking
- [ ] **Phase 7: Crawl Operations** - Incremental crawls, scope controls, scheduling, progress monitoring
- [ ] **Phase 8: Advanced Search & Quality** - Cross-encoder reranking, filtering by section/version/content-type

## Phase Details

### Phase 0: CI & Quality Gate
**Goal**: A CI pipeline running locally and on GitHub that enforces quality gates -- unit tests, integration tests, mutation testing, dead code detection, and architecture tests -- so every subsequent phase is built with automated quality assurance from the start
**Depends on**: Nothing (first phase)
**Requirements**: None (cross-cutting concern)
**Success Criteria** (what must be TRUE):
  1. `./gradlew check` (or equivalent) runs all quality gates locally: unit tests, integration tests, mutation tests, dead code detection, and architecture tests
  2. GitHub Actions workflow runs the same quality gates on every push and PR, blocking merge on failure
  3. Mutation testing (via PIT or equivalent) runs on the codebase and reports a mutation coverage score
  4. Dead code detection (via detekt/UnusedDeclarations or equivalent) flags unused code as build warnings or errors
  5. Architecture tests (via ArchUnit) enforce package dependency rules and architectural constraints
  6. CI pipeline completes in under 5 minutes for an empty/skeleton project
**Plans:** 2 plans

Plans:
- [ ] 00-01-PLAN.md -- Gradle project skeleton with all quality gate plugins configured and verified
- [ ] 00-02-PLAN.md -- Local quality.sh script and GitHub Actions CI workflow with SonarCloud

### Phase 1: Foundation & Infrastructure
**Goal**: A running Docker Compose stack with PostgreSQL+pgvector, Spring Boot application, and in-process ONNX embedding generation -- the base layer everything else builds on
**Depends on**: Phase 0
**Requirements**: INFRA-01, INFRA-02, INFRA-04, CHUNK-04
**Success Criteria** (what must be TRUE):
  1. `docker compose up` starts all services (Java app, PostgreSQL+pgvector) and they reach healthy status without manual intervention
  2. Application can generate a 384-dimension embedding vector from arbitrary text input using the in-process ONNX model (bge-small-en-v1.5-q)
  3. Application can store an embedding with metadata in pgvector and retrieve it by ID
  4. Total memory usage of the running stack stays under 14 GB on a 24 GB machine
  5. Database schema is managed by Flyway migrations (no manual SQL execution required)
**Plans:** 2 plans

Plans:
- [ ] 01-01-PLAN.md -- Gradle dependencies, Spring Boot dual-profile config, Docker Compose stack with pgvector/Crawl4AI/app
- [ ] 01-02-PLAN.md -- Flyway migrations, LangChain4j ONNX embedding beans, integration test proving embed-store-retrieve

### Phase 2: Core Search
**Goal**: Users can perform hybrid semantic+keyword search over indexed documentation and get relevant, cited results -- verifiable with manually inserted test data before any crawling exists
**Depends on**: Phase 1
**Requirements**: SRCH-01, SRCH-02, SRCH-03, SRCH-05, SRCH-06
**Success Criteria** (what must be TRUE):
  1. User can search by meaning (semantic query like "how to configure routing") and get relevant chunks ranked by cosine similarity via pgvector HNSW index
  2. User can search by exact terms (keyword query like "RouterModule") and get relevant chunks via PostgreSQL full-text search (tsvector/tsquery)
  3. Hybrid search combines vector and keyword results via Reciprocal Rank Fusion and returns better results than either method alone
  4. Every search result includes a source URL and section path suitable for citation
  5. User can configure the number of results returned (defaulting to 10)
**Plans:** 2 plans

Plans:
- [x] 02-01-PLAN.md -- V2 Flyway migration (GIN index fix), hybrid EmbeddingStore config, SearchService + domain DTOs
- [x] 02-02-PLAN.md -- Unit tests (SearchRequest, SearchService) + HybridSearchIT integration tests proving all 5 success criteria

### Phase 3: Web Crawling
**Goal**: The system can crawl a documentation site from a root URL, handle JavaScript-rendered pages, and produce clean Markdown output -- the raw material for the ingestion pipeline
**Depends on**: Phase 1
**Requirements**: CRWL-01, CRWL-02, CRWL-04, CRWL-05, CRWL-08
**Success Criteria** (what must be TRUE):
  1. System recursively crawls a documentation site from a root URL via the Crawl4AI sidecar, following internal links to discover pages
  2. Crawled HTML is converted to Markdown that preserves headings, code blocks with language tags, tables, and lists
  3. JavaScript-rendered pages (e.g., React/Vue doc sites) produce the same quality Markdown as static HTML pages
  4. Boilerplate content (navigation bars, footers, sidebars) is stripped from crawled output
  5. System checks for sitemap.xml and uses it for page discovery when available, falling back to recursive link crawling
**Plans:** 2 plans

Plans:
- [ ] 03-01-PLAN.md -- Crawl4AI REST client, Spring RestClient config, request/response DTOs, Docker Compose shm_size fix, integration test
- [ ] 03-02-PLAN.md -- SitemapParser, UrlNormalizer, PageDiscoveryService, CrawlService orchestrator, integration test

### Phase 4: Ingestion Pipeline
**Goal**: Crawled Markdown is transformed into richly-annotated, searchable chunks that preserve code block integrity and heading hierarchy -- the quality-critical transformation layer
**Depends on**: Phase 1, Phase 2, Phase 3
**Requirements**: CHUNK-01, CHUNK-02, CHUNK-03, CHUNK-05, CHUNK-07
**Success Criteria** (what must be TRUE):
  1. System chunks Markdown at heading boundaries (H1/H2/H3) and never splits mid-code-block or mid-table
  2. Chunks carry configurable overlap (default 50-100 tokens) so context is not lost at boundaries
  3. Every chunk carries metadata: source URL, section path (breadcrumb from heading hierarchy), content type, and last updated timestamp
  4. Code examples are extracted as separate chunks tagged with language and content_type="code"
  5. User can optionally provide pre-chunked content (from external tooling or LLM-assisted chunking) bypassing automatic chunking
  6. End-to-end pipeline works: crawl a real documentation site and produce searchable results via hybrid search
**Plans**: TBD

Plans:
- [ ] 04-01: TBD
- [ ] 04-02: TBD

### Phase 5: MCP Server
**Goal**: Claude Code can connect to Alexandria via MCP stdio transport and search indexed documentation -- the integration layer that makes everything usable
**Depends on**: Phase 2, Phase 4
**Requirements**: MCP-01, MCP-02, MCP-03, MCP-04, MCP-05, INFRA-03
**Success Criteria** (what must be TRUE):
  1. Claude Code connects to Alexandria via stdio transport using a documented .mcp.json configuration
  2. `search_docs` tool returns relevant documentation excerpts with source citations, respecting a configurable token budget (default 5000 tokens)
  3. Tool descriptions are clear and front-loaded so Claude Code's LLM can reliably select the right tool
  4. Tool errors return structured, actionable messages (not Java stack traces or raw exceptions)
  5. Server exposes maximum 6 tools as specified: `search_docs`, `list_sources`, `add_source`, `remove_source`, `crawl_status`, `recrawl_source`
**Plans**: TBD

Plans:
- [ ] 05-01: TBD
- [ ] 05-02: TBD

### Phase 6: Source Management
**Goal**: Users can manage documentation sources through MCP tools -- add, remove, list, and inspect the health of their indexed documentation
**Depends on**: Phase 5, Phase 3
**Requirements**: SRC-01, SRC-02, SRC-03, SRC-04, SRC-05
**Success Criteria** (what must be TRUE):
  1. User can add a documentation URL as a source via `add_source` MCP tool and it triggers crawling and indexing
  2. User can list all configured sources with status, last crawl time, and chunk count via `list_sources`
  3. User can remove a source via `remove_source` and all its indexed data (chunks, embeddings) is cascade-deleted
  4. User can see freshness status of each source (time since last crawl, staleness indicator)
  5. User can view index statistics (total chunks, total sources, storage size, embedding dimensions) via MCP tool
**Plans**: TBD

Plans:
- [ ] 06-01: TBD
- [ ] 06-02: TBD

### Phase 7: Crawl Operations
**Goal**: Users have full operational control over crawling -- scope limits, incremental updates, scheduled recrawls, progress monitoring, and llms.txt support
**Depends on**: Phase 6
**Requirements**: CRWL-03, CRWL-06, CRWL-07, CRWL-09, CRWL-10, CRWL-11
**Success Criteria** (what must be TRUE):
  1. User can control crawl scope via URL pattern allowlist/blocklist, max depth, and max pages when adding or recrawling a source
  2. System performs incremental/delta crawls -- only re-processes pages whose content hash (SHA-256) has changed since last crawl
  3. User can schedule periodic recrawls for a source at a configurable interval
  4. User can check crawl progress (pages crawled, pages remaining, errors) via `crawl_status` MCP tool
  5. User can trigger a manual recrawl of an existing source via `recrawl_source` MCP tool
  6. System can ingest llms.txt and llms-full.txt files as documentation sources and use them for page discovery
**Plans**: TBD

Plans:
- [ ] 07-01: TBD
- [ ] 07-02: TBD

### Phase 8: Advanced Search & Quality
**Goal**: Search results are more precise through cross-encoder reranking and richer filtering options -- the quality multiplier layer
**Depends on**: Phase 4, Phase 6
**Requirements**: SRCH-07, SRCH-08, SRCH-09, SRCH-10, SRCH-04, CHUNK-06
**Success Criteria** (what must be TRUE):
  1. System re-ranks top candidates via cross-encoder model, measurably improving precision over RRF-only results
  2. User can filter search results by section path (e.g., search only within "API Reference")
  3. User can filter search results by version tag (e.g., "Spring Boot 3.5" only)
  4. User can filter search results by content type (code examples vs prose vs all)
  5. User can filter search results by source name
  6. User can tag each source with a version label that persists and is filterable in search
**Plans**: TBD

Plans:
- [ ] 08-01: TBD
- [ ] 08-02: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 0 -> 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7 -> 8
Note: Phases 2 and 3 can execute in parallel (both depend only on Phase 1).

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 0. CI & Quality Gate | 0/2 | Planned | - |
| 1. Foundation & Infrastructure | 0/2 | Planned | - |
| 2. Core Search | 2/2 | ✓ Complete | 2026-02-15 |
| 3. Web Crawling | 0/TBD | Not started | - |
| 4. Ingestion Pipeline | 0/TBD | Not started | - |
| 5. MCP Server | 0/TBD | Not started | - |
| 6. Source Management | 0/TBD | Not started | - |
| 7. Crawl Operations | 0/TBD | Not started | - |
| 8. Advanced Search & Quality | 0/TBD | Not started | - |
