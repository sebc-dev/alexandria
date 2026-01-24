# Feature Landscape: RAG Evaluation Toolkit

**Domain:** RAG Evaluation for Hybrid Search System (vector + knowledge graph)
**Project:** Alexandria v0.4 Milestone
**Researched:** 2026-01-24
**Overall Confidence:** HIGH (verified against existing research + multiple 2025 sources)

---

## Executive Summary

A comprehensive RAG evaluation toolkit for Alexandria must cover six evaluation dimensions: retrieval metrics, embeddings quality, vector index performance, knowledge graph validation, LLM-as-judge evaluation, and monitoring/observability. This document categorizes features as table stakes (must-have), differentiators (competitive advantage), and anti-features (deliberately avoid).

**Key insight from existing research:** The project already has a solid foundation with the 8-week implementation roadmap in `Validation d'un systeme RAG hybride sans outils payants.md`. This features document complements that by specifying expected behaviors and acceptance criteria.

---

## 1. Retrieval Metrics

Features for evaluating document retrieval quality before generation.

### Table Stakes

| Feature | Expected Behavior | Complexity | Acceptance Criteria |
|---------|-------------------|------------|---------------------|
| **Precision@k** | Proportion of relevant documents in top-k results | Low | Returns value 0.0-1.0; k configurable (default k=5) |
| **Recall@k** | Proportion of all relevant documents captured in top-k | Low | Returns value 0.0-1.0; requires ground truth annotation |
| **MRR (Mean Reciprocal Rank)** | Average of 1/rank of first relevant document | Low | Returns value 0.0-1.0; higher = faster first hit |
| **Per-query breakdown** | Individual scores per query, not just aggregate | Low | JSON output with query_id, scores, retrieved_docs |
| **Deterministic results** | Same inputs produce same outputs | Low | No LLM required; pure computation |

**Why table stakes:** These are fundamental retrieval metrics used by every RAG evaluation framework (RAGAS, DeepEval, TruLens). Missing any would make the toolkit incomplete.

**Implementation note:** Implement in Java without LLM dependency. The existing research recommends pytrec_eval for Python; for Java, implement directly using formulas.

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **NDCG (Normalized Discounted Cumulative Gain)** | Handles graded relevance (not just binary) | Medium | Penalizes relevant docs lower in ranking; relevance scores 0-2 |
| **MAP (Mean Average Precision)** | Precision averaged at each relevant position | Medium | Popular for recommendation systems |
| **Hybrid search attribution** | Separate scores for vector vs full-text vs graph retrieval | Medium | Unique to Alexandria's hybrid architecture |
| **Recall@k curve visualization** | Graph showing recall improvement as k increases | Low | Helps tune optimal k value |

**Why differentiators:** NDCG with graded relevance goes beyond binary relevant/not-relevant. Hybrid search attribution is unique to systems like Alexandria with multiple retrieval paths.

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Real-time metric computation in production** | Adds latency; metrics need ground truth | Batch evaluation on golden dataset |
| **Automatic threshold alerts on retrieval metrics** | Metrics fluctuate naturally; creates noise | Monitor trends over time, alert on sustained regression |
| **Custom metric formulas** | Leads to non-comparable results | Use standard formulas; document any deviations |

---

## 2. Embeddings Quality

Features for evaluating the quality of vector representations.

### Table Stakes

| Feature | Expected Behavior | Complexity | Acceptance Criteria |
|---------|-------------------|------------|---------------------|
| **Silhouette score** | Cluster coherence measure (-1 to 1) | Medium | Score > 0.3 = good for documentation; uses cosine distance |
| **Embedding export** | Dump embeddings to CSV/NumPy for analysis | Low | Supports all-MiniLM-L6-v2 (384 dims) |
| **Basic clustering** | K-Means with configurable k | Medium | Returns cluster assignments and centroids |
| **Dimension verification** | Confirm embedding dimensions match index | Low | Fail fast if mismatch detected |

**Why table stakes:** Silhouette score is the standard intrinsic metric for embeddings without ground truth. Export enables external tooling (UMAP, scikit-learn).

**Implementation note:** Use SMILE library (Java native) as recommended in existing research. LGPL-3.0 license, Maven compatible.

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Drift detection** | Detect when new documents diverge from corpus | High | Domain classifier ROC-AUC > 0.55 indicates drift |
| **UMAP visualization** | 2D projection for visual inspection | Medium | Requires Python CLI or REST wrapper |
| **Davies-Bouldin Index** | Alternative cluster quality metric | Low | DBI < 1.0 = good; complement to silhouette |
| **Temporal drift tracking** | Track embedding distribution over ingestion batches | Medium | Store baseline centroids; compare new batches |

