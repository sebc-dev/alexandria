# Project Structure & Boundaries

## Complete Project Directory Structure

```
alexandria/
├── README.md
├── package.json
├── bun.lockb
├── tsconfig.json
├── .env.example
├── .env
├── .gitignore
├── .prettierrc
├── eslint.config.json
├── docker-compose.yml
│
├── .github/
│   └── workflows/
│       └── ci.yml                          # GitHub Actions CI/CD pipeline
│
├── .claude/
│   ├── agents/
│   │   └── alexandria-reformulation.md     # Sub-agent Haiku 4.5 pour Layer 3
│   └── skills/
│       └── alexandria.md                   # Skill orchestration Layer 1+2+3
│
├── drizzle/
│   ├── schema.ts                          # Drizzle schema (conventions, docs, technologies, projects)
│   └── migrations/
│       ├── 0001_initial_schema.sql
│       ├── 0002_create_hnsw_index.sql     # HNSW index creation
│       └── meta/
│
├── scripts/
│   ├── rotate-logs.sh                     # Log rotation script
│   ├── seed-data.ts                       # Seed data pour développement
│   └── migrate.ts                         # Run Drizzle migrations
│
├── logs/                                  # .gitignore (logs runtime)
│   └── .gitkeep
│
├── docs/
│   ├── api/
│   │   └── mcp-tools.md                   # Documentation MCP tools
│   ├── architecture/
│   │   ├── hexagonal-architecture.md
│   │   └── rag-pipeline.md
│   └── setup/
│       └── installation.md
│
├── src/
│   ├── index.ts                           # Entrypoint Bun + Hono MCP server
│   │
│   ├── config/
│   │   ├── constants.ts                   # DEFAULT_TOP_K, EMBEDDING_MODEL, etc.
│   │   ├── env.schema.ts                  # Zod schema pour .env validation
│   │   └── logger.config.ts               # Configuration dual logging
│   │
│   ├── shared/
│   │   ├── errors/
│   │   │   ├── DomainError.ts
│   │   │   └── InfrastructureError.ts
│   │   ├── types/
│   │   │   └── mcp-protocol.types.ts
│   │   └── utils/
│   │       └── validation.utils.ts
│   │
│   ├── domain/
│   │   ├── entities/
│   │   │   ├── Convention.ts              # Immutable avec readonly
│   │   │   ├── Documentation.ts
│   │   │   ├── Technology.ts
│   │   │   └── Project.ts
│   │   │
│   │   ├── value-objects/
│   │   │   ├── Embedding.ts               # Value Object pour embeddings
│   │   │   ├── ProjectId.ts               # UUID validation
│   │   │   ├── ConformityScore.ts
│   │   │   └── SimilarityScore.ts
│   │   │
│   │   ├── errors/
│   │   │   ├── ConventionNotFoundError.ts
│   │   │   ├── InvalidProjectIdError.ts
│   │   │   ├── EmbeddingGenerationError.ts
│   │   │   └── ValidationError.ts
│   │   │
│   │   └── use-cases/
│   │       ├── layer1/
│   │       │   └── SearchConventionsBySemanticSimilarity.ts  # Layer 1 RAG
│   │       ├── layer2/
│   │       │   └── LinkRelatedDocumentation.ts               # Layer 2 RAG
│   │       ├── conventions/
│   │       │   ├── CreateConvention.ts
│   │       │   ├── UploadConvention.ts
│   │       │   ├── UpdateConvention.ts
│   │       │   ├── DeleteConvention.ts
│   │       │   └── ListConventions.ts
│   │       ├── documentation/
│   │       │   ├── CreateDocumentation.ts
│   │       │   ├── UploadDocumentation.ts
│   │       │   ├── DeleteDocumentation.ts
│   │       │   └── ListDocumentation.ts
│   │       ├── validation/
│   │       │   └── ValidateCodeConformity.ts
│   │       ├── projects/
│   │       │   ├── CreateProject.ts
│   │       │   ├── ListProjects.ts
│   │       │   └── GetProjectById.ts
│   │       └── technologies/
│   │           ├── CreateTechnology.ts
│   │           └── ListTechnologies.ts
│   │
│   ├── ports/
│   │   ├── primary/
│   │   │   └── MCPServerPort.ts           # Interface MCP server
│   │   │
│   │   └── secondary/
│   │       ├── ConventionRepositoryPort.ts
│   │       ├── DocumentationRepositoryPort.ts
│   │       ├── TechnologyRepositoryPort.ts
│   │       ├── ProjectRepositoryPort.ts
│   │       ├── EmbeddingGeneratorPort.ts
│   │       └── LoggerPort.ts
│   │
│   └── adapters/
│       ├── primary/
│       │   └── mcp-server/
│       │       ├── HonoMCPServer.ts       # Hono server implementation
│       │       ├── middleware/
│       │       │   └── ZodValidationMiddleware.ts
│       │       ├── tools/
│       │       │   ├── RetrieveRawContextTool.ts       # Layer 1+2
│       │       │   ├── ValidateCodeTool.ts
│       │       │   ├── UploadConventionTool.ts
│       │       │   ├── ListConventionsTool.ts
│       │       │   ├── ReadConventionTool.ts
│       │       │   ├── DeleteConventionTool.ts
│       │       │   └── ListProjectsTool.ts
│       │       └── schemas/
│       │           ├── RetrieveRawContextSchema.ts     # Zod schemas
│       │           ├── ValidateCodeSchema.ts
│       │           ├── UploadConventionSchema.ts
│       │           └── CommonSchemas.ts
│       │
│       └── secondary/
│           ├── database/
│           │   ├── DrizzleConventionRepository.ts
│           │   ├── DrizzleDocumentationRepository.ts
│           │   ├── DrizzleTechnologyRepository.ts
│           │   ├── DrizzleProjectRepository.ts
│           │   └── errors/
│           │       └── RepositoryError.ts
│           │
│           ├── embedding/
│           │   ├── OpenAIEmbeddingGenerator.ts
│           │   └── errors/
│           │       └── EmbeddingServiceError.ts
│           │
│           └── logging/
│               ├── BunDualLoggerAdapter.ts             # Console + File
│               └── formatters/
│                   └── JsonLogFormatter.ts
│
└── tests/
    ├── fixtures/
    │   ├── conventions.fixture.ts
    │   ├── documentation.fixture.ts
    │   ├── multi-project.fixture.ts           # Pour tester isolation
    │   └── embeddings.fixture.ts
    │
    ├── unit/
    │   ├── domain/
    │   │   ├── entities/
    │   │   │   ├── Convention.test.ts
    │   │   │   ├── Documentation.test.ts
    │   │   │   ├── Technology.test.ts
    │   │   │   └── Project.test.ts
    │   │   ├── value-objects/
    │   │   │   ├── Embedding.test.ts
    │   │   │   ├── ProjectId.test.ts
    │   │   │   └── ConformityScore.test.ts
    │   │   └── use-cases/
    │   │       ├── layer1/
    │   │       │   └── SearchConventionsBySemanticSimilarity.test.ts
    │   │       ├── layer2/
    │   │       │   └── LinkRelatedDocumentation.test.ts
    │   │       ├── conventions/
    │   │       │   ├── CreateConvention.test.ts
    │   │       │   └── DeleteConvention.test.ts
    │   │       └── validation/
    │   │           └── ValidateCodeConformity.test.ts
    │   │
    │   └── adapters/
    │       ├── DrizzleConventionRepository.test.ts     # Avec mocks Drizzle
    │       ├── OpenAIEmbeddingGenerator.test.ts        # Avec mocks OpenAI
    │       └── BunDualLoggerAdapter.test.ts
    │
    ├── integration/
    │   ├── database/
    │   │   ├── VectorSearch.test.ts                   # PostgreSQL + pgvector réel
    │   │   ├── MultiProjectIsolation.test.ts          # Test isolation
    │   │   └── HNSWIndexPerformance.test.ts
    │   ├── mcp-server/
    │   │   ├── RetrieveRawContext.test.ts
    │   │   ├── UploadConvention.test.ts
    │   │   └── ValidateCode.test.ts
    │   └── embedding/
    │       └── OpenAIEmbedding.test.ts                # OpenAI API réel
    │
    └── architecture/
        └── hexagonal.arch.test.ts                     # Dependency Cruiser validation
```

