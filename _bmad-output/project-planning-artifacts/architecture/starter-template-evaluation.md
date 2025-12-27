# Starter Template Evaluation

## Primary Technology Domain

**Developer Tool + MCP Server** (Model Context Protocol server avec intégration Claude Code native)

Alexandria n'est PAS un projet web app classique (Next.js, Vite, React) ni une API REST standard (Express, NestJS). C'est un système spécialisé combinant:
- MCP Server (Hono framework)
- Architecture RAG 3-layer avec vector search (pgvector)
- Intégration Claude Code (skills, sub-agents, slash commands)
- Architecture hexagonale stricte (contrainte non-négociable)

## Starter Options Considered

**Évaluation des starters génériques:**

Starters classiques évalués et **rejetés**:
- ❌ **Next.js / T3 Stack**: Web app focus, pas de MCP protocol, architecture non-hexagonale
- ❌ **NestJS**: Trop opinionated (decorators), pas de Hono, complexité inutile pour MCP server
- ❌ **Vite + React**: Frontend focus, inadapté pour backend MCP server
- ❌ **Express boilerplate**: Pas de Bun support natif, pas d'architecture hexagonale

**Starters spécialisés recherchés:**
- ❌ **MCP Server starters**: Aucun starter mature trouvé pour Hono + MCP protocol
- ❌ **Hexagonal architecture starters**: Peu de starters TypeScript hexagonaux stricts
- ❌ **Bun + Hono starters**: Templates basiques sans architecture hexagonale

**Conclusion:** Aucun starter template existant ne correspond aux besoins spécifiques d'Alexandria (MCP + Hexagonal + RAG + pgvector + Bun/Hono stack complète).

## Selected Approach: Custom Project Structure

**Rationale:**

1. **Stack ultra-spécifique**: Bun 1.3.5 + Hono 4.11.1 + Drizzle 0.36.4 + Zod 4.2.1 + pgvector 0.8.1 nécessite structure custom
2. **Architecture hexagonale stricte**: Contrainte fondamentale NON-NÉGOCIABLE, absente des starters génériques
3. **RAG 3-layer**: Organisation domain core spécifique (layer1/layer2/layer3) non standard
4. **MCP protocol**: Support MCP tools nécessite routes/middleware custom Hono
5. **Testabilité maximale**: Structure tests (unit domain/integration adapters) alignée avec hexagonale

**Structure de Projet Custom pour Alexandria:**

