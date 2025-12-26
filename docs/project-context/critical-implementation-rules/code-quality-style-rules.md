# Code Quality & Style Rules

## 1. Linting avec Biome (Bun-First)

```json
// biome.json
{
  "$schema": "https://biomejs.dev/schemas/1.9.4/schema.json",
  "organizeImports": { "enabled": true },
  "linter": {
    "enabled": true,
    "rules": {
      "recommended": true,
      "suspicious": {
        "noExplicitAny": "error",
        "noDebugger": "error"
      },
      "correctness": {
        "noUnusedVariables": "error",
        "noUnreachable": "error"
      },
      "style": {
        "useConst": "error",
        "noVar": "error"
      }
    }
  },
  "formatter": {
    "enabled": true,
    "indentStyle": "space",
    "indentWidth": 2,
    "lineWidth": 100
  }
}
```

**Pourquoi** : Biome est 3x plus rapide que ESLint+Prettier pour Bun. NFR22 exige conventions de nommage cohérentes et linting. Zero warnings tolérés en CI/CD.

## 2. Code Organization (Architecture Hexagonale)

```
src/
├── domain/                     # Pure business logic, ZERO dependencies
│   ├── entities/
│   │   ├── Convention.ts
│   │   └── Documentation.ts
│   ├── value-objects/
│   │   ├── ConventionContent.ts
│   │   ├── Embedding.ts
│   │   └── ProjectId.ts
│   └── ports/                  # Interfaces abstraites
│       ├── ConventionRepositoryPort.ts
│       ├── DocumentationRepositoryPort.ts
│       ├── EmbeddingGeneratorPort.ts
│       └── LoggerPort.ts
├── application/                # Use cases orchestration
│   └── use-cases/
│       ├── RetrieveRawContextUseCase.ts
│       ├── ValidateCodeUseCase.ts
│       └── UploadConventionUseCase.ts
├── adapters/
│   ├── primary/                # Driving adapters (MCP server)
│   │   └── mcp-server/
│   │       ├── server.ts
│   │       └── tools/
│   │           ├── retrieve.ts
│   │           ├── validate.ts
│   │           └── upload.ts
│   └── secondary/              # Driven adapters (infrastructure)
│       ├── drizzle/
│       │   ├── DrizzleConventionAdapter.ts
│       │   ├── DrizzleDocumentationAdapter.ts
│       │   └── schemas/
│       ├── openai/
│       │   └── OpenAIEmbeddingAdapter.ts
│       └── logging/
│           └── BunConsoleLoggerAdapter.ts
├── config/
│   └── env.ts                  # Zod validation environment
└── index.ts                    # Composition root UNIQUEMENT
```

**Pourquoi** : Architecture hexagonale exige séparation stricte domain/application/adapters. NFR22 demande organisation claire des responsabilités.

## 3. Naming Conventions (Strictes)

```typescript
// ✅ CORRECT - Entities et Value Objects: PascalCase
class Convention { }
class ConventionContent { }
class Embedding { }

// ✅ CORRECT - Ports: Interface avec Port suffix
interface ConventionRepositoryPort { }
interface EmbeddingGeneratorPort { }

// ✅ CORRECT - Adapters: Adapter suffix
class DrizzleConventionAdapter implements ConventionRepositoryPort { }
class OpenAIEmbeddingAdapter implements EmbeddingGeneratorPort { }

// ✅ CORRECT - Use Cases: UseCase suffix
class RetrieveRawContextUseCase { }
class ValidateCodeUseCase { }

// ✅ CORRECT - Fichiers: kebab-case
// convention-repository.port.ts
// drizzle-convention.adapter.ts
// retrieve-raw-context.use-case.ts

// ✅ CORRECT - Tests: .test.ts ou .spec.ts
// convention.test.ts
// retrieve-raw-context.use-case.test.ts

// ✅ CORRECT - Constantes: SCREAMING_SNAKE_CASE
const MAX_EMBEDDING_DIMENSIONS = 1536;
const DEFAULT_TOP_K = 10;

// ❌ INCORRECT - Inconsistent naming
class conventionRepository { } // ❌ Should be PascalCase
interface IConventionRepo { } // ❌ No I prefix, use Port suffix
class DrizzleRepo { } // ❌ Not descriptive, missing Adapter suffix
```

