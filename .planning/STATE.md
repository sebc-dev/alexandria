# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-01-19)

**Core value:** Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.
**Current focus:** Phase 4 - Recherche Base (Complete)

## Current Position

Phase: 4 of 7 (Recherche Base)
Plan: 2 of 2 in current phase
Status: Phase complete
Last activity: 2026-01-20 - Completed 04-02-PLAN.md (Search Implementation)

Progress: [#########.] 64%

## Performance Metrics

**Velocity:**
- Total plans completed: 9
- Average duration: 3.5 min
- Total execution time: 0.53 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-infrastructure | 2 | 5 min | 2.5 min |
| 02-ingestion-core | 3 | 13 min | 4.3 min |
| 03-graph-relations | 2 | 12 min | 6.0 min |
| 04-recherche-base | 2 | 4 min | 2.0 min |

**Recent Trend:**
- Last 5 plans: 03-01 (4 min), 03-02 (8 min), 04-01 (1 min), 04-02 (3 min)
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
- Character-based token approximation (~4 chars/token) for LangChain4j DocumentSplitters
- ChunkPair uses String content to keep domain model independent of LangChain4j types
- Port interfaces (ChunkerPort, MarkdownParserPort) for clean unit testing with mocks
- SHA-256 content hash prevents redundant re-indexing of unchanged files
- Upsert pattern: delete old chunks then insert new, not update in place
- Testcontainers with pgvector/pgvector:pg17 for integration testing
- GraphRepository: cypher() function with AS clause for AGE queries
- DETACH DELETE for cascading vertex/edge deletion in graph
- Graph data deleted before PostgreSQL data during re-indexing
- CrossReferenceExtractorPort interface for hexagonal architecture and testability
- Cross-references extracted even for short files (links provide value)
- Forward references only in v1 (target must be indexed first)
- Gson for agtype JSON parsing (Spring Boot managed version)
- SearchResult includes parentContext for LLM context expansion
- SearchFilters validates maxResults (1-100), minSimilarity (0-1) in compact constructor
- SearchRepository searches CHILD chunks only, returns parent context via JOIN
- Post-filter minSimilarity in Java (pgvector post-filters AFTER HNSW scan)
- Fetch 3x results when minSimilarity set to compensate for client-side filtering
- Use tags @> containment operator for array filtering with GIN index

### Pending Todos

None yet.

### Blockers/Concerns

- Docker Desktop WSL integration not enabled - verification of 01-01 and 01-02 pending
- Integration tests cannot be executed without Docker

## Session Continuity

Last session: 2026-01-20 15:46 UTC
Stopped at: Completed 04-02-PLAN.md (Search Implementation)
Resume file: None