## Architectural Boundaries

**API Boundaries:**

**MCP Protocol Layer (stdio transport):**
- **Entry Point**: `src/adapters/primary/mcp-server/HonoMCPServer.ts`
- **Transport**: stdio (pas HTTP) → sécurité native
- **Validation**: Zod middleware sur chaque tool
- **Tools Exposés** (7 tools):
  1. `retrieve_raw_context` → Layer 1 + Layer 2 (pas Layer 3)
  2. `validate_code` → Validation conformité
  3. `upload_convention` → Upload markdown
  4. `list_conventions` → Liste filtrable
  5. `read_convention` → Lecture single
  6. `delete_convention` → Suppression avec cascade
  7. `list_projects` → Liste projets disponibles

**Sub-Agent Communication:**
- **Boundary**: `.claude/agents/alexandria-reformulation.md`
- **Invocation**: Via Skill Alexandria (pas directement par Alexandria TypeScript)
- **Input**: JSON brut de `retrieve_raw_context`
- **Output**: Contexte reformulé (Layer 3)
- **Model**: Claude Haiku 4.5 (`claude-haiku-4-5`)

**Component Boundaries:**

**Domain Layer (Pur TypeScript):**
- **NO external dependencies**: Pas d'import Zod, Drizzle, Hono dans domain/
- **Immutability**: Entities et Value Objects avec `readonly` properties
- **Business Logic**: Use-cases orchestrent entities via ports
- **Boundary enforcement**: Dependency Cruiser (CI/CD) + ESLint Plugin Boundaries (local) vérifient isolation