**Pourquoi** : NFR22 exige conventions de nommage cohérentes. Consistance facilite navigation codebase et compréhension architecture hexagonale.

## 4. Documentation Requirements (NFR21)

```typescript
/**
 * Retrieves raw context (conventions + documentation) for a given project and query.
 *
 * This use case implements the Active Compliance Filter's Layer 1+2:
 * - Layer 1: Vector search on conventions (pgvector HNSW)
 * - Layer 2: JOIN documentation via convention_technologies
 *
 * @param projectId - Unique identifier of the project
 * @param query - Natural language query for context retrieval
 * @param options - Optional retrieval options (topK, filters)
 * @returns RawContext containing conventions and linked documentation
 * @throws ProjectNotFoundError if project doesn't exist
 *
 * @example
 * ```typescript
 * const context = await useCase.execute('project-1', 'TypeScript error handling');
 * // Returns: { conventions: [...], documentation: [...] }
 * ```
 */
export class RetrieveRawContextUseCase {
  /**
   * @param conventionRepo - Port for convention persistence
   * @param docRepo - Port for documentation retrieval
   * @param embeddingGen - Port for query embedding generation
   * @param logger - Port for structured logging
   */
  constructor(
    private readonly conventionRepo: ConventionRepositoryPort,
    private readonly docRepo: DocumentationRepositoryPort,
    private readonly embeddingGen: EmbeddingGeneratorPort,
    private readonly logger: LoggerPort
  ) {}

  async execute(projectId: string, query: string): Promise<RawContext> {
    // Complex RAG logic requires inline comments
    // Step 1: Generate query embedding for vector search
    const embedding = await this.embeddingGen.generate(query);

    // Step 2: Layer 1 - HNSW vector search on conventions (NFR2: ≤1s)
    const conventions = await this.conventionRepo.search(projectId, embedding, 10);

    // Step 3: Layer 2 - JOIN documentation via technologies
    const techIds = [...new Set(conventions.flatMap(c => c.technologies))];
    const docs = await this.docRepo.findByTechnologies(techIds);

    return { conventions, documentation: docs };
  }
}
```

**Pourquoi** : NFR21 exige JSDoc pour fonctions publiques et commentaires inline pour logique RAG complexe. Facilite compréhension architecture 3 layers.

## 5. Import Organization

```typescript
// ✅ CORRECT - Imports groupés et triés
// 1. Node/Bun built-ins
import { createHash } from 'crypto';

// 2. External dependencies
import { drizzle } from 'drizzle-orm/bun-sqlite';
import { z } from 'zod';

// 3. Internal - Domain
import { Convention } from '@/domain/entities/Convention';
import { ConventionRepositoryPort } from '@/domain/ports/ConventionRepositoryPort';

// 4. Internal - Application
import { RetrieveRawContextUseCase } from '@/application/use-cases/RetrieveRawContextUseCase';

// 5. Internal - Adapters
import { DrizzleConventionAdapter } from '@/adapters/secondary/drizzle/DrizzleConventionAdapter';

// 6. Types only
import type { DrizzleDB } from '@/types/drizzle';

// ❌ INCORRECT - Mélangés et désordonnés
import { Convention } from '@/domain/entities/Convention';
import { z } from 'zod';
import { createHash } from 'crypto';
import type { DrizzleDB } from '@/types/drizzle';
```

**Pourquoi** : Biome `organizeImports: true` trie automatiquement. Groupement facilite lecture et détecte violations de dépendances (ex: domain important adapter).

## 6. File Length Limits

```typescript
// ✅ CORRECT - Fichier focalisé, <300 lignes
// convention-repository.port.ts (interface = court)
// drizzle-convention.adapter.ts (<250 lignes)

// ⚠️ WARNING - Fichier dépassant 300 lignes
// Si use case dépasse 300 lignes, décomposer en sous-services

// ❌ INCORRECT - God class de 1000+ lignes
// Signe de violation Single Responsibility Principle
```

