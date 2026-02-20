# Alexandria

## What This Is

Alexandria est un systeme RAG self-hosted qui crawle, indexe et expose de la documentation technique a Claude Code via un serveur MCP. Un developpeur donne des URLs de documentation, Alexandria les ingere et les rend cherchables directement depuis Claude Code — recherche semantique hybride avec reranking cross-encoder, exemples de code, gestion complete du cycle de vie des sources. Le tout tourne dans un docker-compose unique (app Java, Crawl4AI sidecar Python, PostgreSQL+pgvector).

## Core Value

Claude Code peut trouver et retourner des extraits de documentation technique pertinents et precis pour n'importe quel framework ou librairie indexe, a la demande.

## Requirements

### Validated

- ✓ Ingestion de documentation depuis des URLs arbitraires — v0.1
- ✓ Crawling web via Crawl4AI (sidecar Python/Docker) — v0.1
- ✓ Chunking markdown-aware (respecte blocs de code, tableaux, hierarchie de titres) — v0.1
- ✓ Embeddings in-process via ONNX (bge-small-en-v1.5-q) — v0.1
- ✓ Stockage vectoriel pgvector avec recherche HNSW — v0.1
- ✓ Recherche hybride (vecteur + PostgreSQL FTS via RRF) — v0.1
- ✓ Metadonnees riches (framework, version, section path, content type) — v0.1
- ✓ Serveur MCP (stdio) exposant la recherche a Claude Code — v0.1
- ✓ Outils MCP de gestion : ajouter une source, declencher un crawl, voir le statut — v0.1
- ✓ Mise a jour incrementale des sources deja indexees — v0.1
- ✓ Docker-compose tout-en-un (app Java + Crawl4AI + PostgreSQL) — v0.1
- ✓ Cross-encoder reranking pour precision amelioree — v0.1
- ✓ Filtrage par source, version, section, type de contenu — v0.1
- ✓ Cascade delete des sources et chunks associes — v0.1
- ✓ Statistiques d'index via MCP tool — v0.1

### Active

(Defined in REQUIREMENTS.md for v0.2)

### Out of Scope

- Support multi-langue — documentation anglaise uniquement, simplifie le modele d'embedding
- Interface web — Claude Code est l'interface unique
- GPU / API payantes — tout tourne sur CPU, zero cout externe
- Techniques RAG avancees (GraphRAG, Self-RAG, RAPTOR) — la recherche hybride + reranking couvre 90%+ des cas
- Scheduling automatique des recrawls — le recrawl manuel via MCP suffit pour v0.x
- Migration modele d'embedding (nomic, bge-m3) — benchmarker d'abord, migrer dans un futur milestone
- Fine-tuning embeddings — investissement disproportionne pour v0.2
- Contextual Retrieval avec LLM local — trop lourd pour CPU contraint
- ColBERT reranking (bge-m3) — stockage prohibitif, hors scope

## Current Milestone: v0.2 Audit & Optimisation

**Goal:** Auditer et optimiser la qualite RAG, la robustesse du code, la performance et l'observabilite du systeme, en s'appuyant sur les 4 axes d'audit documentes dans `docs/audit/`.

**Target features:**
- Parent-child retrieval (reunir code+prose)
- Convex Combination remplacant RRF pour la fusion hybride
- Prefixe query BGE pour +1-5% retrieval
- Candidats reranking configurables (20/30/50)
- Golden set 100 requetes + metriques IR + ablation study
- Pack qualite complet (Error Prone, NullAway, Spotless, Trivy, OWASP DC, CycloneDX, jqwik)
- Tests MCP snapshot + round-trip integration
- Tuning ONNX Runtime + PostgreSQL + HikariCP
- Stack monitoring (Micrometer + VictoriaMetrics + Grafana + postgres_exporter)

## Context

- Machine cible : 4 cores, 24 Go RAM, CPU-only, pas de GPU
- v0.1 shipped : 9,839 LOC Java, 10 phases, 28 plans, 171 commits en 7 jours
- Stack : Java 21 + Spring Boot 3.5 + LangChain4j 1.0 + Crawl4AI sidecar
- 7 outils MCP : search_docs, list_sources, add_source, remove_source, crawl_status, recrawl_source, index_statistics
- Les rapports de recherche dans `docs/` documentent les decisions de stack
- Budget memoire estime : ~10-14 Go pour l'ensemble de la stack
- Tech debt : 9 items Low/Info identifies dans l'audit v0.1
- Audits detailles dans `docs/audit/` : 4 axes (chunking/embeddings, qualite/securite, performance, retrieval)

## Constraints

- **Stack**: Java 21 + Spring Boot 3.5 + LangChain4j 1.0+ — choix documente dans `docs/stack.md`
- **Infrastructure**: Docker-compose uniquement, zero service cloud externe
- **Embedding**: ONNX in-process (bge-small-en-v1.5-q, 384d) — zero dependance externe pour les embeddings
- **Reranking**: Cross-encoder ONNX in-process — zero dependance externe
- **RAM**: 24 Go total, stack complete doit tenir dans ~14 Go max
- **Crawling**: Crawl4AI (Python sidecar) — gere le JavaScript rendered content
- **Transport MCP**: stdio pour integration locale avec Claude Code

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| LangChain4j plutot que Spring AI pour le coeur RAG | Embeddings in-process ONNX, RRF natif, plus de features RAG | ✓ Good — hybrid search, ONNX embeddings, pgvector store fonctionnent parfaitement |
| pgvector plutot qu'une base vectorielle dediee | Zero infra supplementaire, SQL natif, iterative index scans depuis 0.8.x | ✓ Good — HNSW + GIN indexes performants, halfvec(384) economise 57% stockage |
| Crawl4AI (Python sidecar) plutot que JSoup | Gere le JavaScript rendered content, plus robuste pour le crawling web moderne | ✓ Good — sitemap, llms.txt, JS rendering, boilerplate removal |
| bge-small-en-v1.5-q comme modele d'embedding initial | Prepackage Maven LangChain4j, 384d, ~65 Mo, bon rapport qualite/taille sur CPU | ✓ Good — precision suffisante pour documentation technique |
| halfvec(384) pour le stockage vectoriel | 57% d'economie de stockage vs float, suffisant pour la precision de recherche | ✓ Good — aucune degradation de qualite de recherche observee |
| CommonMark AST pour le chunking | Chunking structurel fiable vs regex, preserve code blocks et tables | ✓ Good — headings/code/tables jamais coupes |
| Cross-encoder reranking ONNX in-process | Precision amelioree sans API externe | ✓ Good — reranking ameliore la precision sur les requetes complexes |
| Architecture hybride pragmatique (feature packages) | Simplicite vs clean/hexagonal, Spring idioms | ✓ Good — ArchUnit enforce les regles, pas de sur-ingenierie |
| Flyway pour les migrations DB | Schema versionne, reproductible, automatique | ✓ Good — 8 migrations, zero intervention manuelle |
| Spring AI MCP @Tool pour le serveur MCP | Annotations declaratives, stdio transport natif | ✓ Good — 7 outils exposes proprement |

| Renumerotation v1.5 → v0.1, v0.2 milestone | Le projet est pre-v1.0 : la v1.0 sera le premier release stable complet | — Pending |
| Convex Combination remplace RRF | CC surpasse systematiquement RRF (Bruch et al. 2022), meilleure utilisation des scores | — Pending |
| Parent-child retrieval | Reunir code+prose dans des chunks parents, resout le probleme #1 identifie par l'audit | — Pending |

---
*Last updated: 2026-02-20 after v0.2 milestone start*
