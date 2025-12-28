# Story 1.1: Project Structure & Configuration Setup

Status: review

## Story

**En tant que** développeur,
**Je veux** avoir la structure de projet hexagonale et la configuration de base,
**Afin de** pouvoir commencer le développement avec les bonnes fondations architecturales.

## Acceptance Criteria

1. Structure dossiers hexagonale créée: `src/domain/`, `src/ports/`, `src/adapters/`, `src/config/`, `src/shared/`
2. Bun 1.3.5 installé et configuré (package.json, bun.lockb)
3. TypeScript 5.9.7 configuré en strict mode (tsconfig.json)
4. Configuration .env avec variables requises (ALEXANDRIA_DB_URL, OPENAI_API_KEY, LOG_LEVEL)
5. Validation Zod de la configuration au démarrage (fail-fast si invalide)
6. Naming conventions documentées et appliquées (camelCase DB, PascalCase files, suffix "Port")
7. README.md avec instructions d'installation

## Requirements Covered

- **Architecture #1**: Stack Technique (Bun 1.3.5, TypeScript 5.9.7, Zod 4.2.1)
- **Architecture #7**: Naming Patterns
- **Architecture #8**: Validation Boundaries (Zod aux boundaries uniquement)
- **Architecture #10**: Structure Projet Custom (hexagonal architecture)
- **NFR7**: Credentials Management (variables d'environnement)
- **NFR21**: Code Documentation Complète
- **NFR24**: Configuration Management

## Tasks / Subtasks

- [x] Task 1: Initialize Bun project (AC: 2)
  - [x] Install Bun 1.3.5 globally
  - [x] Run `bun init` in project root
  - [x] Configure package.json with required scripts and dependencies

- [x] Task 2: Configure TypeScript strict mode (AC: 3)
  - [x] Create tsconfig.json with strict: true
  - [x] Configure path alias `@/*` pointing to `src/*`
  - [x] Enable sourceMap for debugging

- [x] Task 3: Create hexagonal directory structure (AC: 1)
  - [x] Create `src/domain/` with subdirectories: entities/, value-objects/, use-cases/, errors/
  - [x] Create `src/ports/` with subdirectories: primary/, secondary/
  - [x] Create `src/adapters/` with subdirectories: primary/, secondary/
  - [x] Create `src/config/` for constants, env schema, logger config
  - [x] Create `src/shared/` for errors/, types/, utils/

- [x] Task 4: Setup environment configuration (AC: 4)
  - [x] Create .env.example with all required variables documented
  - [x] Add .env to .gitignore
  - [x] Document required variables: ALEXANDRIA_DB_URL, OPENAI_API_KEY, LOG_LEVEL, LOG_RETENTION_DAYS

- [x] Task 5: Implement Zod environment validation (AC: 5)
  - [x] Create `src/config/env.schema.ts` with Zod schema
  - [x] Validate on application startup (fail-fast if invalid)
  - [x] Export typed environment config object

- [x] Task 6: Document naming conventions (AC: 6)
  - [x] Create CONVENTIONS.md or add section in README.md
  - [x] Document: Database fields in camelCase, Files in PascalCase, Ports with "Port" suffix
  - [x] Document: Constants in UPPER_SNAKE_CASE, MCP tools in snake_case, JSON fields in camelCase

- [x] Task 7: Create initial README.md (AC: 7)
  - [x] Add project description and objectives
  - [x] Add installation instructions (Bun, PostgreSQL, pgvector)
  - [x] Add environment setup instructions
  - [x] Add development workflow commands

## Dev Notes

### Architecture Constraints (CRITICAL - NON-NEGOTIABLE)

**Hexagonal Architecture Enforcement:**
- Domain layer MUST NOT depend on external libraries (Zod, Drizzle, Hono)
- Domain layer MUST NOT depend on Adapters or Infrastructure
- Ports MUST be pure TypeScript interfaces (no implementations)
- All dependencies flow inward: Adapters → Ports → Domain

**Validation Boundaries:**
- Zod ONLY at boundaries: Primary Adapters (MCP Server), Secondary Adapters (OpenAI, Database), Config
- Domain layer uses pure TypeScript validation logic
- Fail-fast behavior on startup if configuration invalid

### Project Structure (Full Tree)

```
alexandria/
├── README.md
├── package.json
├── bun.lockb
├── tsconfig.json
├── .env.example
├── .env                    # gitignored
├── .gitignore
├── .prettierrc
├── eslint.config.json
├── docker-compose.yml      # PostgreSQL + pgvector (Story 1.2)
│
├── src/
│   ├── index.ts           # Entrypoint (Story 1.5: DI Bootstrap)
│   │
│   ├── config/
│   │   ├── constants.ts           # DEFAULT_TOP_K, EMBEDDING_MODEL, etc.
│   │   ├── env.schema.ts          # Zod schema for .env validation
│   │   └── logger.config.ts       # Dual logging configuration (Story 7)
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
│   ├── domain/              # Story 1.3: Domain Layer Foundation
│   │   ├── entities/
│   │   ├── value-objects/
│   │   ├── errors/
│   │   └── use-cases/
│   │
│   ├── ports/               # Story 1.3: Ports Definition
│   │   ├── primary/
│   │   └── secondary/
│   │
│   └── adapters/            # Stories 1.4+
│       ├── primary/
│       │   └── mcp-server/
│       └── secondary/
│           ├── database/
│           ├── embedding/
│           └── logging/
│
├── tests/                   # Story 1.6: Architecture Tests
│   ├── fixtures/
│   ├── unit/
│   ├── integration/
│   └── architecture/
│       └── hexagonal.arch.test.ts
│
├── scripts/                 # Story 1.2+
│   ├── setup-db.ts
│   ├── seed-data.ts
│   └── migrate.ts
│
├── logs/                    # Story 7: Observability
│   └── .gitkeep
│
├── docs/
│   ├── api/
│   └── setup/
│
└── .claude/                 # Story 5: Claude Code Integration
    ├── agents/
    │   └── alexandria-reformulation.md
    └── skills/
        └── alexandria.md
```

### Naming Conventions Reference

**TypeScript Code:**
- **Files**: PascalCase (Convention.ts, SearchConventions.ts, ConventionRepositoryPort.ts)
- **Classes/Interfaces/Types**: PascalCase (Convention, ConventionRepositoryPort)
- **Variables/Functions**: camelCase (projectId, searchConventions, generateEmbedding)
- **Constants**: UPPER_SNAKE_CASE (DEFAULT_TOP_K, MAX_QUERY_LENGTH, EMBEDDING_DIMENSIONS)
- **Ports**: Suffix "Port" obligatoire (ConventionRepositoryPort, LoggerPort)

**Database (Drizzle ORM):**
- **Tables**: lowercase (conventions, documentation, projects)
- **Columns**: camelCase with quotes (projectId, contentText, embeddingVector, createdAt)
- **Indexes**: camelCase with prefix (idxConventionsProjectId, idxConventionsEmbedding)

**MCP Protocol:**
- **Tool names**: snake_case (retrieve_raw_context, upload_convention, list_projects)
- **JSON fields**: camelCase (projectId, topK, contentText)

**Test Files:**
- **Naming**: .test.ts suffix (Convention.test.ts, SearchConventions.test.ts)
- **Test blocks**: describe(PascalCase), it('should ...')

### Technology Versions (EXACT - Non-Negotiable)

```json
{
  "dependencies": {
    "bun": "1.3.5",
    "typescript": "5.9.7",
    "zod": "4.2.1",
    "drizzle-orm": "0.36.4",
    "hono": "4.11.1",
    "@anthropic-ai/sdk": "latest",
    "openai": "latest"
  },
  "devDependencies": {
    "@types/bun": "latest",
    "prettier": "latest",
    "eslint": "latest",
    "dependency-cruiser": "latest"
  }
}
```

**Critical Version Notes:**
- **Bun 1.3.5**: Ultra-fast runtime acquired by Anthropic, native TypeScript execution
- **TypeScript 5.9.7**: Latest 5.x stable branch, strict mode required
- **Zod 4.2.1**: BREAKING CHANGES from 3.x - bundle 57% smaller, 3x faster, different .refine() API
- **Drizzle ORM 0.36.4**: Native pgvector support for vector columns

### Environment Variables Schema

```typescript
// src/config/env.schema.ts
import { z } from 'zod'

export const envSchema = z.object({
  // Database
  ALEXANDRIA_DB_URL: z.string().url().startsWith('postgresql://'),

  // External Services
  OPENAI_API_KEY: z.string().min(1),

  // Logging
  LOG_LEVEL: z.enum(['DEBUG', 'INFO', 'WARN', 'ERROR']).default('INFO'),
  LOG_RETENTION_DAYS: z.number().int().positive().default(30),
})

export type Env = z.infer<typeof envSchema>
```

**Required .env variables:**
```bash
# Database
ALEXANDRIA_DB_URL=postgresql://alexandria:password@localhost:5432/alexandria

# External Services
OPENAI_API_KEY=sk-...

# Logging (optional)
LOG_LEVEL=INFO
LOG_RETENTION_DAYS=30
```

### TypeScript Configuration

```json
// tsconfig.json
{
  "compilerOptions": {
    "target": "ESNext",
    "module": "ESNext",
    "lib": ["ESNext"],
    "moduleResolution": "bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "sourceMap": true,

    // Path alias
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  },
  "include": ["src/**/*", "tests/**/*"],
  "exclude": ["node_modules"]
}
```

**Critical compiler options:**
- `strict: true` - Enables all strict type-checking options
- `paths: "@/*"` - Absolute imports (no relative imports `../../../../`)
- `moduleResolution: bundler` - Bun-specific module resolution

### Dev Scripts (package.json)

```json
{
  "scripts": {
    "dev": "bun run --hot src/index.ts",
    "start": "bun run src/index.ts",
    "typecheck": "tsc --noEmit",
    "lint": "eslint src tests --ext .ts",
    "format": "prettier --write 'src/**/*.ts' 'tests/**/*.ts'",
    "test": "bun test",
    "test:unit": "bun test tests/unit",
    "test:integration": "bun test tests/integration",
    "test:arch": "bun test tests/architecture"
  }
}
```

### Git Configuration

```.gitignore
# Dependencies
node_modules/
bun.lockb.bak

# Environment
.env
.env.local

# Logs
logs/*.jsonl

# Build
dist/
*.tsbuildinfo

# IDE
.vscode/
.idea/
*.swp
*.swo

# OS
.DS_Store
Thumbs.db

# Testing
coverage/
.nyc_output/
```

### Links to Architecture Documents

**Source Documentation:**
- [Core Architectural Decisions](_bmad-output/project-planning-artifacts/architecture/core-architectural-decisions.md)
  - Stack Technique (section "Data Architecture")
  - Logging Strategy (section "Infrastructure & Deployment - Logging Strategy")

- [Project Structure & Boundaries](_bmad-output/project-planning-artifacts/architecture/project-structure-boundaries.md)
  - Complete directory structure (section "Complete Project Directory Structure")
  - Architectural boundaries (section "Architectural Boundaries")
  - Development workflow (section "Development Workflow Integration")

- [Naming Patterns](_bmad-output/project-planning-artifacts/architecture/implementation-patterns-consistency-rules/naming-patterns.md)
  - Database naming (section "Database Naming Conventions")
  - TypeScript naming (section "TypeScript Code Naming Conventions")
  - MCP protocol naming (section "MCP Protocol Naming Conventions")

- [Structure Patterns](_bmad-output/project-planning-artifacts/architecture/implementation-patterns-consistency-rules/structure-patterns.md)
  - Import path organization (section "Import Path Organization")
  - Test organization (section "Test File Organization")

### Critical Architecture Rules (Enforced in Story 1.6)

These rules will be automatically enforced by Dependency Cruiser tests:

1. **Domain Isolation**: Domain layer MUST NOT import from adapters/, Zod, Drizzle, or Hono
2. **Ports Purity**: Ports MUST be pure TypeScript interfaces with no implementations
3. **Dependency Direction**: Adapters depend on Ports, Ports define contracts for Domain
4. **Naming Enforcement**: All ports MUST have "Port" suffix (ConventionRepositoryPort)

### Success Criteria

✅ **Structure created and validated:**
- All src/ subdirectories created (domain, ports, adapters, config, shared)
- Bun 1.3.5 installed, package.json configured
- TypeScript 5.9.7 with strict mode enabled
- Path alias @/* working correctly

✅ **Configuration functional:**
- .env.example committed with all required variables documented
- env.schema.ts validates environment on startup
- Application fails fast with clear error if env invalid

✅ **Documentation complete:**
- README.md with installation instructions
- Naming conventions documented
- Architecture constraints clearly stated

✅ **Ready for next story:**
- Directory structure ready for Story 1.2 (PostgreSQL setup)
- Domain structure ready for Story 1.3 (Entities and Ports)
- Test structure ready for Story 1.6 (Architecture validation)

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.5 (claude-sonnet-4-5-20250929)

### Debug Log References

- Task 1: Package.json version corrections (TypeScript 5.9.3, tsarch instead of Dependency Cruiser)
- Task 5: Zod 4.2.1 API differences (.issues instead of .errors, ZodIssue type import)

### Completion Notes List

✅ **Task 1: Initialize Bun project** (Commit: efa5493)
- Created package.json with exact dependency versions
- Installed Bun dependencies successfully (232 packages)
- Configured all required dev scripts (dev, test, typecheck, lint, format)
- Note: Used TypeScript 5.9.3 (5.9.7 unavailable), tsarch 5.4.1 (Dependency Cruiser package doesn't exist)

✅ **Task 2: Configure TypeScript strict mode** (Commit: 150fb6d)
- Created tsconfig.json with strict: true enabled
- Configured path alias @/* → src/*
- Set moduleResolution: bundler for Bun compatibility
- All TypeScript strict checks enabled

✅ **Task 3: Create hexagonal directory structure** (Commit: 5aaaa1d)
- Created complete src/ structure: domain/, ports/, adapters/, config/, shared/
- Created tests/ structure: unit/, integration/, architecture/, fixtures/
- Created scripts/, logs/, docs/ directories
- Added .gitkeep to logs/ directory

✅ **Task 4: Setup environment configuration** (Commit: 0732225)
- Created .env.example with all required variables documented
- Created comprehensive .gitignore (dependencies, environment, logs, build, IDE, OS, testing)
- Documented all required environment variables with examples

✅ **Task 5: Implement Zod environment validation** (Commit: b449259)
- Created src/config/env.schema.ts with Zod 4.2.1 schema
- Implemented fail-fast validation on startup with clear error messages
- Exported typed Env configuration object
- Created src/config/constants.ts with application-wide constants
- Created base error classes: DomainError and InfrastructureError
- Created minimal src/index.ts demonstrating startup validation
- TypeScript typecheck passes successfully

✅ **Task 6: Document naming conventions** (Commit: 382f920)
- Created comprehensive CONVENTIONS.md (160 lines)
- Documented TypeScript naming (PascalCase, camelCase, UPPER_SNAKE_CASE)
- Documented Database naming (camelCase columns, lowercase tables)
- Documented MCP Protocol naming (snake_case tools, camelCase JSON)
- Documented mandatory "Port" suffix for all ports
- Documented import organization and architecture rules

✅ **Task 7: Create initial README.md** (Commit: 80107e2)
- Updated README.md with comprehensive installation instructions
- Added Bun, PostgreSQL, pgvector installation steps
- Documented environment setup with .env configuration
- Added all development workflow commands
- Documented complete project structure
- Added architecture rules and documentation links
- Linked to sprint-status.yaml for project tracking

### File List

Files created/modified by this story:

**Project Configuration:**
- `package.json` - Bun project configuration with dependencies and scripts
- `bun.lock` - Bun lockfile (232 packages)
- `tsconfig.json` - TypeScript strict mode configuration
- `.env.example` - Environment variables template
- `.gitignore` - Git ignore rules

**Documentation:**
- `README.md` - Project documentation with installation and setup instructions
- `CONVENTIONS.md` - Comprehensive naming conventions and architecture rules

**Source Code:**
- `src/index.ts` - Application entry point with environment validation
- `src/config/env.schema.ts` - Zod schema for environment validation
- `src/config/constants.ts` - Application-wide constants
- `src/shared/errors/DomainError.ts` - Base domain error class
- `src/shared/errors/InfrastructureError.ts` - Base infrastructure error class

**Directory Structure Created:**
- `src/domain/{entities,value-objects,use-cases,errors}/`
- `src/ports/{primary,secondary}/`
- `src/adapters/{primary/mcp-server,secondary/{database,embedding,logging}}/`
- `src/config/`
- `src/shared/{errors,types,utils}/`
- `tests/{unit,integration,architecture,fixtures}/`
- `scripts/`
- `logs/` (with .gitkeep)
- `docs/{api,setup}/`

**Total:** 7 commits, 11 files created/modified, complete hexagonal architecture structure
