# Naming Patterns

## **Database Naming Conventions (Drizzle ORM + PostgreSQL)**

**Décision:** TypeScript-First - camelCase partout

```typescript
// ✅ CORRECT: camelCase columns (quoted in PostgreSQL)
export const conventions = pgTable('conventions', {
  id: uuid('id').primaryKey(),
  projectId: uuid('projectId').notNull(),              // camelCase
  contentText: text('contentText').notNull(),
  embeddingVector: vector('embeddingVector', { dimensions: 1536 }),
  createdAt: timestamp('createdAt').defaultNow()
})

// Migration SQL générée:
CREATE TABLE conventions (
  id UUID PRIMARY KEY,
  "projectId" UUID NOT NULL,           -- Quoted camelCase
  "contentText" TEXT NOT NULL,
  "embeddingVector" vector(1536),
  "createdAt" TIMESTAMP DEFAULT NOW()
);

// Queries Drizzle
db.select()
  .from(conventions)
  .where(eq(conventions.projectId, projectId))  // TypeScript idiomatic
  .orderBy(sql`"embeddingVector" <=> ${queryEmbedding}`)

// ❌ INCORRECT: snake_case
export const conventions = pgTable('conventions', {
  project_id: uuid('project_id').notNull(),    // ❌ snake_case
  created_at: timestamp('created_at')
})
```

**Rationale:**
- Cohérence totale TypeScript code ↔ Database
- Type-safety end-to-end (Drizzle inferred types → Domain entities)
- Pas d'SQL brut externe (Drizzle queries uniquement)
- Drizzle Studio compatible

**Règles obligatoires:**
- ✅ Toutes les colonnes en camelCase
- ✅ Tables en lowercase (ex: `conventions`, `documentation`)
- ✅ Index: camelCase avec prefix (ex: `idxConventionsProjectId`)
- ✅ Foreign keys: camelCase (ex: `projectId`, pas `project_id`)

---

## **TypeScript Code Naming Conventions**

**File Naming - PascalCase:**
```typescript
// ✅ CORRECT
src/domain/entities/Convention.ts
src/domain/use-cases/layer1/SearchConventions.ts
src/ports/secondary/ConventionRepositoryPort.ts
src/adapters/secondary/database/repositories/DrizzleConventionRepository.ts

// ❌ INCORRECT
src/domain/entities/convention.ts           // ❌ kebab-case
src/domain/use-cases/layer1/search-conventions.ts
```

**Variables & Functions - camelCase:**
```typescript
// ✅ CORRECT
const projectId = 'proj-123'
const embeddingVector = [0.1, 0.2, ...]
function searchConventions(query: string) { }
async function generateEmbedding(text: string): Promise<number[]> { }

// ❌ INCORRECT
const project_id = 'proj-123'              // ❌ snake_case
function SearchConventions() { }           // ❌ PascalCase (réservé classes)
```

**Classes, Interfaces, Types - PascalCase:**
```typescript
// ✅ CORRECT
class Convention { }
interface ConventionRepositoryPort { }
type ProjectId = string

// ❌ INCORRECT
class convention { }                       // ❌ camelCase
interface conventionRepositoryPort { }     // ❌ camelCase
interface IConventionRepository { }        // ❌ Prefix I (anti-pattern)
```

**Constants - UPPER_SNAKE_CASE:**
```typescript
// src/config/constants.ts
// ✅ CORRECT
export const DEFAULT_TOP_K = 10
export const MAX_TOP_K = 100
export const MAX_QUERY_LENGTH = 500
export const EMBEDDING_DIMENSIONS = 1536
export const HNSW_M_PARAMETER = 16
export const HNSW_EF_CONSTRUCTION = 64

// ❌ INCORRECT
export const defaultTopK = 10              // ❌ camelCase
export const max_top_k = 100               // ❌ lowercase snake_case
```

**Ports - Suffix "Port" obligatoire:**
```typescript
// ✅ CORRECT
interface ConventionRepositoryPort { }
interface EmbeddingGeneratorPort { }
interface LoggerPort { }

// ❌ INCORRECT
interface ConventionRepository { }         // ❌ Pas de suffix (confusion avec implémentation)
interface IConventionRepository { }        // ❌ Prefix I
```

---

## **MCP Protocol Naming Conventions**

**MCP Tool Names - snake_case:**
```typescript
// ✅ CORRECT (API boundary)
app.post('/retrieve_raw_context', ...)
app.post('/upload_convention', ...)
app.post('/list_conventions', ...)
app.delete('/delete_convention', ...)
app.get('/list_projects', ...)

// ❌ INCORRECT
app.post('/retrieveRawContext', ...)      // ❌ camelCase (non-standard pour APIs)
```

**JSON Field Names - camelCase:**
```typescript
// ✅ CORRECT (cohérence avec Database + TypeScript)
// Input
{
  "query": "hexagonal architecture",
  "projectId": "abc-123",                  // camelCase
  "topK": 10
}

// Output
{
  "conventions": [
    {
      "id": "conv-1",
      "projectId": "abc-123",              // camelCase
      "contentText": "...",
      "embeddingVector": [...],
      "createdAt": "2025-12-26T10:30:00Z"
    }
  ],
  "linkedDocumentation": [...]
}

// Zod schema
const retrieveInputSchema = z.object({
  query: z.string().min(1).max(500),
  projectId: z.string().uuid(),            // camelCase
  topK: z.number().int().positive().max(100).optional()
})

// ❌ INCORRECT
{
  "project_id": "abc-123",                 // ❌ snake_case (incohérent avec DB)
  "top_k": 10
}
```

**Séparation claire:**
- **API tool names** (externe): snake_case (`retrieve_raw_context`)
- **JSON fields** (data): camelCase (`projectId`, `topK`)
- **TypeScript code** (interne): camelCase (`searchConventions()`)

---