**Ports Layer (Interfaces Pures):**
- **Primary Ports**: Définissent comment MCP server interagit avec domain
- **Secondary Ports**: Définissent contrats pour repositories, embedding, logging
- **NO implementations**: Uniquement interfaces TypeScript

**Adapters Layer (Implémentations):**
- **Primary Adapters**: Hono MCP server implémente MCPServerPort
- **Secondary Adapters**:
  - Database: Drizzle implémente repository ports
  - Embedding: OpenAI implémente EmbeddingGeneratorPort
  - Logging: Bun implémente LoggerPort (dual output)

**Service Boundaries:**

**Layer 1 - Vector Search Service:**
- **Location**: `src/domain/use-cases/layer1/SearchConventionsBySemanticSimilarity.ts`
- **Input**: Query string + ProjectId + optional top_k
- **Output**: Convention[] triées par similarity score (cosine distance)
- **Dependencies**: ConventionRepositoryPort + EmbeddingGeneratorPort + LoggerPort
- **Isolation**: Application-level filtering via `WHERE project_id`

**Layer 2 - Technology Linking Service:**
- **Location**: `src/domain/use-cases/layer2/LinkRelatedDocumentation.ts`
- **Input**: Convention[] (depuis Layer 1) + ProjectId
- **Output**: Documentation[] liée via technology_id (SQL JOIN)
- **Dependencies**: DocumentationRepositoryPort + TechnologyRepositoryPort + LoggerPort
- **Logic**: Extraction technology_id depuis conventions → JOIN table pivot

**Layer 3 - Reformulation Service (External):**
- **Location**: `.claude/agents/alexandria-reformulation.md`
- **Orchestration**: Skill Alexandria appelle MCP tool → invoque sub-agent
- **Input**: Raw context (Layer 1 + Layer 2 JSON)
- **Output**: Reformulated context (instructions mono-approche)
- **Fallback**: Si sub-agent échoue, Skill retourne Layer 1+2 brut (graceful degradation)

**Data Boundaries:**

**PostgreSQL Schema (Drizzle):**
- **Tables**:
  - `conventions`: id, project_id, content_text, embedding (vector(1536)), technology_id, created_at
  - `documentation`: id, project_id, content_text, embedding (vector(1536)), technology_id, created_at
  - `technologies`: id, name, slug (pivot table)
  - `projects`: id, name, description, created_at

- **Indexes**:
  - HNSW index: `idx_conventions_embedding` (m=16, ef_construction=64)
  - B-tree index: `idx_conventions_project_id` (isolation performance)
  - B-tree index: `idx_documentation_technology_id` (JOIN performance)

**Vector Search Boundary:**
- **Distance Metric**: Cosine similarity via pgvector `<=>` operator
- **Query Pattern**:
  ```typescript
  db.select()
    .from(conventions)
    .where(eq(conventions.projectId, projectId.value))
    .orderBy(sql`embedding <=> ${queryEmbedding}`)
    .limit(topK)
  ```

