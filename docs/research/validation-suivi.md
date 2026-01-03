# Alexandria - Suivi de Validation des Recherches

**Date de création:** 2026-01-03
**Objectif:** Valider chaque recommandation du tech-spec en fonction des résultats de recherche

---

## Synthese Globale

| Composant | Statut | Action Requise |
|-----------|--------|----------------|
| Runtime (Bun) | :warning: PROBLEME | Migrer vers Node.js |
| PostgreSQL 18 + pgvector | :white_check_mark: VALIDE | Aucune |
| halfvec | :white_check_mark: VALIDE | Aucune |
| Drizzle ORM | :white_check_mark: VALIDE | Aucune |
| Qwen3-Embedding-0.6B | :warning: PROBLEME CRITIQUE | Changer serveur d'inference |
| bge-reranker-v2-m3 | :white_check_mark: VALIDE | Aucune |
| Infinity | :x: INCOMPATIBLE | Utiliser TEI ou vLLM |
| Architecture Hexagonale | :grey_question: NON RECHERCHE | Lancer recherche |
| SDK MCP | :grey_question: NON RECHERCHE | Lancer recherche |
| Skills Claude Code | :grey_question: NON RECHERCHE | Lancer recherche |

---

## 1. Runtime & Stack

### 1.1 Bun Runtime

**Fichier de resultat:** `Bun runtime pour serveur MCP.md`

**Statut:** :warning: A MODIFIER

**Recommandation du tech-spec:**
- Bun 1.3.5+ pour performance et TypeScript natif

**Constats de la recherche:**