```
alexandria/
├── src/
│   ├── domain/                          # 🎯 Domain Core (pure TypeScript, zero dépendances infrastructure)
│   │   ├── entities/                    # Entités métier (Convention, Documentation, Technology, Project)
│   │   │   ├── Convention.ts
│   │   │   ├── Documentation.ts
│   │   │   ├── Technology.ts
│   │   │   └── Project.ts
│   │   ├── use-cases/                   # Logique métier RAG Layer 1+2 (Layer 3 orchestré HORS Alexandria)
│   │   │   ├── layer1/                  # Layer 1: Vector Search (HNSW + cosine)
│   │   │   │   ├── SearchConventions.ts
│   │   │   │   └── GenerateEmbeddings.ts
│   │   │   ├── layer2/                  # Layer 2: Technology Linking
│   │   │   │   ├── LinkDocumentation.ts
│   │   │   │   └── FilterByTechnology.ts
│   │   │   ├── RetrieveRawContext.ts    # Pipeline Layer 1→2 (retourne brut, pas de reformulation)
│   │   │   ├── ValidateCode.ts
│   │   │   ├── UploadDocument.ts
│   │   │   └── ManageProjects.ts
│   │   └── value-objects/               # Value Objects immutables
│   │       ├── Embedding.ts
│   │       ├── ConformityScore.ts
│   │       └── ProjectId.ts
│   │
│   ├── ports/                           # 🔌 Ports (interfaces abstraites hexagonales)
│   │   ├── primary/                     # Ports primaires (driven by external actors)
│   │   │   ├── MCPServerPort.ts
│   │   │   ├── SkillPort.ts
│   │   │   └── SlashCommandPort.ts
│   │   └── secondary/                   # Ports secondaires (driving infrastructure)
│   │       ├── ConventionRepositoryPort.ts
│   │       ├── DocumentationRepositoryPort.ts
│   │       ├── TechnologyLinkerPort.ts
│   │       ├── EmbeddingGeneratorPort.ts
│   │       ├── LLMRefinerPort.ts
│   │       └── LoggerPort.ts
│   │
│   ├── adapters/                        # 🔧 Adapters (implémentations concrètes)
│   │   ├── primary/                     # Adapters primaires (MCP Server, Skills)
│   │   │   ├── mcp-server/             # MCP Server Hono 4.11.1
│   │   │   │   ├── server.ts           # Bootstrap Hono app
│   │   │   │   ├── routes/             # MCP tools routes (7 tools)
│   │   │   │   │   ├── retrieve.ts
│   │   │   │   │   ├── validate.ts
│   │   │   │   │   ├── upload.ts
│   │   │   │   │   ├── list.ts
│   │   │   │   │   ├── read.ts
│   │   │   │   │   ├── delete.ts
│   │   │   │   │   └── list-projects.ts
│   │   │   │   ├── middleware/
│   │   │   │   │   ├── zodValidation.ts    # Zod 4.2.1 validation auto
│   │   │   │   │   ├── errorHandler.ts     # MCP-compliant errors
│   │   │   │   │   └── logger.ts           # Structured logging
│   │   │   │   └── schemas/            # Zod 4.2.1 schemas MCP tools
│   │   │   │       ├── retrieveSchema.ts
│   │   │   │       ├── validateSchema.ts
│   │   │   │       └── uploadSchema.ts
│   │   │   └── skills/                 # Claude Code Skills
│   │   │       ├── alexandria-context.ts
│   │   │       └── alexandria-validate.ts
│   │   │
│   │   └── secondary/                   # Adapters secondaires (infrastructure)
│   │       ├── database/               # Drizzle ORM 0.36.4 + PostgreSQL 17.7 + pgvector 0.8.1
│   │       │   ├── drizzle.config.ts
│   │       │   ├── connection.ts       # Pool PostgreSQL
│   │       │   ├── schema/             # Drizzle schemas
│   │       │   │   ├── conventions.ts
│   │       │   │   ├── documentation.ts
│   │       │   │   ├── technologies.ts
│   │       │   │   ├── projects.ts
│   │       │   │   └── pivot-tables.ts
│   │       │   ├── migrations/         # Drizzle Kit migrations versionnées
│   │       │   └── repositories/       # Repository implementations (ports secondaires)
│   │       │       ├── DrizzleConventionRepository.ts
│   │       │       ├── DrizzleDocumentationRepository.ts
│   │       │       └── DrizzleTechnologyLinker.ts
│   │       ├── openai/                 # OpenAI Embeddings API
│   │       │   ├── OpenAIEmbeddingAdapter.ts
│   │       │   └── schemas/            # Zod validation responses OpenAI
│   │       │       └── embeddingResponseSchema.ts
│   │       └── logging/                # Bun Dual Logger (console JSON + fichiers .jsonl)
│   │           └── BunDualLogger.ts
│   │
│   ├── config/                          # ⚙️ Configuration (Zod validation fail-fast)
│   │   ├── env.ts                       # Zod 4.2.1 validation .env au startup
│   │   ├── constants.ts
│   │   └── types.ts                     # Types globaux TypeScript
│   │
│   ├── shared/                          # 🔧 Utilitaires partagés
│   │   ├── errors/                      # Custom errors
│   │   │   ├── DomainError.ts
│   │   │   ├── ValidationError.ts
│   │   │   └── InfrastructureError.ts
│   │   └── utils/
│   │       ├── retry.ts                 # Retry logic avec exponential backoff
│   │       └── timing.ts                # performance.now() pour metrics NFR26-33
│   │
│   └── index.ts                         # 🚀 Entry point (bootstrap app, dependency injection)
│
├── tests/                               # 🧪 Tests (Bun test runner ultra-rapide)
│   ├── unit/                            # Tests unitaires domain core (sans mocks)
│   │   ├── domain/
│   │   │   ├── entities/
│   │   │   └── use-cases/              # Tests Layer 1+2 isolés
│   │   └── adapters/                    # Tests unitaires adapters (avec mocks ports)
│   ├── integration/                     # Tests intégration (PostgreSQL, OpenAI réels)
│   │   ├── database/                    # Tests Drizzle + pgvector HNSW queries
│   │   ├── mcp-server/                  # Tests routes MCP tools end-to-end
│   │   └── pipeline/                    # Tests pipeline Layer 1→2 (retrieve_raw_context)
│   └── fixtures/                        # Test data (conventions, documentation JSON)
│       ├── conventions.json
│       └── documentation.json
│
├── logs/                                # 📊 Logs persistés (.jsonl rotatifs, gitignored)
│   └── .gitkeep                         # Garde le dossier vide dans git
│
├── scripts/                             # 📜 Scripts utilitaires
│   ├── setup-db.ts                      # Setup PostgreSQL 17.7 + pgvector 0.8.1
│   ├── seed-data.ts                     # Seed données test
│   ├── generate-types.ts                # Génération types Drizzle inferred
│   └── rotate-logs.sh                   # Rotation logs (cron job optionnel)
│
├── docs/                                # 📚 Documentation
│   ├── architecture/
│   │   ├── hexagonal-overview.md        # Architecture hexagonale expliquée
│   │   ├── ports-and-adapters.md        # Détail ports/adapters
│   │   └── rag-pipeline.md              # Pipeline RAG 3-layer
│   └── api/
│       └── mcp-tools.md                 # Documentation MCP tools
│
├── .env.example                         # 📋 Template variables environnement (ALEXANDRIA_DB_URL, OPENAI_API_KEY)
├── .gitignore                           # Git ignore (.env, node_modules, logs/*.jsonl, bun.lockb optionnel)
├── package.json                         # Dependencies Bun (hono, zod, drizzle-orm, postgres, openai)
├── bun.lockb                            # Lockfile Bun (versions exactes pinnées)
├── tsconfig.json                        # TypeScript 5.9.7 strict mode (strict=true, noImplicitAny, strictNullChecks)
├── Dockerfile                           # Image Bun 1.3.5 officielle
├── docker-compose.yml                   # PostgreSQL 17.7 + pgvector 0.8.1 service
└── README.md                            # Documentation projet (setup, architecture, usage)
```