**Embedding Generation Boundary:**
- **Provider**: OpenAI API
- **Model**: `text-embedding-3-small` (1536 dimensions) ou `text-embedding-3-large` (3072 dimensions)
- **Input**: Convention/Documentation content_text
- **Output**: Float array normalized (cosine-ready)
- **Validation**: Zod schema vérifie array length et normalization

**Multi-Project Isolation:**
- **Pattern**: Application-level filtering (pas Row-Level Security PostgreSQL)
- **Implementation**: Tous les use-cases injectent ProjectId value object
- **Enforcement**: Drizzle queries systématiquement `.where(eq(table.projectId, projectId.value))`
- **Testing**: Fixtures multi-projets vérifient étanchéité

## Requirements to Structure Mapping

**Feature/Epic Mapping:**

**Convention & Documentation Management (FR1-FR15):**
- **Entities**: `src/domain/entities/Convention.ts`, `src/domain/entities/Documentation.ts`
- **Use-Cases**:
  - CRUD: `src/domain/use-cases/conventions/*`
  - Upload: `src/domain/use-cases/conventions/UploadConvention.ts`
  - Cascade delete: `src/domain/use-cases/conventions/DeleteConvention.ts`
- **Repositories**: `src/adapters/secondary/database/DrizzleConventionRepository.ts`
- **MCP Tools**: `src/adapters/primary/mcp-server/tools/{Upload,List,Read,Delete}*Tool.ts`
- **Tests**: `tests/integration/mcp-server/UploadConvention.test.ts`

**Active Compliance Filter - 3 Layer RAG (FR16-FR28):**
- **Layer 1 Use-Case**: `src/domain/use-cases/layer1/SearchConventionsBySemanticSimilarity.ts`
- **Layer 2 Use-Case**: `src/domain/use-cases/layer2/LinkRelatedDocumentation.ts`
- **Layer 3 Sub-Agent**: `.claude/agents/alexandria-reformulation.md`
- **Orchestration**: `.claude/skills/alexandria.md`
- **Vector Adapter**: `src/adapters/secondary/database/DrizzleConventionRepository.ts` (méthode `searchBySimilarity`)
- **Embedding Adapter**: `src/adapters/secondary/embedding/OpenAIEmbeddingGenerator.ts`
- **Tests**: `tests/integration/database/VectorSearch.test.ts`

**Context Retrieval & Delivery (FR29-FR38):**
- **MCP Tool**: `src/adapters/primary/mcp-server/tools/RetrieveRawContextTool.ts`
- **Schema**: `src/adapters/primary/mcp-server/schemas/RetrieveRawContextSchema.ts`
- **Skill Orchestration**: `.claude/skills/alexandria.md` (appelle tool → sub-agent → reformule)
- **Tests**: `tests/integration/mcp-server/RetrieveRawContext.test.ts`

**Code Validation & Conformity (FR39-FR49):**
- **Use-Case**: `src/domain/use-cases/validation/ValidateCodeConformity.ts`
- **Value Object**: `src/domain/value-objects/ConformityScore.ts`
- **MCP Tool**: `src/adapters/primary/mcp-server/tools/ValidateCodeTool.ts`
- **Tests**: `tests/unit/domain/use-cases/validation/ValidateCodeConformity.test.ts`

**Claude Code Integration (FR50-FR74):**
- **Skills**: `.claude/skills/alexandria.md`
- **Sub-Agent**: `.claude/agents/alexandria-reformulation.md`
- **MCP Server**: `src/adapters/primary/mcp-server/HonoMCPServer.ts` (stdio transport)
- **Zod Validation**: `src/adapters/primary/mcp-server/middleware/ZodValidationMiddleware.ts`

**Project & Technology Configuration (FR75-FR91, FR104-FR106):**
- **Entities**: `src/domain/entities/Project.ts`, `src/domain/entities/Technology.ts`
- **Use-Cases**: `src/domain/use-cases/projects/*`, `src/domain/use-cases/technologies/*`
- **Repositories**: `src/adapters/secondary/database/{DrizzleProject,DrizzleTechnology}Repository.ts`
- **Isolation Logic**: ProjectId value object propagé dans tous les use-cases
- **Tests**: `tests/integration/database/MultiProjectIsolation.test.ts`

