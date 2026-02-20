# Phase 7: Crawl Operations - Context

**Gathered:** 2026-02-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Users have full operational control over crawling -- scope limits, incremental updates, manual recrawls, progress monitoring, and llms.txt support. No automatic/scheduled recrawls in this phase. Scheduling is deferred.

</domain>

<decisions>
## Implementation Decisions

### Scope Controls
- Glob patterns for allowlist/blocklist (e.g., `/docs/**`, `!/docs/archive/**`)
- Scope defined at `add_source` time, overridable via `recrawl_source` params
- Max depth unlimited by default (no default cap)
- Max pages configurable per source
- URLs filtered by scope are logged (not silently skipped) -- available in `crawl_status` output

### Incremental Crawl Behavior
- Content hash (SHA-256) per page determines change detection
- Unchanged pages (same hash): skip entirely -- no re-chunking, no re-embedding
- Changed pages (different hash): delete all old chunks for that page, re-chunk and re-embed from scratch (replace-all strategy)
- Deleted pages: detected by comparing crawled URLs against indexed URLs post-crawl -- chunks for missing pages are deleted
- No diff-based chunk comparison -- full replacement per page is simpler and reliable

### Scheduling & Recrawl
- **No automatic/scheduled recrawls** -- only manual recrawl via `recrawl_source` MCP tool
- Recrawl is incremental by default, with a `full` flag to force complete re-processing
- Recrawl can override scope params (patterns, max_depth, max_pages) for that run, defaults to original source config

### llms.txt Support
- Auto-detection: check `/llms.txt` and `/llms-full.txt` at domain root when adding a source
- User can manually provide llms.txt URL if auto-detection fails
- Discovery priority cascade: llms.txt > sitemap.xml > link crawl (each level supplements the previous)
- llms-full.txt: hybrid ingestion -- use as primary content source (ingest directly), then crawl any pages from llms.txt/sitemap/links not covered by llms-full.txt
- Handles incomplete llms-full.txt gracefully by filling gaps via crawling

### Claude's Discretion
- Progress reporting format and detail level in `crawl_status`
- Hash storage mechanism (column on existing table vs separate tracking table)
- Glob pattern matching library choice
- llms.txt parsing implementation details

</decisions>

<specifics>
## Specific Ideas

- "J'ai deja vu des llms-full.txt qui n'etait pas complet" -- hybrid approach important to fill gaps
- Scheduling explicitly deferred: user prefers manual control for now
- Scope overrides on recrawl should not persist -- they're one-time for that crawl run

</specifics>

<deferred>
## Deferred Ideas

- Automatic/scheduled recrawls (cron or interval-based) -- future phase or backlog
- Scope persistence updates (ability to permanently change a source's scope config after initial add)

</deferred>

---

*Phase: 07-crawl-operations*
*Context gathered: 2026-02-20*