| Point | Resultat | Impact |
|-------|----------|--------|
| Version stable | Bun v1.3.5 (17 dec 2025) | OK |
| Compatibilite postgres.js | :white_check_mark: Support officiel | OK |
| Compatibilite Drizzle | :warning: Bug concurrence bun-sql | Utiliser postgres.js |
| Compatibilite @modelcontextprotocol/sdk | :warning: Non officiel (node >= 20) | Risque |
| **Bug stdin >= 16KB** | :x: **BLOQUANT** (Issue #9041) | Messages MCP RAG > 16KB |
| Memory leaks serveurs long-running | :x: Issues documentees | Production risquee |
| SSE | :x: Regressions critiques | Si fallback HTTP prevu |

**Problemes critiques:**
1. **Bug stdin bloquant >= 16KB** - Les messages JSON MCP avec embeddings/contexte RAG depasseront facilement cette limite
2. **Memory leaks documentes** - Serveur long-running (jours/semaines) = non recommande
3. SDK MCP non supporte officiellement par Bun

**Decision a prendre:**

- [ ] **Option A (Recommandee)**: Migrer vers Node.js 22+ pour production
- [ ] **Option B**: Garder Bun pour dev, Node.js pour prod
- [ ] **Option C**: Accepter les risques Bun (monitoring + restart automatique)

**Recherche supplementaire necessaire:** Non

---

### 1.2 Drizzle ORM + postgres.js

**Fichier de resultat:** `Drizzle ORM et pgvector integration for Alexandria RAG server.md`

**Statut:** :white_check_mark: VALIDE

**Recommandation du tech-spec:**
- Drizzle ORM + postgres.js pour typage fort et requetes performantes

**Constats de la recherche:**

| Point | Resultat | Impact |
|-------|----------|--------|
| Version drizzle-orm | 0.45.1 (stable, dec 2025) | OK |
| Version postgres.js | 3.4.7 (mai 2025) | OK |
| Support natif pgvector | :white_check_mark: Depuis v0.31.0 (mai 2024) | OK |
| Support halfvec | :white_check_mark: Natif | OK |
| Support Bun | :white_check_mark: postgres.js natif | OK |
| Distance functions | :white_check_mark: cosineDistance, l2Distance, innerProduct | OK |

**Configuration validee:**
```typescript
import { pgTable, halfvec, index } from 'drizzle-orm/pg-core';
import { cosineDistance } from 'drizzle-orm';

// Schema avec halfvec(1024) - FONCTIONNE
export const chunks = pgTable('chunks', {
  embedding: halfvec('embedding', { dimensions: 1024 }),
}, (table) => [
  index('chunks_embedding_idx')
    .using('hnsw', table.embedding.op('halfvec_cosine_ops')),
]);
```

**Decision:** AUCUNE MODIFICATION REQUISE

**Recherche supplementaire necessaire:** Non

---

## 2. Base de Donnees

### 2.1 PostgreSQL + pgvector Versions

**Fichier de resultat:** `PostgreSQL 18 and pgvector configuration.md`

**Statut:** :white_check_mark: VALIDE

**Recommandation du tech-spec:**
- PostgreSQL 18 + pgvector 0.8.1
- halfvec pour 50% economie memoire

**Constats de la recherche:**

| Point | Resultat | Impact |
|-------|----------|--------|
| PostgreSQL 18 | :white_check_mark: v18.1 GA (25 sept 2025) | OK |
| pgvector | :white_check_mark: v0.8.1 stable | OK |
| halfvec | :white_check_mark: Depuis pgvector 0.7.0 | OK |
| Economie stockage halfvec | 50% (2056 bytes vs 4104 pour 1024D) | Confirme |
| Impact recall halfvec | Negligeable (benchmarks identiques) | OK |
| Impact performance halfvec | +2-16% QPS, -4-14% latence p99 | Bonus |
| HNSW max dimensions halfvec | 4000 (vs 2000 pour vector) | OK pour 1024D |

**Configuration PostgreSQL validee (24GB RAM):**
```ini
shared_buffers = 6GB
effective_cache_size = 18GB
work_mem = 256MB
maintenance_work_mem = 2GB
max_parallel_workers = 4
```

**Decision:** AUCUNE MODIFICATION REQUISE

**Recherche supplementaire necessaire:** Non

---

### 2.2 Indexation HNSW pgvector

**Fichier de resultat:** `Indexation HNSW pgvector pour RAG.md`

**Statut:** :white_check_mark: VALIDE

**Recommandation du tech-spec:**
- Index HNSW pour recherche vectorielle

**Constats de la recherche:**

| Point | Resultat | Impact |
|-------|----------|--------|
| HNSW vs IVFFlat | HNSW superieur a petite echelle | OK |
| Seuil sans index | < 10 000 vecteurs = scan OK | Simplification possible |
| Params m pour 1024D | 24-32 (defaut 16 insuffisant) | A ajuster |
| ef_construction | 100-128 (defaut 64) | A ajuster |
| ef_search runtime | 100-200 (defaut 40) | A ajuster |
| Operateur pour embeddings normalises | `<#>` (inner product) 3-5x plus rapide | Optimisation |
| Index composites | Non supportes | Index separes metadata |
| Iterative scan (0.8.0) | `hnsw.iterative_scan = 'relaxed_order'` | Pour filtres selectifs |

**Configuration HNSW validee:**
```sql
CREATE INDEX ON documents USING hnsw (embedding vector_ip_ops)
WITH (m = 32, ef_construction = 128);
SET hnsw.ef_search = 100;
```

**Decision:** Ajuster les parametres HNSW dans le tech-spec (m=32, ef_construction=128, ef_search=100)

**Recherche supplementaire necessaire:** Non

---

## 3. Embeddings

### 3.1 Qwen3-Embedding-0.6B Validation

**Fichier de resultat:** `Qwen3-Embedding-0.6B - complete technical analysis for RAG deployment.md`

**Statut:** :white_check_mark: MODELE VALIDE mais :x: SERVEUR INCOMPATIBLE

**Recommandation du tech-spec:**
- Qwen3-Embedding-0.6B (1024 dimensions, 32K tokens context)
- Via Infinity sur RunPod

**Constats de la recherche:**

| Point | Resultat | Impact |
|-------|----------|--------|
| Modele existe | :white_check_mark: `Qwen/Qwen3-Embedding-0.6B` (juin 2025) | OK |
| Dimensions | 1024 (natif), 32-1024 via Matryoshka | OK |
| Context length | 32 768 tokens | OK |
| Parametres | ~509M (1.21GB) | OK |
| VRAM FP16 | ~1.5-2GB | OK pour L4 |
| MTEB English | 70.70 (vs BGE-M3: 59.56) | Excellent |
| Prefixes | Instruction-based pour queries | Different de E5 |
| Normalisation | NON normalise par defaut | A normaliser |
| **Compatibilite Infinity** | :x: **NON SUPPORTE** (Issue #642) | **BLOQUANT** |

**PROBLEME CRITIQUE:**
```
KeyError: 'qwen3'
ValueError: The checkpoint you are trying to load has model type `qwen3`
but Transformers does not recognize this architecture.
```

**Alternatives serveur d'inference:**
1. **HuggingFace TEI** (recommande) - Support natif Qwen3
2. **vLLM** - Support natif Qwen3
3. **Infinity + gte-Qwen2** - Fallback si Qwen3 problematique

**Decision a prendre:**

- [ ] **Option A (Recommandee)**: Changer Infinity -> TEI pour embeddings
- [ ] **Option B**: Utiliser `Alibaba-NLP/gte-Qwen2-1.5B-instruct` avec Infinity
- [ ] **Option C**: Build custom Infinity avec transformers >= 4.51.0

**Recherche supplementaire necessaire:** Oui - Configuration TEI pour Qwen3

---

### 3.2 Alternatives Embedding Models

**Fichier de resultat:** `Modeles d'embedding pour RAG technique.md`

**Statut:** :white_check_mark: VALIDE (confirme Qwen3 comme meilleur choix)

**Constats de la recherche:**

| Modele | MTEB Multi | Dims | Context | VRAM | Licence | Matryoshka |
|--------|------------|------|---------|------|---------|------------|
| **Qwen3-0.6B** | 64.33 | 1024 | 32K | 1.2GB | Apache 2.0 | :white_check_mark: |
| BGE-M3 | 59.56 | 1024 | 8K | 2-10GB | MIT | :x: |
| Jina-v3 | 64.44 | 1024 | 8K | 1.5GB | CC BY-NC | :white_check_mark: |
| Nomic-v1.5 | ~62 | 768 | 8K | 0.5GB | Apache 2.0 | :white_check_mark: |

**Decision:** Qwen3-Embedding-0.6B reste le meilleur choix, mais serveur a changer

---

### 3.3 Strategie de Chunking

**Fichier de resultat:** `Strategies de chunking pour RAG sur documentation markdown technique.md`

**Statut:** :white_check_mark: VALIDE

**Constats de la recherche:**

| Point | Recommandation | Impact |
|-------|----------------|--------|
| Taille chunks | 400-512 tokens | OK |
| Overlap | 10-20% (50-100 tokens) | OK |
| Strategie | Markdown-aware hierarchique | RecursiveCharacterTextSplitter |
| Blocs de code | Ne jamais couper | Preservation frontières |
| Headers | Metadata breadcrumb (H1 > H2 > H3) | Enrichissement |
| llms.txt | Section H2 = chunk logique | OK |

**Stack TypeScript recommandee:**
- `gray-matter` pour YAML front matter
- `@langchain/textsplitters` avec `fromLanguage("markdown")`
- **Attention:** `MarkdownHeaderTextSplitter` n'existe qu'en Python

**Configuration validee:**
```typescript
const splitter = RecursiveCharacterTextSplitter.fromLanguage("markdown", {
  chunkSize: 500,
  chunkOverlap: 75, // 15%
});
```

**Decision:** AUCUNE MODIFICATION REQUISE

**Recherche supplementaire necessaire:** Non

---

## 4. Reranking

### 4.1 BGE Reranker Validation

**Fichier de resultat:** `BGE reranker-v2-m3 for RAG on RunPod with Infinity.md`

**Statut:** :white_check_mark: VALIDE

**Recommandation du tech-spec:**
- bge-reranker-v2-m3 pour ameliorer precision

**Constats de la recherche:**

| Point | Resultat | Impact |
|-------|----------|--------|
| Modele existe | :white_check_mark: `BAAI/bge-reranker-v2-m3` (mars 2024) | OK |
| Downloads | 2.8M/mois | Mature |
| Parametres | 568M | OK |
| Context length | 8192 tokens | OK |
| VRAM FP16 | ~1.5GB | OK pour L4 |
| **Compatibilite Infinity** | :white_check_mark: **Teste et valide** | OK |
| Improvement MRR | +30-40% vs semantic seul | Excellent |
| Latence 30-50 docs | 200-400ms | Acceptable |

**Architecture two-stage recommandee:**
- Stage 1: pgvector → 30-50 candidats
- Stage 2: bge-reranker → 3-5 resultats finaux

**Decision:** AUCUNE MODIFICATION REQUISE

**Recherche supplementaire necessaire:** Non

---

## 5. Infinity Server

### 5.1 Configuration Infinity

**Fichier de resultat:** `Infinity Embedding Server.md`

**Statut:** :warning: PARTIELLEMENT VALIDE

**Recommandation du tech-spec:**
- Infinity sur RunPod pour embeddings + reranking

**Constats de la recherche:**

| Point | Resultat | Impact |
|-------|----------|--------|
| Version stable | v0.0.77 (aout 2025) | OK |
| Multi-model | :white_check_mark: Embedding + reranking simultane | OK |
| API | OpenAI-compatible | OK |
| bge-reranker-v2-m3 | :white_check_mark: Supporte nativement | OK |
| **Qwen3-Embedding** | :x: **NON SUPPORTE** | **BLOQUANT** |
| gte-Qwen2 | :white_check_mark: Supporte (fallback) | Alternative |

**PROBLEME CRITIQUE:** Infinity ne supporte pas Qwen3-Embedding

**Options:**
1. **TEI pour embeddings + Infinity pour reranking** (2 services)
2. **TEI pour les deux** (si TEI supporte reranking)
3. **Infinity + gte-Qwen2** (modele moins performant)

**Decision a prendre:**

- [ ] **Option A**: Architecture mixte TEI (embed) + Infinity (rerank)
- [ ] **Option B**: Tout sur TEI (verifier support reranking)
- [ ] **Option C**: Tout sur Infinity avec gte-Qwen2-1.5B comme embedding

**Recherche supplementaire necessaire:** Oui - Capacites reranking de TEI

---

### 5.2 Deploiement RunPod

**Couvert partiellement dans les fichiers existants**

**Points valides:**
- GPU L4 (24GB) recommande pour les deux modeles
- Cout ~$0.50-0.75/heure
- Serverless avec scale-to-zero possible
- Cold start 10-30 secondes

**Recherche supplementaire necessaire:** Non

---

## 6. MCP Protocol

### 6.1 SDK MCP TypeScript

**Statut:** :grey_question: NON RECHERCHE

**Points a investiguer:**
- Package npm @modelcontextprotocol/sdk
- Compatibilite Bun/Node.js
- Structure serveur MCP minimal
- Transports supportes (stdio, HTTP/SSE)

**Recherche supplementaire necessaire:** :white_check_mark: OUI - CRITIQUE

---

### 6.2 Design des Outils MCP pour RAG

**Statut:** :grey_question: NON RECHERCHE

**Points a investiguer:**
- Schema outils search/ingest/list/delete
- Gestion resultats volumineux
- Pagination
- Metadata dans reponses

**Recherche supplementaire necessaire:** :white_check_mark: OUI

---

### 6.3 Skills Claude Code

**Statut:** :grey_question: NON RECHERCHE

**Points a investiguer:**
- Creation de Skills Claude Code
- Integration Skills + MCP
- Configuration dans Claude Code

**Recherche supplementaire necessaire:** :white_check_mark: OUI

---

## 7. Architecture

### 7.1 Architecture Hexagonale TypeScript

**Statut:** :grey_question: NON RECHERCHE

**Points a investiguer:**
- Structure dossiers recommandee
- Implementation Ports & Adapters
- Dependency Injection sans framework
- Placement du serveur MCP

**Recherche supplementaire necessaire:** :white_check_mark: OUI

---

## 8. Format llms.txt

### 8.1 Specification llms.txt

**Partiellement couvert dans:** `Strategies de chunking pour RAG sur documentation markdown technique.md`

**Points valides:**
- Specification llmstxt.org
- llms.txt (navigation) vs llms-full.txt (contenu complet)
- Section H2 = chunk logique

**Points a investiguer:**
- Parser TypeScript/JavaScript
- Strategie complete d'ingestion

**Recherche supplementaire necessaire:** Optionnel

---

## 9. Securite & Production

### 9.1 Securite et Resilience

**Statut:** :grey_question: NON RECHERCHE

**Points a investiguer:**
- Gestion secrets (env vars, Cloudflare, etc.)
- HTTPS vers Infinity/TEI
- Retry logic / exponential backoff
- Timeouts
- Health checks

**Recherche supplementaire necessaire:** :white_check_mark: OUI

---

## 10. Performance

### 10.1 Optimisation PostgreSQL

**Partiellement couvert dans:** `PostgreSQL 18 and pgvector configuration.md`

**Points valides:**
- Configuration memoire pour 24GB RAM
- Parametres pgvector
- Capacite estimee (~10M vecteurs dans 20GB)

**Recherche supplementaire necessaire:** Non

---

## Resume des Actions

### Decisions a prendre immediatement

| # | Decision | Options | Recommandation |
|---|----------|---------|----------------|
| 1 | Runtime | Bun vs Node.js | **Node.js 22+** |
| 2 | Serveur embeddings | Infinity vs TEI vs vLLM | **TEI** (supporte Qwen3) |
| 3 | Architecture inference | Service unique vs separes | **TEI (embed) + Infinity (rerank)** ou tout TEI |

### Recherches supplementaires a lancer

| # | Sujet | Priorite | Prompt de reference |
|---|-------|----------|---------------------|
| 1 | SDK MCP TypeScript + Node.js | CRITIQUE | Section 6.1 du guide |
| 2 | TEI reranking capabilities | HAUTE | Nouvelle recherche |
| 3 | Design outils MCP RAG | MOYENNE | Section 6.2 du guide |
| 4 | Skills Claude Code | MOYENNE | Section 6.3 du guide |
| 5 | Architecture Hexagonale TS | MOYENNE | Section 7.1 du guide |
| 6 | Securite et Resilience | BASSE | Section 9.1 du guide |

### Modifications du tech-spec

| Composant | Avant | Apres | Raison |
|-----------|-------|-------|--------|
| Runtime | Bun 1.3.5+ | **Node.js 22+** | Bug stdin 16KB, memory leaks |
| Serveur embeddings | Infinity | **TEI** | Incompatibilite Qwen3 |
| HNSW m | (non specifie) | **m=32** | Optimal pour 1024D |
| HNSW ef_construction | (non specifie) | **ef_construction=128** | Optimal pour 1024D |
| ef_search runtime | (non specifie) | **ef_search=100** | Recall > 95% |
| Operateur distance | (non specifie) | **inner product (<#>)** | 3-5x plus rapide |

---

## Validation Checklist

- [ ] Decision runtime prise (Bun vs Node.js)
- [ ] Decision serveur inference prise (TEI vs Infinity vs mixte)
- [ ] Recherche SDK MCP lancee et validee
- [ ] Recherche TEI reranking lancee
- [ ] Tech-spec mis a jour avec nouvelles recommandations
- [ ] Architecture inference finalisee