**Testing, Debugging & Observability (FR92-FR103):**
- **Logging Adapter**: `src/adapters/secondary/logging/BunDualLoggerAdapter.ts`
- **Log Config**: `src/config/logger.config.ts`
- **Log Files**: `logs/alexandria-YYYY-MM-DD.jsonl` (rotation quotidienne)
- **Scripts**: `scripts/rotate-logs.sh`
- **Debug Queries**: Logging contexte dans use-cases (query, layer, latency, matches)
- **Tests**: `tests/unit/adapters/BunDualLoggerAdapter.test.ts`

**Cross-Cutting Concerns:**

**Error Handling System:**
- **Base Classes**: `src/shared/errors/DomainError.ts`, `src/shared/errors/InfrastructureError.ts`
- **Domain Errors**: `src/domain/errors/*Error.ts`
- **Adapter Errors**: `src/adapters/secondary/{database,embedding}/errors/*Error.ts`
- **MCP Error Mapping**: `src/adapters/primary/mcp-server/HonoMCPServer.ts` (JSON-RPC 2.0 format)

**Configuration Management:**
- **Environment Variables**: `.env` file (gitignored)
- **Validation**: `src/config/env.schema.ts` (Zod schema, fail-fast au startup)
- **Constants**: `src/config/constants.ts` (DEFAULT_TOP_K, EMBEDDING_MODEL, etc.)
- **Example**: `.env.example` (committed)

**Type Safety:**
- **Drizzle Schema**: `drizzle/schema.ts` (source of truth pour DB types)
- **MCP Protocol**: `src/shared/types/mcp-protocol.types.ts`
- **tsconfig.json**: Strict mode enabled, path alias `@/` configuré

## Integration Points

**Internal Communication:**

**Use-Case → Repository (via Port):**
```typescript
// src/domain/use-cases/layer1/SearchConventionsBySemanticSimilarity.ts
constructor(
  private conventionRepo: ConventionRepositoryPort,
  private embeddingGen: EmbeddingGeneratorPort,
  private logger: LoggerPort
) {}

async execute(query: string, projectId: ProjectId, topK: number): Promise<Convention[]> {
  const embedding = await this.embeddingGen.generate(query)
  const conventions = await this.conventionRepo.searchBySimilarity(embedding, projectId, topK)
  this.logger.info('Layer 1 search completed', { query, matches: conventions.length })
  return conventions
}
```

**MCP Tool → Use-Case:**
```typescript
// src/adapters/primary/mcp-server/tools/RetrieveRawContextTool.ts
async handle(input: RetrieveRawContextInput): Promise<RetrieveRawContextOutput> {
  const projectId = ProjectId.create(input.project_id)

  // Layer 1
  const conventions = await this.searchConventionsUseCase.execute(
    input.query,
    projectId,
    input.top_k ?? DEFAULT_TOP_K
  )

  // Layer 2
  const documentation = await this.linkDocumentationUseCase.execute(
    conventions,
    projectId
  )

  return { conventions, linked_documentation: documentation }
}
```

**Skill → MCP Tool → Sub-Agent:**
```markdown
<!-- .claude/skills/alexandria.md -->
1. Call MCP tool `retrieve_raw_context` with user query
2. Receive raw context (Layer 1 + Layer 2 JSON)
3. Invoke sub-agent `alexandria-reformulation` with raw context
4. Receive reformulated context (Layer 3)
5. Inject reformulated context into user's generation request
```

**External Integrations:**

**OpenAI API Integration:**
- **Adapter**: `src/adapters/secondary/embedding/OpenAIEmbeddingGenerator.ts`
- **Endpoint**: `https://api.openai.com/v1/embeddings`
- **Model**: `text-embedding-3-small` (configurable via env var)
- **Auth**: `OPENAI_API_KEY` environment variable
- **Validation**: Zod schema vérifie response structure
- **Error Handling**: `EmbeddingServiceError` wraps OpenAI errors

**PostgreSQL + pgvector Integration:**
- **Adapter**: `src/adapters/secondary/database/Drizzle*Repository.ts`
- **Connection**: `ALEXANDRIA_DB_URL` environment variable
- **Driver**: Bun native PostgreSQL driver
- **ORM**: Drizzle ORM 0.36.4
- **Extension**: pgvector 0.8.1 (HNSW index support)
- **Migrations**: `drizzle/migrations/*.sql`