## Initialization Commands

**Structure de projet sera créée via première story d'implémentation avec ces commandes:**

```bash
# Créer structure complète folders (Layer 3 orchestré HORS Alexandria, pas de folder layer3/)
mkdir -p alexandria/{src/{domain/{entities,use-cases/{layer1,layer2},value-objects},ports/{primary,secondary},adapters/{primary/{mcp-server/{routes,middleware,schemas},skills},secondary/{database/{schema,migrations,repositories},openai/schemas,logging}},config,shared/{errors,utils}},tests/{unit/{domain,adapters},integration/{database,mcp-server,pipeline},fixtures},logs,scripts,docs/{architecture,api}}

# Init Bun project
cd alexandria
bun init -y

# Install dependencies runtime
bun add hono zod drizzle-orm postgres openai

# Install dev dependencies
bun add -d drizzle-kit @types/node typescript @types/postgres

# Create config files
touch tsconfig.json drizzle.config.ts .env.example Dockerfile docker-compose.yml README.md

# Create scripts
touch scripts/setup-db.ts scripts/seed-data.ts scripts/generate-types.ts scripts/rotate-logs.sh

# Create logs .gitkeep
touch logs/.gitkeep
```

## Architectural Decisions Provided by Custom Structure

**Language & Runtime:**
- TypeScript 5.9.7 (strict mode obligatoire: tsconfig.json avec strict=true, noImplicitAny, strictNullChecks, noUncheckedIndexedAccess)
- Bun 1.3.5 runtime (pas de Node.js nécessaire, 3-4x plus rapide I/O)
- ESM modules (type: "module" dans package.json)

**Framework Web (MCP Server):**
- Hono 4.11.1 (framework web minimaliste ~12KB, edge-ready, multi-runtime)
- Middleware Zod 4.2.1 pour validation automatique inputs/outputs MCP tools
- Error handler MCP-compliant (transforme errors en format MCP protocol)
- Structured logging middleware (Bun console JSON)

**ORM & Database:**
- Drizzle ORM 0.36.4 (type-safe queries, ~10x moins overhead que Prisma)
- Support natif pgvector via `import { vector } from 'drizzle-orm/pg-core'`
- Migrations versionnées avec Drizzle Kit (rollback strategy)
- Connection pooling PostgreSQL (pg pool, timeout configs dans drizzle.config.ts)

