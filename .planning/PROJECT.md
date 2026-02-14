# Alexandria

## What This Is

Alexandria est un systeme RAG self-hosted qui crawle, indexe et expose de la documentation technique a Claude Code via un serveur MCP. Un developpeur donne des URLs de documentation, Alexandria les ingere et les rend cherchables directement depuis Claude Code — recherche semantique, exemples de code, gestion du pipeline. Le tout tourne dans un docker-compose unique (app Java, Crawl4AI sidecar Python, PostgreSQL+pgvector).

## Core Value

Claude Code peut trouver et retourner des extraits de documentation technique pertinents et precis pour n'importe quel framework ou librairie indexe, a la demande.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Ingestion de documentation depuis des URLs arbitraires
- [ ] Crawling web via Crawl4AI (sidecar Python/Docker)
- [ ] Chunking markdown-aware (respecte blocs de code, tableaux, hierarchie de titres)
- [ ] Embeddings in-process via ONNX (bge-small-en-v1.5-q)
- [ ] Stockage vectoriel pgvector avec recherche HNSW
- [ ] Recherche hybride (vecteur + PostgreSQL FTS via RRF)
- [ ] Metadonnees riches (framework, version, section path, content type)
- [ ] Serveur MCP (stdio) exposant la recherche a Claude Code
- [ ] Outils MCP de gestion : ajouter une source, declencher un crawl, voir le statut
- [ ] Mise a jour incrementale des sources deja indexees
- [ ] Docker-compose tout-en-un (app Java + Crawl4AI + PostgreSQL)

### Out of Scope

- Support multi-langue — documentation anglaise uniquement, simplifie le modele d'embedding
- Interface web — Claude Code est l'interface unique
- GPU / API payantes — tout tourne sur CPU, zero cout externe
- Techniques RAG avancees (GraphRAG, Self-RAG, RAPTOR) — la recherche hybride basique + bons chunks couvre 80-90% des cas
- Re-ranking cross-encoder — complexite supplementaire non justifiee pour v1

## Context

- Machine cible : 4 cores, 24 Go RAM, CPU-only, pas de GPU
- Les rapports de recherche dans `docs/` documentent les decisions de stack
- L'ecosysteme Java RAG a atteint la maturite production en mai 2025 (LangChain4j 1.0, Spring AI 1.0 GA)
- Crawl4AI est un outil Python — il tourne en sidecar Docker avec une API HTTP que Java appelle
- Le chunking et les metadonnees ont plus d'impact sur la qualite RAG que le choix du modele d'embedding pour de la documentation technique structuree
- Budget memoire estime : ~10-14 Go pour l'ensemble de la stack

## Constraints

- **Stack**: Java 21 + Spring Boot 3.5 + LangChain4j 1.0+ — choix documente dans `docs/stack.md`
- **Infrastructure**: Docker-compose uniquement, zero service cloud externe
- **Embedding**: ONNX in-process (bge-small-en-v1.5-q, 384d) — zero dependance externe pour les embeddings
- **RAM**: 24 Go total, stack complete doit tenir dans ~14 Go max
- **Crawling**: Crawl4AI (Python sidecar) au lieu de JSoup — gere le JavaScript rendered content
- **Transport MCP**: stdio pour integration locale avec Claude Code

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| LangChain4j plutot que Spring AI pour le coeur RAG | Embeddings in-process ONNX, RRF natif, plus de features RAG | — Pending |
| pgvector plutot qu'une base vectorielle dediee | Zero infra supplementaire, SQL natif, iterative index scans depuis 0.8.x | — Pending |
| Crawl4AI (Python sidecar) plutot que JSoup | Gere le JavaScript rendered content, plus robuste pour le crawling web moderne | — Pending |
| bge-small-en-v1.5-q comme modele d'embedding initial | Prepackage Maven LangChain4j, 384d, ~65 Mo, bon rapport qualite/taille sur CPU | — Pending |
| halfvec(384) pour le stockage vectoriel | 57% d'economie de stockage vs float, suffisant pour la precision de recherche | — Pending |

---
*Last updated: 2026-02-14 after initialization*
