# Requirements: Alexandria v0.4 RAG Evaluation Toolkit

**Defined:** 2026-01-24
**Core Value:** Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.

## v0.4 Requirements

Requirements for RAG evaluation toolkit. Each maps to roadmap phases.

### Retrieval Evaluation

- [ ] **RETR-01**: Precision@k calcule pour k=5 et k=10 sur golden dataset
- [ ] **RETR-02**: Recall@k calcule pour k=10 et k=20 sur golden dataset
- [ ] **RETR-03**: Mean Reciprocal Rank (MRR) calcule sur golden dataset
- [ ] **RETR-04**: NDCG@k calcule pour evaluation ranking qualite
- [ ] **RETR-05**: Format golden dataset JSON avec champs requires_kg, reasoning_hops, question type
- [ ] **RETR-06**: Evaluation segmentee par type de question (factual, multi-hop, graph_traversal)
- [ ] **RETR-07**: Rapport d'evaluation avec breakdown par categorie

### Embeddings Evaluation

- [ ] **EMB-01**: Silhouette score calcule via SMILE avec distance cosinus
- [ ] **EMB-02**: Baseline embedding quality snapshot sauvegarde
- [ ] **EMB-03**: Drift detection comparant embeddings actuels vs baseline
- [ ] **EMB-04**: Visualisation UMAP 2D des embeddings exportee en image

### pgvector HNSW Benchmark

- [ ] **VEC-01**: Recall@k mesure pour ef_search = 40, 100, 200
- [ ] **VEC-02**: Latence p50/p95/p99 mesuree avec pg_stat_statements
- [ ] **VEC-03**: Cache warming automatique avant benchmark (pg_prewarm)
- [ ] **VEC-04**: Courbe recall/latence documentee et exportable
- [ ] **VEC-05**: Comparaison exact search vs HNSW approximatif

### Graph Validation

- [ ] **GRAPH-01**: Requete Cypher detectant les noeuds orphelins
- [ ] **GRAPH-02**: Requete Cypher detectant les doublons
- [ ] **GRAPH-03**: Statistiques basiques calculees (nodes, edges, density)
- [ ] **GRAPH-04**: Analyse WCC (composantes connexes) via JGraphT ou NetworkX
- [ ] **GRAPH-05**: Export GraphML pour visualisation Gephi

### LLM-as-Judge

- [ ] **LLM-01**: Ollama configure avec modele 7B+ (Llama 3.1 8B recommande)
- [ ] **LLM-02**: Faithfulness score calcule (claims supportes / total claims)
- [ ] **LLM-03**: Answer relevancy score calcule
- [ ] **LLM-04**: Support multi-modele pour eviter self-enhancement bias
- [ ] **LLM-05**: Batch evaluation avec rapport de synthese

### Monitoring Stack

- [ ] **MON-01**: VictoriaMetrics deploye pour stockage metriques
- [ ] **MON-02**: Grafana avec dashboard RAG preconfigures
- [ ] **MON-03**: Metriques Micrometer exposees par l'application
- [ ] **MON-04**: Loki + Promtail pour logging centralise
- [ ] **MON-05**: Alertes configurees sur latence et taux d'erreur

### Infrastructure

- [ ] **INFRA-01**: Docker Compose avec profile `eval` pour stack optionnelle
- [ ] **INFRA-02**: Scripts de lancement evaluation (./eval, ./benchmark)
- [ ] **INFRA-03**: Documentation usage du toolkit dans README
- [ ] **INFRA-04**: Health checks avec condition service_healthy
- [ ] **INFRA-05**: Python sidecar FastAPI pour outils avances (NetworkX, UMAP)

## v0.5+ Requirements

Deferred to future release. Tracked but not in current roadmap.

### Advanced Evaluation

- **ADV-01**: Real-time evaluation pendant les requetes MCP
- **ADV-02**: A/B testing entre configurations de search
- **ADV-03**: Regression detection automatique sur CI

### Golden Dataset

- **DATA-01**: Generation automatique de questions via LLM
- **DATA-02**: Validation automatique des reponses generees
- **DATA-03**: Interface web pour annotation manuelle

## Out of Scope

| Feature | Reason |
|---------|--------|
| Modeles LLM < 7B pour evaluation | Jugements instables, JSON parsing failures |
| Silhouette score temps reel | Complexite O(N^2), batch seulement |
| Same model pour generation et evaluation | Self-enhancement bias |
| Prometheus au lieu de VictoriaMetrics | Consommation RAM 4x superieure |
| Thresholds de couverture bloquants | Rapport-only, pas de gates |
| Evaluation sur chaque PR | Trop lent, manuel ou scheduled |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| MON-03 | Phase 15 | Complete |
| MON-01 | Phase 16 | Complete |
| MON-02 | Phase 16 | Complete |
| MON-04 | Phase 16 | Complete |
| MON-05 | Phase 16 | Complete |
| INFRA-01 | Phase 16 | Complete |
| INFRA-04 | Phase 16 | Complete |
| RETR-01 | Phase 17 | Pending |
| RETR-02 | Phase 17 | Pending |
| RETR-03 | Phase 17 | Pending |
| RETR-04 | Phase 17 | Pending |
| RETR-05 | Phase 17 | Pending |
| RETR-06 | Phase 17 | Pending |
| RETR-07 | Phase 17 | Pending |
| EMB-01 | Phase 18 | Pending |
| EMB-02 | Phase 18 | Pending |
| EMB-03 | Phase 18 | Pending |
| EMB-04 | Phase 18 | Pending |
| VEC-01 | Phase 18 | Pending |
| VEC-02 | Phase 18 | Pending |
| VEC-03 | Phase 18 | Pending |
| VEC-04 | Phase 18 | Pending |
| VEC-05 | Phase 18 | Pending |
| GRAPH-01 | Phase 19 | Pending |
| GRAPH-02 | Phase 19 | Pending |
| GRAPH-03 | Phase 19 | Pending |
| GRAPH-04 | Phase 19 | Pending |
| GRAPH-05 | Phase 19 | Pending |
| INFRA-05 | Phase 19 | Pending |
| LLM-01 | Phase 20 | Pending |
| LLM-02 | Phase 20 | Pending |
| LLM-03 | Phase 20 | Pending |
| LLM-04 | Phase 20 | Pending |
| LLM-05 | Phase 20 | Pending |
| INFRA-02 | Phase 20 | Pending |
| INFRA-03 | Phase 20 | Pending |

**Coverage:**
- v0.4 requirements: 32 total
- Mapped to phases: 32
- Unmapped: 0

---
*Requirements defined: 2026-01-24*
*Last updated: 2026-01-24 after roadmap creation*