**Claude Code Integration:**
- **MCP Server**: stdio transport via `src/index.ts`
- **Discovery**: Claude Code auto-découvre MCP server via `.claude/mcp.json`
- **Skills**: `.claude/skills/alexandria.md` auto-invocable
- **Sub-Agent**: `.claude/agents/alexandria-reformulation.md` invocable via Skill

**Data Flow:**

**RAG Pipeline End-to-End:**

```
User Query (Claude Code)
    ↓
Skill Alexandria (.claude/skills/alexandria.md)
    ↓
MCP Tool: retrieve_raw_context (stdio)
    ↓
RetrieveRawContextTool.handle()
    ↓
┌─────────────────────────────────────────────┐
│ Layer 1: Semantic Vector Search             │
│ ─────────────────────────────────────────── │
│ SearchConventionsBySemanticSimilarity       │
│   → OpenAIEmbeddingGenerator (query)        │
│   → DrizzleConventionRepository.searchBySimilarity() │
│   → PostgreSQL HNSW index (cosine <=>)      │
│   → Returns: Convention[] (top_k)           │
└─────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────┐
│ Layer 2: Technology Linking                 │
│ ─────────────────────────────────────────── │
│ LinkRelatedDocumentation                    │
│   → Extract technology_id from conventions  │
│   → DrizzleDocumentationRepository.findByTechnologyIds() │
│   → PostgreSQL JOIN via pivot table         │
│   → Returns: Documentation[]                │
└─────────────────────────────────────────────┘
    ↓
Raw Context JSON { conventions[], linked_documentation[] }
    ↓
Return to Skill Alexandria
    ↓
┌─────────────────────────────────────────────┐
│ Layer 3: LLM Reformulation (External)       │
│ ─────────────────────────────────────────── │
│ Skill invokes sub-agent:                    │
│   alexandria-reformulation (Haiku 4.5)      │
│   → Receives raw context JSON               │
│   → Reformulates into mono-approche guide   │
│   → Returns: Reformulated context string    │
│                                             │
│ Fallback: If sub-agent fails → use Layer 1+2 raw │
└─────────────────────────────────────────────┘
    ↓
Reformulated Context
    ↓
Injected into Claude Code generation request
    ↓
Generated Code (conforms to conventions)
```

**Convention Upload Flow:**

```
User uploads markdown file
    ↓
MCP Tool: upload_convention (stdio)
    ↓
UploadConventionTool.handle()
    ↓
Zod validation (schema: UploadConventionSchema)
    ↓
UploadConvention Use-Case
    ↓
OpenAIEmbeddingGenerator.generate(content_text)
    ↓
Convention Entity.create()
    ↓
DrizzleConventionRepository.save()
    ↓
PostgreSQL INSERT (conventions table)
    ↓
BunDualLoggerAdapter.info('Convention uploaded', { conventionId, projectId })
    ↓
Console log + File log (logs/alexandria-YYYY-MM-DD.jsonl)
    ↓
Success response → MCP JSON-RPC 2.0 format
```

## File Organization Patterns

**Configuration Files:**

**Root Configuration:**
- `package.json`: Bun dependencies + scripts
- `bun.lockb`: Bun lockfile
- `tsconfig.json`: TypeScript config (strict mode, path alias `@/*`)
- `.env`: Runtime secrets (gitignored)
- `.env.example`: Template (committed)
- `.gitignore`: Exclude logs/, .env, node_modules
- `.prettierrc`: Code formatting
- `eslint.config.json`: Linting rules
- `docker-compose.yml`: PostgreSQL + pgvector service

**Build Configuration:**
- Pas de build step nécessaire (Bun exécute TypeScript nativement)
- Drizzle migrations: `drizzle.config.ts` (si besoin)

**Source Organization:**

**Hexagonal Architecture Layers (strictement séparés):**

1. **Domain Layer** (`src/domain/`):
   - Entities: Immutable, business logic methods
   - Value Objects: Validation, immutability, identity-less
   - Use-Cases: Business workflows, orchestration
   - Errors: Domain-specific errors
   - **NO external dependencies** (vérifié par Dependency Cruiser + ESLint Plugin Boundaries)

2. **Ports Layer** (`src/ports/`):
   - Primary: Interfaces pour drivers (MCP server)
   - Secondary: Interfaces pour driven (repositories, services)
   - **Pure TypeScript interfaces** (pas de classes)

