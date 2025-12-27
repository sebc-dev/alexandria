# Core Architectural Decisions

## Decision Priority Analysis

**Critical Decisions (Block Implementation):**

1. **Sub-Agent Communication Protocol** - Architecture orchestrée via Skill Alexandria
2. **Vector Search Configuration** - HNSW index + cosine similarity pour Layer 1
3. **Multi-Project Isolation** - Application-level filtering via Drizzle WHERE clauses
4. **Logging Strategy** - Dual approach (console + fichiers .jsonl rotatifs)

**Important Decisions (Shape Architecture):**

1. **CI/CD Pipeline** - GitHub Actions avec tests unitaires, intégration, linting, Dependency Cruiser
2. **Layer 3 Caching** - Pas de cache pour MVP (simplicité prioritaire)
3. **API Security** - MCP protocol natif sans exposition HTTP publique

**Deferred Decisions (Post-MVP):**

1. **Rate Limiting** - Non nécessaire pour usage local, peut être ajouté si déploiement multi-utilisateurs
2. **Advanced Caching** - Redis ou PostgreSQL cache table si reformulations deviennent coûteuses
3. **Distributed Logging** - Dashboard centralisé si besoin d'observabilité avancée

---

## Data Architecture

**Vector Search - PostgreSQL 17.7 + pgvector 0.8.1:**

- **Index Type**: HNSW (Hierarchical Navigable Small World)
  - Rationale: Performance optimale pour high-recall searches (~100-1000 conventions)
  - Configuration: `m=16, ef_construction=64` (valeurs standard recommandées)
  - Trade-off: Build time légèrement plus long qu'IVFFlat mais queries 10x plus rapides

- **Distance Metric**: Cosine Similarity
  - Rationale: Standard de facto pour embeddings OpenAI (normalized vectors)
  - Compatible avec: `text-embedding-3-small` (1536 dimensions) et `text-embedding-3-large` (3072 dimensions)
  - Fonction pgvector: `<=>` operator pour cosine distance

**ORM & Type-Safety - Drizzle ORM 0.36.4:**

- **Schema Definition**: Type-safe schema avec support natif pgvector
  - `vector(1536)` type pour colonnes embeddings
  - Inference automatique TypeScript types depuis schema
  - Migrations versionnées via `drizzle-kit`

- **Query Builder**: Drizzle queries pour vector similarity
  ```typescript
  // Exemple query Layer 1
  db.select()
    .from(conventions)
    .where(eq(conventions.projectId, projectId))
    .orderBy(sql`embedding <=> ${queryEmbedding}`)
    .limit(topK)
  ```

**Caching Strategy:**

- **Decision**: Pas de cache pour Layer 3 reformulations (MVP)
- **Rationale**:
  - Simplicité architecturale prioritaire
  - Reformulations via sub-agent Haiku 4.5 (rapide + économique)
  - Quota Plan Max suffisant pour usage développeur
  - Cache ajouterait complexité (invalidation, storage, TTL)
- **Future**: Si latence Layer 3 devient problématique, évaluer PostgreSQL cache table avec TTL

**Multi-Project Isolation:**

- **Decision**: Application-level filtering via Drizzle WHERE clauses
- **Rationale**:
  - Simplicité vs Row-Level Security PostgreSQL (complexe à tester)
  - Contrôle explicite dans use-cases Domain layer
  - Performance identique (PostgreSQL optimise WHERE project_id via index)
- **Implementation**:
  - Tous les use-cases reçoivent `ProjectId` value object
  - Tous les repository adapters filtrent par `project_id`
  - Tests unitaires vérifient isolation via fixtures multi-projets
- **Index**: `CREATE INDEX idx_conventions_project_id ON conventions(project_id)`

---

## Authentication & Security

**Authorization Pattern:**

