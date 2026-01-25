# Project Research Summary

**Project:** Alexandria v0.4 — RAG Evaluation Toolkit
**Domain:** RAG system evaluation and observability
**Researched:** 2026-01-24
**Confidence:** MEDIUM-HIGH

## Executive Summary

Building a RAG evaluation toolkit for Alexandria requires a strategic blend of pure Java implementations for deterministic metrics and selective Python integration for advanced analytics. The research reveals that **retrieval metrics (Precision@k, Recall@k, MRR) should be implemented first** as they require no LLM, provide immediate feedback, and establish the foundation for more complex evaluations. The recommended stack extends Alexandria's existing Java 21 / Spring Boot / LangChain4j base with SMILE 3.1.1 for clustering, JGraphT 1.5.2 for graph analysis, and VictoriaMetrics for metrics storage.

The key technical insight is that **Quarkus LangChain4j Testing is NOT compatible with Spring Boot** — it requires the Quarkus runtime. For Alexandria, this means building custom evaluation services using LangChain4j core + Micrometer metrics rather than adopting an existing framework. Similarly, Apache AGE lacks native graph algorithms (no WCC, PageRank), making NetworkX export mandatory for connected component analysis. These constraints shape the architecture into a hexagonal pattern with Python sidecar integration for unavoidable gaps.

The primary risk is **distance metric mismatch** (using Euclidean instead of cosine for embeddings), which invalidates all downstream metrics. Secondary risks include silhouette score O(N^2) complexity causing OOM on large corpora, pgvector benchmarks on cold cache producing misleading results, and LLM-as-Judge JSON parsing failures with models under 7B parameters. All critical pitfalls have documented prevention strategies.

## Key Findings

### Recommended Stack

The evaluation toolkit extends Alexandria's existing stack without replacing any core components. All additions are optional and enabled via Docker Compose profiles or Maven profiles.

**Core technologies:**
- **SMILE 3.1.1** (not 5.x): K-Means clustering, silhouette score — pure Java, no native BLAS dependencies
- **JGraphT 1.5.2**: Connected components, graph metrics — replaces NetworkX for basic graph analysis
- **VictoriaMetrics 1.133.0**: Metrics storage — 4x less RAM than Prometheus (4GB vs 16-23GB)
- **Grafana 11.5.0 + Loki 3.6.0 + Alloy 1.7.5**: Dashboards and log aggregation — Alloy replaces deprecated Promtail
- **Ollama with Llama 3.1 8B**: LLM-as-Judge — recommended over Mistral 7B (MMLU 73% vs 68%)
- **LangChain4j Ollama client**: LLM integration — already in project, stable API

**Critical version notes:**
- SMILE 5.x requires native BLAS — avoid for simpler deployment
- Grafana Promtail is EOL 2026 — use Alloy for new deployments
- Models <7B parameters produce unreliable JSON parsing — minimum 7B for LLM-as-Judge

### Expected Features

**Must have (table stakes):**
- Precision@k, Recall@k, MRR — deterministic retrieval metrics, no LLM needed
- Per-query breakdown — individual scores, not just aggregates
- Latency metrics (p50/p95/p99) — Micrometer Timer + VictoriaMetrics
- Golden dataset infrastructure — JSONL format, versioned storage
- Orphan/duplicate detection for knowledge graph — pure Cypher queries

**Should have (competitive):**
- Silhouette score for embeddings — SMILE with sampling (max 5000 docs)
- pgvector recall/latency benchmarks — ef_search tuning curves
- NDCG for graded relevance — handles non-binary relevance scores
- Faithfulness scoring via LLM-as-Judge — Ollama + LangChain4j
- Grafana dashboards — pre-built RAG performance panels

**Defer (v2+):**
- Drift detection — requires stable baseline first
- Connected components analysis — needs NetworkX sidecar
- Multi-judge consensus — complexity vs value tradeoff
- Full OpenTelemetry tracing — adds deployment complexity
- UMAP visualization — Python dependency for marginal value

### Architecture Approach

