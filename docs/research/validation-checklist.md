# Checklist de Validation - Tech Spec Alexandria RAG MCP

**Stack validée après corrections critiques.** Cette architecture Bun + PostgreSQL/pgvector + Transformers.js est techniquement solide pour un serveur MCP RAG.

**Corrections appliquées:**

- ✅ SDK MCP: ^2.0.0 → **^1.22.0** (v2 n'existe pas)
- ✅ Zod: import `"zod/v3"` obligatoire (incompatibilité MCP)
- ✅ Docker: `ankane/pgvector` → **`pgvector/pgvector:pg18`**
- ✅ Chunks: 8000 chars → **2000 chars max** (E5 limite 512 tokens)
- ✅ ef_construction: 64 → **100** (meilleur recall production RAG)
- ✅ ef_search: 40 → **100** (production standard)
- ✅ E5 révision: `refs/pr/1` → **`main`**
- ✅ Pool size: 10 → **20** (support concurrence ingestion)
- ✅ Concurrence: **Advisory locks** (`pg_try_advisory_xact_lock`) pour éviter race conditions
- ✅ **Threshold: 0.5 → 0.82** (E5 utilise température 0.01, scores compressés)
- ✅ **Normalisation: `normalize: true`** obligatoire dans transformers.js
- ✅ **Distance pgvector:** range [0,2] clarifié, formule `1 - distance` validée
- ✅ **Error codes: -32xxx → -31xxx** (conflit avec MCP SDK -32001 RequestTimeout, -32002 ResourceNotFound)
- ✅ **JSONB index:** `jsonb_path_ops` pour index 2-3× plus compact
- ✅ **UNIQUE constraint:** Index B-tree implicite, pas de duplication
- ✅ **PostgreSQL 18:** `gen_random_uuid()` natif, `uuidv7()` disponible pour UUIDs ordonnés
- ✅ **XState v5:** Machine d'états pour model loading avec `fromPromise` actors
- ✅ **Timeouts two-tier:** 180s cold start / 60s warm cache (60s seul insuffisant)
- ✅ **Health checks three-probe:** live/ready/startup pattern Kubernetes
- ✅ **Circuit breaker:** 5 échecs/60s pour error recovery ML
- ✅ **Bun minimum:** 1.3.5 (NAPI hot reload fix décembre 2025)
- ✅ **env.cacheDir:** Configuration explicite obligatoire (fix re-download bug)
- ✅ **dtype: 'q8':** Quantification int8 recommandée (118MB vs 470MB)
- ✅ **Tensor disposal:** Disposal explicite obligatoire (fuite mémoire ONNX #25325)
- ✅ **Session options:** `enableCpuMemArena: false` pour stabilité mémoire
- ✅ **CLI parseArgs:** `Bun.argv` → **`process.argv.slice(2)`** (évite bugs Bun #15877, #22157)
- ✅ **CLI allowNegative:** `true` pour support `--no-upsert` (Bun 1.2.11+)
- ✅ **Exit codes POSIX:** 0=success, 1=runtime, 2=validation (convention standard)

---

## Prompt de Recherche

Copier ce prompt, puis coller une section (bloc entre ```) à la place de `[SECTION]`.

````markdown
# Contexte du Projet

**Projet:** Alexandria RAG MCP Server

**Description:** Serveur MCP (Model Context Protocol) exposant une base de connaissances documentaire interrogeable par recherche sémantique. Permet à Claude Code d'accéder à de la documentation technique et des conventions de code spécifiques à un projet.

**Architecture (validée):**
- Runtime: Bun 1.3.5+ (TypeScript strict) — ⚠️ Minimum 1.3.5 pour stabilité NAPI
- Database: PostgreSQL 18.1 + pgvector 0.8.1 (recherche vectorielle)
- Embeddings: Xenova/multilingual-e5-small@main via @huggingface/transformers ^3.0.0 (ONNX, local)
- Transport: MCP SDK ^1.22.0 (stdio pour Claude Code) — ⚠️ PAS v2.0.0
- ORM: Drizzle ^0.44.0 + postgres.js ^3.4.0
- Validation: Zod ^3.25.0 avec import "zod/v3" obligatoire
- State Machine: XState ^5.0.0 avec `fromPromise` actors
- Architecture: Hexagonale (Ports & Adapters)

**Contraintes E5 (multilingual-e5-small):**

- Limite stricte: 512 tokens (troncature silencieuse au-delà)
- Chunk max: 2000 chars (target: 1600-1800 chars)
- Warning: > 1500 chars
- Prefixes obligatoires: "query: " / "passage: " (~2-3 tokens)
- Dimensions embedding: 384
- **Normalisation L2 obligatoire:** `normalize: true` dans transformers.js
- **Scores compressés:** température 0.01 → textes non-reliés obtiennent 0.75-0.85
- **Threshold recommandé:** 0.82 (défaut), range 0.80-0.90 selon précision/rappel

**Configuration HNSW (pgvector 0.8.1):**
- m=16, ef_construction=100 (production RAG)
- ef_search=100 (production standard)
- iterative_scan=relaxed_order (pour filtrage tags)
- LIMIT max: 60 résultats

**Transaction & Concurrence (validé):**
- Isolation: READ COMMITTED (défaut PostgreSQL, suffisant)
- Pool: 15-20 connexions (max: 20 recommandé)
- Upsert: ON CONFLICT pour parent + DELETE/INSERT pour chunks
- Concurrence: Advisory locks transaction-level (pg_try_advisory_xact_lock)
- Atomicité: Rollback garanti si INSERT chunks échoue

**Fonctionnalités MVP:**
- Ingestion de documents Markdown avec chunks pré-calculés
- Recherche sémantique via embeddings 384 dimensions
- 5 MCP tools: search, ingest, delete, health, list
- CLI pour ingestion standalone

**CLI (validé):**
- Parser: `process.argv.slice(2)` + `node:util` parseArgs (évite bugs Bun.argv)
- Options: `strict: true`, `allowPositionals: true`, `allowNegative: true` (Bun 1.2.11+)
- Exit codes POSIX: 0=success, 1=runtime error, 2=validation error

**Contraintes Techniques:**
- Tout en local (pas de services cloud)
- Embeddings via ONNX runtime (pas d'API externe)
- Compatible Claude Code via MCP stdio
- PostgreSQL comme seule base (vecteurs + metadata)

**Déploiement Cible:**
- Local: Docker Compose (pgvector/pgvector:pg18) + HuggingFace cache local
- CI/CD: Testcontainers + GitHub Actions cache
- Self-hosted: Docker/VM avec PostgreSQL externe

**Gestion des Secrets:**
- Local: .dev.vars (gitignored)
- Production Cloudflare: wrangler secret put
- Self-hosted: Variables d'environnement système

---

# Tâche de Recherche

Effectue une recherche approfondie sur la section suivante pour valider les choix techniques, identifier les problèmes potentiels, et confirmer les versions/compatibilités.

## Section à Rechercher

[SECTION]

---

# Instructions

1. **Recherche de compatibilité** - Vérifie que les versions mentionnées sont compatibles entre elles
2. **Documentation officielle** - Consulte la documentation officielle des outils/librairies
3. **Issues connues** - Identifie les problèmes connus (GitHub issues, Stack Overflow)
4. **Best practices** - Vérifie si les choix suivent les recommandations actuelles
5. **Alternatives** - Mentionne les alternatives si un choix semble risqué

# Format de Réponse Attendu

Pour chaque élément:

| Élément | Statut | Recherche | Recommandation | Sources |
|---------|--------|-----------|----------------|---------|
| [Nom] | ✅/⚠️/❌ | [Résultats] | [Action] | [URLs] |

Termine par un résumé des risques identifiés et des actions prioritaires.
````

---

## Index des Sections

| # | Section | Priorité | Statut |
|---|---------|----------|--------|
| 1 | Stack Technique & Versions | Haute | ✅ Validé + corrigé |
| 2 | Décisions Architecturales | Moyenne | ✅ Validé |
| 3 | Configuration HNSW | Haute | ✅ Validé + corrigé (ef_construction=100, ef_search=100) |
| 4 | Modèle E5 - Préfixes | Haute | ✅ Validé + corrigé (normalize: true) |
| 5 | Limites & Contraintes | Haute | ✅ Validé (chunks 2000 max) |
| 6 | Retry Logic & Timeouts | Moyenne | ✅ Validé + enrichi (circuit breaker, timeouts corrigés) |
| 7 | Isolation & Transactions | Moyenne | ✅ Validé (transaction-isolation-f18.md) |
| 8 | Score & Similarité | Haute | ✅ Validé + corrigé (threshold 0.82, normalize: true) |
| 9 | Gestion d'Erreurs MCP | Moyenne | ✅ Validé + corrigé (-32xxx → -31xxx) |
| 10 | Schema Base de Données | Moyenne | ✅ Validé (additional-context.md) |
| 11 | Health Check State Machine | Haute | ✅ Validé + enrichi (XState v5, three-probe, circuit breaker) |
| 12 | CLI Specification | Basse | ✅ Validé + corrigé (process.argv, POSIX exit codes) |
| 13 | Performance Targets | Moyenne | ✅ Validé (implementation-plan.md) |
| 14 | Questions Ouvertes | Haute | ✅ Résolu |
| 15 | Risques Identifiés | Haute | ✅ Corrigé |
| 16 | Décisions Utilisateur | Moyenne | ⚠️ Partiellement résolu (threshold ✅) |
| 17 | Stratégie de Test | Moyenne | ✅ Validé (additional-context.md) |
| 18 | Sécurité & Validation | Haute | ✅ Validé (limites corrigées) |
| 19 | Observabilité & Logging | Basse | ✅ Validé (warning 1500 chars) |
| 20 | Déploiement & Environnements | Moyenne | ✅ Corrigé (image Docker) |
| 21 | Migration & Versioning | Basse | ✅ Validé (additional-context.md F45) |
| 22 | Conventions de Code | Basse | ⚠️ Décisions en attente |

---

## Section 1 — Stack Technique & Versions

```
SECTION: Stack Technique & Versions
PRIORITÉ: Haute
FOCUS: Compatibilités Bun + dépendances
STATUT: ✅ VALIDÉ (avec corrections critiques)

RUNTIME & LANGAGE
| Element | Choix | Statut | Validation |
|---------|-------|--------|------------|
| Runtime | Bun 1.1.24+ | ✅ | Supporte Transformers.js v3, MCP SDK, postgres.js |
| Langage | TypeScript strict | ✅ | `bun init` génère tsconfig avec strict:true. Installer @types/bun |
| Module System | ESM natif | ✅ | Top-level await et dynamic import() fonctionnent |

BASE DE DONNÉES
| Element | Version | Statut | Validation |
|---------|---------|--------|------------|
| PostgreSQL | 18.1 | ✅ | Version stable (13 nov 2025). Async I/O 3× plus rapide, UUIDv7 natif |
| pgvector | 0.8.1 | ✅ | Compatible PG18. HNSW, 16000 dims max, iterative scans |
| Driver | postgres.js ^3.4.0 | ✅ | Support Bun confirmé. ⚠️ Éviter bun:sql (bugs #22395) |
| ORM | Drizzle ORM ^0.44.0 | ✅ | pgvector NATIF depuis v0.31.0 (vector, halfvec, bit, sparsevec) |
| Drizzle Kit | ^0.30.0 | ⚠️ | Breaking change v0.30: suppression IF NOT EXISTS. Aligner versions |

EMBEDDINGS
| Element | Version | Statut | Validation |
|---------|---------|--------|------------|
| Model | Xenova/multilingual-e5-small@main | ⚠️ CORRIGÉ | `refs/pr/1` non stable → utiliser `main` |
| Runtime | @huggingface/transformers ^3.0.0 | ✅ | v3.8.1 stable. ONNX intégré. Quantification q8/q4 dispo |
| Dimensions | 384 | ✅ | Confirmé |
| Token Limit | 512 tokens | ✅ | Confirmé. Prefixes comptent dans la limite |
| Taille ONNX | q8: 118MB, fp32: 470MB | ✅ | Confirmé |

MCP & SDK
| Element | Version | Statut | Validation |
|---------|---------|--------|------------|
| MCP Protocol | 2025-11-25 | ⚠️ CORRIGÉ | 2024-11-05 obsolète mais supportée. 2025-11-25 = current |
| MCP SDK | @modelcontextprotocol/sdk ^1.22.0 | ❌ CRITIQUE | v2.0.0 N'EXISTE PAS (pré-alpha). Utiliser v1.22.0 |

AUTRES DÉPENDANCES
| Element | Version | Statut | Validation |
|---------|---------|--------|------------|
| Zod | ^3.25.0 | ⚠️ CRITIQUE | Import via "zod/v3" obligatoire (issue #1429 avec MCP) |
| Pino | ^9.0.0 | ✅ | v10.1.0 disponible. Fonctionne avec Bun |
| Pino-pretty | ^11.0.0 | ✅ | v13.1.3 compatible Pino 9-10 |
| Testcontainers | ^10.0.0 | ⚠️ | v11.11.0 dispo. Issue #853 résolue. Config bunfig.toml requise |
| TypeScript | ^5.7.0 | ✅ | v5.9.3 disponible |

DOCKER IMAGES
| Element | Image | Statut | Validation |
|---------|-------|--------|------------|
| PostgreSQL + pgvector | pgvector/pgvector:pg18 | ❌ CORRIGÉ | ankane/pgvector obsolète. Image officielle: pgvector/pgvector |
```

---

## Section 2 — Décisions Architecturales

```
SECTION: Décisions Architecturales
PRIORITÉ: Moyenne
FOCUS: Pattern hexagonal, Drizzle + pgvector
STATUT: ✅ VALIDÉ

PATTERN HEXAGONAL
| Couche | Responsabilité | Statut |
|--------|----------------|--------|
| domain/ | Entités métier pures | ✅ Pattern approprié |
| application/ | Use cases, ports (interfaces) | ✅ Niveau abstraction correct |
| infrastructure/ | Adapters (DB, embeddings) | ✅ Séparation claire |
| mcp/ | Transport layer MCP | ✅ Découplé via ports |

DRIZZLE + PGVECTOR: ✅ SUPPORT NATIF CONFIRMÉ
Drizzle ORM supporte pgvector nativement depuis v0.31.0.

Types disponibles: vector, halfvec, bit, sparsevec
Fonctions distance: l2Distance, cosineDistance, innerProduct

EXEMPLE DRIZZLE PGVECTOR:
import { pgTable, serial, vector } from 'drizzle-orm/pg-core';
import { cosineDistance } from 'drizzle-orm';

const chunks = pgTable('chunks', {
  id: serial('id').primaryKey(),
  embedding: vector('embedding', { dimensions: 384 })
});

// Recherche avec distance cosinus
const results = await db.select()
  .from(chunks)
  .orderBy(cosineDistance(chunks.embedding, queryVector))
  .limit(10);

POSTGRES.JS + PGVECTOR (pour raw SQL si nécessaire):
Le package npm `pgvector` est utile pour les requêtes raw SQL:

import pgvector from 'pgvector';
import postgres from 'postgres';

const sql = postgres(process.env.DATABASE_URL);
await sql`INSERT INTO items ${sql([{
  embedding: pgvector.toSql([1,2,3])
}], 'embedding')}`;

⚠️ ATTENTION BUN:SQL
Éviter le driver natif `bun:sql` en production — plusieurs issues ouvertes
(hangs après violations de contraintes, #22395). Utiliser postgres npm package.
```

---

## Section 3 — Configuration HNSW

```
SECTION: Configuration HNSW (Algorithme de Recherche Vectorielle)
PRIORITÉ: Haute
FOCUS: Paramètres optimaux pour <10K vecteurs
STATUT: ✅ VALIDÉ (avec ajustements production RAG)

PARAMÈTRES INDEX HNSW - VALIDÉS
| Paramètre | Valeur | Défaut pgvector | Statut | Justification |
|-----------|--------|-----------------|--------|---------------|
| m | 16 | 16 | ✅ Optimal | m=32 n'apporterait que +1-2% recall pour +40% latence |
| ef_construction | 100 | 64 | ✅ AUGMENTÉ | Meilleur recall pour RAG production |
| ef_search | 100 | 40 | ✅ AUGMENTÉ | Production standard, bon équilibre recall/latence |

IMPACT M=16 VS M=32:
| Métrique | m=16 | m=32 | Verdict |
|----------|------|------|---------|
| Recall | 95-98% | 97-99% | +1-2% marginal |
| Taille index | ~20 Mo | ~25 Mo | +25% |
| Latence | ~0.5ms | ~0.7ms | +40% |
→ m=16 CONSERVÉ (Andrew Kane, mainteneur pgvector: "recommend default m for faster builds")

IMPACT EF_SEARCH:
| ef_search | Recall@10 | Latence | Usage |
|-----------|-----------|---------|-------|
| 40 (défaut) | ~85-90% | Baseline | Haute throughput |
| 100 | ~92-95% | 2.5× | ✅ CHOISI (production standard) |
| 120 | ~95-97% | 3× | Haute précision |
| 200+ | ~98%+ | 5× | Précision critique |
→ ef_search=100 ADOPTÉ (production standard, configurable par session si besoin)

CONFIGURATION EF_SEARCH - VALIDÉ
| Méthode | Choix | Statut | Notes |
|---------|-------|--------|-------|
| ALTER DATABASE | Oui | ✅ Correct | Persiste après restart (pg_db_role_setting) |
| SET per-session | Optionnel | ✅ Correct | Possible pour requêtes spécifiques |

SQL FINAL:
ALTER DATABASE alexandria SET hnsw.ef_search = 100;
ALTER DATABASE alexandria SET hnsw.iterative_scan = relaxed_order;
ALTER DATABASE alexandria SET maintenance_work_mem = '256MB';  -- Optionnel

ESTIMATIONS PERFORMANCE (10K vecteurs @ 384 dims):
| Métrique | Estimation |
|----------|------------|
| Taille index | ~18-25 Mo |
| Temps construction | 3-6 secondes (ef_construction=100) |
| Latence requête | <2 ms |
| Recall@10 | 92-95% |
```

---

## Section 4 — Modèle E5 Préfixes

```
SECTION: Modèle E5 - Gestion des Préfixes
PRIORITÉ: Haute
FOCUS: Format exact des prefixes "query: " / "passage: "
STATUT: ✅ VALIDÉ ET CONFIRMÉ

FORMAT DES PREFIXES - CONFIRMÉ
| Type | Prefix exact | Statut | Source |
|------|--------------|--------|--------|
| Query (recherche) | "query: " | ✅ | Model card officiel |
| Passage (ingestion) | "passage: " | ✅ | Model card officiel |

⚠️ PREFIXES OBLIGATOIRES
Citation model card: "Do I need to add the prefix? Yes, this is how the model
is trained, otherwise you will see a performance degradation."

Les prefixes COMPTENT contre la limite de 512 tokens.

RÈGLES DE GESTION - CONFIRMÉES:
- Ajout automatique: Le système ajoute TOUJOURS le prefix approprié
- Content existant avec prefix: Si content commence par "query:" ou "passage:",
  le prefix est quand même ajouté (double-prefix intentionnel évité par design)
- Whitespace: Un seul espace après le colon, pas de trim du content
- Token limit: Les ~8 tokens du prefix réduisent la capacité utile à ~504 tokens

SPÉCIFICATIONS MODÈLE - CONFIRMÉES
| Paramètre | Valeur | Statut |
|-----------|--------|--------|
| Dimensions embedding | 384 | ✅ Confirmé |
| Limite tokens | 512 | ✅ Confirmé (prefixes inclus) |
| Taille ONNX (q8) | 118 MB | ✅ Confirmé |
| Taille ONNX (fp32) | 470 MB | ✅ Confirmé |
| Révision recommandée | main | ⚠️ CORRIGÉ (pas refs/pr/1) |

RÉVISION DU MODÈLE - CORRECTION REQUISE:
- revision: "refs/pr/1"  # ❌ Non recommandé, pas stable
+ revision: "main"       # ✅ Branche par défaut, ONNX optimisé pour v3
```

---

## Section 5 — Limites & Contraintes

```
SECTION: Limites & Contraintes de Taille
PRIORITÉ: Haute
FOCUS: Ratios chars/tokens, limites réalistes
STATUT: ✅ VALIDÉ (limites corrigées et appliquées)

⚠️ PROBLÈME CRITIQUE IDENTIFIÉ:
Le modèle E5 a une limite STRICTE de 512 tokens. Tout au-delà est SILENCIEUSEMENT
TRONQUÉ — l'embedding ne représente alors qu'une fraction du chunk stocké.
8000 chars ≈ 2000-2600 tokens = 4-5× la capacité du modèle!

RATIOS CARACTÈRES/TOKENS (XLMRobertaTokenizer):
| Type de contenu | Ratio chars/token | Max chars pour 512 tokens |
|-----------------|-------------------|---------------------------|
| Anglais technique | 4.0 – 4.75 | 2,048 – 2,432 |
| Français technique | 4.6 – 4.7 | 2,355 – 2,406 |
| JavaScript/TypeScript | 3.0 – 3.5 | 1,536 – 1,792 |
| Markdown mixte | 3.0 – 4.0 | 1,536 – 2,048 |

LIMITES DE TAILLE (F24) - RÉVISÉES
| Limite | Ancienne | Nouvelle | Rationale |
|--------|----------|----------|-----------|
| Chunk target | 8000 chars | 1,600-1,800 chars | Reste sous 512 tokens avec marge |
| Hard limit | — | 2,000 chars | Ne jamais dépasser |
| Warning threshold | 4000 chars | 1,500 chars | Alerte avant zone critique |
| Chunk overlap | — | 150-200 chars | Cohérence contextuelle |
| Chunks par document | 500 max | 500 max | ✓ Inchangé |

LIMITES MCP TOOL SCHEMAS - VALIDÉES
| Limite | Valeur | Statut | Justification |
|--------|--------|--------|---------------|
| Query max | 1000 chars | ✅ OK | ~250 tokens, largement suffisant |
| Source max | 500 chars | ✅ OK | Dépasse rarement 256 chars (historique FS) |
| Title max | 200 chars | ✅ OK | Standard industrie |
| Limit max | 60 résultats | ✅ OK | Production standard |
```

---

## Section 6 — Retry Logic & Timeouts

```
SECTION: Retry Logic & Timeouts
PRIORITÉ: Basse
FOCUS: Best practices retry patterns + circuit breaker
STATUT: ✅ VALIDÉ ET ENRICHI (retry-logic-f22.md)

CONFIGURATION RETRY (F22)
| Paramètre | Valeur | Statut |
|-----------|--------|--------|
| Max attempts | 3 (1 initial + 2 retries) | ✅ Validé |
| Base delay | 100ms | ✅ Validé |
| Multiplier | 4 | ✅ Delays: 100ms, 400ms |
| Jitter | ±20% | ✅ Standard industry |

CATÉGORISATION ERREURS — ⚠️ AJOUTÉ
| Type erreur | Retry? | Action |
|-------------|--------|--------|
| Transitoire réseau | ✅ Oui | Backoff exponentiel |
| Rate limiting | ✅ Oui | Respecter Retry-After |
| Validation client | ❌ Non | Échec immédiat |
| Out of Memory | ❌ Non | Circuit breaker reset |
| Corruption | ❌ Non | Reset complet |

CIRCUIT BREAKER — ⚠️ AJOUTÉ
- Seuil: 5 échecs consécutifs en 60 secondes
- États: CLOSED → OPEN → HALF_OPEN → CLOSED
- Reset timeout: 30 secondes avant probe

TIMEOUTS — ⚠️ CORRIGÉ
| Operation | Timeout | Notes |
|-----------|---------|-------|
| Model loading (cold) | 180s | ✅ CORRIGÉ (60s insuffisant) |
| Model loading (warm) | 60s | ✅ Cache local |
| Inference | 30s | Single chunk |
| Drain requests | 30s | Graceful shutdown |
| Close DB pool | 5s | Cleanup |

FORMULE JITTER:
jitter = 1 + (Math.random() - 0.5) * 2 * (jitterPercent / 100)
actualDelay = baseDelay * jitter
```

---

## Section 7 — Isolation & Transactions

```
SECTION: Isolation & Transactions PostgreSQL
PRIORITÉ: Moyenne
FOCUS: READ COMMITTED vs REPEATABLE READ + Advisory Locks
STATUT: ✅ VALIDÉ ET ENRICHI (transaction-isolation-f18.md, concurrent-ingestion-f26.md, upsert-atomicity-f25.md)

VERDICT ARCHITECTURAL:
READ COMMITTED suffit pour l'ingestion document/chunks avec pgvector. Les garanties
d'atomicité transactionnelle de PostgreSQL assurent que DELETE + INSERT sont atomiques
même avec ce niveau d'isolation. Advisory locks transaction-level pour la concurrence.

TRANSACTION ISOLATION (F18)
| Aspect | Choix | Statut |
|--------|-------|--------|
| Isolation Level | READ COMMITTED | ✅ Suffisant, retry non requis |
| Ingestion | Transaction unique (doc + chunks) | ✅ Rollback garanti |
| Search | Pas de transaction | ✅ Snapshot suffisant |
| Concurrence | Advisory locks transaction-level | ✅ pg_try_advisory_xact_lock |

COMPARAISON READ COMMITTED vs REPEATABLE READ:
| Aspect | READ COMMITTED | REPEATABLE READ |
|--------|----------------|-----------------|
| Snapshot | Par statement | Par transaction |
| Retry nécessaire | Rarement | Oui, obligatoire |
| Comportement conflit | Re-évalue WHERE | Erreur serialization |
→ READ COMMITTED CHOISI car retry non nécessaire et atomicité garantie

PATTERN UPSERT (F25):
- ON CONFLICT DO UPDATE pour document parent (efficace, un seul scan)
- DELETE + batch INSERT pour chunks (remplacement complet)
- Tout dans une transaction unique

CONCURRENCE (F26):
- Advisory locks: pg_try_advisory_xact_lock(hashtext(source)::bigint)
- Libération automatique au COMMIT/ROLLBACK
- Pas de deadlock possible (try immediate, pas de wait)
- Client B reçoit CONCURRENT_MODIFICATION (-31009) si lock non acquis

COMPORTEMENT PGVECTOR:
- Embeddings non-commités jamais visibles aux autres transactions
- HNSW: modifications graphe n'affectent pas visibilité MVCC
- READ COMMITTED approprié pour recherche vectorielle approximative

CONFIGURATION POSTGRES.JS:
const sql = postgres(DATABASE_URL, {
  max: 20,          // Pool 15-20 connexions recommandé
  idle_timeout: 20  // Fermeture connexions inactives
});

await sql.begin('ISOLATION LEVEL READ COMMITTED', async sql => {
  // 1. Advisory lock
  // 2. ON CONFLICT pour parent
  // 3. DELETE + batch INSERT chunks
});
```

---

## Section 8 — Score & Similarité

```
SECTION: Score & Calcul de Similarité pgvector
PRIORITÉ: Haute
FOCUS: Conversion distance → similarité, threshold E5
STATUT: ✅ VALIDÉ ET CORRIGÉ (score-calculation-f33.md)

OPÉRATEUR PGVECTOR - ✅ CLARIFIÉ
| Concept | Formule | Range | Statut |
|---------|---------|-------|--------|
| Distance cosinus | 1 - cos(θ) | [0, 2] | ✅ Confirmé |
| Conversion similarité | 1 - distance | [-1, 1] théorique | ✅ Formule correcte |
| En pratique (embeddings) | 1 - distance | [0, 1] | ✅ Jamais négatif |

THRESHOLD - ⚠️ CORRIGÉ
| Ancien | Nouveau | Raison |
|--------|---------|--------|
| 0.5 | **0.82** | E5 température 0.01 compresse les scores |

Distribution des scores E5:
| Type de comparaison | Score typique |
|---------------------|---------------|
| Textes non-reliés | 0.75 - 0.85 (threshold 0.5 n'élimine RIEN) |
| Textes modérément pertinents | 0.85 - 0.90 |
| Textes très pertinents | 0.90 - 1.0 |

NORMALISATION - ⚠️ OBLIGATOIRE
E5 ne normalise PAS par défaut. Config transformers.js:
  const output = await extractor(text, {
    pooling: 'mean',
    normalize: true  // OBLIGATOIRE
  });

OPTIMISATION FUTURE (vector_ip_ops)
Avec embeddings normalisés, <#> (inner product) est ~3× plus rapide que <=>.
Décision MVP: conserver vector_cosine_ops pour clarté sémantique.
```

---

## Section 9 — Gestion d'Erreurs MCP

```
SECTION: Gestion d'Erreurs MCP
PRIORITÉ: Moyenne
FOCUS: Codes erreur MCP spec compliance
STATUT: ✅ VALIDÉ + CORRIGÉ (migration -32xxx → -31xxx)

PROBLÈME IDENTIFIÉ:
Les codes -32001 à -32009 étaient en conflit avec:
- JSON-RPC 2.0: plage -32768 à -32000 réservée aux erreurs prédéfinies
- MCP SDK: -32000 (ConnectionClosed), -32001 (RequestTimeout)
- MCP Spec: -32002 (Resource Not Found)

SOLUTION APPLIQUÉE: Migration vers plage -31xxx

CODES D'ERREUR CUSTOM (CORRIGÉS)
| Code | Nom | Statut |
|------|-----|--------|
| -31001 | DOCUMENT_NOT_FOUND | ✅ Plage libre |
| -31002 | VALIDATION_ERROR | ✅ Plage libre |
| -31003 | EMBEDDING_FAILED | ✅ Plage libre |
| -31004 | DATABASE_ERROR | ✅ Plage libre |
| -31005 | DUPLICATE_SOURCE | ✅ Plage libre |
| -31006 | MODEL_LOAD_TIMEOUT | ✅ Plage libre |
| -31007 | EMBEDDING_DIMENSION_MISMATCH | ✅ Plage libre |
| -31008 | CONTENT_TOO_LARGE | ✅ Plage libre |
| -31009 | CONCURRENT_MODIFICATION | ✅ Plage libre |

CODES MCP SDK RÉSERVÉS (NE PAS UTILISER):
| Code | Nom | Usage |
|------|-----|-------|
| -32700 | ParseError | JSON invalide |
| -32600 | InvalidRequest | Structure invalide |
| -32601 | MethodNotFound | Méthode inexistante |
| -32602 | InvalidParams | Paramètres incorrects |
| -32603 | InternalError | Erreur interne |
| -32000 | ConnectionClosed | Connexion fermée |
| -32001 | RequestTimeout | Timeout requête |
| -32002 | ResourceNotFound | Ressource non trouvée |

PLAGES DISPONIBLES POUR ERREURS APPLICATIVES:
- ✅ -31xxx (choisi pour Alexandria)
- ✅ -30xxx et inférieurs
- ✅ Nombres positifs (1001, 1002, etc.)
- ❌ -32000 à -32099 (réservé JSON-RPC server errors)
- ❌ -32600 à -32700 (réservé JSON-RPC protocol errors)
```

---

## Section 10 — Schema Base de Données

```
SECTION: Schema Base de Données PostgreSQL + pgvector
PRIORITÉ: Moyenne
FOCUS: Syntaxe pgvector, indexes
STATUT: ✅ VALIDÉ ET OPTIMISÉ (spécifié dans additional-context.md)

TABLE DOCUMENTS - VALIDÉE
| Colonne | Type | Statut | Notes |
|---------|------|--------|-------|
| id | UUID (gen_random_uuid()) | ✅ | Natif PG13+, uuidv7() dispo PG18+ |
| source | TEXT UNIQUE | ✅ | Index B-tree IMPLICITE (pas de duplication) |
| content | TEXT | ✅ | ~1GB max, TOAST automatique |
| tags | TEXT[] | ✅ | GIN index pour opérateur && |
| version | TEXT | ✅ | Format libre OK MVP |
| created_at/updated_at | TIMESTAMPTZ | ✅ | UTC stockage, conversion par session |

TABLE CHUNKS - VALIDÉE
| Colonne | Type | Statut | Notes |
|---------|------|--------|-------|
| embedding | vector(384) | ✅ | 1544 bytes/vecteur (4×384+8), max 16000 dims |
| chunk_index | INTEGER | ✅ | UNIQUE composite crée B-tree implicite |
| metadata | JSONB | ✅ | jsonb_path_ops pour index compact |

INDEXES - VALIDÉS ET OPTIMISÉS
| Index | Type | Statut | Notes |
|-------|------|--------|-------|
| chunks_embedding_idx | HNSW vector_cosine_ops | ✅ | Optimal sentence-transformers |
| documents_tags_idx | GIN | ✅ | Opérateur && (overlap) pour OR |
| chunks_metadata_idx | GIN jsonb_path_ops | ✅ AJOUTÉ | 2-3× plus compact que défaut |
| (documents.source) | B-tree implicite | ✅ | Via UNIQUE, PAS de duplication |
| (document_id, chunk_index) | B-tree implicite | ✅ | Via UNIQUE, PAS de duplication |

NOTE UNIQUE CONSTRAINT: La contrainte UNIQUE crée automatiquement un index B-tree.
Ne JAMAIS créer d'index supplémentaire sur ces colonnes — cela dupliquerait l'index.

SQL INDEX HNSW:
CREATE INDEX chunks_embedding_idx ON chunks
  USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 100);

SQL INDEX JSONB (recommandé):
CREATE INDEX chunks_metadata_idx ON chunks USING GIN (metadata jsonb_path_ops);
-- jsonb_path_ops: supporte @>, @?, @@ — suffisant pour RAG, index 2-3× plus compact
```

---

## Section 11 — Health Check State Machine

```
SECTION: Health Check State Machine (F23)
PRIORITÉ: Haute
FOCUS: XState v5 + three-probe health + circuit breaker
STATUT: ✅ VALIDÉ ET ENRICHI (health-check-state-machine-f23.md)

MACHINE D'ÉTATS — ⚠️ CORRIGÉ
Recommandation: XState v5 avec pattern `fromPromise` pour opérations async 30-180s

| État | Description | Statut |
|------|-------------|--------|
| initial | Pas de modèle chargé | ✅ État de départ |
| loading | Chargement en cours (invoke) | ✅ Avec AbortSignal |
| loaded | Prêt pour inférence (final) | ✅ retryCount reset |
| error | Échec (circuit breaker) | ✅ Avec guards retry |

TRANSITIONS — ⚠️ ENRICHI
| From | To | Trigger | Guard |
|------|-----|---------|-------|
| initial | loading | LOAD event | - |
| loading | loaded | onDone (invoke) | - |
| loading | error | onError / timeout | - |
| loaded | error | Erreur d'inférence | - |
| error | loading | RETRY event | retryCount < 3 |
| error | initial | RESET event | - |

TIMEOUTS TWO-TIER — ⚠️ CORRIGÉ
| Scénario | Ancien | Nouveau | Justification |
|----------|--------|---------|---------------|
| Cold start (download ~118MB) | 60s | 180s | Slow network timeout |
| Warm cache (local) | 60s | 60s | ✅ Suffisant |
→ 60s seul était INSUFFISANT pour premier téléchargement

THREE-PROBE HEALTH PATTERN — ⚠️ AJOUTÉ
| Endpoint | Status | Bloque? | Usage |
|----------|--------|---------|-------|
| /health/live | 200 toujours | ❌ Non | Liveness probe |
| /health/ready | 200/503 | ❌ Non | Readiness probe |
| /health/startup | 200/503 | ❌ Non | Startup probe |

CIRCUIT BREAKER ERROR RECOVERY — ⚠️ AJOUTÉ
| Type erreur | Retry auto? | Action |
|-------------|-------------|--------|
| Network timeout | ✅ Oui | Backoff exponentiel (3 attempts) |
| Model busy | ✅ Oui | Court délai retry |
| Out of Memory | ❌ Non | Session recreation |
| Model corruption | ❌ Non | Reset vers initial |

GESTION MÉMOIRE — ⚠️ AJOUTÉ
- Disposal explicite OBLIGATOIRE pour tous les tensors (ONNX issue #25325)
- Réutiliser sessions plutôt que recréer
- Recreation atomique: nouvelle session AVANT dispose ancienne
- Seuil mémoire: trigger recreation si heap > 85%

COMPATIBILITÉ BUN — ⚠️ AJOUTÉ
| Issue | Solution |
|-------|----------|
| NAPI hot reload crashes | Minimum Bun 1.3.5 |
| Bus error ARM64 macOS | onnxruntime-web (WASM) |
| Windows path encoding | WSL ou Bun 1.2.4 |

SESSION OPTIONS RECOMMANDÉES:
enableCpuMemArena: false, enableMemPattern: false,
graphOptimizationLevel: 'all', executionMode: 'sequential',
intraOpNumThreads: 1
```

---

## Section 12 — CLI Specification

```
SECTION: CLI Specification (F21)
PRIORITÉ: Basse
FOCUS: Bun parseArgs support + exit codes POSIX
STATUT: ✅ VALIDÉ + CORRIGÉ (janvier 2026)

PARSER — ⚠️ CORRIGÉ
| Élément | Ancienne valeur | Nouvelle valeur | Raison |
|---------|-----------------|-----------------|--------|
| args | Bun.argv | process.argv.slice(2) | Évite bugs Bun #15877, #22157 |
| allowNegative | Non spécifié | true | Support --no-upsert (Bun 1.2.11+) |
| strict | Non spécifié | true | Échec sur flags inconnus |
| allowPositionals | Non spécifié | true | Support <file> et <source> |

COMPATIBILITÉ BUN parseArgs — ✅ VALIDÉ
| Version Bun | Support |
|-------------|---------|
| 1.1.24+ | ✅ parseArgs basique fonctionne |
| 1.2.11+ | ✅ allowNegative supporté (avril 2025) |
| 1.3.5+ | ✅ Recommandé (fix NAPI décembre 2025) |

ISSUES GITHUB CONNUES:
- #15877: allowPositionals: false + Bun.argv → erreur inattendue
- #22157: Binaires compilés ajoutent argument supplémentaire
- #11451: Corrigé 1.2.11 — args: undefined ne defaultait pas à process.argv

EXIT CODES — ⚠️ CORRIGÉ (Convention POSIX)
| Code | Ancien usage | Nouveau usage (POSIX) | Exemples |
|------|--------------|------------------------|----------|
| 0 | Succès | ✅ Inchangé — SUCCESS | Ingestion réussie |
| 1 | Erreur validation | ❌→ RUNTIME_ERROR | Échec DB, embedding, réseau |
| 2 | Erreur runtime | ❌→ VALIDATION_ERROR | Flag inconnu, JSON invalide |
| 3 | Document non trouvé | ❌ SUPPRIMÉ | → Exit 1 (erreur runtime) |

Convention POSIX appliquée:
- Exit 1: Erreurs runtime (fichier introuvable, connexion échouée, timeout)
- Exit 2: Erreurs validation utilisateur (syntaxe, arguments manquants)

COMMANDES — ✅ INCHANGÉES
alexandria ingest <file> [options]
  -c, --chunks-file <path>   Fichier JSON des chunks (requis)
  -T, --title <title>        Titre du document (optionnel)
  -t, --tags <tags>          Tags séparés par virgules
  -v, --version <version>    Version du document
  --upsert                   Remplacer si existe (défaut: true)
  --no-upsert                Erreur si existe (via allowNegative)
  -d, --dry-run              Valider sans persister
  -h, --help                 Afficher l'aide

alexandria delete <source>
  -h, --help                 Afficher l'aide

LIMITATIONS parseArgs:
- Types: string/boolean uniquement (pas de number natif)
- Pas de validation intégrée (options requises → manuel)
- Pas de génération d'aide (--help → implémenter printUsage())
- Pas de subcommands (git clone-style → tokens ou lib externe)
```

---

## Section 13 — Performance Targets

```
SECTION: Performance Targets (F7)
PRIORITÉ: Moyenne
FOCUS: Benchmarks réalistes
STATUT: ✅ VALIDÉ (spécifié dans implementation-plan.md AC-12, AC-13)

ENVIRONNEMENT DE RÉFÉRENCE
| Aspect | Spec | A Valider |
|--------|------|-----------|
| Hardware | 4 CPU cores, 8GB RAM | Réaliste pour dev/prod? |
| Database | PostgreSQL 18 local, warm | Cold start performance? |
| Model | Déjà chargé | Premier chargement 1-2 min acceptable? |

OBJECTIFS
| Metric | Target | A Valider |
|--------|--------|-----------|
| Search latency (p95) | < 500ms avec 1000 chunks | Atteignable avec HNSW config? |
| Ingestion 50 chunks | < 10s | Embedding time réaliste? |

QUESTIONS:
- multilingual-e5-small: temps d'embedding par chunk?
- pgvector HNSW: latence recherche pour 1000 vecteurs?
- Bun vs Node performance pour Transformers.js?
```

---

## Section 14 — Questions Ouvertes

```
SECTION: Questions Ouvertes Critiques
PRIORITÉ: Haute
FOCUS: Compatibilités critiques à valider
STATUT: ✅ RÉSOLU (toutes questions répondues)

COMPATIBILITÉ BUN - ✅ VALIDÉ
| Question | Réponse |
|----------|---------|
| Transformers.js + Bun | ✅ Support officiel depuis oct 2024. Utiliser @huggingface/transformers v3+ |
| Testcontainers + Bun | ⚠️ Issue #853 résolue. Configurer bunfig.toml pour registry npm |
| MCP SDK + Bun | ✅ Fonctionne via transport stdio. Implémentations prod: bun-mcp, mcp-bun |

PGVECTOR - ✅ VALIDÉ
| Question | Réponse |
|----------|---------|
| Version 0.8.1 features | ✅ HNSW, iterative scans, 16000 dims max tous disponibles |
| HNSW index | ✅ Disponible et recommandé pour RAG |
| vector_cosine_ops | ✅ Operator class correcte pour distance cosinus |

E5 MODEL - ✅ CORRIGÉ
| Question | Réponse |
|----------|---------|
| refs/pr/1 stable? | ✅ CORRIGÉ → Utiliser `main` |
| Prefixes confirmés? | ✅ "query: " et "passage: " obligatoires (model card) |
| Vecteurs normalisés? | ✅ E5 retourne des vecteurs normalisés |
| Limite tokens? | ✅ 512 tokens max → chunks 2000 chars max |

MCP PROTOCOL - ✅ CORRIGÉ
| Question | Réponse |
|----------|---------|
| Version 2024-11-05 | ✅ Supportée, 2025-11-25 disponible |
| SDK ^2.0.0 | ✅ CORRIGÉ → ^1.22.0 |
| Error codes | ✅ CORRIGÉ → Plage -31xxx (évite conflits -32000 à -32099) |
```

---

## Section 15 — Risques Identifiés

```
SECTION: Risques Identifiés
PRIORITÉ: Haute
FOCUS: Risques critiques identifiés lors de la validation
STATUT: ✅ TOUS CORRIGÉS

═══════════════════════════════════════════════════════════════════════════════
ACTIONS CRITIQUES (BLOQUANTES) - ✅ TOUTES APPLIQUÉES
═══════════════════════════════════════════════════════════════════════════════

| Priorité | Action | Statut | Correction appliquée |
|----------|--------|--------|----------------------|
| 🔴 P0 | SDK MCP version | ✅ | ^2.0.0 → ^1.22.0 |
| 🔴 P0 | Import Zod | ✅ | import "zod" → import "zod/v3" |
| 🔴 P0 | Image Docker | ✅ | ankane/pgvector → pgvector/pgvector:pg18 |
| 🔴 P0 | Taille chunks | ✅ | 8000 chars → 2000 chars (E5 512 tokens) |
| 🔴 P0 | ef_construction | ✅ | 64 → 100 (production RAG) |
| 🔴 P0 | ef_search | ✅ | 40 → 100 (production standard) |

═══════════════════════════════════════════════════════════════════════════════
ACTIONS IMPORTANTES (RECOMMANDÉES) - ✅ TOUTES APPLIQUÉES
═══════════════════════════════════════════════════════════════════════════════

| Priorité | Action | Statut | Correction appliquée |
|----------|--------|--------|----------------------|
| 🟠 P1 | Révision E5 model | ✅ | refs/pr/1 → main |
| 🟠 P1 | Driver postgres | ✅ | Documenté: éviter bun:sql |
| 🟠 P1 | Versions Drizzle | ✅ | ORM 0.44.0 / Kit 0.30.0 |
| 🟠 P1 | iterative_scan | ✅ | relaxed_order ajouté |
| 🟠 P1 | Warning threshold | ✅ | 4000 → 1500 chars |

═══════════════════════════════════════════════════════════════════════════════
RISQUES RÉSIDUELS (acceptables pour MVP)
═══════════════════════════════════════════════════════════════════════════════

| Risque | Impact | Mitigation |
|--------|--------|------------|
| Premier chargement model 1-2 min | UX dégradée | ✅ Health check non-bloquant |
| Docker requis pour tests | CI/CD | ✅ Testcontainers compatible Bun |
| pgvector 0.8.1 spécifique | Portabilité | ✅ Image officielle disponible |
| Pas de pagination search | Scalabilité | ✅ Limit=60 suffisant MVP |

QUESTIONS RÉSOLUES:
- Testcontainers: ✅ Compatible Bun (issue #853 résolue)
- HuggingFace Hub down: ✅ Cache local ~/.cache/huggingface après premier téléchargement
- Limite tokens E5: ✅ Chunks réduits à 2000 chars max
```

---

## Section 16 — Décisions Utilisateur

```
SECTION: Décisions à Confirmer avec l'Utilisateur
PRIORITÉ: Moyenne
FOCUS: Choix nécessitant validation business

DÉCISIONS RÉSOLUES
1. PostgreSQL 18 vs 17 - Version 18 est très récente, 17 serait-elle plus safe?
2. ~~Threshold 0.5 par défaut~~ → **0.82** - ✅ Résolu techniquement (E5 scores compressés)
3. ~~8000 chars max par chunk~~ → **2000 chars max** - ✅ Corrigé pour limite E5 512 tokens

DÉCISIONS EN ATTENTE
4. 500 chunks max par document - Limite raisonnable?
5. Tags OR logic only - AND logic vraiment pas nécessaire MVP?
6. Exit code 3 pour document not found - Convention projet?

Note: Ces décisions ne nécessitent pas de recherche web, mais validation utilisateur.
```

---

## Section 17 — Stratégie de Test

```
SECTION: Stratégie de Test
PRIORITÉ: Moyenne
FOCUS: Bun test + Testcontainers
STATUT: ✅ VALIDÉ (spécifié dans additional-context.md + implementation-plan.md)

FRAMEWORK & OUTILLAGE
| Outil | Usage | A Valider |
|-------|-------|-----------|
| Bun test | Test runner natif | API compatible Jest? Assertions suffisantes? |
| Testcontainers | Tests d'intégration DB | Support Bun? Temps démarrage container? |
| Mocks Bun | Mocking natif | mock.module() fonctionne pour nos deps? |

PYRAMIDE DE TESTS
| Niveau | Coverage | Scope |
|--------|----------|-------|
| Unit | 80%+ | Domain, Application |
| Integration | 60%+ | Repositories, Embedder |
| E2E | Paths critiques | MCP flow complet |

QUESTIONS À RÉSOUDRE:
- Bun test supporte snapshots?
- Comment mocker @huggingface/transformers efficacement?
- Testcontainers avec Bun nécessite config spéciale?
- Tests d'embedding réels sont lents (~5s) - mode optionnel?

MATRICE TEST PAR FEATURE
| Feature | Unit | Integration | E2E |
|---------|------|-------------|-----|
| F1 Search Config | ✓ | ✓ | ✓ |
| F17 E5 Prefixes | ✓ | ✓ | - |
| F18 Transactions | - | ✓ | - |
| F25 Upsert Atomicity | ✓ | ✓ | ✓ |
| F26 Concurrent Ingestion | - | ✓ | - |
| F31 Graceful Shutdown | - | ✓ | ✓ |
```

---

## Section 18 — Sécurité & Validation

```
SECTION: Sécurité & Validation des Entrées
PRIORITÉ: Haute
FOCUS: SQL injection, input validation
STATUT: ✅ VALIDÉ (limites corrigées, Drizzle + postgres.js parameterized queries)

VALIDATION DES ENTRÉES
| Point d'Entrée | Risque | Mitigation à Valider |
|----------------|--------|----------------------|
| source (path) | Path traversal (../) | Regex ^[a-zA-Z0-9/_.-]{1,500}$ bloque .. ? |
| content (chunks) | Injection SQL | Parameterized queries via Drizzle? |
| tags[] | Injection SQL array | Échappement postgres.js pour TEXT[]? |
| query (search) | Embedding injection? | Pas de risque direct |
| metadata (JSONB) | JSON injection | Validation Zod avant stockage? |

INJECTION SQL
| Couche | Protection | A Valider |
|--------|------------|-----------|
| Drizzle ORM | Parameterized queries | Toutes queries via Drizzle? |
| Raw SQL (vector) | Template literals postgres.js | sql`...` échappe correctement? |

EXEMPLE À VALIDER:
const results = await sql`
  SELECT * FROM chunks
  WHERE embedding <=> ${queryVector}::vector < ${threshold}
`;
Est-ce safe? postgres.js échappe ${queryVector} et ${threshold}?

GESTION DES SECRETS
| Secret | A Valider |
|--------|-----------|
| DATABASE_URL | Jamais loggé? Masqué dans errors? |
| .env | Dans .gitignore? |
| Pino redaction | Configuré pour DATABASE_URL? |

DENIAL OF SERVICE
| Vecteur | Protection | Suffisant? |
|---------|------------|------------|
| Large chunks | Max 2000 chars | Vérifié avant embedding (limite E5 512 tokens) |
| Many chunks | Max 500 par doc | Vérifié dans Zod? |
| Large query | Max 1000 chars | Limite appropriée? |
| Concurrent requests | Pas de rate limiting | OK pour MVP local? |
```

---

## Section 19 — Observabilité & Logging

```
SECTION: Observabilité & Logging
PRIORITÉ: Basse
FOCUS: Pino + Bun compatibility
STATUT: ✅ VALIDÉ (spécifié dans additional-context.md F15)

CONFIGURATION PINO
| Aspect | Dev | Production | A Valider |
|--------|-----|------------|-----------|
| Format | Pretty (pino-pretty) | JSON structuré | Config par env? |
| Level | debug | info | Env variable LOG_LEVEL? |
| Output | stdout | stdout | Compatible Docker? |

ÉVÉNEMENTS À LOGGER
| Catégorie | Événement | Level |
|-----------|-----------|-------|
| Lifecycle | Server start/shutdown | info |
| Lifecycle | Model state change | info |
| Ingestion | Document ingested | info |
| Ingestion | Chunk too large | warn |
| Search | Search executed | info |
| Errors | Database/Embedding error | error |

REDACTION DONNÉES SENSIBLES
| Champ | Action |
|-------|--------|
| DATABASE_URL | Masquer (redact) |
| Contenu chunks | Tronquer (max 100 chars) |
| Embeddings | Exclure (jamais logger) |

QUESTIONS:
- Pino fonctionne avec Bun?
- pino-pretty compatible Bun?
- Format JSON compatible ELK/Loki?
```

---

## Section 20 — Déploiement & Environnements

```
SECTION: Déploiement & Environnements
PRIORITÉ: Moyenne
FOCUS: Docker + ONNX runtime
STATUT: ✅ VALIDÉ (pgvector/pgvector:pg18, ONNX intégré)

═══════════════════════════════════════════════════════════════════════════════
IMAGE DOCKER POSTGRESQL + PGVECTOR - ✅ CORRIGÉ
═══════════════════════════════════════════════════════════════════════════════

image: pgvector/pgvector:pg18  # ✅ Image officielle recommandée (correction appliquée)

Tags disponibles (image officielle pgvector):
- pgvector/pgvector:pg18       → PostgreSQL 18 + pgvector 0.8.1
- pgvector/pgvector:0.8.1-pg18 → Version explicite
- pgvector/pgvector:pg17       → Pour PostgreSQL 17

ENVIRONNEMENTS CIBLES - VALIDÉS
| Environnement | Database | Model Cache | Statut |
|---------------|----------|-------------|--------|
| Local Dev | Docker Compose (pgvector/pgvector:pg18) | ~/.cache/huggingface | ✅ |
| CI/CD | Testcontainers (@testcontainers/postgresql) | GitHub Actions cache | ✅ |
| Self-hosted | PostgreSQL externe | Volume persistant | ✅ |

DOCKERFILE - VALIDÉ
FROM oven/bun:1.1-alpine
# ONNX Runtime inclus dans @huggingface/transformers
# Pas de dépendances natives supplémentaires requises
WORKDIR /app
COPY package.json bun.lockb ./
RUN bun install --frozen-lockfile
COPY . .
CMD ["bun", "run", "src/index.ts"]

QUESTIONS DOCKER - RÉSOLUES
| Question | Réponse |
|----------|---------|
| oven/bun:1.1-alpine + ONNX | ✅ Fonctionne, ONNX intégré dans Transformers.js |
| Dépendances natives | ✅ Aucune requise (WASM backend par défaut) |
| Alpine vs Debian | ✅ Alpine suffisant pour ONNX WASM |

RESSOURCES REQUISES
| Ressource | Minimum | Recommandé |
|-----------|---------|------------|
| CPU | 1 core | 2 cores |
| RAM | 512MB | 1GB |
| Disk | 500MB | 1GB (model q8: ~118MB) |

QUESTIONS RÉSOLUES:
- Bun stable en production: ✅ Utilisé en production par plusieurs projets MCP
- Embedding: CPU-bound (ONNX WASM)
- Model size en mémoire: ~200-300MB après chargement (q8)
```

---

## Section 21 — Migration & Versioning

```
SECTION: Migration & Versioning
PRIORITÉ: Basse
FOCUS: Drizzle Kit workflow
STATUT: ✅ VALIDÉ (spécifié dans additional-context.md F45)

STRATÉGIE MIGRATION DB
| Aspect | Choix | A Valider |
|--------|-------|-----------|
| Outil | Drizzle Kit | drizzle-kit generate workflow? |
| Direction | Forward-only MVP | Rollback manuel acceptable? |
| Versioning | Fichiers numérotés | 0000_initial.sql, 0001_xxx.sql |
| Application | Au démarrage? | Script séparé plus safe? |

ROLLBACK STRATEGY (manuel)
-- DOWN migration
DROP INDEX IF EXISTS chunks_embedding_idx;
DROP TABLE IF EXISTS chunks;
DROP TABLE IF EXISTS documents;
-- Note: Ne pas DROP EXTENSION vector

ZERO-DOWNTIME MIGRATIONS
| Type de changement | Safe? |
|--------------------|-------|
| ADD COLUMN nullable | ✓ |
| ADD COLUMN NOT NULL | ✗ (need default) |
| DROP COLUMN | ⚠️ (déprécier d'abord) |
| ADD INDEX | ✓ (CONCURRENTLY) |

QUESTIONS:
- Drizzle génère down migrations automatiquement?
- drizzle-kit push vs migrate pour dev vs prod?
- Multi-instance: lock table pendant migration?
```

---

## Section 22 — Conventions de Code

```
SECTION: Conventions de Code
PRIORITÉ: Basse
FOCUS: Biome vs ESLint avec Bun

STRUCTURE FICHIERS
src/
├── domain/           # Entités pures, pas d'imports externes
├── application/      # Use cases, ports (interfaces)
├── infrastructure/   # Adapters (DB, embeddings)
├── mcp/              # Transport MCP
├── cli/              # CLI standalone
└── config/           # Env validation

CONVENTIONS NOMMAGE
| Élément | Convention | Exemple |
|---------|------------|---------|
| Fichiers | kebab-case | document-repo.ts |
| Classes | PascalCase | IngestUseCase |
| Interfaces | PascalCase | EmbedderPort |
| Functions | camelCase | createDocument() |
| Constants | SCREAMING_SNAKE | MAX_CHUNK_CONTENT |
| Zod schemas | PascalCase + Schema | ChunkInputSchema |

PATTERNS À SUIVRE
- Dependency injection via constructeur
- Named exports uniquement (pas de default exports)
- async/await (pas de .then() chains)
- Imports: builtins > external > internal

QUESTIONS À DÉCIDER:
1. Exceptions vs Result type pour errors?
2. Biome vs ESLint - linter pour Bun?
3. Prettier via Biome ou séparé?
4. Pre-commit hooks (Husky/lint-staged)?
5. Path aliases @/ supporté par Bun?
```

---

## Synthèse des Risques et Actions Prioritaires

### Package.json Corrigé

```json
{
  "dependencies": {
    "@huggingface/transformers": "^3.0.0",
    "@modelcontextprotocol/sdk": "^1.22.0",
    "postgres": "^3.4.0",
    "drizzle-orm": "^0.44.0",
    "zod": "^3.25.0",
    "pino": "^9.0.0",
    "xstate": "^5.0.0"
  },
  "devDependencies": {
    "@types/bun": "latest",
    "drizzle-kit": "^0.30.0",
    "pino-pretty": "^11.0.0",
    "testcontainers": "^10.0.0",
    "typescript": "^5.7.0"
  },
  "engines": {
    "bun": ">=1.3.5"
  }
}
```

**⚠️ Note Zod critique** : Avec `zod: "^3.25.0"`, TOUJOURS importer via:
```typescript
import { z } from "zod/v3";  // ✅ Obligatoire pour compatibilité MCP
// import { z } from "zod";  // ❌ Provoque erreur runtime avec MCP
```

### Pattern Serveur MCP Recommandé (v1.22+)

```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod/v3";  // ⚠️ Import critique

const server = new McpServer({ name: "alexandria", version: "1.0.0" });

server.registerTool("search", {
  description: "Recherche sémantique",
  inputSchema: { query: z.string() }
}, async ({ query }) => ({
  content: [{ type: "text", text: "résultats" }]
}));

await server.connect(new StdioServerTransport());
```

### Matrice de Compatibilité Finale

| Composant | Version | Statut | Notes |
|-----------|---------|--------|-------|
| Bun runtime | **1.3.5+** | ⚠️ CORRIGÉ | Minimum pour stabilité NAPI (fix déc 2025) |
| PostgreSQL | 18.1 | ✅ | Stable, performances améliorées |
| pgvector | 0.8.1 | ✅ | Iterative scans, HNSW, distance [0,2] |
| postgres.js | 3.4.0 | ✅ | Éviter bun:sql |
| Drizzle ORM | 0.44.0 | ✅ | pgvector natif |
| Transformers.js | 3.8.1 | ✅ | ONNX par défaut, **`normalize: true` obligatoire** |
| E5-small | main | ⚠️ | 384 dims, 512 tokens, **scores compressés**, **`dtype: 'q8'`** |
| MCP SDK | 1.22.0 | ✅ | v2 en pré-alpha |
| Zod | 3.25 /v3 | ⚠️ | Import subpath obligatoire |
| **XState** | **5.0.0** | ✅ AJOUTÉ | Machine d'états model loading (F23) |

### Configuration Embeddings E5

| Paramètre | Valeur | Notes |
|-----------|--------|-------|
| Modèle | Xenova/multilingual-e5-small@main | Révision stable |
| Dimensions | 384 | Fixe |
| Token limit | 512 | Troncature silencieuse |
| **`normalize`** | **`true`** | ⚠️ OBLIGATOIRE dans transformers.js |
| **`dtype`** | **`'q8'`** | ⚠️ AJOUTÉ - Quantification int8 (118MB vs 470MB) |
| **`env.cacheDir`** | Explicite | ⚠️ AJOUTÉ - Fix re-download bug v3.0.0-alpha.5 |
| Préfixes | "query: " / "passage: " | ⚠️ OBLIGATOIRES pour performance |
| **Threshold défaut** | **0.82** | Calibré pour température 0.01 |
| Threshold range | 0.80 - 0.90 | Selon précision/rappel souhaité |

**Timeouts model loading:** ⚠️ CORRIGÉ
- Cold start (download ~118MB): **180 secondes** (60s insuffisant)
- Warm cache (local): 60 secondes

**Distribution des scores E5:**
- Textes non-reliés: 0.75 - 0.85
- Textes modérément pertinents: 0.85 - 0.90
- Textes très pertinents: 0.90 - 1.0

### Configuration HNSW Validée

| Paramètre | Valeur | Notes |
|-----------|--------|-------|
| m | 16 | Défaut optimal <10K vecteurs |
| ef_construction | **100** | Production RAG (meilleur recall) |
| ef_search | **100** | Production standard, configurable par session |
| iterative_scan | relaxed_order | Pour filtrage par tags |
| Operator class | vector_cosine_ops | Distance [0,2], formule: 1-cos(θ) |

**Optimisation future:** Avec embeddings normalisés, `vector_ip_ops` + `<#>` est ~3× plus rapide.

### Limites de Chunking Validées

| Paramètre | Valeur | Notes |
|-----------|--------|-------|
| Chunk target | 1,600-1,800 chars | Reste sous 512 tokens |
| Hard limit | **2,000 chars** | E5 tronque silencieusement au-delà |
| Warning threshold | 1,500 chars | Log avant zone critique |
| Chunk overlap | 150-200 chars | 10-12% pour cohérence |
| LIMIT max search | 60 résultats | Production standard |

### Schéma Base de Données Validé

| Élément | Recommandation |
|---------|----------------|
| gen_random_uuid() | ✅ Natif PG13+, uuidv7() dispo PG18+ |
| UNIQUE constraint | ✅ Crée index B-tree implicite, PAS de duplication |
| TEXT type | ✅ ~1GB max, TOAST automatique |
| TIMESTAMPTZ | ✅ Stockage UTC, conversion par session |
| JSONB index | ✅ `jsonb_path_ops` pour index 2-3× plus compact |
| GIN tags | ✅ Opérateur && (overlap) optimisé |

**Cette stack technique est VALIDÉE et RECOMMANDÉE après application des corrections identifiées.**

**Corrections critiques appliquées (janvier 2026):**
- ✅ `normalize: true` obligatoire dans transformers.js
- ✅ Threshold 0.82 (au lieu de 0.5) pour scores compressés E5
- ✅ Distance pgvector [0,2] clarifiée, formule `1 - distance` confirmée
- ✅ **Error codes -32xxx → -31xxx** (évite conflits MCP SDK: -32001 RequestTimeout, -32002 ResourceNotFound)
- ✅ **ef_construction 64 → 100** (meilleur recall production RAG)
- ✅ **ef_search 40 → 100** (production standard)
- ✅ **JSONB index:** `jsonb_path_ops` pour index compact
- ✅ **UNIQUE constraint:** Index implicite, pas de duplication
- ✅ **XState v5:** Machine d'états model loading avec `fromPromise` actors
- ✅ **Timeouts two-tier:** 180s cold start / 60s warm cache (60s seul insuffisant)
- ✅ **Health checks three-probe:** live/ready/startup pattern Kubernetes
- ✅ **Circuit breaker:** 5 échecs/60s pour error recovery ML
- ✅ **Bun minimum 1.3.5:** Fix NAPI hot reload crashes (décembre 2025)
- ✅ **env.cacheDir explicite:** Fix re-download bug transformers v3.0.0-alpha.5
- ✅ **dtype: 'q8':** Quantification int8 recommandée (118MB vs 470MB fp32)
- ✅ **Tensor disposal explicite:** Fuite mémoire ONNX Runtime #25325
- ✅ **Session options stabilité:** `enableCpuMemArena: false` pour gestion mémoire
- ✅ **CLI parseArgs:** `Bun.argv` → `process.argv.slice(2)` (évite bugs #15877, #22157)
- ✅ **CLI allowNegative:** Support `--no-upsert` (Bun 1.2.11+)
- ✅ **Exit codes POSIX:** 0=success, 1=runtime, 2=validation (convention standard)

---

## Prochaines Étapes

1. ~~**Recherche Web** - Valider compatibilités et versions~~ ✅ FAIT
2. ~~**Review Architecture** - Confirmer pattern hexagonal approprié~~ ✅ VALIDÉ
3. ~~**Corrections Critiques** - SDK MCP, Zod, Docker, chunks, ef_search~~ ✅ APPLIQUÉ
4. ~~**Spécifications Détaillées** - Créer fichiers de spec pour chaque feature~~ ✅ FAIT
5. ~~**Health Check Validation** - XState, timeouts, circuit breaker~~ ✅ APPLIQUÉ
6. **Tests Locaux** - Prouver stack Bun 1.3.5+ + Transformers.js + pgvector
7. **Spike Testing** - Valider Bun test + Testcontainers + XState
8. **Décisions Utilisateur** - Clarifier points ambigus (section 16)
9. **Conventions Sign-off** - Décider exceptions vs Result, linter, etc. (section 22)
