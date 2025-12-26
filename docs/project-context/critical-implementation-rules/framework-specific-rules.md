# Framework-Specific Rules

## 1. Hono - Type Preservation avec Method Chaining

```typescript
// ✅ CORRECT - Chain methods for RPC type preservation
const app = new Hono()
  .get('/retrieve', retrieveHandler)
  .post('/upload', uploadHandler)
  .delete('/delete/:id', deleteHandler);

export type AppType = typeof app;

// ❌ INCORRECT - Breaks RPC types
const app = new Hono();
app.get('/retrieve', retrieveHandler);
app.post('/upload', uploadHandler);
```

**Pourquoi** : Le chaining préserve l'inférence de types pour le client MCP. Casser la chaîne perd les types de routes.

## 2. Hono - Context Typing pour Dependency Injection

```typescript
type Env = {
  Variables: {
    conventionRepo: ConventionRepositoryPort;
    docRepo: DocumentationRepositoryPort;
    embeddingGen: EmbeddingGeneratorPort;
    logger: LoggerPort;
  };
  Bindings: {
    DATABASE_URL: string;
    OPENAI_API_KEY: string;
  };
};

const app = new Hono<Env>();

// Usage dans un handler
app.post('/retrieve', async (c) => {
  const repo = c.get('conventionRepo');  // Type-safe!
});
```

**Pourquoi** : Dependency injection type-safe via Hono context. Évite les `any` et permet autocomplete sur les services.

## 3. Drizzle - HNSW Index Usage (CRITIQUE PERFORMANCE)

```typescript
// ✅ CORRECT - Uses HNSW index (NFR2: Layer 1 ≤1s)
const results = await db
  .select()
  .from(conventions)
  .orderBy(cosineDistance(conventions.embedding, queryVector))
  .limit(10);

// ❌ INCORRECT - Does NOT use index, scans entire table
const results = await db
  .select()
  .from(conventions)
  .orderBy(desc(sql`1 - ${cosineDistance(conventions.embedding, queryVector)}`))
  .limit(10);
```

**Pourquoi** : NFR2 exige Layer 1 vector search ≤1s. Ordering directement par distance utilise l'index HNSW. Calculer `1 - distance` force un table scan complet, performance s'effondre avec >10k embeddings.

## 4. Drizzle - Identity Columns (PostgreSQL 17)

```typescript
// ✅ CORRECT - PostgreSQL 17 modern pattern
export const conventions = pgTable('conventions', {
  id: integer('id').primaryKey().generatedAlwaysAsIdentity(),
  projectId: text('project_id').notNull(),
  content: text('content').notNull(),
  embedding: vector('embedding', { dimensions: 1536 }),
});

// ❌ INCORRECT - Deprecated pattern
id: serial('id').primaryKey(),
```

**Pourquoi** : Alexandria utilise PostgreSQL 17.7. `serial` est legacy, `generatedAlwaysAsIdentity()` est la norme recommandée.

## 5. Drizzle - Transaction Pattern (tx vs db)

```typescript
// ✅ CORRECT - Use tx object exclusively inside transaction
await db.transaction(async (tx) => {
  const doc = await tx.insert(conventions).values(data).returning();
  await tx.insert(embeddings).values({ conventionId: doc.id, vector });
  // tx.rollback() throws - don't await it
});

// ❌ INCORRECT - Using db inside transaction breaks atomicity
await db.transaction(async (tx) => {
  await tx.insert(conventions).values(data);
  await db.insert(embeddings).values(...);  // ❌ db au lieu de tx!
});
```

**Pourquoi** : NFR18 exige transactions pour upload + embedding generation. Utiliser `db` au lieu de `tx` casse l'atomicité et peut créer des données orphelines.

## 6. MCP Protocol - Stdout vs Stderr (BLOQUANT)

```typescript
// ✅ CORRECT - All logging to stderr
console.error('[INFO] Processing retrieve request');
console.error('[ERROR] Convention not found:', id);

// ❌ INCORRECT - Breaks protocol, corrupts message stream
console.log('Processing retrieve request');  // ❌ JAMAIS!
```

**Pourquoi** : **HARD REQUIREMENT** du protocole MCP. Stdout est réservé exclusivement aux messages JSON-RPC. Un seul `console.log()` corrompt le stream et casse toute communication avec Claude Code.

## 7. MCP - Tool Errors vs Protocol Errors

