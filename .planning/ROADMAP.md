# Roadmap: Alexandria v0.4 RAG Evaluation Toolkit

## Milestones

- v0.1 Core RAG System (Phases 01-07) - SHIPPED 2025-12-14
- v0.2 Docker & CI (Phases 08-10) - SHIPPED 2026-01-13
- v0.3 Quality Gate (Phases 11-14) - SHIPPED 2026-01-23
- **v0.4 RAG Evaluation Toolkit** (Phases 15-20) - IN PROGRESS

## Overview

This milestone delivers an optional RAG evaluation toolkit enabling systematic quality measurement. The journey starts with application metrics instrumentation (Phase 15), builds the observability stack (Phase 16), establishes golden dataset infrastructure for reproducible evaluation (Phase 17), adds embedding and vector index benchmarking (Phase 18), validates knowledge graph integrity (Phase 19), and culminates with LLM-as-Judge integration for semantic evaluation (Phase 20). All evaluation components are optional and enabled via Docker Compose profiles.

## Phases

**Phase Numbering:**
- Integer phases (15, 16, 17...): Planned milestone work
- Decimal phases (15.1, 15.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 15: Metrics Foundation** - Micrometer instrumentation exposing application metrics
- [x] **Phase 16: Monitoring Stack** - VictoriaMetrics + Grafana + Loki deployment
- [ ] **Phase 17: Golden Dataset & Retrieval Evaluation** - JSONL schema and pure Java retrieval metrics
- [ ] **Phase 18: Embedding & Vector Index Evaluation** - SMILE clustering and pgvector benchmarks
- [ ] **Phase 19: Graph Validation** - Cypher queries and WCC analysis via Python sidecar
- [ ] **Phase 20: LLM-as-Judge Integration** - Ollama + faithfulness/relevancy scoring

## Phase Details

### Phase 15: Metrics Foundation
**Goal**: Application exposes Micrometer metrics for external scraping
**Depends on**: Nothing (foundation phase)
**Requirements**: MON-03
**Success Criteria** (what must be TRUE):
  1. `/actuator/prometheus` endpoint returns metrics in Prometheus format
  2. Search latency tracked with Timer (p50/p95/p99 percentiles available)
  3. Embedding generation latency tracked with Timer
  4. Document ingestion count tracked with Counter
**Plans**: 1 plan

Plans:
- [x] 15-01-PLAN.md - Add Prometheus registry and instrument core services

### Phase 16: Monitoring Stack
**Goal**: Full observability stack running in Docker with preconfigured dashboards
**Depends on**: Phase 15
**Requirements**: MON-01, MON-02, MON-04, MON-05, INFRA-01, INFRA-04
**Success Criteria** (what must be TRUE):
  1. `docker compose --profile eval up` starts VictoriaMetrics, Grafana, Loki, and Alloy
  2. Grafana dashboard shows RAG-specific panels (search latency, ingestion rate, error rate)
  3. Application logs visible in Grafana via Loki datasource
  4. Alerts fire when latency exceeds threshold or error rate spikes
  5. All services have health checks with `service_healthy` condition
**Plans**: 3 plans

Plans:
- [x] 16-01-PLAN.md - Add VictoriaMetrics + Grafana to docker-compose with eval profile
- [x] 16-02-PLAN.md - Add Loki + Alloy for log aggregation
- [x] 16-03-PLAN.md - Create Grafana dashboards and alerts

### Phase 17: Golden Dataset & Retrieval Evaluation
**Goal**: Golden dataset infrastructure and pure Java retrieval metrics enabling reproducible evaluation
**Depends on**: Phase 16
**Requirements**: RETR-01, RETR-02, RETR-03, RETR-04, RETR-05, RETR-06, RETR-07
**Success Criteria** (what must be TRUE):
  1. Golden dataset JSONL file loads with fields: query, expected_doc_ids, requires_kg, reasoning_hops, question_type
  2. Running evaluation command outputs Precision@5, Precision@10, Recall@10, Recall@20, MRR, NDCG@10
  3. Results are segmented by question type (factual, multi-hop, graph_traversal)
  4. Evaluation report shows breakdown by category with pass/fail indication
**Plans**: 3 plans

Plans:
- [ ] 17-01-PLAN.md - Golden dataset JSONL schema and loader
- [ ] 17-02-PLAN.md - Pure Java retrieval metrics (Precision@k, Recall@k, MRR, NDCG)
- [ ] 17-03-PLAN.md - EvaluationService and CLI command with segmented reporting

### Phase 18: Embedding & Vector Index Evaluation
**Goal**: Embedding quality and pgvector HNSW performance benchmarking
**Depends on**: Phase 17
**Requirements**: EMB-01, EMB-02, EMB-03, EMB-04, VEC-01, VEC-02, VEC-03, VEC-04, VEC-05
**Success Criteria** (what must be TRUE):
  1. Silhouette score calculated via SMILE with cosine distance on sampled embeddings
  2. Baseline embedding snapshot saved and drift detection compares current vs baseline
  3. UMAP 2D visualization exported as PNG image
  4. pgvector recall@k measured for ef_search = 40, 100, 200 against exact search baseline
  5. Latency p50/p95/p99 measured with pg_stat_statements after cache warming
  6. Recall/latency curve exported as data file
**Plans**: TBD

Plans:
- [ ] 18-01: SMILE integration for silhouette score with sampling
- [ ] 18-02: Embedding baseline snapshot and drift detection
- [ ] 18-03: pgvector HNSW benchmark with cache warming and exact search comparison
- [ ] 18-04: UMAP visualization via Python sidecar

### Phase 19: Graph Validation
**Goal**: Knowledge graph integrity validation and connectivity analysis
**Depends on**: Phase 18
**Requirements**: GRAPH-01, GRAPH-02, GRAPH-03, GRAPH-04, GRAPH-05, INFRA-05
**Success Criteria** (what must be TRUE):
  1. Cypher query returns list of orphan nodes (nodes with no edges)
  2. Cypher query returns list of duplicate nodes
  3. Graph stats command outputs node count, edge count, and density
  4. WCC analysis identifies connected components via Python sidecar (NetworkX)
  5. GraphML export works for Gephi visualization
  6. Python sidecar FastAPI runs as optional Docker service
**Plans**: TBD

Plans:
- [ ] 19-01: Cypher queries for orphans, duplicates, and basic stats
- [ ] 19-02: Python sidecar FastAPI with NetworkX WCC analysis
- [ ] 19-03: GraphML export for Gephi visualization

### Phase 20: LLM-as-Judge Integration
**Goal**: Semantic evaluation using LLM-as-Judge with Ollama
**Depends on**: Phase 19
**Requirements**: LLM-01, LLM-02, LLM-03, LLM-04, LLM-05, INFRA-02, INFRA-03
**Success Criteria** (what must be TRUE):
  1. Ollama service runs in Docker with Llama 3.1 8B (or configurable model)
  2. Faithfulness score calculated (supported claims / total claims)
  3. Answer relevancy score calculated
  4. Different model can be configured to avoid self-enhancement bias
  5. Batch evaluation produces synthesis report with averages and breakdown
  6. `./eval` and `./benchmark` scripts exist for easy evaluation
  7. README documents toolkit usage with examples
**Plans**: TBD

Plans:
- [ ] 20-01: Ollama Docker integration and LangChain4j client setup
- [ ] 20-02: Faithfulness and relevancy scoring with prompt engineering
- [ ] 20-03: Batch evaluation with synthesis report
- [ ] 20-04: Scripts and documentation

## Progress

**Execution Order:**
Phases execute in numeric order: 15 -> 16 -> 17 -> 18 -> 19 -> 20

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 15. Metrics Foundation | v0.4 | 1/1 | Complete | 2026-01-24 |
| 16. Monitoring Stack | v0.4 | 3/3 | Complete | 2026-01-25 |
| 17. Golden Dataset & Retrieval | v0.4 | 0/3 | Planned | - |
| 18. Embedding & Vector Index | v0.4 | 0/4 | Not started | - |
| 19. Graph Validation | v0.4 | 0/3 | Not started | - |
| 20. LLM-as-Judge | v0.4 | 0/4 | Not started | - |

**Total:** 4/18 plans complete (22%)