The evaluation toolkit integrates with Alexandria's existing hexagonal architecture through a new `eval` package: `core/eval/` for pure Java metrics calculation and port interfaces, `infra/eval/` for external tool adapters (SMILE, Ollama, Python sidecar). Docker Compose extends with an optional `eval` profile containing VictoriaMetrics, Grafana, Loki, and Ollama services. This pattern preserves testability and follows established project conventions.

**Major components:**
1. **core/eval/MetricsCalculator** — Pure Java retrieval metrics (P@k, Recall@k, MRR, NDCG)
2. **core/eval/EvaluationService** — Orchestrates evaluation runs, coordinates all metrics
3. **core/eval/GoldenDataset** — Loads/parses JSONL golden dataset files
4. **infra/eval/SmileEmbeddingAnalyzer** — SMILE clustering + silhouette score
5. **infra/eval/OllamaJudgeAdapter** — LangChain4j client for LLM-as-judge
6. **infra/eval/PythonSidecarClient** — REST client for NetworkX/UMAP (optional)
7. **api/eval/EvalCommands** — Spring Shell commands for CLI evaluation

### Critical Pitfalls

1. **Distance metric mismatch** — Always use cosine distance (`<=>` operator, `vector_cosine_ops` index) for all-MiniLM-L6-v2 embeddings. Euclidean distance produces semantically incorrect rankings.

2. **pgvector cold cache benchmarks** — Pre-warm index with `pg_prewarm()` and 100+ warmup queries before measurement. Cold cache shows 10-100x worse latency than production.

3. **Silhouette score O(N^2) OOM** — Sample max 5000 embeddings for silhouette calculation. Full corpus computation causes heap exhaustion.

4. **AGE missing graph algorithms** — Apache AGE has no WCC, PageRank, or APOC procedures. Export to NetworkX via CSV for connected component analysis.

5. **LLM <7B JSON failures** — Models under 7B parameters produce malformed JSON. Use Llama 3.1 8B minimum with robust JSON extraction fallbacks.

6. **Same model for gen+eval bias** — Using identical LLM for RAG generation and evaluation produces inflated scores. Use different models or at minimum different temperatures.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Metrics Infrastructure
**Rationale:** Foundation for all evaluation; no external dependencies needed
**Delivers:** Micrometer instrumentation, `/actuator/prometheus` endpoint, pure Java retrieval metrics
**Addresses:** Latency metrics, Precision@k, Recall@k, MRR
**Avoids:** Distance metric mismatch (verify cosine usage in SearchService)

### Phase 2: Docker Compose Monitoring Stack
**Rationale:** Provides visibility before complex features; validates scraping pipeline
**Delivers:** VictoriaMetrics + Grafana + Loki containers, basic dashboards
**Uses:** Docker Compose profiles (`--profile eval`)
**Avoids:** Prometheus OOM issues (use VictoriaMetrics instead)

### Phase 3: Golden Dataset Infrastructure
**Rationale:** Required foundation for all evaluation; enables reproducible testing
**Delivers:** JSONL schema, GoldenDataset loader, versioned storage structure
**Addresses:** Per-query breakdown, deterministic evaluation
**Avoids:** Golden dataset overfitting (20% holdout from start)

### Phase 4: Embedding & Vector Index Evaluation
**Rationale:** SMILE integration after metrics infrastructure proven
**Delivers:** Silhouette score, pgvector recall/latency benchmarks
**Uses:** SMILE 3.1.1, pg_prewarm, EXPLAIN ANALYZE verification
**Avoids:** Silhouette OOM (sampling), cold cache bias (warmup procedure)

### Phase 5: Knowledge Graph Validation
**Rationale:** Cypher queries work now; defer NetworkX until needed
**Delivers:** Orphan detection, duplicate detection, basic graph stats
**Uses:** Pure Cypher on Apache AGE
**Avoids:** AGE algorithm assumptions (export to NetworkX only if WCC needed)

### Phase 6: LLM-as-Judge Integration
**Rationale:** Most complex; requires all prior infrastructure
**Delivers:** Faithfulness scoring, answer relevancy, Ollama integration
**Uses:** Ollama + Llama 3.1 8B + LangChain4j Ollama client
**Avoids:** JSON parsing failures (7B+ model), self-enhancement bias (different model)

### Phase Ordering Rationale

