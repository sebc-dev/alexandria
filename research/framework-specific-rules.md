# Framework-Specific Rules for Alexandria's Hexagonal Architecture

Alexandria's governance tool stack—Hono, Drizzle, MCP, Zod, and Claude Code sub-agents—requires precise integration patterns to maintain hexagonal architecture purity while maximizing type safety. This framework establishes comprehensive rules for each technology layer, preventing common mistakes and ensuring consistent patterns across the codebase.

## Hono 4.11.1 patterns for type-safe web servers

Hono serves as Alexandria's HTTP adapter layer, translating external requests into application use cases. The framework's strength lies in its type inference system, but only when patterns are followed precisely.

**Context variables provide dependency injection.** Define a typed `Env` interface for all dependencies accessed via context:

```typescript
type Env = {
  Variables: {
    userService: UserService;
    documentRepository: DocumentRepository;
    requestId: string;
  };
  Bindings: {
    DATABASE_URL: string;
  };
};

const app = new Hono<Env>();
```

**Chain route methods for RPC type preservation.** Breaking the chain loses type inference:

```typescript
// ✅ Correct - preserves types for client generation
const app = new Hono()
  .get('/documents', handler)
  .post('/documents', handler)
  .get('/documents/:id', handler);

export type AppType = typeof app;

// ❌ Wrong - breaks RPC types
const app = new Hono();
app.get('/documents', handler);
app.post('/documents', handler);
```

**Keep handlers inline for automatic type inference.** Extracting handlers to separate functions loses path parameter typing:

```typescript
// ✅ Inline handler gets automatic param typing
app.get('/documents/:id', (c) => {
  const id = c.req.param('id'); // Typed as string
  return c.json({ id });
});

// ❌ Extracted handler loses param inference
const getDocument = (c: Context) => c.json({ id: c.req.param('id') });
app.get('/documents/:id', getDocument);
```

**Middleware registration order follows the onion model.** Place logging first, then CORS, security headers, authentication, then route-specific middleware. Error handling wraps all layers through `app.onError()`.

The Zod validator wrapper pattern ensures consistent error responses across all endpoints:

```typescript
import { zValidator as zv } from '@hono/zod-validator';
import { HTTPException } from 'hono/http-exception';

export const zValidator = <T extends ZodSchema, Target extends keyof ValidationTargets>(
  target: Target,
  schema: T
) => zv(target, schema, (result) => {
  if (!result.success) {
    throw new HTTPException(400, { cause: result.error });
  }
});
```

**Bun 1.3.5 runtime integration** requires exporting the app as the default export. Static file serving uses `hono/bun` adapter. Testing uses `app.fetch()` directly with `bun:test`.

## Drizzle ORM 0.36.4 with pgvector patterns

Drizzle serves as Alexandria's persistence adapter. Schema definitions must use modern PostgreSQL 17 patterns while maintaining strict type safety for vector operations.

**Use identity columns over serial for new tables.** PostgreSQL 17 recommends `generatedAlwaysAsIdentity()`:

```typescript
export const documents = pgTable('documents', {
  id: integer('id').primaryKey().generatedAlwaysAsIdentity(),
  title: text('title').notNull(),
  embedding: vector('embedding', { dimensions: 1536 }),
  createdAt: timestamp('created_at').defaultNow().notNull(),
});
```

**Type inference uses `$inferSelect` and `$inferInsert`.** These generate domain types from schema:

```typescript
export type Document = typeof documents.$inferSelect;
export type NewDocument = typeof documents.$inferInsert;
```