```typescript
// ✅ CORRECT - Tool execution failure (recoverable by Claude)
return {
  isError: true,
  content: [{
    type: 'text',
    text: `Document "${id}" not found. Available documents: ${suggestions.join(', ')}`
  }]
};

// ✅ CORRECT - Protocol error (malformed request only)
if (!validToolName) {
  throw new Error(`Unknown tool: ${toolName}`);
}

// ❌ INCORRECT - Throwing on business logic failure
if (!document) {
  throw new Error('Document not found');  // ❌ Use isError instead
}
```

**Pourquoi** : Tool errors (`isError: true`) permettent à Claude de voir l'erreur et potentiellement récupérer (ex: retry avec ID différent). Protocol errors cassent complètement l'invocation.

## 8. Zod 4.2.1 - Nouvelle Syntaxe (Breaking Change)

```typescript
// ✅ CORRECT - Zod 4.2.1 syntax (Alexandria uses this)
const schema = z.object({
  projectId: z.string({ error: 'Project ID is required' }),
  query: z.string({ error: 'Query cannot be empty' }),
  topK: z.number().min(1).max(50).default(10),
});

// ❌ INCORRECT - Zod 3 syntax (deprecated in v4)
const schema = z.object({
  projectId: z.string({ message: 'Project ID is required' }),  // ❌
  query: z.string({ required_error: 'Query required' }),       // ❌
});
```

**Pourquoi** : Alexandria utilise Zod 4.2.1 avec breaking changes depuis v3. La syntaxe `{ message, required_error, invalid_type_error }` ne fonctionne plus, utiliser `{ error }`.

## 9. Zod - Environment Validation (Fail Fast)

```typescript
// src/config/env.ts
import { z } from 'zod';

const envSchema = z.object({
  DATABASE_URL: z.string().url(),
  OPENAI_API_KEY: z.string().min(1),
  DEBUG: z.stringbool().default(false),  // ✅ z.stringbool() for env vars
  PORT: z.coerce.number().default(3000),
});

const result = envSchema.safeParse(process.env);
if (!result.success) {
  console.error('❌ Invalid environment:', result.error.flatten());
  process.exit(1);  // Fail fast (NFR7)
}

export const env = result.data;
```

**Pourquoi** : NFR7 exige fail fast au startup si credentials manquantes. `z.stringbool()` parse correctement `"false"`, `"0"`, `"no"` comme `false` (contrairement à `z.coerce.boolean()`).

## 10. Architecture Hexagonale - Ports sans Types Framework

```typescript
// ✅ CORRECT - Port abstrait, pas de types Drizzle
export interface ConventionRepositoryPort {
  findById(id: string): Promise<Convention | null>;
  search(projectId: string, embedding: number[], topK: number): Promise<Convention[]>;
  save(convention: Convention): Promise<void>;
}

// ❌ INCORRECT - Leaky abstraction, expose Drizzle
export interface ConventionRepositoryPort {
  findAll(): Promise<typeof conventions.$inferSelect[]>;  // ❌ Type Drizzle!
  query(q: SQLWrapper): Promise<any>;  // ❌ Expose SQL!
}
```

**Pourquoi** : Les ports du domaine doivent rester purs (pas de dépendances infrastructure). Exposer des types Drizzle, Zod, ou Hono dans les ports viole l'architecture hexagonale.

## 11. Architecture Hexagonale - Composition Root

```typescript
// src/index.ts - Bootstrap centralisé
export function createApp(): Hono {
  // Infrastructure layer - Adapters
  const db = drizzle(env.DATABASE_URL);
  const conventionRepo = new DrizzleConventionAdapter(db);
  const docRepo = new DrizzleDocumentationAdapter(db);
  const embeddingGen = new OpenAIEmbeddingAdapter(env.OPENAI_API_KEY);
  const logger = new BunConsoleLoggerAdapter();

  // Application layer - Use cases
  const retrieveContext = new RetrieveRawContextUseCase(
    conventionRepo,
    docRepo,
    embeddingGen,
    logger
  );

  // Presentation layer - MCP Server
  const app = new Hono<Env>();
  app.post('/retrieve', async (c) => {
    const input = retrieveInputSchema.parse(await c.req.json());
    const result = await retrieveContext.execute(input.projectId, input.query);
    return c.json(result);
  });

  return app;
}
```

**Pourquoi** : Le composition root (`src/index.ts`) est le SEUL endroit où les adapters concrets sont instanciés. Le reste du code dépend des ports abstraits injectés via constructeur.
