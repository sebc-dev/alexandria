# Requirements: Alexandria v0.4 RAG Evaluation Toolkit

**Defined:** 2026-01-24
**Core Value:** Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.

## v0.4 Requirements

Requirements for RAG evaluation toolkit. Each maps to roadmap phases.

### Retrieval Evaluation

- [ ] **RETR-01**: Precision@k calculé pour k=5 et k=10 sur golden dataset
- [ ] **RETR-02**: Recall@k calculé pour k=10 et k=20 sur golden dataset
- [ ] **RETR-03**: Mean Reciprocal Rank (MRR) calculé sur golden dataset
- [ ] **RETR-04**: NDCG@k calculé pour évaluation ranking qualité
- [ ] **RETR-05**: Format golden dataset JSON avec champs requires_kg, reasoning_hops, question type
- [ ] **RETR-06**: Évaluation segmentée par type de question (factual, multi-hop, graph_traversal)
- [ ] **RETR-07**: Rapport d'évaluation avec breakdown par catégorie

### Embeddings Evaluation

- [ ] **EMB-01**: Silhouette score calculé via SMILE avec distance cosinus
- [ ] **EMB-02**: Baseline embedding quality snapshot sauvegardé
- [ ] **EMB-03**: Drift detection comparant embeddings actuels vs baseline
- [ ] **EMB-04**: Visualisation UMAP 2D des embeddings exportée en image

### pgvector HNSW Benchmark

- [ ] **VEC-01**: Recall@k mesuré pour ef_search = 40, 100, 200
- [ ] **VEC-02**: Latence p50/p95/p99 mesurée avec pg_stat_statements
- [ ] **VEC-03**: Cache warming automatique avant benchmark (pg_prewarm)
- [ ] **VEC-04**: Courbe recall/latence documentée et exportable
- [ ] **VEC-05**: Comparaison exact search vs HNSW approximatif

### Graph Validation

- [ ] **GRAPH-01**: Requête Cypher détectant les nœuds orphelins
- [ ] **GRAPH-02**: Requête Cypher détectant les doublons
- [ ] **GRAPH-03**: Statistiques basiques calculées (nodes, edges, density)
- [ ] **GRAPH-04**: Analyse WCC (composantes connexes) via JGraphT ou NetworkX
- [ ] **GRAPH-05**: Export GraphML pour visualisation Gephi

### LLM-as-Judge

- [ ] **LLM-01**: Ollama configuré avec modèle 7B+ (Llama 3.1 8B recommandé)
- [ ] **LLM-02**: Faithfulness score calculé (claims supportés / total claims)
- [ ] **LLM-03**: Answer relevancy score calculé
- [ ] **LLM-04**: Support multi-modèle pour éviter self-enhancement bias
- [ ] **LLM-05**: Batch evaluation avec rapport de synthèse

### Monitoring Stack

- [ ] **MON-01**: VictoriaMetrics déployé pour stockage métriques
- [ ] **MON-02**: Grafana avec dashboard RAG préconfigurés
- [ ] **MON-03**: Métriques Micrometer exposées par l'application
- [ ] **MON-04**: Loki + Promtail pour logging centralisé
- [ ] **MON-05**: Alertes configurées sur latence et taux d'erreur

### Infrastructure

- [ ] **INFRA-01**: Docker Compose avec profile `eval` pour stack optionnelle
- [ ] **INFRA-02**: Scripts de lancement évaluation (./eval, ./benchmark)
- [ ] **INFRA-03**: Documentation usage du toolkit dans README
- [ ] **INFRA-04**: Health checks avec condition service_healthy
- [ ] **INFRA-05**: Python sidecar FastAPI pour outils avancés (NetworkX, UMAP)

## v0.5+ Requirements

Deferred to future release. Tracked but not in current roadmap.

### Advanced Evaluation

- **ADV-01**: Real-time evaluation pendant les requêtes MCP
- **ADV-02**: A/B testing entre configurations de search
- **ADV-03**: Regression detection automatique sur CI

### Golden Dataset

- **DATA-01**: Génération automatique de questions via LLM
- **DATA-02**: Validation automatique des réponses générées
- **DATA-03**: Interface web pour annotation manuelle

## Out of Scope

| Feature | Reason |
|---------|--------|
| Modèles LLM < 7B pour évaluation | Jugements instables, JSON parsing failures |
| Silhouette score temps réel | Complexité O(N²), batch seulement |
| Same model pour génération et évaluation | Self-enhancement bias |
| Prometheus au lieu de VictoriaMetrics | Consommation RAM 4x supérieure |
| Thresholds de couverture bloquants | Rapport-only, pas de gates |
| Evaluation sur chaque PR | Trop lent, manuel ou scheduled |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| (To be filled by roadmapper) | | |

**Coverage:**
- v0.4 requirements: 32 total
- Mapped to phases: 0
- Unmapped: 32

---
*Requirements defined: 2026-01-24*
*Last updated: 2026-01-24 after initial definition*
