# Requirements: Alexandria

**Defined:** 2026-02-20
**Core Value:** Claude Code peut trouver et retourner des extraits de documentation technique pertinents et precis pour n'importe quel framework ou librairie indexe, a la demande.

## v0.2 Requirements

Requirements for the Audit & Optimisation milestone. Each maps to roadmap phases.

### Chunking & Retrieval

- [x] **CHUNK-01**: Le systeme produit des parent chunks (section H2/H3 complete code+prose) et des child chunks (paragraphes/blocs individuels) avec lien parent-child en metadata
- [x] **CHUNK-02**: La recherche retourne les parent chunks complets quand un child chunk matche, reunissant code et prose dans le contexte
- [ ] **CHUNK-03**: Le prefixe query BGE est applique sur les requetes de recherche (pas sur les documents a l'indexation)

### Search Fusion

- [ ] **FUSE-01**: La fusion hybride utilise Convex Combination (score normalise) au lieu de RRF
- [ ] **FUSE-02**: Le parametre alpha de Convex Combination est configurable via application properties
- [ ] **FUSE-03**: Le nombre de candidats reranking est configurable (defaut: 30, testable 20/30/50)

### Retrieval Evaluation

- [ ] **EVAL-01**: Une classe RetrievalMetrics implemente Recall@k, Precision@k, MRR, NDCG@k, MAP, Hit Rate
- [ ] **EVAL-02**: Un golden set de 100 requetes annotees couvre les 4 types (factuelle, conceptuelle, code lookup, troubleshooting)
- [ ] **EVAL-03**: Un test JUnit 5 parametrise execute le golden set et asserte des seuils minimaux (Recall@10 >= 0.70, MRR >= 0.60)
- [ ] **EVAL-04**: Une ablation study compare vector-only, FTS-only, hybride CC, hybride CC+reranking
- [ ] **EVAL-05**: Les resultats d'evaluation sont exportes en CSV pour suivi de tendance

### Quality Tooling

- [ ] **QUAL-01**: Error Prone 2.45+ est integre au build Gradle avec les checks ERROR actifs
- [ ] **QUAL-02**: NullAway est integre et les packages annotes avec @NullMarked/@Nullable
- [ ] **QUAL-03**: Spotless + google-java-format enforce le formatage sur le code modifie (ratchetFrom)
- [x] **QUAL-04**: jqwik teste les invariants structurels du MarkdownChunker (conservation contenu, bornes taille, code blocks equilibres, tables completes)

### Security

- [ ] **SECU-01**: Trivy scanne les 3 images Docker et le filesystem Java dans le CI
- [ ] **SECU-02**: OWASP Dependency-Check est integre au build Gradle avec seuil CVSS 7.0
- [ ] **SECU-03**: CycloneDX genere un SBOM a chaque build

### MCP Testing

- [ ] **MCPT-01**: Un snapshot test verifie le schema de tools/list contre un fichier de reference versionne
- [ ] **MCPT-02**: Des tests d'integration round-trip via McpAsyncClient couvrent les 7 outils MCP (happy path + cas d'erreur)

### Performance Tuning

- [ ] **PERF-01**: Le thread spinning ONNX est desactive (allow_spinning=0) et les pools de threads ONNX sont configures globalement
- [ ] **PERF-02**: PostgreSQL est tune pour le workload RAG (shared_buffers, ef_search=100, JIT off, maintenance_work_mem)
- [ ] **PERF-03**: HikariCP est configure pour les virtual threads (pool 10-15, connection-timeout 5-10s)

### Monitoring

- [ ] **MONI-01**: Micrometer instrumente chaque etape du pipeline search avec timers et percentiles (p50/p95/p99)
- [ ] **MONI-02**: VictoriaMetrics + Grafana + postgres_exporter sont deployes dans docker-compose
- [ ] **MONI-03**: Un dashboard Grafana affiche les metriques RAG (latence par etape, empty rate, top score distribution, pool HikariCP)
- [ ] **MONI-04**: Des alertes sont configurees sur les seuils critiques (p95 > 2s, cache hit < 90%, error rate > 5%)

## Future Requirements

### Embedding Model Migration

- **EMBD-01**: Benchmarker nomic-embed-text-v1.5 vs bge-small sur le golden set
- **EMBD-02**: Migrer vers nomic-embed-text-v1.5 si benchmarks positifs (reindexation complete)
- **EMBD-03**: Exploiter Matryoshka a 256d pour economie memoire

### Advanced Reranking

- **RANK-01**: Upgrader vers ms-marco-MiniLM-L-12-v2 ou bge-reranker-base
- **RANK-02**: Fine-tuner le reranker sur le domaine documentation technique

### Advanced Testing

- **TEST-01**: Property-based testing etendu avec generateurs Markdown complexes
- **TEST-02**: WireMock pour Crawl4AI (resilience client)
- **TEST-03**: Tests delta crawl (idempotence, hash deterministe)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Migration modele d'embedding | Benchmarker d'abord dans un futur milestone, reindexation lourde |
| Fine-tuning embeddings | Investissement disproportionne pour v0.2, gain incertain |
| Contextual Retrieval avec LLM local | TinyLlama 5-15s/chunk sur CPU, impraticable |
| ColBERT reranking (bge-m3) | Stockage ~128KB/passage, prohibitif a 500K chunks |
| Checker Framework | Overhead compilation 2.8-5.1x, NullAway couvre 80% de la valeur |
| Qodana | Redondant avec SpotBugs + SonarCloud |
| Pact/Spring Cloud Contract pour MCP | JSON-RPC sur SSE, pas REST — effort disproportionne |
| Semantic chunking | Contre-productif pour le code, le parent-child est superieur |
| HyDE | 5-15s latence par requete sur CPU, impraticable en interactif |
| Gradle Dependency Verification | Maintenance lourde, a activer apres stabilisation des deps |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| QUAL-01 | Phase 11 | Pending |
| QUAL-02 | Phase 11 | Pending |
| QUAL-03 | Phase 11 | Pending |
| SECU-01 | Phase 11 | Pending |
| SECU-02 | Phase 11 | Pending |
| SECU-03 | Phase 11 | Pending |
| PERF-01 | Phase 12 | Pending |
| PERF-02 | Phase 12 | Pending |
| PERF-03 | Phase 12 | Pending |
| CHUNK-03 | Phase 12 | Pending |
| EVAL-01 | Phase 13 | Pending |
| EVAL-02 | Phase 13 | Pending |
| EVAL-03 | Phase 13 | Pending |
| EVAL-05 | Phase 13 | Pending |
| CHUNK-01 | Phase 14 | Complete |
| CHUNK-02 | Phase 14 | Pending |
| QUAL-04 | Phase 14 | Complete |
| FUSE-01 | Phase 15 | Pending |
| FUSE-02 | Phase 15 | Pending |
| FUSE-03 | Phase 15 | Pending |
| MCPT-01 | Phase 16 | Pending |
| MCPT-02 | Phase 16 | Pending |
| MONI-01 | Phase 17 | Pending |
| MONI-02 | Phase 17 | Pending |
| MONI-03 | Phase 17 | Pending |
| MONI-04 | Phase 17 | Pending |
| EVAL-04 | Phase 18 | Pending |

**Coverage:**
- v0.2 requirements: 27 total
- Mapped to phases: 27
- Unmapped: 0

---
*Requirements defined: 2026-02-20*
*Last updated: 2026-02-20 after roadmap creation*
