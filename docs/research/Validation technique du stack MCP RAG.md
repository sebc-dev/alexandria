# Validation technique du stack MCP RAG "Project Alexandria"

**La stack proposée est globalement viable mais présente 3 problèmes critiques à corriger** : la version du SDK MCP n'existe pas (^2.0.0), l'incompatibilité Zod v4 avec MCP, et l'image Docker incorrecte. Après corrections, cette architecture Bun + PostgreSQL/pgvector + Transformers.js est techniquement solide pour un serveur MCP RAG.

---

## Runtime Bun : pleinement compatible

### Bun + @huggingface/transformers — ✅ Validé

Transformers.js v3 supporte officiellement Bun depuis octobre 2024. HuggingFace maintient un exemple officiel Bun dans leur dépôt. **Attention** : utiliser `@huggingface/transformers` (v3+), PAS l'ancien package `@xenova/transformers` (v2.x) qui causait des problèmes.

**Recommandation** : `"@huggingface/transformers": "^3.0.0"` (dernière version : **3.8.1**)

### Bun + @modelcontextprotocol/sdk — ✅ Validé

Plusieurs implémentations production démontrent la compatibilité : `bun-mcp`, `mcp-bun`. Le SDK fonctionne avec Bun via le transport stdio. Pour HTTP, préférer Streamable HTTP aux solutions Express-based.

### Support ESM natif — ✅ Validé

Bun supporte nativement les ES Modules sans configuration. Top-level `await` et dynamic `import()` fonctionnent. Mixage ESM/CommonJS possible dans le même projet.

### TypeScript strict mode — ✅ Validé

`bun init` génère automatiquement un tsconfig.json avec `"strict": true`. Installer `@types/bun` pour les définitions de types Bun.

---

## Base de données : corrections nécessaires

### PostgreSQL 18 — ✅ Validé

PostgreSQL **18.1** est la version stable actuelle, publiée le **13 novembre 2025**. PostgreSQL 18 apporte :

- Nouveau sous-système async I/O avec **jusqu'à 3× d'amélioration** des performances
- Support natif UUIDv7 (`uuidv7()`)
- Authentification OAuth 2.0
- Colonnes générées virtuelles par défaut

### pgvector 0.8.1 — ✅ Validé

Version **0.8.1** publiée le 4 septembre 2025, compatible PostgreSQL 18. Fonctionnalités clés :

| Feature | Détail |
|---------|--------|
| Index HNSW | Meilleure performance requêtes, recommandé pour RAG |
| Index IVFFlat | Build plus rapide, moins de mémoire |
| Dimensions max | **16,000** stockage / **2,000** indexées |
| Iterative scans | Résout le problème d'over-filtering |

### Image Docker — ⚠️ Correction requise

```diff
- image: ankane/pgvector:pg18    # ❌ Potentiellement obsolète
+ image: pgvector/pgvector:pg18  # ✅ Image officielle recommandée
```

L'image officielle est maintenant sous l'organisation `pgvector`. Tags disponibles :
- `pgvector/pgvector:pg18` — PostgreSQL 18 + pgvector 0.8.1
- `pgvector/pgvector:0.8.1-pg18` — Version explicite

### postgres.js ^3.4.0 — ✅ Validé avec réserves

Le repo officiel postgres.js confirme : support "Node.js, Deno, **Bun** and CloudFlare". Fonctionne out-of-the-box.

