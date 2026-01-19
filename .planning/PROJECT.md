# Documentation RAG

## What This Is

Un système de Retrieval-Augmented Generation (RAG) personnel utilisant PostgreSQL avec pgvector et Apache AGE pour indexer et rechercher de la documentation technique. Construit avec Java 21 et LangChain4j, exposé via un serveur MCP pour intégration avec Claude Code.

## Core Value

Claude Code peut accéder à ma documentation technique personnelle pendant l'implémentation pour respecter mes conventions et bonnes pratiques.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Indexer des fichiers markdown (documentation, conventions, bonnes pratiques)
- [ ] Pipeline RAG avec LangChain4j 1.0+
- [ ] Embeddings locaux ONNX (all-MiniLM-L6-v2, 384 dimensions)
- [ ] Stockage vectoriel PostgreSQL 17 + pgvector 0.8.1 (index HNSW)
- [ ] Graph de documents avec Apache AGE 1.6.0
- [ ] Chunking hiérarchique (parent 1000 tokens, child 200 tokens)
- [ ] Exposer la recherche via serveur MCP (Java SDK)
- [ ] Recherche sémantique sur la documentation indexée
- [ ] Commande CLI pour indexer/réindexer manuellement
- [ ] Skill Claude Code pour déclencher l'indexation
- [ ] Retourner les documents pertinents avec leur contexte

### Out of Scope

- Indexation automatique (watch mode) — complexité inutile, commande manuelle suffit
- Interface web — usage via Claude Code uniquement
- Multi-utilisateur — usage personnel uniquement
- Knowledge graph complet (Microsoft GraphRAG) — overkill pour quelques milliers de docs
- Extraction d'entités NLP — complexité excessive pour v1

## Context

**Cas d'usage principal :**
Pendant une implémentation basée sur des specs, Claude Code lance une recherche sur la documentation de la technologie utilisée. Il récupère la documentation pertinente avec conventions et bonnes pratiques pour implémenter correctement.

**Contenu à indexer :**
- Documentation de frameworks/librairies
- Conventions de code personnelles
- Bonnes pratiques maintenues manuellement
- Format : fichiers markdown

**Intégration :**
- Serveur MCP Java connecté à Claude Code
- Claude Code utilise les tools MCP pour chercher dans la base

**Architecture cible :**
```
┌─────────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER (Java 21)                   │
├─────────────────────────────────────────────────────────────────┤
│  LangChain4j 1.0+                                               │
│  ├── EmbeddingStoreContentRetriever (vector search)            │
│  ├── Document ingestion pipeline (hierarchical chunking)        │
│  └── MCP Server (Java SDK)                                      │
├─────────────────────────────────────────────────────────────────┤
│                    EMBEDDING LAYER                               │
├─────────────────────────────────────────────────────────────────┤
│  AllMiniLmL6V2EmbeddingModel (in-process ONNX, 384 dim)        │
├─────────────────────────────────────────────────────────────────┤
│                    DATABASE LAYER                                │
├─────────────────────────────────────────────────────────────────┤
│  PostgreSQL 17                                                  │
│  ├── pgvector 0.8.1 (HNSW index, vector_cosine_ops)           │
│  ├── Apache AGE 1.6.0 (document relationships)                 │
│  └── Standard tables (documents, metadata, chunks)              │
└─────────────────────────────────────────────────────────────────┘
```

## Constraints

- **Stack**: Java 21 + LangChain4j 1.0+ — framework RAG mature
- **Database**: PostgreSQL 17 + pgvector 0.8.1 + Apache AGE 1.6.0
- **Embeddings**: all-MiniLM-L6-v2 via ONNX (in-process, ~100MB RAM)
- **Interface**: MCP Java SDK — intégration Claude Code native
- **Usage**: Personnel — pas besoin de gestion multi-utilisateur

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| LangChain4j over Spring AI | Meilleure intégration graph, plus léger | — Pending |
| PostgreSQL unified (pgvector + AGE) | Un seul service, pas de Neo4j séparé | — Pending |
| HNSW over IVFFlat | Meilleur speed-recall, pas de reindex | — Pending |
| all-MiniLM-L6-v2 local | Zero config, ~100MB RAM, 200-400 emb/sec | — Pending |
| Hierarchical chunking | Child pour matching, parent pour contexte | — Pending |
| MCP Java SDK | Serveur MCP natif en Java | — Pending |
| Pas de knowledge graph complet | Overkill pour quelques milliers de docs | — Pending |

---
*Last updated: 2026-01-19 after stack research*
