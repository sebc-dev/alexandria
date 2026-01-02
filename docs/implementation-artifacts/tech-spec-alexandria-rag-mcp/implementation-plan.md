# Implementation Plan

## Tasks

### Phase 1: Project Setup

- [ ] **Task 1.1**: Initialiser le projet Bun
  - File: `package.json`, `tsconfig.json`, `bunfig.toml`
  - Action: `bun init`, configurer TypeScript strict mode, ESM, paths aliases
  - Notes: Ajouter scripts `dev`, `build`, `test`, `db:migrate`, `db:studio`

- [ ] **Task 1.2**: Installer les dépendances
  - File: `package.json`
  - Action: `bun add @modelcontextprotocol/sdk@^1.22.0 @huggingface/transformers drizzle-orm postgres zod pino`
  - Notes: Dev deps: `drizzle-kit`, `@types/bun`, `pino-pretty`. ⚠️ MCP SDK 2.0.0 n'existe pas (pré-alpha). Note: `pgvector` npm non requis (Drizzle support natif)

- [ ] **Task 1.3**: Configurer l'environnement
  - File: `.env.example`, `src/config/env.ts`
  - Action: Créer schema Zod pour DATABASE_URL, LOG_LEVEL, HF_HOME (F30)
  - Notes: Valider au démarrage, fail-fast si manquant

- [ ] **Task 1.4**: Setup Docker Compose pour dev
  - File: `docker-compose.yml`
  - Action: PostgreSQL 18 avec pgvector 0.8.1, volume persistant, volume HF cache (F30)
  - Notes: Port 5432, health check inclus

- [ ] **Task 1.5**: Configurer Pino logger (F15)
  - File: `src/infrastructure/logger.ts`
  - Action: Setup Pino avec JSON output, log levels, request context
  - Notes: Env LOG_LEVEL (debug|info|warn|error), pino-pretty pour dev

### Phase 2: Domain Layer

- [ ] **Task 2.1**: Définir les entités Document et Chunk
  - File: `src/domain/document.ts`
  - Action: Types avec validation taille max (F24)
  - Notes: MAX_CHUNK_CONTENT=2000, WARNING_THRESHOLD=1500, MAX_CHUNKS_PER_DOC=500

- [ ] **Task 2.2**: Définir les value objects de recherche
  - File: `src/domain/search.ts`
  - Action: Types `SearchQuery`, `SearchResult` avec chunk_index, heading, total_chunks (F29)
  - Notes: threshold=0.82, limit=10 par défaut (⚠️ E5 scores compressés, 0.5 trop bas)

- [ ] **Task 2.3**: Définir les erreurs domaine (F8)
  - File: `src/domain/errors.ts`
  - Action: Classes d'erreur avec tous les codes MCP incluant nouveaux (F20, F24, F26)
  - Notes: EMBEDDING_DIMENSION_MISMATCH, CONTENT_TOO_LARGE, CONCURRENT_MODIFICATION

### Phase 3: Application Layer (Ports)

- [ ] **Task 3.1**: Définir les ports (interfaces)
  - File: `src/application/ports.ts`
  - Action: Interfaces avec transaction support et cancel pour embedder (F31)
  - Notes: `cancelLoading()` pour graceful shutdown

- [ ] **Task 3.2**: Implémenter le use case d'ingestion (F2, F6, F25)
  - File: `src/application/ingest.ts`
  - Action: Transaction READ COMMITTED (F18), retry avec jitter (F22), upsert atomique (F25)
  - Notes: Validation taille chunks avant embedding (F24)

- [ ] **Task 3.3**: Implémenter le use case de recherche (F1)
  - File: `src/application/search.ts`
  - Action: Inclure chunk_index, heading, total_chunks dans résultats (F29)
  - Notes: Retourner [] si aucun résultat > threshold

### Phase 4: Infrastructure - Database

- [ ] **Task 4.1**: Définir le schema Drizzle (F4, F5, F19)
  - File: `src/infrastructure/db/schema.ts`
  - Action: UNIQUE sur source, HNSW avec m=16, ef_construction=100
  - Notes: ef_search=100 via ALTER DATABASE (production standard), pas SET per-session

- [ ] **Task 4.2**: Configurer la connexion DB (F18)
  - File: `src/infrastructure/db/index.ts`
  - Action: READ COMMITTED isolation (ef_search via ALTER DATABASE, pas per-session)
  - Notes: Graceful shutdown avec timeout

- [ ] **Task 4.3**: Créer la migration initiale
  - File: `drizzle/0000_initial.sql`
  - Action: CREATE EXTENSION vector, HNSW (m=16, ef_construction=100), ALTER DATABASE (ef_search=100, iterative_scan=relaxed_order)
  - Notes: ef_construction=100 (production RAG), ef_search=100 (production standard). Ajouter `maintenance_work_mem = '256MB'` (optionnel)