**Pourquoi** : Fichiers <300 lignes = maintenabilité (NFR21-25). Si dépassement, décomposer responsabilités.

## 7. Error Handling with Custom Errors

```typescript
// src/domain/errors/ProjectNotFoundError.ts
export class ProjectNotFoundError extends Error {
  constructor(public readonly projectId: string) {
    super(`Project "${projectId}" not found`);
    this.name = 'ProjectNotFoundError';
  }
}

// src/domain/errors/ConventionNotFoundError.ts
export class ConventionNotFoundError extends Error {
  constructor(public readonly conventionId: string) {
    super(`Convention "${conventionId}" not found`);
    this.name = 'ConventionNotFoundError';
  }
}

// Usage
async findById(id: string): Promise<Convention> {
  const result = await this.db.query(...);
  if (!result) {
    throw new ConventionNotFoundError(id);
  }
  return Convention.create(result);
}
```

**Pourquoi** : NFR17 exige error messages de qualité avec contexte actionnable. Custom errors facilitent handling spécifique et logging.

## 8. Magic Numbers/Strings Interdits

```typescript
// ✅ CORRECT - Constantes nommées
const MAX_EMBEDDING_DIMENSIONS = 1536;
const DEFAULT_TOP_K = 10;
const MAX_QUERY_LENGTH = 500;
const OPENAI_MODEL = 'text-embedding-3-small' as const;

const embedding = await generate(query);
expect(embedding).toHaveLength(MAX_EMBEDDING_DIMENSIONS);

// ❌ INCORRECT - Magic numbers/strings
const embedding = await generate(query);
expect(embedding).toHaveLength(1536); // ❌ Pourquoi 1536?

await client.embeddings.create({
  model: 'text-embedding-3-small', // ❌ Hardcoded string
  input: query
});
```

**Pourquoi** : Constantes nommées = intention explicite. Facilite maintenance (changement en un seul endroit).

## 9. Code Coverage Thresholds (NFR23)

```toml
# bunfig.toml
[test]
coverage = true
coverageReporter = ["text", "lcov", "html"]
coverageThreshold = {
  lines = 80,
  functions = 85,
  branches = 75,
  statements = 80
}
coverageSkipTestFiles = true
```

**CI/CD Enforcement:**

```yaml
# .github/workflows/test.yml
- name: Run tests with coverage
  run: bun test --coverage

- name: Check coverage thresholds
  run: |
    if ! bun test --coverage --coverage-reporter=json-summary; then
      echo "❌ Coverage below thresholds"
      exit 1
    fi
```

**Pourquoi** : NFR23 exige tests unitaires et d'intégration. Thresholds garantissent coverage minimum avant merge.

## 10. Git Hooks avec Husky + lint-staged

```json
// package.json
{
  "lint-staged": {
    "*.ts": [
      "biome check --write",
      "bun test --related"
    ]
  }
}
```

```bash
# .husky/pre-commit
#!/usr/bin/env sh
bun run lint-staged
```

**Pourquoi** : Format auto + tests liés avant commit. Évite merge de code non-formatté ou cassant tests (NFR22).

## 11. Code Review Checklist

Chaque PR doit valider:

- [ ] ✅ Architecture hexagonale respectée (pas d'import domain → adapter)
- [ ] ✅ Tests unitaires ajoutés (Domain 100%, Application >85%)
- [ ] ✅ JSDoc pour nouvelles fonctions publiques
- [ ] ✅ Pas de `any`, `!`, `@ts-ignore` sans justification
- [ ] ✅ Performance NFRs respectées (si code Layer 1-3)
- [ ] ✅ MCP protocol compliance (si modification tools)
- [ ] ✅ Biome check pass (zero warnings)
- [ ] ✅ Coverage thresholds maintenus

**Pourquoi** : NFR21-25 exigent maintenabilité et documentation. Checklist garantit qualité constante.
