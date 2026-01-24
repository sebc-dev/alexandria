# Documentation RAG (Alexandria)

## What This Is

A personal RAG (Retrieval-Augmented Generation) system using PostgreSQL with pgvector and Apache AGE to index and search technical documentation. Built with Java 21 and LangChain4j 1.0-beta3, exposed via MCP server for Claude Code integration and CLI for maintenance.

## Core Value

Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.

## Current State

**Latest shipped:** v0.3 Better DX and Quality Gate (2026-01-23)

**Delivered:** Test quality visibility through JaCoCo code coverage, PIT mutation testing, CI integration with coverage badges, and convenience scripts for quality analysis.

## Current Milestone: v0.4 RAG Evaluation Toolkit

**Goal:** Boîte à outils optionnelle d'évaluation complète du système RAG — retrieval, embeddings, pgvector, graphe, LLM-as-judge, et monitoring temps réel.

**Target features:**
- Métriques retrieval (Precision@k, Recall@k, MRR, NDCG) avec golden dataset
- Évaluation embeddings (silhouette score SMILE, drift detection, UMAP)
- Benchmark pgvector HNSW (recall vs ef_search, latence p50/p95/p99)
- Validation graphe Apache AGE (orphelins, connectivité NetworkX, visualisation)
- LLM-as-Judge via Ollama (faithfulness, answer relevancy)
- Stack monitoring (VictoriaMetrics + Grafana + Loki)
- Infrastructure Docker pour tout le tooling

## Requirements

### Validated

- ✓ Indexer des fichiers markdown (documentation, conventions, bonnes pratiques) — v0.1
- ✓ Pipeline RAG avec LangChain4j 1.0+ — v0.1
- ✓ Embeddings locaux ONNX (all-MiniLM-L6-v2, 384 dimensions) — v0.1
- ✓ Stockage vectoriel PostgreSQL 17 + pgvector 0.8.1 (index HNSW) — v0.1
- ✓ Graph de documents avec Apache AGE 1.6.0 — v0.1
- ✓ Chunking hierarchique (parent 1000 tokens, child 200 tokens) — v0.1
- ✓ Exposer la recherche via serveur MCP (Java SDK) — v0.1
- ✓ Recherche semantique sur la documentation indexee — v0.1
- ✓ Commande CLI pour indexer/reindexer manuellement — v0.1
- ✓ Retourner les documents pertinents avec leur contexte — v0.1
- ✓ Recherche hybride vector + full-text — v0.1
- ✓ Traversee graph pour documents lies — v0.1
- ✓ Dockerfile multi-stage pour build + runtime — v0.2
- ✓ Service app dans docker-compose.yml — v0.2
- ✓ MCP transport HTTP/SSE configurable (+ STDIO existant) — v0.2
- ✓ Script wrapper CLI (`alexandria` command) — v0.2
- ✓ GitHub Actions pour build et push image — v0.2
- ✓ Configuration externalisee (env vars, .env, volumes) — v0.2
- ✓ JaCoCo coverage reports avec rapport HTML local — v0.3
- ✓ JaCoCo coverage reports dans artifacts CI — v0.3
- ✓ PIT mutation testing en mode incremental local — v0.3
- ✓ Tests d'integration Testcontainers en CI (PR + push master) — v0.3
- ✓ Scripts/commandes pour lancer l'analyse qualite facilement — v0.3
- ✓ Badge de couverture dynamique dans README — v0.3

### Active

<!-- v0.4 RAG Evaluation Toolkit -->

**Retrieval Evaluation:**
- [ ] Métriques retrieval Java (Precision@k, Recall@k, MRR, NDCG)
- [ ] Format golden dataset JSON avec champs KG (requires_kg, reasoning_hops)
- [ ] Évaluation sur commande via Docker

**Embeddings Evaluation:**
- [ ] Silhouette score via SMILE (Java natif)
- [ ] Drift detection (Evidently ou calcul centroid)
- [ ] Visualisation UMAP des embeddings

**pgvector HNSW Benchmark:**
- [ ] Recall@k vs ef_search (40, 100, 200)
- [ ] Latence p50/p95/p99 avec pg_stat_statements
- [ ] Courbe recall/latence documentée

**Graph Validation:**
- [ ] Requêtes Cypher : orphelins, doublons, stats basiques
- [ ] Analyse connectivité WCC via NetworkX (export CSV)
- [ ] Visualisation graphe (AGE Viewer ou export Gephi)

**LLM-as-Judge:**
- [ ] Ollama avec Mistral 7B ou Llama 3.1 8B
- [ ] Métriques faithfulness et answer relevancy
- [ ] Intégration Quarkus LangChain4j Testing ou wrapper RAGAS

**Monitoring Stack:**
- [ ] VictoriaMetrics pour stockage métriques
- [ ] Grafana avec dashboards RAG préconfigurés
- [ ] Loki + Promtail pour logging centralisé
- [ ] Métriques Micrometer exposées par l'application

**Infrastructure:**
- [ ] Docker Compose pour stack évaluation complète
- [ ] Scripts de lancement évaluation
- [ ] Documentation usage du toolkit