**Why differentiators:** Drift detection is critical for documentation that evolves over time. UMAP visualization provides human-interpretable quality checks.

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Real-time silhouette computation** | O(N^2) complexity; blocks ingestion | Scheduled batch computation (daily/weekly) |
| **Automatic re-embedding on drift** | Expensive; may not improve quality | Alert for human review; manual re-embedding decision |
| **Euclidean distance metrics** | Wrong for text embeddings normalized to unit sphere | Always use cosine similarity/distance |

**Threshold reference (from existing research):**
| Metric | Bad | Acceptable | Good |
|--------|-----|------------|------|
| Silhouette score (cosine) | < 0.1 | 0.1-0.3 | > 0.3 |
| Davies-Bouldin Index | > 2.0 | 1.0-2.0 | < 1.0 |
| Drift ROC-AUC | > 0.7 | 0.55-0.7 | < 0.55 |

---

## 3. Vector Index Performance

Features for benchmarking pgvector HNSW index.

### Table Stakes

| Feature | Expected Behavior | Complexity | Acceptance Criteria |
|---------|-------------------|------------|---------------------|
| **Recall@k vs exact search** | Compare HNSW results to brute-force | Low | Recall@10 > 95% at default ef_search=40 |
| **Query latency measurement** | p50, p95, p99 latency percentiles | Low | p50 < 2ms warm cache; p99 < 20ms |
| **ef_search tuning benchmarks** | Test recall/latency at ef_search = 40, 100, 200 | Low | Document optimal tradeoff for corpus size |
| **Warm vs cold cache measurement** | Separate metrics for first query vs subsequent | Low | Cold: 5-50ms; Warm: 0.3-2ms expected |

**Why table stakes:** HNSW is approximate; recall measurement validates acceptable accuracy. Latency benchmarks ensure production readiness.

**Implementation note:** Use pg_stat_statements + EXPLAIN ANALYZE as recommended in existing research. No external tools required.

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Recall/latency curve visualization** | Graph showing tradeoff at different ef_search values | Low | Helps choose optimal settings |
| **QPS benchmarks under load** | Measure queries per second with concurrent users | Medium | Use pgbench with custom script |
| **Index build time tracking** | Monitor HNSW construction time as corpus grows | Low | Important for ingestion planning |
| **Memory footprint monitoring** | Track index size vs corpus size | Low | HNSW typically 3-10x vector data size |

**Why differentiators:** These go beyond basic benchmarking to operational concerns (concurrent load, index growth).

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Production traffic benchmarking** | Affects live users; unreliable results | Dedicated benchmark environment or off-hours |
| **Automatic ef_search adjustment** | Query-time parameter changes cause unpredictable latency | Set fixed ef_search; document as configuration |
| **Comparing to external vector DBs** | Different constraints; not actionable | Focus on internal optimization |

**Threshold reference (from existing research):**
| ef_search | Recall@10 Expected | Latency Relative |
|-----------|-------------------|------------------|
| 40 (default) | ~95-98% | 1x |
| 100 | ~98-99% | 2x |
| 200 | ~99%+ | 4x |

---

## 4. Knowledge Graph Validation

Features for validating Apache AGE graph structure and quality.

### Table Stakes

| Feature | Expected Behavior | Complexity | Acceptance Criteria |
|---------|-------------------|------------|---------------------|
| **Orphan node detection** | Count nodes with no relationships | Low | < 5% orphan nodes is healthy |
| **Duplicate entity detection** | Find nodes representing same concept | Low | Report duplicates for manual review |
| **Basic statistics** | Node count, edge count, relationship types | Low | Dashboard or CLI output |
| **Property coverage** | Percentage of nodes with key properties (description, etc.) | Low | > 80% coverage target |

**Why table stakes:** Orphans and duplicates indicate extraction failures. Statistics provide health baseline.

**Implementation note:** Pure Cypher queries on Apache AGE. No external tools required.

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Connected components analysis** | Identify disconnected subgraphs | High | Requires NetworkX export (AGE lacks native WCC) |
| **Graph density metrics** | Measure relationship richness | Low | Density 0.01-0.1 typical for documentation |
| **Concept reuse rate** | Percentage of entities referenced by multiple documents | Low | > 30% indicates good cross-referencing |
| **Average node degree** | Mean number of connections per node | Low | Higher = more connected knowledge |

