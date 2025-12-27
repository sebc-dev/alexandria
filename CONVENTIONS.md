# Alexandria - Naming Conventions

Ce document définit les conventions de nommage strictes pour le projet Alexandria. Ces règles sont **non-négociables** et seront validées automatiquement par les tests d'architecture (Story 1.6).

## TypeScript Code

### Files
- **Pattern**: PascalCase
- **Examples**:
  - `Convention.ts`
  - `SearchConventions.ts`
  - `ConventionRepositoryPort.ts`

### Classes, Interfaces, Types
- **Pattern**: PascalCase
- **Examples**:
  - `Convention`
  - `ConventionRepositoryPort`
  - `EmbeddingVector`

### Variables & Functions
- **Pattern**: camelCase
- **Examples**:
  - `projectId`
  - `searchConventions()`
  - `generateEmbedding()`

### Constants
- **Pattern**: UPPER_SNAKE_CASE
- **Examples**:
  - `DEFAULT_TOP_K`
  - `MAX_QUERY_LENGTH`
  - `EMBEDDING_DIMENSIONS`

### Ports (Critical)
- **Pattern**: PascalCase with **mandatory "Port" suffix**
- **Examples**:
  - `ConventionRepositoryPort`
  - `EmbeddingServicePort`
  - `LoggerPort`
- **Rationale**: Enforces clear distinction between ports (interfaces) and implementations

## Database (Drizzle ORM)

### Table Names
- **Pattern**: lowercase (singular or plural based on context)
- **Examples**:
  - `conventions`
  - `documentation`
  - `projects`

### Column Names
- **Pattern**: camelCase with **quotes in schema definition**
- **Examples**:
  ```typescript
  projectId: varchar('projectId', { length: 255 })
  contentText: text('contentText')
  embeddingVector: vector('embeddingVector', { dimensions: 1536 })
  createdAt: timestamp('createdAt')
  ```

### Index Names
- **Pattern**: camelCase with descriptive prefix
- **Examples**:
  - `idxConventionsProjectId`
  - `idxConventionsEmbedding`
  - `idxDocumentationTechnologyId`

## MCP Protocol

### Tool Names
- **Pattern**: snake_case
- **Examples**:
  - `retrieve_raw_context`
  - `upload_convention`
  - `list_projects`

### JSON Fields
- **Pattern**: camelCase
- **Examples**:
  ```json
  {
    "projectId": "alexandria",
    "topK": 5,
    "contentText": "..."
  }
  ```

## Test Files

### File Naming
- **Pattern**: Same name as file under test + `.test.ts` suffix
- **Examples**:
  - `Convention.test.ts`
  - `SearchConventions.test.ts`
  - `ConventionRepository.test.ts`

### Test Blocks
- **Pattern**:
  - `describe()`: PascalCase (matches class/file name)
  - `it()`: lowercase sentence starting with "should"
- **Example**:
  ```typescript
  describe('SearchConventions', () => {
    it('should return top K conventions by similarity', () => {
      // ...
    })
  })
  ```

## Architecture-Specific Conventions

### Domain Layer
- **MUST NOT** depend on external libraries (Zod, Drizzle, Hono, OpenAI SDK)
- **MUST NOT** import from `adapters/` or infrastructure code
- Pure TypeScript only

### Ports
- **MUST** be pure TypeScript interfaces (no implementations)
- **MUST** have "Port" suffix
- **MUST NOT** depend on adapters or infrastructure

### Adapters
- **CAN** depend on external libraries
- **MUST** implement ports from `ports/` layer
- **MUST** use Zod for validation at boundaries

## Import Organization

### Path Aliases
- Use `@/*` for absolute imports from `src/`
- **Prefer**: `import { Convention } from '@/domain/entities/Convention'`
- **Avoid**: `import { Convention } from '../../../../domain/entities/Convention'`

### Import Order
1. External libraries (Node.js built-ins, npm packages)
2. Internal absolute imports (`@/domain`, `@/ports`, `@/adapters`)
3. Relative imports (if necessary)

**Example**:
```typescript
import { z } from 'zod'
import { openai } from 'openai'

import { Convention } from '@/domain/entities/Convention'
import { ConventionRepositoryPort } from '@/ports/secondary/ConventionRepositoryPort'

import { config } from './config'
```

## Enforcement

Ces conventions seront automatiquement validées par :

1. **TypeScript Compiler** (`strict: true` mode)
2. **ESLint** (import rules, naming patterns)
3. **ts-arch** (Story 1.6 - architecture compliance tests)
4. **Code Review** (CodeRabbit AI + manual review)

Toute violation bloque la CI/CD pipeline.