- **Infrastructure first:** Phases 1-2 establish visibility before adding complexity
- **Golden dataset before evaluation:** Phase 3 enables reproducible testing for subsequent phases
- **Pure Java before external deps:** Phases 1, 3, 5 have no new dependencies; defer SMILE/Ollama until needed
- **Graph validation before LLM:** Cypher queries are simpler; LLM-as-Judge is most complex
- **Pitfall-aware ordering:** Each phase includes prevention for its relevant pitfalls

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 4:** SMILE API verification — silhouette score implementation details need validation
- **Phase 6:** LLM-as-Judge prompts — faithfulness/relevancy prompt engineering requires iteration

Phases with standard patterns (skip research-phase):
- **Phase 1:** Micrometer integration — well-documented Spring Boot pattern
- **Phase 2:** Docker Compose profiles — standard Docker feature
- **Phase 3:** JSONL parsing — trivial Java implementation
- **Phase 5:** Cypher queries — queries documented in research

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | MEDIUM-HIGH | Core recommendations verified; SMILE silhouette API needs validation |
| Features | HIGH | Aligned with existing research; clear prioritization |
| Architecture | HIGH | Follows established Alexandria hexagonal pattern |
| Pitfalls | HIGH | All critical pitfalls have verified prevention strategies |

**Overall confidence:** MEDIUM-HIGH

### Gaps to Address

- **SMILE silhouette score API:** Research confirms no built-in method; manual implementation provided but needs testing
- **Quarkus LangChain4j scorer:** Confirmed incompatible with Spring Boot; custom service approach validated
- **Grafana dashboard templates:** No RAG-specific templates found; will need custom creation
- **Python sidecar necessity:** JGraphT may cover enough graph analysis to defer Python integration
- **LLM-as-Judge prompt engineering:** Prompts for faithfulness/relevancy need iteration during implementation

## Sources

### Primary (HIGH confidence)
- [JGraphT Official Site](https://jgrapht.org/) — Maven coordinates, version 1.5.2
- [VictoriaMetrics Quick Start](https://docs.victoriametrics.com/victoriametrics/quick-start/) — Docker image v1.133.0
- [Grafana Loki Docker Install](https://grafana.com/docs/loki/latest/setup/install/docker/) — Version 3.6.0, Alloy migration
- [LangChain4j Ollama Integration](https://docs.langchain4j.dev/integrations/language-models/ollama/) — Java client for Ollama
- [Quarkus LangChain4j Testing](https://docs.quarkiverse.io/quarkus-langchain4j/dev/testing.html) — Confirmed requires Quarkus runtime
- [Micrometer Timers](https://docs.micrometer.io/micrometer/reference/concepts/timers.html) — Timer and histogram concepts
- [pgvector GitHub](https://github.com/pgvector/pgvector) — Distance operators, HNSW configuration
- [Docker Docs: Control Startup Order](https://docs.docker.com/compose/how-tos/startup-order/) — Health check patterns

### Secondary (MEDIUM confidence)
- [SMILE GitHub](https://github.com/haifengl/smile) — Version 3.1.1 vs 5.x tradeoffs
- [Ollama Llama 3.3](https://ollama.com/library/llama3.3) — Model capabilities
- [Last9: Prometheus vs VictoriaMetrics](https://last9.io/blog/prometheus-vs-victoriametrics/) — RAM comparison
- [Crunchy Data: pgvector Performance](https://www.crunchydata.com/blog/pgvector-performance-for-developers) — Benchmark methodology
- [Evidently AI: LLM-as-a-Judge](https://www.evidentlyai.com/llm-guide/llm-as-a-judge) — Best practices

### Tertiary (LOW confidence)
- Grafana version 11.5.0 — Inferred from release patterns; verify on Docker Hub
- Llama 3.1 8B vs Mistral 7B quality for LLM-as-Judge — Based on MMLU benchmarks, not judge-specific tests
- SMILE 3.1.1 API stability — Older version; verify Maven Central availability

### Project-Specific Research
- `Validation d'un systeme RAG hybride sans outils payants.md` — 8-week implementation roadmap
- `Creer un golden dataset RAG hybride.md` — Golden dataset schema and methodology

---
*Research completed: 2026-01-24*
*Ready for roadmap: yes*