**Why differentiators:** Connected components reveal structural issues invisible in simple stats. Concept reuse measures knowledge integration.

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Neo4j APOC procedures** | AGE does not support APOC | Use pure Cypher + NetworkX export |
| **Real-time graph validation** | Blocks ingestion; expensive for large graphs | Scheduled validation (daily) |
| **Automatic orphan deletion** | May delete legitimate standalone concepts | Report for human review |
| **PageRank computation** | Overkill for documentation graph; AGE lacks native support | Focus on structural metrics |

**Cypher queries for table stakes (from existing research):**
```cypher
-- Orphan nodes
MATCH (n) WHERE NOT (n)-[]-() RETURN count(n)

-- Duplicate detection (by name)
MATCH (n:Concept)
WITH n.name as name, count(*) as cnt
WHERE cnt > 1 RETURN name, cnt
```

---

## 5. LLM-as-Judge Evaluation

Features for using LLM to evaluate generation quality.

### Table Stakes

| Feature | Expected Behavior | Complexity | Acceptance Criteria |
|---------|-------------------|------------|---------------------|
| **Faithfulness scoring** | Measure if answer is grounded in retrieved context | Medium | Score 0-1; > 0.8 is acceptable |
| **Answer relevancy** | Measure if answer addresses the question | Medium | Score 0-1; > 0.7 is acceptable |
| **Ollama integration** | Support local LLM (Mistral 7B, Llama 3.1 8B) | Medium | No external API costs |
| **Golden dataset evaluation** | Evaluate on curated Q&A pairs | Low | Support JSONL/YAML input format |

**Why table stakes:** Faithfulness and relevancy are the core LLM-as-judge metrics. Ollama enables cost-free local evaluation.

**Implementation note:** Use Quarkus LangChain4j Testing Extension for Java-native integration as recommended in existing research.

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **QAG (Question-Answer Generation) scoring** | More reliable than direct LLM scoring | Medium | Extracts claims, verifies each against context |
| **Multi-judge consensus** | Use multiple models, aggregate scores | High | Reduces single-model bias |
| **Claim-level breakdown** | Show which specific claims are unsupported | Medium | Actionable debugging info |
| **Context precision** | Evaluate retrieval ranking quality via LLM | Medium | Were relevant docs ranked higher? |

**Why differentiators:** QAG avoids unreliable direct scoring. Claim-level breakdown enables targeted improvements.

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Models < 7B parameters** | Produce unstable, unreliable judgments | Use Mistral 7B or Llama 3.1 8B minimum |
| **Same model for generation and evaluation** | Self-enhancement bias | Use different models for each |
| **Generic "goodness" scores** | Don't catch real issues; not actionable | Define specific evaluation criteria |
| **Single-score aggregates hiding failures** | Masks retrieval vs generation errors | Separate component scores |
| **Paid API dependencies** | Ongoing cost; external dependency | Ollama for all evaluation |

**Threshold reference (from existing research):**
| Metric | Threshold |
|--------|-----------|
| Faithfulness | >= 0.8 |
| Answer Relevancy | >= 0.7 |
| Precision@5 (retrieval) | >= 0.7 |
| Recall@10 (retrieval) | >= 0.8 |
| MRR | >= 0.6 |

---

## 6. Monitoring and Observability

Features for production monitoring and debugging.

### Table Stakes

| Feature | Expected Behavior | Complexity | Acceptance Criteria |
|---------|-------------------|------------|---------------------|
| **Latency metrics** | E2E, retrieval, embedding, graph traversal times | Low | Micrometer Timer; p50/p95/p99 |
| **Error rate tracking** | Failed queries, timeout rate | Low | Micrometer Counter; alert if > 5% |
| **Query volume** | Queries per second/minute | Low | Gauge for current load |
| **Grafana dashboard** | Visualize all metrics | Medium | Pre-built panels for RAG metrics |

**Why table stakes:** Basic observability is non-negotiable for production systems.

**Implementation note:** Use VictoriaMetrics (not Prometheus) for 4x lower RAM usage as recommended in existing research.