**Validation:**
- Zod 4.2.1 aux boundaries (MCP tools schemas, .env validation startup, OpenAI responses)
- Fail-fast startup si .env invalide (config/env.ts exports validated env)
- Type-safety end-to-end: Zod schemas → TypeScript types inférés → Domain types

**Code Organization (Architecture Hexagonale Stricte):**

**Domain Core (src/domain/):**
- Zero dépendances infrastructure (pas de Drizzle, Hono, Zod imports)
- Entités métier immutables (readonly properties, value objects)
- Use cases organisés par RAG layers (layer1/layer2 uniquement - Layer 3 orchestré HORS Alexandria)
- Pipeline orchestration dans `RetrieveRawContext.ts` (retourne Layer 1+2 brut)
- Testable sans mocks (pure TypeScript logic)

**Ports Layer (src/ports/):**
- Interfaces TypeScript strictes (readonly, immutability)
- Ports primaires: `MCPServerPort`, `SkillPort`, `SlashCommandPort`
- Ports secondaires: `ConventionRepositoryPort`, `DocumentationRepositoryPort`, `TechnologyLinkerPort`, `EmbeddingGeneratorPort`, `LoggerPort`
- Définissent QUOI faire, pas COMMENT (contrats abstraits)
- **Note**: Pas de `LLMRefinerPort` - Layer 3 orchestré via Skill Alexandria (externe)

**Adapters Layer (src/adapters/):**
- Implémentations concrètes des ports
- Adapters primaires: MCP Server Hono (routes + middleware + schemas)
- Adapters secondaires: Drizzle repositories (HNSW + cosine), OpenAI client, BunDualLogger
- Swappables sans toucher domain core (hexagonal principle)
- **Note**: Pas de Claude Code Sub-Agent adapter - Layer 3 orchestré HORS Alexandria

**Folder Structure Principles:**
- `domain/` → Logique métier pure
- `ports/` → Contrats abstraits (interfaces)
- `adapters/` → Implémentations concrètes (infrastructure)
- `config/` → Configuration centralisée (Zod validation)
- `shared/` → Utilitaires cross-cutting (errors, retry, timing)
- `tests/` → Tests organisés par layer (unit/integration/e2e)

**Testing Framework:**
- Bun test runner intégré (ultra-rapide, syntaxe Jest-compatible)
- Tests unitaires domain core sans mocks (pure logic, isolated)
- Tests integration adapters avec PostgreSQL/OpenAI réels (ou test containers)
- Tests e2e pipeline Layer 1→2 (`retrieve_raw_context` MCP tool)
- Coverage intégré Bun (`bun test --coverage`)
- Watch mode pour TDD (`bun test --watch`)
- **Note**: Layer 3 testé via Skill Alexandria (tests séparés du MCP server)

**Development Experience:**

**Hot Reloading:**
- Bun dev server avec hot reload natif (`bun --watch src/index.ts`)
- Reload instantané TypeScript (pas de transpilation nécessaire)

**Type Safety:**
- TypeScript strict mode (errors compile-time, pas runtime)
- Drizzle inferred types (schemas → TypeScript types auto)
- Zod inferred types (schemas → TypeScript types auto)
- End-to-end type-safety (MCP inputs → Domain → Database)

**Debugging:**
- Bun debugger intégré (compatible VSCode/Chrome DevTools)
- Structured logging JSON (parsing facile, grep-friendly)
- Performance metrics avec `performance.now()` (NFR26-33)

**Linting & Formatting:**
- ESLint + Prettier (ou Biome pour all-in-one ultra-rapide)
- Pre-commit hooks (lint + format + tests unitaires)
- TypeScript compiler errors fail CI/CD

**Database Tooling:**
- Drizzle Studio pour visualisation database (UI web interactive)
- Migrations CLI (`drizzle-kit generate`, `drizzle-kit push`)
- Seed scripts pour données test (`scripts/seed-data.ts`)

**Containerization:**
- Dockerfile avec image Bun 1.3.5 officielle
- docker-compose.yml pour PostgreSQL 17.7 + pgvector 0.8.1
- Multi-stage build (dev vs production optimisé)

**Note:** Cette structure custom sera implémentée comme première story, incluant setup complet dependencies (package.json), configs (tsconfig.json, drizzle.config.ts, .env.example), folder structure, et validation architecture hexagonale via architecture tests.
