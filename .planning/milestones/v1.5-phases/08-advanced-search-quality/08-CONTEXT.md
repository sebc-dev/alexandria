# Phase 8: Advanced Search & Quality - Context

**Gathered:** 2026-02-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Improve search precision through cross-encoder reranking and add filtering capabilities (section path, version tag, content type, source name) to the existing `search_docs` MCP tool. This phase does NOT add new tools — it enhances the existing search pipeline and adds parameters to `search_docs`.

</domain>

<decisions>
## Implementation Decisions

### Cross-encoder reranking
- Always-on — every search goes through reranking automatically, no opt-in parameter
- Pipeline: RRF hybrid retrieves top 50 candidates → cross-encoder re-ranks → return top K (maxResults, default 10)
- ONNX in-process only — no external sidecar or API call, consistent with bge-small embedding approach
- On failure (model missing, ONNX error): return explicit error in MCP response, do NOT silently fall back to RRF-only results
- Include reranking score in each search result so Claude Code can judge confidence

### Filter experience
- Flat parameters on search_docs: query, source, sectionPath, version, contentType, maxResults, rrfK, minScore
- AND logic when combining multiple filters — all specified filters must match
- Empty result + explanatory message when filters match nothing (e.g., "No results for version 'React 19'. Available versions: ...")
- sectionPath filter uses prefix matching (hierarchical) — "API Reference" matches "API Reference > Authentication"
- contentType accepts existing enum values (PROSE, CODE, MIXED) case-insensitive

### Version tagging
- Version label lives on the Source entity — one version per source
- Assigned via optional `version` parameter on `add_source`, modifiable via `recrawl_source`
- Free-form string format — no semver constraint ("3.5", "React 19", "latest" all valid)
- Denormalized into chunk metadata at ingestion time (key: `version`) for direct pgvector filtering without JOIN
- On version change: batch update all chunk metadata for that source

### Search defaults
- maxResults default stays at 10 (reranking improves quality, not quantity)
- RRF k becomes configurable via optional `rrfK` parameter (default 60)
- Cross-encoder minScore as optional parameter — results below threshold excluded, may return fewer than maxResults

### Claude's Discretion
- Choice of cross-encoder ONNX model (research best available for documentation/code)
- Exact SQL query construction for metadata filters
- Default value for minScore if not provided (or no threshold by default)
- Error message formatting for empty filter results

</decisions>

<specifics>
## Specific Ideas

- Reranking score exposed in results helps Claude Code assess retrieval confidence and decide whether to search again with different terms
- The explanatory message on empty filter results should list available values (e.g., available versions, available sources) to help Claude Code self-correct

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 08-advanced-search-quality*
*Context gathered: 2026-02-20*