### Differentiators

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| **Evaluation score trends** | Track faithfulness/relevancy over time | Medium | Batch eval results to metrics store |
| **Component-level latency breakdown** | See where time is spent | Medium | Separate timers for retrieval, rerank, generation |
| **Similarity score distribution** | Track retrieved document scores | Low | Gauge; detect retrieval quality degradation |
| **Cost tracking** | Tokens used per query (for LLM evaluation) | Low | Counter; budget monitoring |
| **Trace correlation** | Link logs, metrics, traces per request | High | OpenTelemetry integration |

**Why differentiators:** Trend monitoring enables proactive quality management. Component breakdown enables targeted optimization.

### Anti-Features

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| **Prometheus (over VictoriaMetrics)** | 4-6x higher RAM usage (6-23GB vs 4GB) | VictoriaMetrics for self-hosted |
| **Alert on every score dip** | Creates noise; scores fluctuate | Anomaly detection on trends; alert on sustained regression |
| **Full query logging** | Privacy concerns; storage costs | Sample logging; hash sensitive content |
| **Elasticsearch for logs** | Overkill for solo developer; high resource usage | Loki for lightweight log aggregation |

---

## Feature Dependencies

```
Retrieval Metrics (foundation)
     |
     v
Embeddings Quality -----> Vector Index Performance
     |                           |
     v                           v
Knowledge Graph Validation <-----+
     |
     v
LLM-as-Judge Evaluation (requires retrieval metrics)
     |
     v
Monitoring/Observability (wraps all above)
```

**Dependency rationale:**
1. Retrieval metrics come first - they require no LLM and provide immediate value
2. Embeddings and vector index can be evaluated in parallel
3. Knowledge graph validation can proceed independently
4. LLM-as-judge requires retrieval metrics to exist for ground truth comparison
5. Monitoring wraps all components

---

## MVP Recommendation

For v0.4 MVP, prioritize:

### Must Have (Weeks 1-4)
1. **Retrieval metrics (Precision@k, Recall@k, MRR)** - No LLM cost; immediate value
2. **Basic monitoring (latency, error rate, Grafana dashboard)** - Production readiness
3. **Golden dataset infrastructure** - Foundation for all evaluation
4. **Orphan/duplicate detection for graph** - Cheap structural validation

### Should Have (Weeks 5-6)
5. **Silhouette score for embeddings** - SMILE library, Java native
6. **pgvector recall/latency benchmarks** - Validate index configuration

### Could Have (Weeks 7-8)
7. **LLM-as-judge (faithfulness, relevancy)** - Ollama + Quarkus LangChain4j
8. **NDCG for graded relevance** - Enhanced retrieval metrics

### Defer to Post-v0.4
- Drift detection (requires stable baseline first)
- Connected components analysis (needs NetworkX integration)
- Multi-judge consensus (complexity vs value)
- Full OpenTelemetry tracing

---

## Sources

### Primary Sources (HIGH confidence)
- Existing research: `Validation d'un systeme RAG hybride sans outils payants.md`
- Existing research: `Creer un golden dataset RAG hybride : methodologies et automatisation LLM.md`

### Web Sources (MEDIUM confidence)
- [RAG Evaluation: A Complete Guide for 2025 - Maxim AI](https://www.getmaxim.ai/articles/rag-evaluation-a-complete-guide-for-2025)
- [The 5 best RAG evaluation tools in 2025 - Braintrust](https://www.braintrust.dev/articles/best-rag-evaluation-tools)
- [LLM-as-a-judge: Complete Guide - Evidently AI](https://www.evidentlyai.com/llm-guide/llm-as-a-judge)
- [Evaluation Metrics for Search and Recommendation - Weaviate](https://weaviate.io/blog/retrieval-evaluation-metrics)
- [5 Methods to Detect Drift in ML Embeddings - Evidently AI](https://www.evidentlyai.com/blog/embedding-drift-detection)
- [LLM-As-Judge: 7 Best Practices - Monte Carlo](https://www.montecarlodata.com/blog-llm-as-judge/)
- [RAG Observability and Evals - Langfuse](https://langfuse.com/blog/2025-10-28-rag-observability-and-evals)
- [Vector Search: Navigating Recall and Performance - OpenSource Connections](https://opensourceconnections.com/blog/2025/02/27/vector-search-navigating-recall-and-performance/)
- [From LLMs to Knowledge Graphs: Building Production-Ready Graph Systems in 2025](https://medium.com/@claudiubranzan/from-llms-to-knowledge-graphs-building-production-ready-graph-systems-in-2025-2b4aff1ec99a)
- [Precision and Recall at K - Evidently AI](https://www.evidentlyai.com/ranking-metrics/precision-recall-at-k)
