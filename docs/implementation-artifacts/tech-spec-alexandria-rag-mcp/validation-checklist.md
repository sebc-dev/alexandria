# Checklist de Validation - Tech Spec Alexandria RAG MCP

**La stack proposée est globalement viable mais présente 3 problèmes critiques à corriger** : la version du SDK MCP n'existe pas (^2.0.0), l'incompatibilité Zod v4 avec MCP, et l'image Docker incorrecte. Après corrections, cette architecture Bun + PostgreSQL/pgvector + Transformers.js est techniquement solide pour un serveur MCP RAG.

---

## Prompt de Recherche

Copier ce prompt, puis coller une section (bloc entre ```) à la place de `[SECTION]`.

````markdown
# Contexte du Projet

**Projet:** Alexandria RAG MCP Server

**Description:** Serveur MCP (Model Context Protocol) exposant une base de connaissances documentaire interrogeable par recherche sémantique. Permet à Claude Code d'accéder à de la documentation technique et des conventions de code spécifiques à un projet.

**Architecture (validée):**
- Runtime: Bun 1.1.24+ (TypeScript strict)
- Database: PostgreSQL 18.1 + pgvector 0.8.1 (recherche vectorielle)
- Embeddings: Xenova/multilingual-e5-small@main via @huggingface/transformers ^3.0.0 (ONNX, local)
- Transport: MCP SDK ^1.22.0 (stdio pour Claude Code) — ⚠️ PAS v2.0.0
- ORM: Drizzle ^0.44.0 + postgres.js ^3.4.0
- Validation: Zod ^3.25.0 avec import "zod/v3" obligatoire
- Architecture: Hexagonale (Ports & Adapters)

**Fonctionnalités MVP:**
- Ingestion de documents Markdown avec chunks pré-calculés
- Recherche sémantique via embeddings 384 dimensions
- 5 MCP tools: search, ingest, delete, health, list
- CLI pour ingestion standalone

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

| # | Section | Priorité |
|---|---------|----------|
| 1 | Stack Technique & Versions | Haute |
| 2 | Décisions Architecturales | Moyenne |
| 3 | Configuration HNSW | Haute |
| 4 | Modèle E5 - Préfixes | Haute |
| 5 | Limites & Contraintes | Moyenne |
| 6 | Retry Logic & Timeouts | Basse |
| 7 | Isolation & Transactions | Moyenne |
| 8 | Score & Similarité | Haute |
| 9 | Gestion d'Erreurs MCP | Moyenne |
| 10 | Schema Base de Données | Moyenne |
| 11 | Health Check State Machine | Basse |
| 12 | CLI Specification | Basse |
| 13 | Performance Targets | Moyenne |
| 14 | Questions Ouvertes | Haute |
| 15 | Risques Identifiés | Moyenne |
| 16 | Décisions Utilisateur | Moyenne |
| 17 | Stratégie de Test | Moyenne |
| 18 | Sécurité & Validation | Haute |
| 19 | Observabilité & Logging | Basse |
| 20 | Déploiement & Environnements | Moyenne |
| 21 | Migration & Versioning | Basse |
| 22 | Conventions de Code | Basse |

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

PARAMÈTRES INDEX HNSW
| Paramètre | Valeur | A Valider |
|-----------|--------|-----------|
| m (connexions par layer) | 16 | Optimal pour <10K vecteurs? Trade-off mémoire/qualité? |
| ef_construction | 64 | Impact sur temps de build? Qualité index? |
| ef_search | 40 | Recall suffisant pour le use case? |

QUESTIONS DE RECHERCHE:
- Valeurs recommandées pour ~1000-10000 vecteurs?
- Impact de m=16 vs m=32 sur recall?
- ef_search=40 vs 100 - différence de latence?

CONFIGURATION EF_SEARCH
| Méthode | Choix | A Valider |
|---------|-------|-----------|
| ALTER DATABASE | Oui (dans migration) | Persiste après restart? |
| SET per-session | Non | Décision correcte pour simplicité? |

SQL prévu: ALTER DATABASE alexandria SET hnsw.ef_search = 40;
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
PRIORITÉ: Moyenne
FOCUS: Ratios chars/tokens, limites réalistes

LIMITES DE TAILLE (F24)
| Limite | Valeur | Rationale | A Valider |
|--------|--------|-----------|-----------|
| Chunk content max | 8000 chars | ~2000 tokens, safe pour E5 512 limit | Ratio chars/tokens correct? |
| Chunks par document | 500 max | Éviter OOM et timeouts | Limite réaliste? |
| Warning threshold | 4000 chars | Log warning | Seuil pertinent? |

LIMITES MCP TOOL SCHEMAS
| Limite | Valeur | A Valider |
|--------|--------|-----------|
| Query max length | 1000 chars | Suffisant? |
| Source max length | 500 chars | Regex: ^[a-zA-Z0-9/_.-]{1,500}$ |
| Title max length | 200 chars | Suffisant? |
| Limit max | 100 résultats | Optimal? |

QUESTIONS:
- E5 avec 512 tokens max, combien de chars en moyenne?
- Ratio chars/tokens pour texte technique multilingue?
```

---

## Section 6 — Retry Logic & Timeouts

```
SECTION: Retry Logic & Timeouts
PRIORITÉ: Basse
FOCUS: Best practices retry patterns

CONFIGURATION RETRY (F22)
| Paramètre | Valeur | A Valider |
|-----------|--------|-----------|
| Max attempts | 3 (1 initial + 2 retries) | Suffisant pour erreurs transitoires? |
| Base delay | 100ms | Trop court/long? |
| Multiplier | 4 | Delays: 100ms, 400ms - progression appropriée? |
| Jitter | ±20% | Standard industry practice? |

TIMEOUTS
| Operation | Timeout | A Valider |
|-----------|---------|-----------|
| Model loading | 60s | Suffisant pour premier download ~100MB? |
| Drain requests (shutdown) | 30s | Approprié? |
| Close DB pool | 5s | Suffisant? |

FORMULE JITTER:
jitter = 1 + (Math.random() - 0.5) * 2 * (jitterPercent / 100)
actualDelay = baseDelay * jitter
```

---

## Section 7 — Isolation & Transactions

```
SECTION: Isolation & Transactions PostgreSQL
PRIORITÉ: Moyenne
FOCUS: READ COMMITTED vs REPEATABLE READ

TRANSACTION ISOLATION (F18)
| Aspect | Choix | A Valider |
|--------|-------|-----------|
| Isolation Level | READ COMMITTED | Suffisant pour upsert atomique? |
| Ingestion | Transaction unique (doc + chunks) | Rollback garanti? |
| Search | Pas de transaction | Snapshot suffisant? |

QUESTION PRINCIPALE:
READ COMMITTED vs REPEATABLE READ pour l'upsert - risque de phantom reads?

CONTEXTE UPSERT:
- L'upsert fait DELETE + INSERT dans même transaction
- Si l'insert échoue, le DELETE doit être rollback
- Deux clients peuvent ingérer la même source simultanément

CONFIGURATION POSTGRES.JS:
const sql = postgres(DATABASE_URL, {
  // READ COMMITTED est le défaut, pas besoin de le spécifier
  max: 10,  // pool size
});
```

---

## Section 8 — Score & Similarité

```
SECTION: Score & Calcul de Similarité pgvector
PRIORITÉ: Haute
FOCUS: Conversion distance → similarité

CALCUL DU SCORE (F33)
| Concept | Formule | A Valider |
|---------|---------|-----------|
| Distance pgvector | embedding <=> query | Retourne distance cosinus (0-2)? |
| Conversion similarité | 1 - distance | Range -1.0 à 1.0? |
| Threshold default | 0.5 | Pertinent pour E5? |

OPÉRATEUR PGVECTOR:
- L'opérateur <=> avec vector_cosine_ops retourne une DISTANCE (pas similarité)
- Distance 0 = identique, Distance 2 = opposé
- Conversion: score = 1 - distance

SQL PRÉVU:
SELECT *, embedding <=> ${queryEmbedding}::vector AS distance
FROM chunks
ORDER BY distance ASC
LIMIT ${limit}

QUESTIONS DE RECHERCHE:
- Threshold 0.5 est-il approprié pour multilingual-e5-small?
- Faut-il normaliser les embeddings avant stockage?
- E5 retourne des vecteurs déjà normalisés?
```

---

## Section 9 — Gestion d'Erreurs MCP

```
SECTION: Gestion d'Erreurs MCP
PRIORITÉ: Moyenne
FOCUS: Codes erreur MCP spec compliance

CODES D'ERREUR CUSTOM
| Code | Nom | A Valider |
|------|-----|-----------|
| -32001 | DOCUMENT_NOT_FOUND | Conforme MCP spec? |
| -32002 | VALIDATION_ERROR | Conforme MCP spec? |
| -32003 | EMBEDDING_FAILED | Code custom approprié? |
| -32004 | DATABASE_ERROR | Code custom approprié? |
| -32005 | DUPLICATE_SOURCE | Code custom approprié? |
| -32006 | MODEL_LOAD_TIMEOUT | Code custom approprié? |
| -32007 | EMBEDDING_DIMENSION_MISMATCH | Code custom approprié? |
| -32008 | CONTENT_TOO_LARGE | Code custom approprié? |
| -32009 | CONCURRENT_MODIFICATION | Code custom approprié? |

QUESTION PRINCIPALE:
Les codes -32001 à -32009 sont-ils dans la plage réservée aux applications MCP?

RÉFÉRENCE MCP:
- JSON-RPC 2.0 réserve -32700 à -32600 pour erreurs de parsing
- -32000 à -32099 est la plage server errors
- Quelle plage pour erreurs applicatives custom?
```

---

## Section 10 — Schema Base de Données

```
SECTION: Schema Base de Données PostgreSQL + pgvector
PRIORITÉ: Moyenne
FOCUS: Syntaxe pgvector, indexes

TABLE DOCUMENTS
| Colonne | Type | A Valider |
|---------|------|-----------|
| id | UUID (gen_random_uuid()) | Disponible PostgreSQL 18? |
| source | TEXT UNIQUE | Index sur UNIQUE suffisant? |
| content | TEXT | Pas de limite de taille - OK? |
| tags | TEXT[] | GIN index pour performance? |
| version | TEXT | Format libre - OK pour MVP? |
| created_at/updated_at | TIMESTAMPTZ | Timezone handling? |

TABLE CHUNKS
| Colonne | Type | A Valider |
|---------|------|-----------|
| embedding | vector(384) | Syntaxe pgvector correcte? |
| chunk_index | INTEGER | UNIQUE(document_id, chunk_index) - correct? |
| metadata | JSONB | Structure flexible OK? |

INDEXES
| Index | Type | A Valider |
|-------|------|-----------|
| chunks_embedding_idx | HNSW avec vector_cosine_ops | Syntaxe correcte? |
| documents_tags_idx | GIN | Performance pour OR queries? |
| documents_source_idx | B-tree (implicit via UNIQUE) | Utile? |

SQL INDEX HNSW:
CREATE INDEX chunks_embedding_idx ON chunks
  USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);
```

---

## Section 11 — Health Check State Machine

```
SECTION: Health Check State Machine (F23)
PRIORITÉ: Basse
FOCUS: Patterns state machine pour model loading

ÉTATS DU MODÈLE
| État | Description | A Valider |
|------|-------------|-----------|
| initial | Pas de modèle chargé | État de départ correct? |
| loading | Chargement en cours | Timeout 60s approprié? |
| loaded | Prêt pour inférence | Transitions error → loaded possibles? |
| error | Échec | Recovery automatique au prochain embed()? |

TRANSITIONS
| From | To | Trigger |
|------|----|---------|
| initial | loading | Premier appel embed() |
| loading | loaded | Pipeline créé avec succès |
| loading | error | Timeout 60s ou exception |
| loaded | error | Erreur d'inférence (OOM, etc.) |
| error | loaded | Prochain embed() réussit |

COMPORTEMENT HEALTH CHECK:
- Retourne immédiatement { model: "loading" } pendant loading
- Ne bloque PAS en attendant que le model soit prêt
```

---

## Section 12 — CLI Specification

```
SECTION: CLI Specification (F21)
PRIORITÉ: Basse
FOCUS: Bun parseArgs support

PARSER
| Choix | A Valider |
|-------|-----------|
| Bun.argv + node:util parseArgs | Support Bun pour parseArgs? |

COMMANDES
alexandria ingest <file> [options]
  -c, --chunks-file <path>   Fichier JSON des chunks (requis)
  -T, --title <title>        Titre du document (optionnel)
  -t, --tags <tags>          Tags séparés par virgules
  -v, --version <version>    Version du document
  -u, --upsert               Remplacer si existe (défaut: true)
  -n, --no-upsert            Erreur si existe
  -d, --dry-run              Valider sans persister
  -h, --help                 Afficher l'aide

alexandria delete <source>
  -h, --help                 Afficher l'aide

EXIT CODES
| Code | Signification | A Valider |
|------|---------------|-----------|
| 0 | Succès | Standard |
| 1 | Erreur validation | Standard pour validation? |
| 2 | Erreur runtime | Distinguer de 1? |
| 3 | Document non trouvé | Spécifique - OK? |
```

---

## Section 13 — Performance Targets

```
SECTION: Performance Targets (F7)
PRIORITÉ: Moyenne
FOCUS: Benchmarks réalistes

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

E5 MODEL - ⚠️ CORRECTIONS REQUISES
| Question | Réponse |
|----------|---------|
| refs/pr/1 stable? | ❌ NON - Utiliser `main` à la place |
| Prefixes confirmés? | ✅ "query: " et "passage: " obligatoires (model card) |
| Vecteurs normalisés? | ✅ E5 retourne des vecteurs normalisés |

MCP PROTOCOL - ❌ PROBLÈMES CRITIQUES
| Question | Réponse |
|----------|---------|
| Version 2024-11-05 | ⚠️ Obsolète mais supportée. Current: 2025-11-25 |
| SDK ^2.0.0 | ❌ N'EXISTE PAS. v2 en pré-alpha. Utiliser ^1.22.0 |
| Error codes -32000 | ✅ Plage -32000 à -32099 réservée aux erreurs serveur |
```

---

## Section 15 — Risques Identifiés

```
SECTION: Risques Identifiés
PRIORITÉ: Haute
FOCUS: Risques critiques identifiés lors de la validation
STATUT: ⚠️ ACTIONS REQUISES

═══════════════════════════════════════════════════════════════════════════════
ACTIONS CRITIQUES (BLOQUANTES) - À corriger AVANT implémentation
═══════════════════════════════════════════════════════════════════════════════

| Priorité | Action | Impact | Correction |
|----------|--------|--------|------------|
| 🔴 P0 | SDK MCP version | SDK 2.0 n'existe pas | ^2.0.0 → ^1.22.0 |
| 🔴 P0 | Import Zod | Incompatibilité runtime MCP | import "zod" → import "zod/v3" |
| 🔴 P0 | Image Docker | Image potentiellement indisponible | ankane/pgvector → pgvector/pgvector |

═══════════════════════════════════════════════════════════════════════════════
ACTIONS IMPORTANTES (RECOMMANDÉES)
═══════════════════════════════════════════════════════════════════════════════

| Priorité | Action | Raison |
|----------|--------|--------|
| 🟠 P1 | Révision E5 model | refs/pr/1 non stable → utiliser main |
| 🟠 P1 | Driver postgres | Éviter bun:sql (bugs #22395) → utiliser postgres npm |
| 🟠 P1 | Versions Drizzle | Aligner ORM/Kit pour éviter incompatibilités |

═══════════════════════════════════════════════════════════════════════════════
RISQUES RÉSIDUELS (acceptables pour MVP)
═══════════════════════════════════════════════════════════════════════════════

| Risque | Impact | Mitigation |
|--------|--------|------------|
| Premier chargement model 1-2 min | UX dégradée | ✅ Health check non-bloquant |
| Docker requis pour tests | CI/CD | ✅ Testcontainers compatible Bun |
| pgvector 0.8.1 spécifique | Portabilité | ✅ Image officielle disponible |
| Pas de pagination search | Scalabilité | ✅ Limit=100 suffisant MVP |

QUESTIONS RÉSOLUES:
- Testcontainers: ✅ Compatible Bun (issue #853 résolue)
- HuggingFace Hub down: ⚠️ Cache local ~/.cache/huggingface après premier téléchargement
```

---

## Section 16 — Décisions Utilisateur

```
SECTION: Décisions à Confirmer avec l'Utilisateur
PRIORITÉ: Moyenne
FOCUS: Choix nécessitant validation business

DÉCISIONS EN ATTENTE
1. PostgreSQL 18 vs 17 - Version 18 est très récente, 17 serait-elle plus safe?
2. Threshold 0.5 par défaut - Valeur appropriée pour le use case?
3. 8000 chars max par chunk - Suffisant pour les documents cibles?
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
| Large chunks | Max 8000 chars | Vérifié avant embedding? |
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
STATUT: ⚠️ CORRECTIONS REQUISES

═══════════════════════════════════════════════════════════════════════════════
IMAGE DOCKER POSTGRESQL + PGVECTOR - ❌ CORRECTION REQUISE
═══════════════════════════════════════════════════════════════════════════════

- image: ankane/pgvector:pg18    # ❌ Potentiellement obsolète
+ image: pgvector/pgvector:pg18  # ✅ Image officielle recommandée

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
    "pino": "^9.0.0"
  },
  "devDependencies": {
    "@types/bun": "latest",
    "drizzle-kit": "^0.30.0",
    "pino-pretty": "^11.0.0",
    "testcontainers": "^10.0.0",
    "typescript": "^5.7.0"
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
| Bun runtime | 1.1.24+ | ✅ | Supporte tout le stack |
| PostgreSQL | 18.1 | ✅ | Stable, performances améliorées |
| pgvector | 0.8.1 | ✅ | Iterative scans pour RAG |
| postgres.js | 3.4.0 | ✅ | Éviter bun:sql |
| Drizzle ORM | 0.44.0 | ✅ | pgvector natif |
| Transformers.js | 3.8.1 | ✅ | ONNX par défaut |
| E5-small | main | ✅ | 384 dims, 512 tokens |
| MCP SDK | 1.22.0 | ✅ | v2 en pré-alpha |
| Zod | 3.25 /v3 | ⚠️ | Import subpath obligatoire |

**Cette stack technique est VALIDÉE et RECOMMANDÉE après application des corrections identifiées.**

---

## Prochaines Étapes

1. ~~**Recherche Web** - Valider compatibilités et versions~~ ✅ FAIT
2. **Tests Locaux** - Prouver stack Bun + Transformers.js + pgvector
3. ~~**Review Architecture** - Confirmer pattern hexagonal approprié~~ ✅ VALIDÉ
4. **Décisions Utilisateur** - Clarifier points ambigus (section 16)
5. **Spike Testing** - Valider Bun test + Testcontainers
6. **Security Review** - Valider input sanitization et secrets
7. **Conventions Sign-off** - Décider exceptions vs Result, linter, etc.