- [ ] **Task 4.4**: Implémenter le repository (F5, F6, F25, F26)
  - File: `src/infrastructure/repositories/document-repo.ts`
  - Action: Upsert atomique avec rollback garanti (F25), gestion conflits (F26)
  - Notes: Log warning sur retry de conflit

### Phase 5: Infrastructure - Embeddings

- [ ] **Task 5.1**: Implémenter le wrapper E5 (F17, F20, F22)
  - File: `src/infrastructure/embeddings/e5-embedder.ts`
  - Action: Prefix handling exact (F17), validation 384 dims (F20), retry avec jitter (F22)
  - Notes: "query: " et "passage: " avec espace après colon

- [ ] **Task 5.2**: Gestion du cache et lifecycle (F30, F31, F23)
  - File: `src/infrastructure/embeddings/e5-embedder.ts`
  - Action: Respecter HF_HOME, cancelLoading() (F31), state machine (F23)
  - Notes: Log état du modèle à chaque transition

### Phase 6: MCP Server

- [ ] **Task 6.1**: Créer le serveur MCP de base (F11, F28, F31)
  - File: `src/mcp/server.ts`
  - Action: Graceful shutdown avec cancel model loading (F31), SDK MCP ^1.22.0 (F28)
  - Notes: Drain timeout 30s, cancel loading immédiat sur SIGTERM. ⚠️ Importer Zod via `"zod/v3"`

- [ ] **Task 6.2**: Implémenter le tool de recherche (F1, F29)
  - File: `src/mcp/tools/search-tool.ts`
  - Action: Retourner chunk_index, heading, total_chunks (F29)
  - Notes: [] si rien > threshold

- [ ] **Task 6.3**: Implémenter le tool d'ingestion (F3, F17, F24)
  - File: `src/mcp/tools/ingest-tool.ts`
  - Action: Validation taille (F24), warning chunks > 1500 chars, erreur > 2000 chars
  - Notes: Option upsert=true par défaut

- [ ] **Task 6.4**: Implémenter le tool de suppression (F5)
  - File: `src/mcp/tools/delete-tool.ts`
  - Action: Delete par source, erreur DOCUMENT_NOT_FOUND si inexistant
  - Notes: CASCADE automatique des chunks

- [ ] **Task 6.5**: Implémenter le health check (F12, F23, F44)
  - File: `src/mcp/tools/health-tool.ts`
  - Action: State machine model (F23), ne pas bloquer pendant loading, inclure état "initial" (F44)
  - Notes: Retourner immédiatement avec status actuel, schema HealthOutputSchema

- [ ] **Task 6.6**: Implémenter le tool list-documents (F42)
  - File: `src/mcp/tools/list-tool.ts`
  - Action: Lister les documents avec pagination et filtrage par tags
  - Notes: Schema ListInputSchema/ListOutputSchema, inclure chunks_count

- [ ] **Task 6.7**: Créer le point d'entrée (F31)
  - File: `src/index.ts`
  - Action: Shutdown sequence avec cancelLoading()
  - Notes: SIGINT, SIGTERM handlers

### Phase 7: CLI

- [ ] **Task 7.1**: Implémenter la CLI (F14, F21)
  - File: `src/cli/ingest.ts`
  - Action: Bun native parseArgs (F21), --dry-run, exit codes standardisés
  - Notes: Help text complet, short flags (-c, -t, -v, -u, -n, -d)

- [ ] **Task 7.2**: Ajouter au package.json
  - File: `package.json`
  - Action: `"bin": { "alexandria": "./src/cli/ingest.ts" }`
  - Notes: Shebang `#!/usr/bin/env bun`

### Phase 8: Tests

- [ ] **Task 8.1**: Tests unitaires domain
  - File: `tests/domain/*.test.ts`
  - Action: Tests limites taille (F24), error codes (F20)
  - Notes: Test threshold defaults, validation

- [ ] **Task 8.2**: Tests unitaires application
  - File: `tests/application/*.test.ts`
  - Action: Tests retry avec jitter (F22), upsert rollback (F25)
  - Notes: Mock embedder pour tester états (F23)

- [ ] **Task 8.3**: Tests d'intégration DB (F27)
  - File: `tests/integration/db.test.ts`
  - Action: Testcontainers avec pgvector (F27), test concurrent ingestion (F26)
  - Notes: Utiliser `pgvector/pgvector:pg18` image (officielle)

- [ ] **Task 8.4**: Tests E2E MCP
  - File: `tests/e2e/mcp.test.ts`
  - Action: Test tous les error codes, health states, search response shape (F29)
  - Notes: Test graceful shutdown avec model loading

