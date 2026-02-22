# Phase 15: Search Fusion Overhaul - Context

**Gathered:** 2026-02-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace RRF (Reciprocal Rank Fusion) with Convex Combination for hybrid search score fusion. Make alpha (vector vs FTS weight) and reranking candidate count configurable via application.properties. This is a complete replacement — no RRF fallback.

</domain>

<decisions>
## Implementation Decisions

### Score normalisation
- Use **min-max normalisation per query** to scale both vector and FTS scores to [0, 1] before combining
- Execute **two separate queries** (vector-only + FTS-only) instead of relying on LangChain4j's built-in RRF hybrid mode
- Run both queries **in parallel** using virtual threads (already enabled via `spring.threads.virtual.enabled: true`)
- Edge case: if `max == min`, normalised score = 1.0

### Alpha parameter
- Default alpha = **0.7** (70% vector, 30% FTS)
- Configurable via `application.properties` only — **no per-request override**
- **Fail-fast validation at startup**: reject alpha outside [0.0, 1.0] — application does not start with invalid config
- Formula: `combined_score = alpha * norm_vector + (1 - alpha) * norm_fts`

### RRF migration
- **Complete removal** of RRF — no fallback, no strategy toggle
- Remove `rrfK` from SearchRequest (currently logged-but-ignored)
- Disable LangChain4j PgVectorEmbeddingStore's built-in hybrid mode; use it as pure vector store

### Reranker pipeline
- Single configurable parameter `search.rerank-candidates` (default 30, integer, bounded range)
- This parameter controls **both** the number of candidates fetched per source AND the number sent to the reranker
- Fetch pattern: N candidates from vector + N candidates from FTS → fusion → deduplicate by parent → rerank top N → substitute parent text
- Pipeline order: **parallel fetch → Convex Combination fusion → parent-child deduplication → cross-encoder reranking → parent text substitution**

### Evaluation
- Run **before/after evaluation** using Phase 13's framework (RetrievalMetrics) to verify no regression
- Evaluation via **manual script**, not automated test
- If regression detected: **adjust alpha** (try 0.5, 0.6, 0.7, 0.8) and re-test until parity or improvement
- Full ablation study deferred to Phase 18

### Claude's Discretion
- Fallback behaviour when one source (vector or FTS) returns zero results
- Exact bounds for `search.rerank-candidates` (e.g., 10-100)
- Property naming conventions (e.g., `alexandria.search.alpha` vs `search.fusion.alpha`)
- Internal implementation of parallel query execution

</decisions>

<specifics>
## Specific Ideas

- The reranker is the final arbiter of quality — normalisation/fusion just needs to be "good enough" to select the right candidates for reranking
- Pipeline should cleanly integrate with Phase 14's parent-child deduplication (already in SearchService)
- Virtual threads make parallel queries nearly free in terms of complexity

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 15-search-fusion-overhaul*
*Context gathered: 2026-02-22*