### Deferred (v0.4+)

- Scoring pondere configurable (vector vs keyword)
- Reranking des resultats
- Support fichiers RST, AsciiDoc
- Tool `similar_docs` pour documents similaires

### Out of Scope

- Indexation automatique (watch mode) — commande manuelle suffit
- Interface web — usage via Claude Code uniquement
- Multi-utilisateur — usage personnel uniquement
- Knowledge graph complet (Microsoft GraphRAG) — overkill pour quelques milliers de docs
- Extraction d'entites NLP — complexite excessive
- API REST — MCP suffit pour l'integration Claude Code
- Embeddings via API externe — doit fonctionner offline

## Context

**Current State (v0.3 shipped):**
- 65 Java files, 9,163 lines of code
- Tech stack: Java 21, Spring Boot 3.4.7, LangChain4j 1.2.0, PostgreSQL 17, pgvector 0.8.1, Apache AGE 1.6.0
- MCP server: 4 tools (search_docs, index_docs, list_categories, get_doc) with HTTP/SSE + STDIO transports
- CLI: 4 commands (index, search, status, clear) with Spring Shell 3.4.1 + Docker wrapper
- Docker: Multi-stage build, auto-publishing to ghcr.io/sebc-dev/alexandria
- Hexagonal architecture enforced by ArchUnit tests
- Quality tools: JaCoCo (44% LINE), PIT mutation (56% mutation score), CI coverage badges
- Scripts: ./coverage, ./mutation, ./quality for quick quality analysis

**Architecture:**
```
┌─────────────────────────────────────────────────────────────────┐
│                    API LAYER                                     │
├─────────────────────────────────────────────────────────────────┤
│  MCP Server (Spring AI MCP 1.0.0, STDIO transport)              │
│  CLI (Spring Shell 3.4.1, non-interactive mode)                  │
├─────────────────────────────────────────────────────────────────┤
│                    CORE LAYER                                    │
├─────────────────────────────────────────────────────────────────┤
│  Services: IngestionService, SearchService                       │
│  Ports: DocumentRepository, ChunkRepository, GraphRepository,    │
│         SearchRepository, EmbeddingGenerator, MarkdownParserPort │
├─────────────────────────────────────────────────────────────────┤
│                    INFRA LAYER                                   │
├─────────────────────────────────────────────────────────────────┤
│  JDBC Repositories (pgvector, AGE via cypher())                 │
│  LangChain4j (AllMiniLmL6V2EmbeddingModel, ONNX in-process)     │
│  CommonMark (markdown parsing, YAML frontmatter)                 │
└─────────────────────────────────────────────────────────────────┘
```

## Constraints

- **Stack**: Java 21 + LangChain4j 1.0+ — framework RAG mature
- **Database**: PostgreSQL 17 + pgvector 0.8.1 + Apache AGE 1.6.0
- **Embeddings**: all-MiniLM-L6-v2 via ONNX (in-process, ~100MB RAM)
- **Interface**: MCP Java SDK — integration Claude Code native
- **Usage**: Personnel — pas besoin de gestion multi-utilisateur

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| LangChain4j over Spring AI | Meilleure integration graph, plus leger | ✓ Good |
| PostgreSQL unified (pgvector + AGE) | Un seul service, pas de Neo4j separe | ✓ Good |
| HNSW over IVFFlat | Meilleur speed-recall, pas de reindex | ✓ Good |
| all-MiniLM-L6-v2 local | Zero config, ~100MB RAM, 200-400 emb/sec | ✓ Good |
| Hierarchical chunking | Child pour matching, parent pour contexte | ✓ Good |
| Spring AI MCP SDK | STDIO transport, wiring simple | ✓ Good |
| Character-based token approximation | ~4 chars/token for LangChain4j splitters | ✓ Good |
| RRF k=60 default | Balanced rank sensitivity for hybrid search | ✓ Good |
| websearch_to_tsquery | User-friendly full-text query syntax | ✓ Good |
| Spring Shell 3.4.1 | @Command annotation style, non-interactive mode | ✓ Good |
| Hexagonal architecture | api -> core <- infra, enforced by ArchUnit | ✓ Good |
| Profile-based MCP transport | STDIO + HTTP/SSE via Spring profiles | ✓ Good |
| 120s health start_period | ONNX model loading on first run | ✓ Good |
| wget over curl | JRE image has no curl, wget is lighter | ✓ Good |
| POSIX shell CLI wrapper | Maximum portability across systems | ✓ Good |
| Official Docker GH Actions | Reliable GHCR publishing with gha cache | ✓ Good |
| JaCoCo CSV + awk parsing | Portable parsing without xmllint dependency | ✓ Good |
| PITest in Maven profile | Opt-in expensive analysis, not blocking builds | ✓ Good |
| Incremental PITest history | 5s incremental runs vs minutes for full analysis | ✓ Good |
| Coverage badges on main only | Avoid PR merge conflicts from badge commits | ✓ Good |
| Quality script --full flag | Fast default (coverage only), opt-in mutation | ✓ Good |

---
*Last updated: 2026-01-24 after starting v0.4 milestone*
