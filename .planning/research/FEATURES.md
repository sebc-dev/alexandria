# Feature Research

**Domain:** Self-hosted RAG documentation system with MCP integration for AI coding assistants
**Researched:** 2026-02-14
**Confidence:** MEDIUM-HIGH (based on multiple verified sources including Context7, kapa.ai, R2R, Crawl4AI docs, MCP specification, and RAG ecosystem analysis)

## Feature Landscape

### Table Stakes (Users Expect These)

Features users assume exist. Missing these = product feels incomplete.

#### TS-1: Document Source Management

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Add documentation URL as a source | Core workflow -- user provides a URL, system ingests it | LOW | MCP tool: `add_source`. Needs URL validation, duplicate detection |
| List configured sources with status | Users need to see what is indexed and what is pending/failed | LOW | MCP tool: `list_sources`. Return name, URL, status, last crawl time, chunk count |
| Remove a source and its indexed data | Cleanup is non-negotiable | LOW | MCP tool: `remove_source`. Must cascade-delete chunks and embeddings |
| Source health/status check | "Is my data fresh?" is a constant question | LOW | Part of `list_sources` or dedicated `source_status` tool |

**Confidence:** HIGH -- every documentation RAG system (kapa.ai, R2R, Context7) exposes source management.

#### TS-2: Web Crawling and Ingestion

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Recursive site crawl from a root URL | Documentation lives across many pages; single-page ingestion is useless | MEDIUM | Must respect robots.txt, handle relative links, stay within domain scope |
| HTML-to-Markdown conversion | Markdown is the lingua franca for LLM consumption -- preserves structure at minimal token cost | MEDIUM | Preserve headings (h1-h6), code blocks with language tags, tables, lists, links. Crawl4AI and Firecrawl both do this natively |
| Crawl depth and scope controls | Users must control blast radius -- "only /docs/", "max depth 3" | LOW | URL pattern allowlist/blocklist, max depth, max pages |
| JavaScript-rendered page support | Many documentation sites (Docusaurus, GitBook, etc.) render via JS | MEDIUM | Requires headless browser (Playwright/Puppeteer). Not all docs need this, but enough do that it is table stakes |
| Boilerplate removal | Navigation, footers, sidebars are noise that pollute search results | MEDIUM | Content extraction (main content area detection) or CSS selector targeting. PruningContentFilter from Crawl4AI is a good reference |

**Confidence:** HIGH -- verified against Crawl4AI, Firecrawl, and kapa.ai feature sets.

#### TS-3: Chunking and Embedding

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Structure-aware chunking | Naive fixed-size splitting destroys context. Must split on heading boundaries, preserve code blocks intact | MEDIUM | Use MarkdownHeaderTextSplitter or equivalent. Chunk at H1/H2/H3 boundaries, never split mid-code-block |
| Chunk overlap | Prevents sentence fragmentation at chunk boundaries | LOW | 50-100 token overlap is standard. Configurable but sane defaults |
| Metadata per chunk | Every chunk needs: source URL, section path (breadcrumb), heading hierarchy, chunk index, last updated timestamp | MEDIUM | This metadata powers filtering, citation, and freshness. Non-negotiable |
| Embedding generation | Vector embeddings are the foundation of semantic search | MEDIUM | Use OpenAI text-embedding-3-small (best cost/quality ratio at $0.02/M tokens). Must be configurable for other providers |
| Batch embedding | Ingesting thousands of chunks requires batched API calls with rate limiting | LOW | OpenAI batch endpoint, with retry logic and backoff |

**Confidence:** HIGH -- verified against Databricks chunking guide, AWS Bedrock docs, and multiple RAG framework implementations.

#### TS-4: Search and Retrieval

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Semantic (vector) search | Core RAG capability -- find conceptually similar content | MEDIUM | MCP tool: `search_docs`. Cosine similarity over embeddings. pgvector or Qdrant |
| Keyword (BM25) search | Semantic search fails on exact terms like error codes, API names, config keys | MEDIUM | Full-text search index. PostgreSQL tsvector/tsquery or dedicated BM25 implementation |
| Hybrid search with fusion | Combining vector + keyword is the production standard, not optional | HIGH | Reciprocal Rank Fusion (RRF) to merge incompatible score scales. This is where quality lives |
| Source/library filtering | "Search only in React docs, not Next.js docs" | LOW | Metadata filter on source_id passed as tool parameter |
| Result citation with source URLs | Users (and the AI) need to verify answers. Every result must link back to its source page and section | LOW | Return source_url and section_path with every search result |
| Configurable result count | Different queries need different amounts of context | LOW | `max_results` parameter with sane default (5-10 chunks) |