**HNSW indexes require specific operator classes.** For cosine similarity (Alexandria's primary use case), use `vector_cosine_ops`:

```typescript
export const documents = pgTable(
  'documents',
  { /* columns */ },
  (table) => [
    index('docs_embedding_idx')
      .using('hnsw', table.embedding.op('vector_cosine_ops')),
  ]
);
```

**Critical: Order by distance directly for index usage.** Computing `1 - cosineDistance()` bypasses the HNSW index entirely:

```typescript
// ✅ Uses HNSW index
const results = await db
  .select()
  .from(documents)
  .orderBy(cosineDistance(documents.embedding, queryVector))
  .limit(10);

// ❌ Does NOT use index - scans entire table
const results = await db
  .select()
  .from(documents)
  .orderBy(desc(sql`1 - ${cosineDistance(documents.embedding, queryVector)}`))
  .limit(10);
```

**Transactions use the `tx` object exclusively.** Never reference `db` inside transaction callbacks:

```typescript
await db.transaction(async (tx) => {
  await tx.insert(documents).values(doc);
  await tx.update(metadata).set({ count: sql`count + 1` });
  // tx.rollback() throws - don't await it
});
```

**Migration workflow uses generate-then-migrate.** Never use `drizzle-kit push` in production. Custom migrations handle pgvector extension creation and HNSW index parameters (`m`, `ef_construction`).

## MCP protocol implementation rules

MCP servers expose Alexandria's governance capabilities to Claude Code. The protocol has strict requirements that differ from typical HTTP APIs.

**Stdout is exclusively for JSON-RPC messages.** All logging must use stderr:

```typescript
// ✅ Correct
console.error('[INFO] Processing tool call');

// ❌ Breaks protocol - corrupts message stream
console.log('Processing tool call');
```

**Tool errors use `isError: true`, not protocol errors.** This allows Claude to see and potentially recover from failures:

```typescript
// Tool execution failure - visible to Claude
return {
  isError: true,
  content: [{
    type: 'text',
    text: `Document "${id}" not found. Available documents: ${suggestions.join(', ')}`
  }]
};

// Protocol error - for malformed requests only
throw new Error('Unknown tool: ' + toolName);
```

**Error messages must be actionable.** Help Claude recover by suggesting alternatives or explaining what went wrong specifically.

**Tool definitions require detailed descriptions.** The LLM uses these to decide when to invoke tools:

```typescript
server.registerTool(
  'search_documents',
  {
    title: 'Document Search',
    description: 'Search governance documents by semantic similarity. Use when looking for policies, guidelines, or rules related to a topic.',
    inputSchema: {
      query: z.string().describe('Natural language search query'),
      limit: z.number().min(1).max(50).default(10)
        .describe('Maximum results to return')
    }
  },
  handler
);
```

**Create connections per tool call, not at server startup.** Global connections make tool listing fail if dependencies are unavailable:

```typescript
// ✅ Self-contained tool
async ({ query }) => {
  const db = await createConnection();
  try {
    return await searchDocuments(db, query);
  } finally {
    await db.close();
  }
};

// ❌ Global connection - tool listing fails if DB down
const db = await createConnection(); // At module load
server.registerTool('search', { ... }, async ({ query }) => {
  return await searchDocuments(db, query);
});
```

## Zod 4.2.1 validation boundaries

Validation belongs at adapter boundaries—HTTP controllers, MCP tool handlers, environment loading—never in the domain layer. This maintains domain purity while ensuring all external input is validated before reaching business logic.

**Zod 4 uses unified `error` parameter.** The v3 pattern with separate `message`, `invalid_type_error`, and `required_error` is deprecated:

```typescript
// Zod 4 pattern
z.string({ error: 'Name is required' });
z.string({
  error: (iss) => iss.input === undefined 
    ? 'Required field' 
    : 'Must be a string'
});
```

**Environment validation uses `z.stringbool()` for booleans.** The coerce pattern treats any non-empty string as truthy:

```typescript
// ✅ Correctly parses "false", "0", "no" as false
const envSchema = z.object({
  DEBUG: z.stringbool().default(false),
  PORT: z.coerce.number().default(3000),
  DATABASE_URL: z.string().url(),
});

export const env = envSchema.parse(process.env);
```

**Fail fast at startup.** Validate environment on module load:

```typescript
const result = envSchema.safeParse(process.env);
if (!result.success) {
  console.error('Invalid environment:', result.error.flatten());
  process.exit(1);
}
```

**Compose schemas using spread over `.extend()`.** This is more efficient for TypeScript compilation:

```typescript
const BaseSchema = z.object({ id: z.string().uuid() });
const TimestampsSchema = z.object({
  createdAt: z.coerce.date(),
  updatedAt: z.coerce.date(),
});

// ✅ Spread is faster for tsc
const DocumentSchema = z.object({
  ...BaseSchema.shape,
  ...TimestampsSchema.shape,
  title: z.string(),
});
```

**Use branded types for domain identifiers.** This prevents mixing IDs from different entity types:

```typescript
const DocumentId = z.string().uuid().brand<'DocumentId'>();
const UserId = z.string().uuid().brand<'UserId'>();

type DocumentId = z.infer<typeof DocumentId>;
type UserId = z.infer<typeof UserId>;

// Compile-time error: types are incompatible
const docId: DocumentId = userId; // ❌
```

## Claude Code sub-agent architecture

Alexandria leverages sub-agents for specialized governance tasks. Agent and skill definitions follow specific file conventions.

**Project agents live in `.claude/agents/*.md` with YAML frontmatter:**

```markdown
---
name: governance-reviewer
description: Reviews code changes against governance policies. Use PROACTIVELY after any code modification.
tools: Read, Grep, Glob
model: sonnet
---

You are a governance compliance reviewer for the Alexandria project.

When reviewing code:
1. Check adherence to hexagonal architecture boundaries
2. Verify domain layer has no infrastructure imports
3. Ensure validation occurs at adapter boundaries only
4. Validate MCP tool implementations follow protocol rules
```

**Skills use directory structure with SKILL.md:**

```
.claude/skills/drizzle-patterns/
├── SKILL.md
├── schema-examples.md
└── migration-guide.md
```

**Progressive disclosure controls context loading.** Level 1 (name/description) loads at startup; Level 2 (SKILL.md body) loads on trigger; Level 3 (referenced files) loads on demand.

**Sub-agents cannot spawn other sub-agents.** Design orchestration to avoid nesting. Use the explore sub-agent (Haiku, fast) for initial investigation, escalate to general-purpose (Sonnet) for modifications.

**Error handling requires explicit fallback strategies.** Sub-agents may timeout or fail; parent agents should include recovery instructions:

```markdown
If the code-analyzer sub-agent fails or times out:
1. Use grep to manually search for relevant patterns
2. Check recent git commits for context
3. Report partial findings rather than failing completely
```

## Hexagonal architecture integration rules

Alexandria's hexagonal architecture separates concerns into domain (entities, ports), application (use cases), and infrastructure (adapters) layers. These rules ensure layer boundaries remain intact.

**Ports define domain interfaces without framework types:**

```typescript
// ✅ Domain port - no Drizzle types
export interface IDocumentRepository {
  findById(id: string): Promise<Document | null>;
  save(document: Document): Promise<void>;
  findSimilar(embedding: number[], limit: number): Promise<Document[]>;
}

// ❌ Leaky abstraction - exposes Drizzle
export interface IDocumentRepository {
  findAll(): Promise<typeof documents.$inferSelect[]>;
}
```

**Adapters implement ports using constructor injection:**

```typescript
export class DrizzleDocumentRepository implements IDocumentRepository {
  constructor(private readonly db: DrizzleDB) {}
  
  async findSimilar(embedding: number[], limit: number): Promise<Document[]> {
    const results = await this.db
      .select()
      .from(documents)
      .orderBy(cosineDistance(documents.embedding, embedding))
      .limit(limit);
    
    return results.map(this.toDomain);
  }
  
  private toDomain(record: typeof documents.$inferSelect): Document {
    return Document.create({ /* map fields */ });
  }
}
```

**The composition root wires all dependencies:**

```typescript
// src/composition-root.ts
export function createApp(): Hono {
  // Infrastructure
  const documentRepo = new DrizzleDocumentRepository(db);
  const embeddingService = new OpenAIEmbeddingService(env.OPENAI_KEY);
  
  // Application
  const searchDocuments = new SearchDocumentsUseCase(documentRepo, embeddingService);
  
  // Presentation
  const documentController = new DocumentController(searchDocuments);
  
  const app = new Hono();
  app.route('/api/documents', documentController.routes);
  return app;
}
```

**Testing follows layer boundaries:**
- **Domain**: Pure unit tests, no mocks needed, test entity behavior
- **Application**: Unit tests with mocked ports, verify use case orchestration
- **Infrastructure**: Integration tests with real dependencies (test database, etc.)

```typescript
// Domain test - pure
describe('Document', () => {
  it('validates title length', () => {
    expect(() => Document.create({ title: '' })).toThrow();
  });
});

// Application test - mocked ports
describe('SearchDocumentsUseCase', () => {
  it('returns similar documents', async () => {
    const mockRepo: IDocumentRepository = {
      findSimilar: vi.fn().mockResolvedValue([mockDocument]),
    };
    const useCase = new SearchDocumentsUseCase(mockRepo, mockEmbedding);
    const results = await useCase.execute({ query: 'test' });
    expect(results).toHaveLength(1);
  });
});

// Infrastructure test - real database
describe('DrizzleDocumentRepository', () => {
  it('persists and retrieves documents', async () => {
    const repo = new DrizzleDocumentRepository(testDb);
    await repo.save(document);
    const retrieved = await repo.findById(document.id);
    expect(retrieved?.title).toBe(document.title);
  });
});
```

## Critical anti-patterns to avoid

Several patterns will silently break type safety, performance, or architectural boundaries:

- **Hono**: Don't wrap `next()` in try/catch—use `app.onError()` instead
- **Hono**: Don't use uppercase header keys in Zod validation—headers are lowercase
- **Drizzle**: Don't use `serial` for new tables—use `generatedAlwaysAsIdentity()`
- **Drizzle**: Don't await `tx.rollback()`—it throws, not returns
- **pgvector**: Don't compute similarity scores in ORDER BY—order by distance directly
- **MCP**: Don't log to stdout—use stderr exclusively
- **MCP**: Don't create global connections—make tools self-contained
- **Zod**: Don't use `z.coerce.boolean()` for env vars—use `z.stringbool()`
- **Hexagonal**: Don't import infrastructure in domain—direction is strictly inward
- **Hexagonal**: Don't validate in domain layer—validate at adapter boundaries

## Performance considerations for Bun runtime

Bun 1.3.5 provides significant performance advantages when patterns align with its runtime characteristics:

- **Native TypeScript execution** eliminates transpilation overhead
- **Built-in SQLite** can serve as test database without PostgreSQL
- **Bun.serve** integration with Hono requires default export pattern
- **Connection pooling** should limit `max` to **10-20** for Bun's async model
- **HNSW index parameters** should use `m=16`, `ef_construction=100` as baseline; tune `ef_search` at query time for recall vs. latency tradeoff

## Conclusion

These framework-specific rules ensure Alexandria maintains hexagonal architecture purity while leveraging each technology's strengths. Hono provides type-safe HTTP handling, Drizzle delivers performant PostgreSQL access with vector search, MCP enables Claude Code integration, Zod enforces validation at boundaries, and sub-agents extend capabilities through specialized expertise. Following these patterns prevents the most common integration mistakes while enabling the full power of each framework in the governance tool stack.