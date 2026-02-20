# Roadmap: Alexandria

## Milestones

- ✅ **v0.1 Full RAG System** — Phases 0-9 (shipped 2026-02-20) — [Archive](milestones/v1.5-ROADMAP.md)
- 🚧 **v0.2 Audit & Optimisation** — Phases 11-18 (in progress)

## Phases

<details>
<summary>✅ v0.1 Full RAG System (Phases 0-9) — SHIPPED 2026-02-20</summary>

- [x] Phase 0: CI & Quality Gate (2/2 plans) — completed 2026-02-14
- [x] Phase 1: Foundation & Infrastructure (2/2 plans) — completed 2026-02-14
- [x] Phase 2: Core Search (2/2 plans) — completed 2026-02-15
- [x] Phase 3: Web Crawling (2/2 plans) — completed 2026-02-15
- [x] Phase 4: Ingestion Pipeline (2/2 plans) — completed 2026-02-18
- [x] Phase 4.5: Code Quality & Test Consolidation (5/5 plans) — completed 2026-02-19
- [x] Phase 5: MCP Server (2/2 plans) — completed 2026-02-20
- [x] ~~Phase 6: Source Management~~ — Superseded by Phase 9
- [x] Phase 7: Crawl Operations (5/5 plans) — completed 2026-02-20
- [x] Phase 8: Advanced Search & Quality (4/4 plans) — completed 2026-02-20
- [x] Phase 9: Source Management Completion (2/2 plans) — completed 2026-02-20

</details>

### 🚧 v0.2 Audit & Optimisation (In Progress)

**Milestone Goal:** Auditer et optimiser la qualite RAG, la robustesse du code, la performance et l'observabilite du systeme. Build evaluation before changing the pipeline, measure before/after.

- [ ] **Phase 11: Quality & Security Tooling** — Safety net before refactoring
- [ ] **Phase 12: Performance Quick Wins** — Config-level tuning with immediate impact
- [ ] **Phase 13: Retrieval Evaluation Framework** — Measurement before pipeline changes
- [ ] **Phase 14: Parent-Child Chunking** — Restructure chunks to reunite code and prose
- [ ] **Phase 15: Search Fusion Overhaul** — Convex Combination replaces RRF
- [ ] **Phase 16: MCP Testing** — Snapshot and round-trip test coverage
- [ ] **Phase 17: Monitoring Stack** — Observability for the production pipeline
- [ ] **Phase 18: Ablation Study & Validation** — Measure the optimised pipeline end-to-end

## Phase Details

### Phase 11: Quality & Security Tooling
**Goal**: The build catches bugs, null safety violations, formatting drift, and vulnerable dependencies automatically
**Depends on**: Nothing (independent of RAG pipeline)
**Requirements**: QUAL-01, QUAL-02, QUAL-03, SECU-01, SECU-02, SECU-03
**Success Criteria** (what must be TRUE):
  1. `./gradlew build` fails if Error Prone detects an ERROR-level bug pattern in any Java file
  2. NullAway reports null safety violations at compile time for all annotated packages
  3. `./gradlew spotlessCheck` fails on code that does not match google-java-format (ratcheted to changed files only)
  4. Trivy scans all 3 Docker images and the Java filesystem in CI, failing on HIGH/CRITICAL CVEs
  5. OWASP Dependency-Check runs in the Gradle build and fails on CVSS >= 7.0; CycloneDX generates an SBOM artifact
**Plans**: TBD

### Phase 12: Performance Quick Wins
**Goal**: Latency and resource usage improve through configuration-only changes with no code refactoring
**Depends on**: Nothing (independent config changes)
**Requirements**: PERF-01, PERF-02, PERF-03, CHUNK-03
**Success Criteria** (what must be TRUE):
  1. ONNX Runtime thread spinning is disabled (allow_spinning=0) and thread pools are configured, reducing idle CPU consumption
  2. PostgreSQL is tuned for RAG workload (shared_buffers, ef_search=100, JIT off, maintenance_work_mem) via docker-compose config or init script
  3. HikariCP pool is sized for virtual threads (10-15 connections, connection-timeout 5-10s) in application properties
  4. Search queries prepend the BGE query prefix to embedding requests, improving retrieval relevance without reindexing
**Plans**: TBD

### Phase 13: Retrieval Evaluation Framework
**Goal**: Retrieval quality is measurable and tracked with a golden set and standard IR metrics before any pipeline changes
**Depends on**: Phase 12 (BGE prefix applied; baseline measurements should include it)
**Requirements**: EVAL-01, EVAL-02, EVAL-03, EVAL-05
**Success Criteria** (what must be TRUE):
  1. A RetrievalMetrics class computes Recall@k, Precision@k, MRR, NDCG@k, MAP, and Hit Rate from search results and relevance judgments
  2. A golden set of 100 annotated queries exists covering factual, conceptual, code lookup, and troubleshooting query types
  3. A JUnit 5 parameterised test executes the full golden set against the live index and asserts Recall@10 >= 0.70 and MRR >= 0.60
  4. Evaluation results are exported to CSV files with timestamped filenames for trend tracking across runs
**Plans**: TBD