**Confidence:** HIGH -- hybrid search as production standard verified across Superlinked VectorHub, Elastic, IBM research, and multiple RAG implementations.

#### TS-5: MCP Server Fundamentals

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| stdio transport | Claude Code connects via stdio by default | LOW | Standard MCP SDK pattern |
| Clear tool descriptions | AI agents select tools based on descriptions. Poorly described tools get misused or ignored | LOW | Front-load critical info in 1-2 sentences per Context7 and Merge.dev best practices. Use verb+resource naming |
| Tool error handling | Graceful errors with actionable messages, not stack traces | LOW | Return structured error objects the LLM can interpret and explain to users |
| Token budget awareness | LLM context windows are finite. Results must respect token limits | MEDIUM | `max_tokens` parameter (like Context7's default 5000). Truncate intelligently, not mid-sentence |

**Confidence:** HIGH -- verified against MCP specification (2025-11-25), Context7 tool design, and Merge.dev MCP tool description best practices.

---

### Differentiators (Competitive Advantage)

Features that set the product apart. Not required, but valuable.

#### D-1: Search Quality Enhancements

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Cross-encoder reranking | Dramatically improves precision by reranking top-50 candidates down to top-5 using a cross-encoder model. The difference between "ok results" and "great results" | HIGH | Adds latency (200-500ms) but worth it. Use a small cross-encoder model. Consider making optional via parameter |
| Section-path filtering | "Search only in the 'API Reference' section" -- enables precision queries that semantic search alone cannot handle | MEDIUM | Requires breadcrumb/heading-path metadata at ingestion time. MCP tool parameter: `section_filter` |
| Code example extraction | Return code blocks separately from prose. Developers want the code snippet, not the paragraph explaining it | MEDIUM | Tag chunks as "code" vs "prose" during ingestion. Filter or boost code chunks in search. MCP tool parameter: `content_type: "code" | "prose" | "all"` |
| Multi-source search | Search across all sources simultaneously with source attribution | LOW | Default behavior, but results must clearly attribute which source each result came from |

**Confidence:** MEDIUM -- reranking verified with ZeroEntropy reranking guide and Superlinked VectorHub. Section-path and code extraction based on Crawl4AI metadata patterns and kapa.ai feature set.

#### D-2: Intelligent Crawling

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Incremental/delta crawling | Only re-crawl changed pages. Saves time, API costs (re-embedding), and compute | HIGH | Requires content hashing (MD5/SHA256 of page content), storing hashes, comparing on recrawl. Skip unchanged pages |
| Scheduled recrawling | Documentation changes; indexes go stale. Automatic refresh keeps results current | MEDIUM | Cron-based or interval-based scheduling. MCP tool: `schedule_crawl` or config-based |
| Sitemap-aware crawling | Use sitemap.xml to discover all pages efficiently instead of blind recursive crawling | MEDIUM | Parse sitemap.xml, extract URLs and lastmod dates. Fall back to recursive crawl if no sitemap |
| Crawl progress reporting | Long crawls (100+ pages) need progress feedback | LOW | MCP tool: `crawl_status` returning pages crawled, pages remaining, errors encountered |

**Confidence:** MEDIUM -- incremental crawling verified with Apify incremental crawler docs and Microsoft Optimal-Freshness-Crawl-Scheduling research. Sitemap approach verified with web crawling best practices.

#### D-3: Version-Aware Documentation

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Version tagging per source | "Search React 18 docs, not React 17" -- critical for libraries with breaking changes across versions | MEDIUM | User-provided version tag at source creation time. Stored as metadata, filterable in search |
| Version-filtered search | MCP parameter: `version: "v3.x"` to scope results to a specific version | LOW | Simple metadata filter, but requires version tagging to exist first |

**Confidence:** MEDIUM -- VersionRAG paper (arxiv 2510.08109) and DocNavigator research validate the need. Low confidence on specific implementation patterns; this is an emerging area.

#### D-4: Pipeline Intelligence

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Source freshness monitoring | Alert or flag when indexed content is significantly older than live content | MEDIUM | Compare last-crawl timestamp with configurable staleness threshold. Surface in `list_sources` |
| Crawl error diagnostics | "Page X returned 404", "Page Y timed out" -- actionable error reporting, not silent failures | LOW | Store per-page crawl status. MCP tool: `crawl_errors` or included in `source_status` |
| Index statistics | Chunk count, embedding dimensions, storage size, sources count. Helps users understand system state | LOW | MCP tool: `system_status` returning aggregate stats |

**Confidence:** MEDIUM -- verified against R2R pipeline management features and kapa.ai analytics patterns.

#### D-5: Developer Experience

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| One-command Docker setup | `docker compose up` and everything works. No manual DB migrations, no env file templates | LOW | Single docker-compose.yaml with postgres+pgvector, app server, and MCP server |
| Sensible defaults, zero config | Chunk size, overlap, embedding model, search parameters -- all have good defaults. User only provides URLs | LOW | Convention over configuration. Power users can override |
| Claude Code integration guide | Clear setup instructions for adding to .claude/mcp.json | LOW | Documentation, not a feature, but its absence makes adoption impossible |

**Confidence:** HIGH -- standard for modern developer tools.

---

### Anti-Features (Commonly Requested, Often Problematic)

Features that seem good but create problems.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Built-in LLM answer generation | "Just give me the answer, not the chunks" | Alexandria is a retrieval system, not a chatbot. The LLM (Claude) already generates answers from retrieved context. Adding a second LLM layer adds latency, cost, and a hallucination vector. Let Claude do what Claude does well | Return high-quality chunks with metadata. Let Claude Code synthesize the answer using its own context window |
| Multi-tenant user management | "Support teams and permissions" | Massive complexity for a self-hosted single-user/small-team tool. Auth, RBAC, tenant isolation -- each is a project unto itself | Single-tenant by design. One instance per developer or team. Docker makes spinning up instances trivial |
| Real-time streaming of crawl output | "Show me pages as they are crawled" | WebSocket complexity, state management, reconnection handling. Crawls are background jobs, not interactive sessions | Poll-based status checking via MCP tool. Return crawl progress on request |
| PDF/Office document ingestion | "I also have PDFs and Word docs" | Fundamentally different parsing pipeline. PDF extraction is unreliable, layout-dependent, and requires OCR. Solving this well is an entire project | Focus on web documentation (HTML/Markdown). PDFs and office docs are a v2+ consideration if ever. Users can convert to Markdown externally |
| Knowledge graph construction | "Build a graph of concepts and relationships" | Extremely high complexity (entity extraction, relationship mapping, graph storage, graph-augmented retrieval). R2R does this but it took them years. Marginal benefit for documentation search vs. the engineering cost | Hierarchical metadata (section paths, heading trees) provides 80% of the structural benefit at 10% of the complexity |
| Custom embedding model hosting | "I want to run my own embedding model locally" | Adds GPU requirements, model serving infrastructure, and maintenance burden. Defeats the "simple docker-compose" value prop | Support configurable embedding providers (OpenAI, Voyage, Cohere) via API keys. Local models are a power-user extension, not core |
| Web UI dashboard | "I want a visual interface to manage sources" | Significant frontend engineering effort. Claude Code IS the interface -- the MCP tools ARE the UI. Building a separate dashboard splits attention and doubles the surface area | Claude Code via MCP tools is the primary interface. A minimal health-check endpoint is sufficient for monitoring |
| Automatic API documentation parsing | "Parse OpenAPI specs, detect endpoints" | Specialized parser that handles a narrow use case. General-purpose documentation crawling handles API docs as regular pages adequately | Ingest API documentation as regular web pages. The structure (headings, code blocks) is preserved by standard crawling |

---

## Feature Dependencies

```
[HTML-to-Markdown Conversion]
    |
    v
[Structure-Aware Chunking]
    |
    v
[Metadata Extraction (section paths, headings)]
    |       |
    v       v
[Embedding Generation]    [Keyword/BM25 Index]
    |                          |
    v                          v
[Vector Search]           [Keyword Search]
    |                          |
    +----------+---------------+
               |
               v
        [Hybrid Search / RRF]
               |
               v
        [Cross-Encoder Reranking] (optional, enhances)
               |
               v
        [MCP Search Tool]

[Source Management (add/remove/list)]
    |
    v
[Recursive Web Crawling]
    |
    v
[HTML-to-Markdown Conversion]

[Version Tagging] --requires--> [Source Management]
[Version-Filtered Search] --requires--> [Version Tagging]
[Section-Path Filtering] --requires--> [Metadata Extraction]
[Code Example Extraction] --requires--> [Structure-Aware Chunking]
[Incremental Crawling] --requires--> [Content Hashing] + [Source Management]
[Scheduled Recrawling] --requires--> [Incremental Crawling] (ideally)
[Sitemap-Aware Crawling] --enhances--> [Recursive Web Crawling]
[Source Freshness Monitoring] --requires--> [Crawl Timestamps in Source Management]
```

### Dependency Notes

- **Hybrid Search requires both Vector and Keyword indexes:** Cannot do RRF without both retrieval methods producing candidate lists. Both must exist before hybrid search is meaningful.
- **Section-Path Filtering requires Metadata Extraction:** If section paths are not captured during chunking, they cannot be filtered on during search. This must be designed into the ingestion pipeline from day one.
- **Cross-Encoder Reranking enhances Hybrid Search:** Reranking is a quality layer on top of retrieval. It requires a candidate set (from hybrid search) and adds a second pass. Optional but high-impact.
- **Code Example Extraction requires Structure-Aware Chunking:** Code blocks must be identified and tagged during the chunking phase. Cannot extract code examples if chunks do not preserve code block boundaries.
- **Incremental Crawling requires Content Hashing:** Must store a hash of each page's content to detect changes on recrawl. Without hashing, every recrawl re-processes everything.
- **Version-Filtered Search requires Version Tagging:** Version metadata must be attached to sources at creation time and propagated to all chunks. Cannot filter on what was never stored.

---

## MCP Tool Design

Based on research into Context7's tool design, MCP specification best practices, and Merge.dev's tool description guidelines, here is the recommended MCP tool surface.

### Naming Convention
Use **snake_case** (recommended by MCP spec, best tokenization for GPT-4o and Claude).

### Core Search Tools

| Tool | Description | Key Parameters | Notes |
|------|-------------|----------------|-------|
| `search_docs` | Search indexed documentation using hybrid semantic and keyword search | `query` (required), `source` (optional, filter by source name), `max_results` (optional, default 10), `max_tokens` (optional, default 5000) | Primary tool. Front-load the description: "Search indexed documentation..." |
| `list_sources` | List all configured documentation sources with their status and last crawl time | None required | Returns array of sources with name, URL, status, chunk_count, last_crawled |

### Pipeline Management Tools

| Tool | Description | Key Parameters | Notes |
|------|-------------|----------------|-------|
| `add_source` | Add a new documentation URL to crawl and index | `url` (required), `name` (optional), `max_depth` (optional, default 3), `url_pattern` (optional, allowlist glob) | Triggers async crawl. Returns source ID and initial status |
| `remove_source` | Remove a documentation source and all its indexed content | `source` (required, name or ID) | Cascade deletes chunks and embeddings |
| `crawl_status` | Check the progress of an ongoing or recent crawl | `source` (optional, defaults to most recent) | Returns pages_crawled, pages_total, errors, status |
| `recrawl_source` | Trigger a recrawl of an existing source to refresh its content | `source` (required) | Ideally incremental (only changed pages) |

### Design Principles

1. **Minimal tool count:** 6 tools maximum for v1. LLMs perform worse with too many tools -- they struggle to select the right one. Context7 exposes only 2 tools.
2. **Front-load descriptions:** AI agents read the first sentence of tool descriptions. Put the most important info there.
3. **Sensible defaults:** Every optional parameter has a good default. The LLM should be able to call `search_docs` with just a `query` and get useful results.
4. **Structured responses:** Return JSON with clear field names. Include `source_url` and `section_path` in every search result for citation.
5. **Token budget:** Respect `max_tokens` parameter. Truncate results intelligently (complete chunks, not mid-sentence). Context7 defaults to 5000 tokens.

---

## MVP Definition

### Launch With (v1)

Minimum viable product -- what is needed to validate "search your docs from Claude Code."

- [x] `add_source` -- Add a documentation URL, trigger crawl
- [x] Recursive web crawling with depth limits
- [x] HTML-to-Markdown conversion preserving headings and code blocks
- [x] Structure-aware chunking with metadata (source URL, section path, heading)
- [x] Embedding generation (OpenAI text-embedding-3-small)
- [x] `search_docs` -- Hybrid search (vector + BM25) with RRF
- [x] `list_sources` -- See what is indexed
- [x] `remove_source` -- Clean up
- [x] Source filtering in search
- [x] Token budget in search results
- [x] Docker Compose single-command setup

**Why this set:** These features form a complete loop: add docs, search docs, manage docs. Hybrid search is table stakes for quality -- pure semantic search will frustrate users on exact-term queries. Everything else is enhancement.

### Add After Validation (v1.x)

Features to add once core is working and users confirm the value proposition.

- [ ] Cross-encoder reranking -- Add when users report "results are close but not quite right"
- [ ] Incremental/delta crawling -- Add when recrawls are slow or embedding costs are high
- [ ] `crawl_status` tool -- Add when crawls take long enough that progress matters
- [ ] Scheduled recrawling -- Add when users complain about stale results
- [ ] Section-path filtering in search -- Add when users need to scope searches to specific doc sections
- [ ] Code example extraction/filtering -- Add when developer feedback shows code snippets are the primary use case
- [ ] Version tagging and version-filtered search -- Add when users work with multiple library versions
- [ ] Sitemap-aware crawling -- Add when recursive crawl misses pages or is inefficient

### Future Consideration (v2+)

Features to defer until product-market fit is established.

- [ ] JavaScript-rendered page support (Playwright) -- Add only when enough users hit JS-rendered doc sites. Adds significant container size and complexity
- [ ] Multiple embedding provider support -- Add when users need Voyage, Cohere, or local models
- [ ] Crawl scheduling with cron expressions -- Add when automated freshness is a top request
- [ ] Source freshness monitoring with alerts -- Add when operational maturity demands it
- [ ] Bulk source import (YAML/JSON config file) -- Add when users manage 10+ sources

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority | MCP Tool Impact |
|---------|------------|---------------------|----------|-----------------|
| Hybrid search (vector + BM25 + RRF) | HIGH | HIGH | P1 | Core of `search_docs` |
| HTML-to-Markdown conversion | HIGH | MEDIUM | P1 | None (pipeline internal) |
| Structure-aware chunking with metadata | HIGH | MEDIUM | P1 | Enables filtering in `search_docs` |
| Source management (add/remove/list) | HIGH | LOW | P1 | `add_source`, `remove_source`, `list_sources` |
| Recursive web crawling | HIGH | MEDIUM | P1 | Triggered by `add_source` |
| Embedding generation | HIGH | MEDIUM | P1 | None (pipeline internal) |
| Token budget in results | HIGH | LOW | P1 | Parameter in `search_docs` |
| Source filtering in search | HIGH | LOW | P1 | Parameter in `search_docs` |
| Docker Compose setup | HIGH | LOW | P1 | None (infrastructure) |
| Cross-encoder reranking | HIGH | MEDIUM | P2 | Quality improvement in `search_docs` |
| Incremental crawling | MEDIUM | HIGH | P2 | Affects `recrawl_source` behavior |
| Crawl progress reporting | MEDIUM | LOW | P2 | `crawl_status` tool |
| Section-path filtering | MEDIUM | MEDIUM | P2 | Parameter in `search_docs` |
| Code example extraction | MEDIUM | MEDIUM | P2 | Parameter in `search_docs` |
| Scheduled recrawling | MEDIUM | MEDIUM | P2 | Config or `schedule_crawl` tool |
| Version tagging | MEDIUM | MEDIUM | P3 | Parameter in `add_source` and `search_docs` |
| Sitemap-aware crawling | MEDIUM | MEDIUM | P3 | Affects `add_source` behavior |
| JS rendering (Playwright) | LOW-MEDIUM | HIGH | P3 | None (pipeline internal) |
| Source freshness monitoring | LOW | MEDIUM | P3 | Enhances `list_sources` |

**Priority key:**
- P1: Must have for launch -- the product is broken without these
- P2: Should have, add when core is stable -- these are quality multipliers
- P3: Nice to have, future consideration -- these serve specific use cases

---

## Competitor Feature Analysis

| Feature | Context7 | kapa.ai | R2R | Alexandria (Our Approach) |
|---------|----------|---------|-----|---------------------------|
| Data sources | Pre-crawled library docs (curated) | 50+ connectors (docs, Slack, Discord) | File uploads (PDF, JSON, HTML, etc.) | User-provided URLs, self-crawled |
| Search method | Semantic (pre-indexed) | Semantic with embeddings | Hybrid (semantic + keyword + RRF) | Hybrid (semantic + BM25 + RRF + optional reranking) |
| MCP integration | Native (2 tools: resolve + query) | Hosted MCP server (one-click) | REST API (no native MCP) | Native MCP server (6 tools) |
| Self-hosted | No (cloud service) | No (SaaS) | Yes (Docker) | Yes (Docker Compose, single command) |
| Version awareness | Library ID includes version | Not documented | Not documented | Version tagging per source (v1.x) |
| Code example handling | Returns code examples in results | Not documented | Not documented | Structure-aware chunking preserves code blocks, filterable |
| Pipeline management | None (managed service) | Dashboard analytics | REST API for ingestion | MCP tools for full pipeline control from Claude Code |
| Cost | Free (community-maintained) | $$$$ (enterprise SaaS) | Free (self-hosted) | Free (self-hosted), user pays only for OpenAI embeddings |

**Key competitive insight:** Context7 is the closest analog but is cloud-only and covers only popular open-source libraries. kapa.ai is powerful but enterprise SaaS. R2R is self-hosted but not MCP-native and is far more complex than needed. Alexandria's niche is: **self-hosted, MCP-native, documentation-focused, quality-first, simple to operate.**

---

## Sources

### Verified (HIGH confidence)
- [Context7 MCP Server - GitHub](https://github.com/upstash/context7) -- Tool design patterns, parameter conventions
- [Context7 Docker MCP Catalog](https://hub.docker.com/mcp/server/context7/tools) -- Tool specifications
- [MCP Specification 2025-11-25](https://modelcontextprotocol.io/specification/2025-11-25) -- Protocol specification
- [MCP Tool Descriptions Best Practices - Merge.dev](https://www.merge.dev/blog/mcp-tool-description) -- Tool naming and description guidelines
- [Crawl4AI Documentation v0.8.x - Markdown Generation](https://docs.crawl4ai.com/core/markdown-generation/) -- Crawler features and configuration
- [Optimizing RAG with Hybrid Search and Reranking - Superlinked VectorHub](https://superlinked.com/vectorhub/articles/optimizing-rag-with-hybrid-search-reranking) -- Hybrid search pipeline design

### Cross-Referenced (MEDIUM confidence)
- [Building Trustworthy Documentation RAG Systems - Substack](https://alexanderfashakin.substack.com/p/building-trustworthy-documentation-rag-systems) -- Documentation RAG architecture patterns
- [kapa.ai Documentation](https://docs.kapa.ai/) -- Competitor feature set
- [R2R Framework - GitHub](https://github.com/SciPhi-AI/R2R) -- Production RAG framework features
- [VersionRAG - arxiv 2510.08109](https://arxiv.org/abs/2510.08109) -- Version-aware retrieval research
- [Chunking Strategies for RAG - Databricks](https://community.databricks.com/t5/technical-blog/the-ultimate-guide-to-chunking-strategies-for-rag-applications/ba-p/113089) -- Chunking best practices
- [Best Embedding Models 2026 - Elephas](https://elephas.app/blog/best-embedding-models) -- Embedding model comparison

### Single Source / Lower Confidence (LOW confidence)
- [MCP Server Naming Conventions - ZazenCodes](https://zazencodes.com/blog/mcp-server-naming-conventions) -- Snake case recommendation
- [RAGOps - arxiv](https://arxiv.org/html/2506.03401v1) -- Pipeline operations patterns
- [RAG Freshness Paradox - RagAboutIt](https://ragaboutit.com/the-rag-freshness-paradox-why-your-enterprise-agents-are-making-decisions-on-yesterdays-data/) -- Freshness monitoring patterns

---
*Feature research for: Self-hosted RAG documentation system with MCP integration*
*Researched: 2026-02-14*