3. **Adapters Layer** (`src/adapters/`):
   - Primary: MCP server (Hono), tools, middleware, schemas
   - Secondary: Database (Drizzle), Embedding (OpenAI), Logging (Bun)
   - **Implémentent les ports** (vérifié par Dependency Cruiser + ESLint Plugin Boundaries)

4. **Shared Layer** (`src/shared/`):
   - Errors: Base classes (DomainError, InfrastructureError)
   - Types: MCP protocol, communs
   - Utils: Fonctions utilitaires

5. **Config Layer** (`src/config/`):
   - Constants: Valeurs par défaut
   - Env schema: Validation Zod
   - Logger config: Configuration dual logging

**Test Organization:**

**Structure Miroir avec Suffixe `.test.ts`:**

- `tests/unit/domain/entities/Convention.test.ts` → `src/domain/entities/Convention.ts`
- `tests/unit/domain/use-cases/layer1/SearchConventionsBySemanticSimilarity.test.ts` → `src/domain/use-cases/layer1/SearchConventionsBySemanticSimilarity.ts`
- `tests/integration/database/VectorSearch.test.ts` → Tests end-to-end vector search

**Fixtures Réutilisables:**
- `tests/fixtures/conventions.fixture.ts`: Données de test conventions
- `tests/fixtures/multi-project.fixture.ts`: Données multi-projets pour isolation
- `tests/fixtures/embeddings.fixture.ts`: Embeddings pré-générés (mock)

**Asset Organization:**

**Logs (Runtime):**
- `logs/alexandria-YYYY-MM-DD.jsonl`: Logs journaliers (gitignored)
- Rotation: Script `scripts/rotate-logs.sh` (optionnel cron job)

**Documentation:**
- `docs/api/mcp-tools.md`: Documentation MCP tools
- `docs/architecture/`: Documentation architecture
- `docs/setup/`: Guide installation

**Claude Code Assets:**
- `.claude/agents/alexandria-reformulation.md`: Sub-agent definition
- `.claude/skills/alexandria.md`: Skill orchestration

**Database Assets:**
- `drizzle/schema.ts`: Schema Drizzle (source of truth)
- `drizzle/migrations/*.sql`: Migrations versionnées

## Development Workflow Integration

**Development Server Structure:**

**Démarrage Local:**
```bash
# 1. Start PostgreSQL + pgvector
docker-compose up -d

# 2. Run migrations
bun run migrate

# 3. (Optional) Seed data
bun run seed

# 4. Start MCP server
bun run dev  # Exécute src/index.ts
```

**Dev Scripts (package.json):**
```json
{
  "scripts": {
    "dev": "bun run --hot src/index.ts",
    "migrate": "bun run scripts/migrate.ts",
    "seed": "bun run scripts/seed-data.ts",
    "test": "bun test",
    "test:unit": "bun test tests/unit",
    "test:integration": "bun test tests/integration",
    "test:arch": "bun test tests/architecture",
    "lint": "eslint src tests --ext .ts",
    "typecheck": "tsc --noEmit",
    "format": "prettier --write 'src/**/*.ts' 'tests/**/*.ts'"
  }
}
```

**Build Process Structure:**

**Pas de Build Transpilation (Bun natif):**
- Bun exécute TypeScript directement (pas de tsc build)
- Type checking: `bun run typecheck` (tsc --noEmit)
- Linting: `bun run lint` (ESLint)
- Formatting: `bun run format` (Prettier)

**Migrations:**
- Drizzle génère SQL depuis schema.ts
- `bun run migrate` applique migrations
- Versioning: `drizzle/migrations/meta/_journal.json`

**Deployment Structure:**

**Local-Only Deployment (MVP):**
- **Runtime**: Bun 1.3.5 (installé localement)
- **Database**: PostgreSQL 17.7 + pgvector 0.8.1 (Docker Compose)
- **MCP Server**: stdio transport (pas d'exposition réseau)
- **Discovery**: Claude Code lit `.claude/mcp.json` configuration

**Future Deployment (si multi-utilisateurs):**
- **Container**: Dockerfile avec Bun officiel image
- **Orchestration**: Docker Compose multi-services
- **Scaling**: Connection pooling (PgBouncer), rate limiting
- **Monitoring**: Prometheus + Grafana (métriques depuis logs JSON)

---
