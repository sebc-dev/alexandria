# Phase 9: Source Management Completion - Context

**Gathered:** 2026-02-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Close all v1.5 audit gaps: fix source_id FK population so cascade delete works, correct chunk_count accuracy, expose last_crawled_at date in MCP responses, implement index_statistics MCP tool, and wire orphaned exports. This is gap closure — no new capabilities.

</domain>

<decisions>
## Implementation Decisions

### Staleness / Freshness indicator
- No stale/fresh label — just expose `last_crawled_at` as ISO date in MCP responses
- Rationale: technical documentation doesn't change frequently enough to justify qualitative staleness labels; the LLM consumer can interpret the date
- `last_crawled_at` already exists on Source entity (TIMESTAMPTZ column in V1 migration)
- Expose in both `list_sources` and `crawl_status` MCP tool responses

### index_statistics MCP tool
- Global statistics only (no per-source breakdown)
- No input parameters — simple call returns all stats
- Metrics to include:
  - Total chunks (across all sources)
  - Total sources
  - Embedding dimensions (384)
  - Estimated storage size (pgvector index)
  - Last activity timestamp (most recent crawl across all sources)
- Tool name: `index_statistics` (as defined in roadmap)

### Cascade delete (remove_source)
- Suppression directe — no confirmation parameter; Claude Code handles user confirmation upstream
- Feedback includes chunk count: "Source 'X' removed (N chunks deleted)."
- If a crawl is in progress on the source: stop the crawl and proceed with deletion
- Orphan chunks (source_id IS NULL): delete via Flyway migration rather than attempting to re-associate
  - Rationale: orphans lack traceability; recrawling produces clean chunks with proper FK

### chunk_count precision
- Real-time COUNT(*) query on document_chunks by source_id (not denormalized column)
- Total + breakdown by content_type: "chunks: 1247 (892 prose, 355 code)"
- Sources with zero chunks show `chunk_count: 0` (explicit zero, not "not indexed")

### Claude's Discretion
- Exact SQL for storage size estimation (pg_relation_size vs pg_total_relation_size)
- How to stop an in-progress crawl during source deletion (virtual thread interruption strategy)
- Query optimization for COUNT with content_type grouping
- updateSourceNameMetadata() call placement during recrawl

</decisions>

<specifics>
## Specific Ideas

- "Les documentations techniques ne bougent pas assez fréquemment pour justifier un système de fraîcheur qualitatif" — user perspective on staleness
- Chunk count feedback on delete: "Source 'Spring Boot' removed (1,247 chunks deleted)." — quantified feedback

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 09-source-management-completion*
*Context gathered: 2026-02-20*