**⚠️ Attention** : Éviter `bun:sql` (driver natif Bun) en production — plusieurs issues ouvertes (hangs après violations de contraintes, #22395). Utiliser `postgres` npm package.

**Support pgvector** : Via le package `pgvector` npm :
```typescript
import pgvector from 'pgvector';
import postgres from 'postgres';

const sql = postgres(process.env.DATABASE_URL);
await sql`INSERT INTO items ${sql([{embedding: pgvector.toSql([1,2,3])}], 'embedding')}`;
```

### Drizzle ORM ^0.38.0 — ✅ Validé

Support pgvector **natif depuis v0.31.0**. Types disponibles : `vector`, `halfvec`, `bit`, `sparsevec`. Fonctions de distance intégrées : `l2Distance`, `cosineDistance`, `innerProduct`.

```typescript
import { pgTable, serial, vector } from 'drizzle-orm/pg-core';
import { l2Distance } from 'drizzle-orm';

const items = pgTable('items', {
  id: serial('id').primaryKey(),
  embedding: vector('embedding', { dimensions: 384 })
});
```

**Dernière version stable** : 0.45.1 (janvier 2026)

### Drizzle Kit ^0.30.0 — ⚠️ Attention versioning

Assurer la compatibilité des versions Drizzle ORM / Drizzle Kit. Breaking change v0.30.0 : suppression automatique des `IF NOT EXISTS` dans les DDL. **Recommandation** : upgrader ensemble vers 0.44.x/0.45.x pour les deux packages.

---

## Embeddings : révision du modèle à corriger

### @huggingface/transformers ^3.0.0 — ✅ Validé

Version stable actuelle : **3.8.1**. ONNX Runtime intégré par défaut. Options de quantification disponibles : fp32, fp16, q8, q4.

### Xenova/multilingual-e5-small — ⚠️ Correction révision

```diff
- revision: "refs/pr/1"  # ❌ Non recommandé
+ revision: "main"       # ✅ Branche par défaut, ONNX optimisé pour v3
```

La révision `refs/pr/1` n'est pas une référence stable. La branche `main` contient les poids ONNX optimisés pour Transformers.js v3.

### Spécifications du modèle — ✅ Confirmé

| Paramètre | Valeur confirmée |
|-----------|------------------|
| Dimensions embedding | **384** |
| Limite tokens | **512** |
| Taille ONNX (q8) | **118 MB** |
| Taille ONNX (fp32) | **470 MB** |

### Prefixes E5 — ✅ Confirmé et important

Les prefixes **comptent contre la limite de 512 tokens** et sont **obligatoires** pour des performances optimales :

- **Documents/passages** : `"passage: "` + texte
- **Requêtes** : `"query: "` + texte

Citation du model card : *"Do I need to add the prefix? **Yes, this is how the model is trained, otherwise you will see a performance degradation.**"*

---

## MCP Protocol : problème critique détecté

### Protocol version 2024-11-05 — ⚠️ Obsolète

```diff
- protocolVersion: "2024-11-05"  # ⚠️ Version initiale, toujours supportée
+ protocolVersion: "2025-11-25"  # ✅ Version actuelle (1er anniversaire MCP)
```

La version **2025-11-25** apporte : Tasks primitives, Extensions framework, OAuth 2.1 amélioré. La 2024-11-05 reste supportée pour rétrocompatibilité.

### @modelcontextprotocol/sdk ^2.0.0 — ❌ PROBLÈME CRITIQUE

```diff
- "@modelcontextprotocol/sdk": "^2.0.0"  # ❌ N'EXISTE PAS
+ "@modelcontextprotocol/sdk": "^1.22.0" # ✅ Version stable production
```

**v2.0.0 est en pré-alpha sur la branche main**. Release stable v2 prévue Q1 2026. Utiliser impérativement v1.x en production.

**Pattern serveur recommandé (v1.22+)** :
```typescript
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

const server = new McpServer({ name: "alexandria", version: "1.0.0" });

server.registerTool("search", {
  description: "Recherche sémantique",
  inputSchema: { query: z.string() }
}, async ({ query }) => ({
  content: [{ type: "text", text: "résultats" }]
}));

await server.connect(new StdioServerTransport());
```

### Transport stdio — ✅ Validé

stdio reste pleinement supporté pour Claude Code. Configuration :
```bash
claude mcp add --transport stdio alexandria -- bun run ./src/index.ts
```

---

## Autres dépendances : incompatibilité Zod critique

### Zod ^3.25.0 — ⚠️ Incompatibilité MCP

```diff
- import { z } from "zod";      # ❌ Si Zod v4 installé
+ import { z } from "zod/v3";   # ✅ Forcer Zod v3 API
```

**Issue critique #1429** : MCP SDK v1.17.5+ incompatible avec Zod v4 (erreur `w._parse is not a function`). Zod 3.25 est une version "bridge" contenant les deux APIs — utiliser explicitement le subpath `/v3`.

### Pino ^9.0.0 — ✅ Validé

Dernière version : **10.1.0**. Fonctionne avec Bun bien que non officiellement supporté. 6.2M téléchargements/semaine.

### pino-pretty ^11.0.0 — ✅ Validé

Dernière version : **13.1.3**. Compatible Pino 9.14.0 à 10.1.0.

### Testcontainers ^10.0.0 — ⚠️ Installation Bun

Dernière version : **11.11.0**. Issue #853 (fermée) : problèmes d'installation avec `bun add`. Solution : configurer correctement le registry npm dans `bunfig.toml`.

**Image pgvector disponible** : `pgvector/pgvector:pg16` ou `pg18` via `@testcontainers/postgresql`.

### TypeScript ^5.7.0 — ✅ Validé

Version 5.7.0 stable et publiée. Dernière : **5.9.3**.

---

## Synthèse des risques et actions prioritaires

### Actions critiques (bloquantes)

| Priorité | Action | Impact |
|----------|--------|--------|
| 🔴 P0 | Changer SDK MCP de `^2.0.0` à `^1.22.0` | SDK 2.0 n'existe pas |
| 🔴 P0 | Utiliser `import { z } from "zod/v3"` | Incompatibilité runtime |
| 🔴 P0 | Changer image Docker vers `pgvector/pgvector:pg18` | Image potentiellement indisponible |

### Actions importantes (recommandées)

| Priorité | Action | Raison |
|----------|--------|--------|
| 🟠 P1 | Utiliser révision `main` pour E5 model | `refs/pr/1` non stable |
| 🟠 P1 | Utiliser `postgres` npm au lieu de `bun:sql` | Bugs connus en production |
| 🟠 P1 | Aligner versions Drizzle ORM/Kit | Éviter incompatibilités |

### Package.json corrigé

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

**Note Zod** : Avec `zod: "^3.25.0"`, importer via `import { z } from "zod/v3"` pour garantir compatibilité MCP.

### Matrice de compatibilité finale

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

Cette stack technique est **validée et recommandée** après application des corrections identifiées.