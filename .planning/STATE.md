# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-19)

**Core value:** Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.
**Current focus:** Phase 2 - Ingestion Core (Plan 1 of 3 complete)

## Current Position

Phase: 2 of 7 (Ingestion Core)
Plan: 1 of 3 in current phase
Status: In progress
Last activity: 2026-01-20 - Completed 02-01-PLAN.md (Domain Models and Markdown Parser)

Progress: [###.......] 21%

## Performance Metrics

**Velocity:**
- Total plans completed: 3
- Average duration: 3.0 min
- Total execution time: 0.15 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-infrastructure | 2 | 5 min | 2.5 min |
| 02-ingestion-core | 1 | 4 min | 4.0 min |

**Recent Trend:**
- Last 5 plans: 01-01 (2 min), 01-02 (3 min), 02-01 (4 min)
- Trend: stable

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Stack: LangChain4j 1.0.0-beta3, PostgreSQL 17, pgvector 0.8.1, Apache AGE 1.6.0
- Embeddings: all-MiniLM-L6-v2 via ONNX (in-process, ~100MB RAM)
- Chunking: Hierarchical parent (1000 tokens) / child (200 tokens)
- HNSW index: m=16, ef_construction=100 for optimal recall
- tsvector: 'simple' config for mixed FR/EN technical content
- Docker: Multi-stage build pattern for smaller images
- Java 21 virtual threads: spring.threads.virtual.enabled=true
- AGE session: HikariCP connection-init-sql loads AGE per connection
- Architecture: Three-layer (api -> core -> infra) with dependency inversion
- CommonMark 0.22.0 for markdown parsing (10-20x faster than pegdown)
- Java 21 records for immutable domain models with defensive copying
- Port/adapter pattern for repository (core defines contract, infra implements)

### Pending Todos

None yet.

### Blockers/Concerns

- Docker Desktop WSL integration not enabled - verification of 01-01 and 01-02 pending
- Integration tests cannot be executed without Docker

## Session Continuity

Last session: 2026-01-20 10:07 UTC
Stopped at: Completed 02-01-PLAN.md (Domain Models and Markdown Parser)
Resume file: None