- **Decision**: Project-based isolation sans authentification utilisateur
- **Rationale**:
  - Alexandria est un MCP server local (pas d'exposition réseau)
  - Claude Code gère l'authentification utilisateur
  - Isolation par `project_id` suffit pour séparer workspaces
- **Validation**: Zod 4.2.1 schemas pour tous les MCP tool inputs/outputs

**API Security:**

- **MCP Protocol**: Sécurité native via stdio transport (pas HTTP)
- **Input Validation**: Zod middleware systématique dans adapters/primary/mcp-server
- **Error Handling**: Structured errors via LoggerPort, pas d'exposition stack traces à l'utilisateur
- **Secrets Management**: `.env` validation via Zod au startup (fail-fast)

**Data Encryption:**

- **Decision**: Pas de chiffrement au repos pour MVP
- **Rationale**:
  - Données stockées: conventions techniques (non sensibles)
  - Base PostgreSQL locale (pas de risque réseau)
  - TLS natif pour OpenAI API calls (embeddings)
- **Future**: Si conventions contiennent secrets, ajouter encryption-at-rest via pgcrypto

---

## API & Communication Patterns

**MCP Protocol Architecture:**

- **Tool Design**: `retrieve_raw_context` retourne Layer 1 + Layer 2 brut
  - Input: `query: string, project_id: string, top_k?: number`
  - Output: `{ conventions: Convention[], linked_documentation: Documentation[] }`
  - Pas de reformulation dans le MCP tool (séparation des responsabilités)

- **Other MCP Tools**: `validate`, `upload`, `list`, `read`, `delete`, `list_projects`
  - Tous validés via Zod schemas
  - Tous loggés via LoggerPort (request/response/errors)

**Sub-Agent Communication Protocol:**

- **Architecture Critique**: Alexandria (Hono/TypeScript) **n'invoque PAS directement** le sub-agent

- **Responsabilités Séparées**:
  1. **MCP Server (Alexandria)**: Expose `retrieve_raw_context` (Layer 1 + Layer 2)
  2. **Sub-Agent Definition**: Fichier `.claude/agents/alexandria-reformulation.md`
     ```markdown
     ---
     model: haiku
     ---
     # Alexandria Reformulation Agent

     Tu es un expert en reformulation de contexte technique pour Claude Code.

     **Ton rôle**: Recevoir le contexte brut (conventions + documentation) et le reformuler
     en instructions précises, actionnables, et adaptées au niveau de compétence de l'utilisateur.

     **Input**: JSON brut de `retrieve_raw_context`
     **Output**: Contexte reformulé optimisé pour génération de code
     ```
  3. **Skill Alexandria**: Orchestre le workflow complet
     - Appelle MCP tool `retrieve_raw_context`
     - Demande explicitement à Claude Code d'utiliser sub-agent `alexandria-reformulation`
     - Sub-agent reformule le contexte (Layer 3)
     - Contexte final injecté dans génération

- **Avantages**:
  - ✅ Pas de `CLAUDE_API_KEY` requise dans Alexandria
  - ✅ Reformulation Layer 3 utilise quota Plan Max existant
  - ✅ Sub-agent invoqué nativement par Claude Code (système interne)
  - ✅ Séparation claire: data (MCP) vs reformulation (sub-agent) vs orchestration (skill)

- **Version**: Claude Haiku 4.5 (`claude-haiku-4-5`)

**Error Handling Standards:**

- **Domain Errors**: Custom error classes héritant `DomainError`
  - `ConventionNotFoundError`, `InvalidProjectIdError`, `EmbeddingGenerationError`
- **Adapter Errors**: Wrapping des erreurs infrastructure
  - Drizzle errors → `RepositoryError`
  - OpenAI errors → `EmbeddingServiceError`
- **MCP Errors**: JSON-RPC 2.0 error format
  ```json
  { "code": -32603, "message": "Internal error", "data": { "type": "RepositoryError" } }
  ```

**API Documentation:**

- **Format**: Markdown documentation dans `/docs/api/mcp-tools.md`
- **Generation**: Manuelle pour MVP (Zod schemas comme source of truth)
- **Future**: Zod → OpenAPI schema generator si besoin

---

## Frontend Architecture

**N/A - Alexandria est un MCP Server Backend**

Alexandria n'a pas d'interface utilisateur traditionnelle. L'interface est Claude Code lui-même via:

1. **MCP Tools**: 7 tools exposés (retrieve, validate, upload, list, read, delete, list_projects)
2. **Skills Auto-Invoqués**: Skill Alexandria orchestre Layer 1+2+3 automatiquement
3. **Slash Commands**: Commands interactifs si nécessaire (ex: `/alexandria-config`)

**User Experience Flow:**

```
Utilisateur → Claude Code → Skill Alexandria → MCP retrieve_raw_context →
Sub-Agent alexandria-reformulation → Contexte reformulé → Claude Code génération
```

---

## Infrastructure & Deployment

**CI/CD Pipeline - GitHub Actions:**

- **Workflow File**: `.github/workflows/ci.yml`

- **Jobs**:
  1. **Lint & Type Check**:
     ```yaml
     - bun run lint        # ESLint + Prettier
     - bun run typecheck   # tsc --noEmit
     ```

  2. **Architecture Compliance**:
     ```yaml
     - bun run arch:check   # Dependency Cruiser validation
     ```
     - Vérifie: Domain ne dépend pas d'Adapters
     - Vérifie: Ports sont pures interfaces
     - Vérifie: Adapters implémentent Ports

  3. **Tests Unitaires**:
     ```yaml
     - bun test tests/unit/**/*.test.ts
     ```
     - Sans mocks pour Domain layer
     - Mocks pour Adapters (Drizzle, OpenAI)

  4. **Tests Intégration**:
     ```yaml
     services:
       postgres:
         image: pgvector/pgvector:pg17
         env:
           POSTGRES_PASSWORD: test
     steps:
       - bun test tests/integration/**/*.test.ts
     ```
     - PostgreSQL réel avec pgvector
     - OpenAI API réel (variable secret `OPENAI_API_KEY`)
     - Fixtures multi-projets pour tester isolation

- **Secrets GitHub**:
  - `OPENAI_API_KEY`: Pour tests intégration embedding generation

**Logging Strategy - Dual Approach:**

- **Console Logging** (Debug Immédiat):
  - Format: JSON structuré via Bun natif
  - Niveau: `debug`, `info`, `warn`, `error`
  - Output: `stdout` (capturé par Claude Code)
  - Exemple:
    ```json
    {"level":"info","timestamp":"2025-12-26T10:30:45Z","layer":"layer1","query":"hexagonal architecture","matches":12,"latency_ms":45}
    ```

- **File Logging** (Persistence):
  - Format: `.jsonl` (JSON Lines)
  - Location: `./logs/alexandria-YYYY-MM-DD.jsonl`
  - Rotation: Quotidienne (nouveau fichier chaque jour)
  - Rétention: Configurable via `LOG_RETENTION_DAYS` env var (défaut: 30 jours)
  - Script: `scripts/rotate-logs.sh` (cron job optionnel)

- **LoggerPort Interface** (Hexagonal):
  ```typescript
  interface LoggerPort {
    debug(message: string, context?: Record<string, unknown>): void
    info(message: string, context?: Record<string, unknown>): void
    warn(message: string, context?: Record<string, unknown>): void
    error(message: string, error?: Error, context?: Record<string, unknown>): void
  }
  ```

- **Adapter Implementation**: `BunDualLoggerAdapter`
  - Écrit simultanément console ET fichier
  - Gestion erreurs I/O fichier (fallback console-only)
  - Buffer writes pour performance (flush toutes les 5s ou 100 messages)

- **Analysis**: `grep`/`jq` sur fichiers JSON
  ```bash
  # Erreurs Layer 1 aujourd'hui
  jq 'select(.level=="error" and .layer=="layer1")' logs/alexandria-2025-12-26.jsonl

  # Latence moyenne Layer 3
  jq -s 'map(select(.layer=="layer3") | .latency_ms) | add/length' logs/*.jsonl
  ```

- **Rationale**:
  - ✅ Pas de PostgreSQL pour logs (évite overhead I/O + pollution schema)
  - ✅ Console pour debug immédiat pendant développement
  - ✅ Fichiers pour troubleshooting post-mortem
  - ✅ JSON structuré pour analyse programmatique
  - ✅ Respecte NFR26-33 (structured, rotatif, exportable)

**Monitoring & Observability:**

- **MVP**: Analyse manuelle via logs JSON
- **Metrics Tracked**:
  - Latence par layer (layer1, layer2, layer3)
  - Taux erreurs par use-case
  - Nombre embeddings générés (coût OpenAI)
  - Sub-agent invocations count
- **Future**: Prometheus + Grafana si besoin dashboard temps réel

**Hosting Strategy:**

- **Environment**: Local execution uniquement (Bun runtime)
- **Database**: PostgreSQL local (Docker Compose pour développement)
- **Docker Compose** (`docker-compose.yml`):
  ```yaml
  services:
    postgres:
      image: pgvector/pgvector:pg17
      environment:
        POSTGRES_DB: alexandria
        POSTGRES_USER: alexandria
        POSTGRES_PASSWORD: ${DB_PASSWORD}
      ports:
        - "5432:5432"
      volumes:
        - pgdata:/var/lib/postgresql/data
  ```

**Scaling Strategy:**

- **MVP**: Single instance locale (pas de scaling nécessaire)
- **Future**: Si multi-utilisateurs → considérer:
  - Connection pooling PostgreSQL (PgBouncer)
  - Rate limiting par project_id
  - Caching Layer 3 (Redis)

---

## Decision Impact Analysis

**Implementation Sequence (Ordre Logique):**

1. **Phase 1 - Infrastructure Foundations** (Bloque tout le reste):
   - PostgreSQL + pgvector setup (Docker Compose)
   - Drizzle schema definition (conventions, documentation, technologies, projects tables)
   - Drizzle migrations avec HNSW index creation
   - Zod validation setup (.env schema)
   - LoggerPort + BunDualLoggerAdapter

2. **Phase 2 - Domain Core** (Pas de dépendances externes):
   - Entities (Convention, Documentation, Technology, Project)
   - Value Objects (Embedding, ProjectId, ConformityScore)
   - Ports definition (primary + secondary interfaces)
   - Domain errors (custom error classes)

3. **Phase 3 - Secondary Adapters** (Implémentent ports):
   - DrizzleConventionAdapter (avec HNSW cosine queries)
   - DrizzleDocumentationAdapter
   - DrizzleTechnologyLinkerAdapter
   - OpenAIEmbeddingAdapter (avec Zod validation responses)

4. **Phase 4 - Use Cases** (Utilisent domain + ports):
   - Layer 1: Vector Search use-case
   - Layer 2: Technology Linking use-case
   - Layer 3: (Orchestré par Skill, pas de use-case interne)

5. **Phase 5 - Primary Adapters** (Exposent MCP):
   - Hono MCP server setup
   - Zod middleware validation
   - MCP tools implementation (retrieve_raw_context, validate, upload, etc.)

6. **Phase 6 - Sub-Agent & Skill** (Orchestration finale):
   - `.claude/agents/alexandria-reformulation.md` sub-agent definition
   - Skill Alexandria (appelle MCP → invoque sub-agent → reformule)

7. **Phase 7 - CI/CD & Tooling**:
   - GitHub Actions workflow
   - Dependency Cruiser rules
   - ESLint Plugin Boundaries configuration
   - Scripts (log rotation, seed data)

**Cross-Component Dependencies:**

- **HNSW + Cosine** → **Drizzle Schema**:
  - Index creation: `CREATE INDEX idx_conventions_embedding ON conventions USING hnsw (embedding vector_cosine_ops)`
  - Query operator: `sql\`embedding <=> ${queryEmbedding}\``
  - Migration file doit spécifier `m=16, ef_construction=64` HNSW parameters

- **Application-Level Filtering** → **All Use Cases**:
  - Signature: `execute(projectId: ProjectId, ...otherParams)`
  - Drizzle queries: `.where(eq(table.projectId, projectId.value))`
  - Tests: Fixtures multi-projets pour vérifier isolation

- **Sub-Agent Architecture** → **MCP Tool Design**:
  - MCP tool `retrieve_raw_context` retourne **uniquement** Layer 1+2 (pas de reformulation)
  - Skill Alexandria devient **orchestrateur critique**
  - Sub-agent `.claude/agents/alexandria-reformulation.md` doit être créé AVANT utilisation

- **Dual Logging** → **LoggerPort Implementation**:
  - Tous les use-cases injectent `LoggerPort` via constructor
  - BunDualLoggerAdapter écrit console ET fichier simultanément
  - Scripts rotation logs dans `/scripts/rotate-logs.sh`
  - `.gitignore` doit exclure `/logs/*.jsonl`

- **GitHub Actions** → **PostgreSQL Service Container**:
  - Tests intégration nécessitent `pgvector/pgvector:pg17` image
  - Migrations Drizzle exécutées avant tests
  - Seed data fixtures chargées via scripts

- **Zod 4.2.1** → **Breaking Changes Migration**:
  - `.refine()` API changée (consulter migration guide)
  - Bundle size réduit 57% (impact positif sur startup Bun)
  - Performance 3x meilleure (validation MCP inputs plus rapide)

- **No Cache Layer 3** → **Sub-Agent Invocations**:
  - Chaque query utilisateur → nouveau sub-agent call
  - Quota Plan Max consommé par reformulations
  - LoggerPort doit tracker sub-agent invocation count (monitoring coût)

**Architectural Constraints Enforced by Dependency Cruiser:**

```javascript
// .dependency-cruiser.js
module.exports = {
  forbidden: [
    {
      name: 'no-domain-to-adapters',
      severity: 'error',
      comment: 'Domain should not depend on Adapters',
      from: { path: '^src/domain' },
      to: { path: '^src/adapters' }
    },
    {
      name: 'no-domain-external-libs',
      severity: 'error',
      comment: 'Domain should not import external libraries except TypeScript',
      from: { path: '^src/domain' },
      to: { path: 'node_modules', pathNot: 'node_modules/typescript' }
    },
    {
      name: 'adapters-must-use-ports',
      severity: 'warn',
      comment: 'Adapters should implement Ports',
      from: { path: '^src/adapters' },
      to: { path: '^src/adapters', pathNot: '^src/ports' }
    }
  ],
  options: {
    tsPreCompilationDeps: true,
    tsConfig: { fileName: './tsconfig.json' },
    moduleSystems: ['es6', 'cjs', 'ts']
  }
};
```
