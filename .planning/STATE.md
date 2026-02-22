# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-02-20)

**Core value:** Claude Code peut trouver et retourner des extraits de documentation technique pertinents et precis pour n'importe quel framework ou librairie indexe, a la demande.
**Current focus:** Phase 15 — Search Fusion Overhaul

## Current Position

Phase: 15 of 18 (Search Fusion Overhaul)
Plan: 01 of 02 complete
Status: Executing phase
Last activity: 2026-02-22 — Plan 15-01 complete (ConvexCombinationFusion TDD)

Progress: [███████░░░] ~60% (14/~22 plans)

## Performance Metrics

**Velocity:**
- v0.1: 28 plans completed across 10 phases in 7 days
- v0.2: Starting fresh

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 11-01 | 1 | 7min | 7min |
| 11-02 | 1 | 21min | 21min |
| 11-03 | 1 | 38min | 38min |
| 12-01 | 1 | 4min | 4min |
| 12-02 | 1 | 3min | 3min |
| 13-01 | 1 | 5min | 5min |
| 13-02 | 1 | 8min | 8min |
| 13-03 | 1 | 4min | 4min |
| 14-01 | 1 | 11min | 11min |
| 14-02 | 1 | 14min | 14min |
| 14-03 | 1 | 7min | 7min |
| 15-01 | 1 | 5min | 5min |

## Accumulated Context

### Decisions

All v0.1 decisions archived in PROJECT.md Key Decisions table.
v0.2 decisions:
- Parent-child retrieval (reunir code+prose) — Phase 14
- Convex Combination remplace RRF — Phase 15
- Build evaluation BEFORE pipeline changes — Phase 13 before 14/15
- Quality/security tooling ships first as safety net — Phase 11
- ratchetFrom disabled in git worktrees (JGit incompatibility); full spotlessCheck runs instead — Phase 11-01
- Error Prone suppressions centralized in config/errorprone/bugpatterns.txt, no inline @SuppressWarnings — Phase 11-01
- NullAway at ERROR severity (not WARNING) to enforce null safety at compile time — Phase 11-02
- JPA entity fields marked @Nullable since uninitialized before JPA save; getters return @Nullable — Phase 11-02
- Mockito test classes use @SuppressWarnings("NullAway.Init") for framework-initialized fields — Phase 11-02
- OSS Index analyzer disabled in OWASP (requires API key); NVD database sufficient — Phase 11-03
- CycloneDX version 2.4.1 (plan specified non-existent 2.2.1) — Phase 11-03
- VMware CPE false positives suppressed on Spring AI MCP libs; Guava/ONNX CVEs suppressed with justification — Phase 11-03
- shared_preload_libraries='vector' required for hnsw.ef_search GUC registration at PostgreSQL startup — Phase 12-01
- effective_cache_size=4GB and work_mem=64MB for ~6GB PG allocation in low-concurrency system — Phase 12-01
- HikariCP max-lifetime and idle-timeout left at defaults (30min/10min) — Phase 12-01
- OnnxRuntimeConfig uses BeanFactoryPostProcessor to initialize OrtEnvironment before any ONNX model bean loads — Phase 12-02
- BGE query prefix applied only to search queries, not to documents at ingestion time — Phase 12-02
- OWASP dependencyCheckAnalyze and CycloneDX cyclonedxBom removed from default build/check tasks; available manually — Phase 12 review
- CI ONNX model cached with actions/cache; security job has timeout-minutes: 15 — Phase 12 review
- NDCG uses log2(rank+1) discount; relevance threshold >= 1 for binary classification from graded judgments — Phase 13-01
- computeAll uses single-pass loop for efficiency; Precision@k divides by actual retrieved count, not k — Phase 13-01
- Clock bean in ClockConfig with @ConditionalOnMissingBean for testable timestamps — Phase 13-02
- CSV values formatted with Locale.US for deterministic output; per-query metrics on first chunk row only — Phase 13-02
- Detailed CSV includes per-query metrics only on first chunk row to avoid redundancy — Phase 13-02
- Chunk ID matching uses sourceUrl + '#' + sectionPath with exact-then-substring fallback for golden set alignment — Phase 13-03
- useJUnitPlatform excludeTags("eval") conditional on -PincludeEvalTag property for on-demand evaluation — Phase 13-03
- Parent chunk text reconstructed from raw source lines including code fences, merged in source order — Phase 14-01
- parentId format: {sourceUrl}#{sectionPath} for deterministic child-to-parent linking — Phase 14-01
- Consolidated appendRawNodeText and appendNodeText into single method (identical logic) — Phase 14-01
- jqwik 1.9.2 for property-based testing; 200 tries per property for coverage vs speed balance — Phase 14-03
- Both parent+child chunks embedded for search; no separate context-only storage mechanism — Phase 14-02
- Reranker scores child text for precision, SearchService substitutes parent text for context richness — Phase 14-02
- Parent text batch-fetched via native query using source_url#section_path composite key — Phase 14-02
- DoubleSummaryStatistics for single-pass min/max in fusion normalisation — Phase 15-01
- EMPTY_EMBEDDING (float[0]) placeholder for FTS-only results in fusion pipeline — Phase 15-01

### Pending Todos

None.

### Blockers/Concerns

None.

## Session Continuity

Last session: 2026-02-22
Stopped at: Completed 15-01-PLAN.md (ConvexCombinationFusion TDD)
Resume file: .planning/phases/15-search-fusion-overhaul/15-01-SUMMARY.md
Next: Plan 15-02 (SearchService pipeline wiring)