## Acceptance Criteria

### Ingestion

- [ ] **AC-1**: Given un fichier Markdown valide et un fichier de chunks JSON conforme au schema (< 2000 chars par chunk), when j'exécute `alexandria ingest doc.md -c chunks.json`, then le document est stocké avec ses chunks vectorisés et je reçois le document ID.

- [ ] **AC-2**: Given un document avec des tags ["typescript", "conventions"], when je l'ingère, then les tags sont persistés et searchables (case-insensitive OR matching).

- [ ] **AC-3**: Given un fichier inexistant, when j'exécute la CLI d'ingestion, then j'obtiens exit code 1 et message incluant le path manquant.

- [ ] **AC-4**: Given un fichier de chunks avec content > 2000 chars (F24), when j'exécute la CLI, then j'obtiens erreur CONTENT_TOO_LARGE (-31008).

- [ ] **AC-4b**: Given --dry-run flag, when j'exécute la CLI, then la validation s'exécute mais aucune donnée n'est persistée.

- [ ] **AC-4c**: Given un document source existant et upsert (défaut), when j'ingère, then l'ancien document est remplacé atomiquement, même si l'insert échoue le rollback restaure l'ancien (F25).

- [ ] **AC-4d**: Given deux clients ingérant simultanément la même source avec upsert (F26), when les deux terminent, then exactement un document existe et aucune erreur critique.

### Recherche

- [ ] **AC-5**: Given des documents ingérés, when je recherche avec threshold=0.82 (défaut E5), then je reçois uniquement les chunks avec score >= 0.82, incluant chunk_index, heading, total_chunks (F29).

- [ ] **AC-5b**: Given une recherche où aucun chunk n'atteint le threshold, when j'exécute, then je reçois un tableau vide `[]`.

- [ ] **AC-6**: Given une recherche avec `limit: 5`, when j'exécute la recherche, then je reçois maximum 5 résultats.

- [ ] **AC-7**: Given une recherche avec `tags: ["conventions"]` et un document tagué ["TypeScript", "Conventions"], when j'exécute, then le document matche (case-insensitive).

- [ ] **AC-8**: Given une query vide ou whitespace-only, when je recherche, then j'obtiens erreur VALIDATION_ERROR (-31002).

### MCP Integration

- [ ] **AC-9**: Given le serveur MCP démarré, when Claude Code liste les tools, then `search`, `ingest`, `delete`, `health`, et `list` apparaissent.

- [ ] **AC-10**: Given Claude Code connecté, when il appelle `search`, then il reçoit source, content, score, document_id, chunk_index, heading, total_chunks (F29).

- [ ] **AC-11**: Given une erreur DB pendant la recherche, when le tool est appelé, then erreur -31004 (DATABASE_ERROR), serveur ne crash pas.

- [ ] **AC-11b**: Given erreur d'embedding pendant ingestion, when tool appelé, then erreur -31003 et rollback complet.

- [ ] **AC-11c**: Given tool `health` appelé pendant model loading (F23), then retourne immédiatement `{ model: "loading" }` sans bloquer.

- [ ] **AC-11d**: Given embedding retourne vecteur != 384 dimensions (F20), when ingestion, then erreur -31007 EMBEDDING_DIMENSION_MISMATCH.

### List Documents (F42)

- [ ] **AC-11e**: Given 10 documents ingérés, when j'appelle `list` avec limit=5, then je reçois 5 documents avec has_more=true et total=10.

- [ ] **AC-11f**: Given documents tagués, when j'appelle `list` avec tags=["api"], then seuls les documents avec ce tag (case-insensitive) sont retournés.

### Performance (F7)

**Environnement de référence:**
- Hardware: 4 CPU cores, 8GB RAM
- Database: PostgreSQL 18 local, données warm, ef_search=100 (F19)
- Model: Déjà chargé en mémoire

- [ ] **AC-12**: Given 1000 chunks indexés et cache warm, when je recherche, then temps < 500ms (p95).

- [ ] **AC-13**: Given un document de 50 chunks (< 2000 chars chacun) et modèle chargé, when je l'ingère, then temps total < 10s.

### Suppression

- [ ] **AC-14**: Given document existant, when `delete` par source, then document et chunks supprimés.

- [ ] **AC-15**: Given source inexistante, when `delete`, then erreur DOCUMENT_NOT_FOUND (-31001).

### Graceful Shutdown (F31)

- [ ] **AC-16**: Given model en état "loading" et SIGTERM reçu (F31), when shutdown, then cancel immédiat du loading (pas d'attente 60s), requêtes en attente reçoivent MODEL_LOAD_TIMEOUT.