### Phase 14: Parent-Child Chunking
**Goal**: Search returns complete context (code + surrounding prose) by linking child chunks to their parent sections
**Depends on**: Phase 13 (evaluation framework measures impact of chunking changes)
**Requirements**: CHUNK-01, CHUNK-02, QUAL-04
**Success Criteria** (what must be TRUE):
  1. The chunker produces parent chunks (full H2/H3 sections with code+prose) and child chunks (individual paragraphs/blocks) with parent-child links in metadata
  2. When a child chunk matches a search query, the search service returns the parent chunk's full content, reuniting code and prose in context
  3. jqwik property-based tests verify chunker invariants: content conservation (no data loss), size bounds respected, code blocks balanced, tables complete
**Plans**: TBD

### Phase 15: Search Fusion Overhaul
**Goal**: Hybrid search uses Convex Combination with configurable parameters, replacing RRF for better score utilisation
**Depends on**: Phase 14 (parent-child chunks indexed; evaluation framework captures before/after)
**Requirements**: FUSE-01, FUSE-02, FUSE-03
**Success Criteria** (what must be TRUE):
  1. Hybrid search fuses vector and FTS scores using Convex Combination (normalised score weighting) instead of RRF
  2. The alpha parameter controlling vector vs FTS weight is configurable via application.properties and can be changed without rebuild
  3. The number of candidates sent to the reranker is configurable (default 30, supports 20/30/50) via application.properties
**Plans**: TBD

### Phase 16: MCP Testing
**Goal**: MCP tool contracts are verified by snapshot tests and round-trip integration tests
**Depends on**: Nothing (independent testing layer; can run after Phase 11 quality tooling is in place)
**Requirements**: MCPT-01, MCPT-02
**Success Criteria** (what must be TRUE):
  1. A snapshot test compares the current tools/list MCP schema against a versioned reference file and fails if the schema changes unexpectedly
  2. Round-trip integration tests via McpAsyncClient exercise all 7 MCP tools (happy path and error cases), verifying end-to-end JSON-RPC communication
**Plans**: TBD

### Phase 17: Monitoring Stack
**Goal**: Every stage of the search pipeline is instrumented and observable via dashboards with alerting on critical thresholds
**Depends on**: Phase 15 (search pipeline finalised; Micrometer instruments the current pipeline, not a pipeline that will change)
**Requirements**: MONI-01, MONI-02, MONI-03, MONI-04
**Success Criteria** (what must be TRUE):
  1. Micrometer timers with p50/p95/p99 percentiles instrument each search pipeline stage (embedding, vector search, FTS, fusion, reranking)
  2. VictoriaMetrics, Grafana, and postgres_exporter are deployed via docker-compose and scraping application + database metrics
  3. A Grafana dashboard displays RAG-specific metrics: latency per stage, empty result rate, top score distribution, HikariCP pool usage
  4. Alerts fire when p95 latency exceeds 2s, cache hit rate drops below 90%, or error rate exceeds 5%
**Plans**: TBD

### Phase 18: Ablation Study & Validation
**Goal**: The optimised pipeline is validated end-to-end with a controlled ablation study comparing all fusion strategies
**Depends on**: Phase 15 (Convex Combination implemented), Phase 13 (evaluation framework ready)
**Requirements**: EVAL-04
**Success Criteria** (what must be TRUE):
  1. An ablation study compares vector-only, FTS-only, hybrid CC, and hybrid CC+reranking on the golden set, with results exported to CSV
  2. The final pipeline configuration (alpha, candidate count) is justified by ablation results showing improvement over the v0.1 RRF baseline
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 11 → 12 → 13 → 14 → 15 → 16 → 17 → 18
Phase 16 (MCP Testing) is independent and can execute in parallel with phases 14-15 if desired.

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 0. CI & Quality Gate | v0.1 | 2/2 | Complete | 2026-02-14 |
| 1. Foundation & Infrastructure | v0.1 | 2/2 | Complete | 2026-02-14 |
| 2. Core Search | v0.1 | 2/2 | Complete | 2026-02-15 |
| 3. Web Crawling | v0.1 | 2/2 | Complete | 2026-02-15 |
| 4. Ingestion Pipeline | v0.1 | 2/2 | Complete | 2026-02-18 |
| 4.5. Code Quality | v0.1 | 5/5 | Complete | 2026-02-19 |
| 5. MCP Server | v0.1 | 2/2 | Complete | 2026-02-20 |
| 7. Crawl Operations | v0.1 | 5/5 | Complete | 2026-02-20 |
| 8. Advanced Search & Quality | v0.1 | 4/4 | Complete | 2026-02-20 |
| 9. Source Management | v0.1 | 2/2 | Complete | 2026-02-20 |
| 11. Quality & Security Tooling | v0.2 | 0/TBD | Not started | - |
| 12. Performance Quick Wins | v0.2 | 0/TBD | Not started | - |
| 13. Retrieval Evaluation Framework | v0.2 | 0/TBD | Not started | - |
| 14. Parent-Child Chunking | v0.2 | 0/TBD | Not started | - |
| 15. Search Fusion Overhaul | v0.2 | 0/TBD | Not started | - |
| 16. MCP Testing | v0.2 | 0/TBD | Not started | - |
| 17. Monitoring Stack | v0.2 | 0/TBD | Not started | - |
| 18. Ablation Study & Validation | v0.2 | 0/TBD | Not started | - |
