---
project_name: 'alexandria'
user_name: 'Negus'
date: '2025-12-26'
sections_completed: ['technology_stack', 'language_specific', 'framework_specific', 'testing_rules', 'code_quality_style', 'development_workflow', 'critical_dont_miss']
status: 'complete'
rule_count: 65
optimized_for_llm: true
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

**Core Technologies:**
- **Bun** 1.3.5 - JavaScript runtime ultra-rapide (acquis par Anthropic déc 2025)
- **TypeScript** 5.9.7 - Strict mode activé, dernière branche 5.x stable
- **Hono** 4.11.1 - Framework web TypeScript minimaliste (~12KB)
- **PostgreSQL** 17.7 - Base de données principale
- **pgvector** 0.8.1 - Extension PostgreSQL pour recherche vectorielle
- **Drizzle ORM** 0.36.4 - ORM type-safe avec support pgvector natif

**Key Dependencies:**
- **Zod** 4.2.1 - Validation runtime type-safe (17 déc 2025, 57% bundle plus petit, 3x plus rapide)
- **OpenAI Embeddings API** - text-embedding-3-small ou text-embedding-3-large
- **Claude Haiku 4.5** - LLM reformulation Layer 3 (via sub-agent Claude Code)

**Version Constraints:**
- pgvector 0.8.1 nécessite PostgreSQL 17.7 minimum (abandonne support PostgreSQL 12)
- Zod 4.2.1 a breaking changes depuis 3.x (migration requise si upgrade)
- Drizzle ORM 0.36.4 stable, v1.0 en pré-release (surveiller pour migration future)
- Bun excellente compatibilité future avec Claude Code (acquisition Anthropic)

## Critical Implementation Rules

### TypeScript Rules

#### 1. Constructor Injection avec Ports

```typescript
// ✅ CORRECT
constructor(
  private readonly conventionRepo: ConventionRepositoryPort,
  private readonly logger: LoggerPort
) {}

// ❌ INCORRECT
constructor(private readonly repo: DrizzleConventionRepository) {}
```

**Pourquoi** : L'architecture hexagonale exige que le domaine dépende d'abstractions (ports), pas d'implémentations. Le bootstrap centralisé dans `src/index.ts` injecte les adapters concrets.

#### 2. Immutabilité des Entities et Value Objects

```typescript
// ✅ CORRECT
export class Convention {
  readonly id: string
  readonly projectId: string

  private constructor(data: ConventionData) { /* ... */ }

  static create(data: ConventionData): Convention { /* validation + new */ }

  withEmbedding(embedding: number[]): Convention {
    return new Convention({ ...this, embedding })  // Nouvelle instance
  }
}

// ❌ INCORRECT
export class Convention {
  public id: string
  setEmbedding(e: number[]) { this.embedding = e }
}
```

**Pourquoi** : Les entities du domaine ne changent jamais après création. Cela évite les effets de bord et rend le code prévisible. Les méthodes métier retournent de nouvelles instances.

#### 3. Nullabilité Explicite

```typescript
// ✅ CORRECT
findById(id: string): Promise<Convention | null>  // null = "pas trouvé"
search(...): Promise<Convention[]>                 // [] = aucun résultat

// ✅ CORRECT
const topK = input.topK ?? DEFAULT_TOP_K
const name = project?.name ?? 'default'

// ❌ INCORRECT
findById(id: string): Promise<Convention | undefined>  // undefined ambigu
const name = project!.name                              // assertion dangereuse
```

**Pourquoi** : `null` signifie explicitement "absence intentionnelle". Les arrays vides représentent "zéro résultats". TypeScript strict mode exige la gestion explicite des cas null.

#### 4. Parallélisation avec Promise.all

```typescript
// ✅ CORRECT - opérations indépendantes en parallèle
const [conventions, baseDocs] = await Promise.all([
  this.conventionRepo.search(projectId, embedding),
  this.docRepo.getCommonDocs(projectId)
])

// ✅ CORRECT - opérations dépendantes en séquentiel
const embedding = await this.embeddingGen.generate(query)
const conventions = await this.conventionRepo.search(projectId, embedding)
```

**Pourquoi** : Performance critique (NFR1: p50 ≤3s). La parallélisation réduit la latence totale quand les opérations sont indépendantes.

#### 5. Early Return Pattern

```typescript
// ✅ CORRECT
async findById(id: string): Promise<Convention | null> {
  const result = await this.db.query(...)
  if (!result) return null
  return Convention.create(result)
}

// ❌ INCORRECT
async findById(id: string): Promise<Convention | null> {
  const result = await this.db.query(...)
  if (result) {
    return Convention.create(result)
  } else {
    return null
  }
}
```

**Pourquoi** : Réduit l'indentation, améliore la lisibilité. Le cas d'erreur/absence est traité immédiatement.

#### 6. Constantes avec `as const`

```typescript
// ✅ CORRECT
const LAYER_TYPES = ['layer1', 'layer2', 'mcp', 'adapter', 'domain'] as const
type LayerType = typeof LAYER_TYPES[number]

const DEFAULT_CONFIG = {
  topK: 10,
  maxQueryLength: 500
} as const
```

**Pourquoi** : Crée des types littéraux au lieu de `string[]`. Permet l'autocomplétion et la vérification compile-time des valeurs valides.

#### 7. Type Guards pour Unions

```typescript
// ✅ CORRECT
function isConvention(doc: Convention | Documentation): doc is Convention {
  return 'technologies' in doc && Array.isArray(doc.technologies)
}

// Usage
if (isConvention(document)) {
  console.log(document.technologies)  // TypeScript sait que c'est Convention
}
```

**Pourquoi** : Permet de discriminer les types dans une union de manière type-safe. TypeScript narrow le type après le guard.

#### 8. Interdictions Strictes

```typescript
// ❌ INTERDIT: any
function process(data: any) { }
// ✅ CORRECT: unknown + validation
function process(data: unknown) {
  const validated = schema.parse(data)
}

// ❌ INTERDIT: non-null assertion
const name = project!.name
// ✅ CORRECT: vérification explicite
const name = project?.name ?? 'default'

// ❌ INTERDIT: @ts-ignore
// @ts-ignore
someProblematicCode()
// ✅ CORRECT: résoudre le problème de typage
```

**Pourquoi** :
- `any` détruit la type-safety et propage l'absence de types
- `!` cache un problème au lieu de le résoudre
- `@ts-ignore` masque des erreurs légitimes

**Exception documentée** : Si une librairie externe est mal typée, une assertion `as Type` est acceptable avec commentaire explicatif.

#### 9. Zod aux Frontières Uniquement

```typescript
// ✅ CORRECT: Validation dans adapter (frontière)
// src/adapters/primary/mcp-server/routes/retrieve.ts
app.post('/retrieve', async (c) => {
  const input = retrieveInputSchema.parse(await c.req.json())  // Zod ici
  const result = await useCase.execute(input.projectId, input.query)
  return c.json(result)
})

// ✅ CORRECT: Domain reçoit données déjà validées
// src/domain/use-cases/RetrieveRawContext.ts
execute(projectId: string, query: string): Promise<RawContext> {
  // Pas de Zod ici - données déjà validées par l'adapter
}

// ❌ INCORRECT: Zod dans le domaine
import { z } from 'zod'  // ❌ Viole architecture hexagonale
```

**Pourquoi** : Le domaine reste pur (pas de dépendances infrastructure). Zod valide aux entrées (MCP tools, config .env) et sorties (réponses API externes) uniquement.

### Framework-Specific Rules

#### 1. Hono - Type Preservation avec Method Chaining

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

#### 2. Hono - Context Typing pour Dependency Injection

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

#### 3. Drizzle - HNSW Index Usage (CRITIQUE PERFORMANCE)

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

#### 4. Drizzle - Identity Columns (PostgreSQL 17)

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

#### 5. Drizzle - Transaction Pattern (tx vs db)

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

#### 6. MCP Protocol - Stdout vs Stderr (BLOQUANT)

```typescript
// ✅ CORRECT - All logging to stderr
console.error('[INFO] Processing retrieve request');
console.error('[ERROR] Convention not found:', id);

// ❌ INCORRECT - Breaks protocol, corrupts message stream
console.log('Processing retrieve request');  // ❌ JAMAIS!
```

**Pourquoi** : **HARD REQUIREMENT** du protocole MCP. Stdout est réservé exclusivement aux messages JSON-RPC. Un seul `console.log()` corrompt le stream et casse toute communication avec Claude Code.

#### 7. MCP - Tool Errors vs Protocol Errors

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

#### 8. Zod 4.2.1 - Nouvelle Syntaxe (Breaking Change)

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

#### 9. Zod - Environment Validation (Fail Fast)

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

#### 10. Architecture Hexagonale - Ports sans Types Framework

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

#### 11. Architecture Hexagonale - Composition Root

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

### Testing Rules

#### 1. Testing par Layer Hexagonal (FONDAMENTAL)

```typescript
// ✅ CORRECT - Domain: 100% pur, JAMAIS de mocks
describe('Convention Entity', () => {
  it('should reject empty content', () => {
    expect(() => Convention.create({ content: '' }))
      .toThrow('Content cannot be empty');
  });

  it('should emit ConventionCreated event', () => {
    const conv = Convention.create({ id: 'c1', content: 'Rule X' });
    expect(conv.domainEvents).toHaveLength(1);
    expect(conv.domainEvents[0].type).toBe('ConventionCreated');
  });
});

// ✅ CORRECT - Application: Fakes in-memory pour ports
const repo = new InMemoryConventionRepository();
const embeddingGen = new FakeEmbeddingGenerator();
const useCase = new RetrieveRawContextUseCase(repo, embeddingGen, logger);

// ✅ CORRECT - Infrastructure: Real dependencies (pgvector, OpenAI)
const testDb = drizzle(env.TEST_DATABASE_URL);
const repo = new DrizzleConventionRepository(testDb);
const realOpenAI = new OpenAIEmbeddingAdapter(env.OPENAI_API_KEY);

// ❌ INCORRECT - Mocks dans Domain layer
const mockRepo = vi.fn(); // ❌ Si Domain nécessite mock = signal architectural
```

**Pourquoi** : Architecture hexagonale exige cette séparation stricte. Si un test Domain nécessite un mock, c'est que le domaine dépend de l'infrastructure (violation hexagonale).

#### 2. Contract Testing pour Ports (Prevent Regressions)

```typescript
// tests/contracts/convention-repository.contract.ts
export function conventionRepositoryContract(
  createRepo: () => ConventionRepositoryPort | Promise<ConventionRepositoryPort>,
  cleanup?: () => Promise<void>
) {
  describe('ConventionRepository Contract', () => {
    let repo: ConventionRepositoryPort;

    beforeEach(async () => {
      repo = await createRepo();
      if (cleanup) await cleanup();
    });

    it('should save and retrieve by id', async () => {
      const conv = Convention.create({ id: 'test-1', content: 'Test' });
      await repo.save(conv);
      const found = await repo.findById('test-1');

      expect(found).not.toBeNull();
      expect(found!.content).toBe('Test');
    });

    it('should return null for non-existent id', async () => {
      const found = await repo.findById('non-existent');
      expect(found).toBeNull();
    });
  });
}

// Usage - InMemory adapter
conventionRepositoryContract(() => new InMemoryConventionRepository());

// Usage - Drizzle adapter
conventionRepositoryContract(
  () => new DrizzleConventionRepository(testDb),
  () => testDb.delete(conventions)
);
```

**Pourquoi** : Garantit que TOUS les adapters (InMemory, Drizzle, futurs) respectent le contrat exact du port. Détecte régressions lors de swap d'adapter. NFR23 exige tests unitaires et d'intégration.

#### 3. HNSW Index Usage Validation (NFR2: Layer 1 ≤1s)

```typescript
// tests/integration/vector-search-performance.test.ts
it('should use HNSW index, not sequential scan', async () => {
  const testVector = Array(1536).fill(0.1);

  const explain = await db.execute(sql`
    EXPLAIN ANALYZE
    SELECT id FROM conventions
    ORDER BY embedding <=> ${testVector}::vector
    LIMIT 10
  `);

  const plan = explain.rows.map(r => r['QUERY PLAN']).join('\n');
  expect(plan).toContain('hnsw');
  expect(plan).not.toContain('Seq Scan');
});
```

**Pourquoi** : NFR2 exige Layer 1 vector search ≤1s pour 95% requêtes. Sans utilisation de l'index HNSW, performance s'effondre avec >10k embeddings (table scan complet).

#### 4. Performance Testing avec NFR Thresholds

```typescript
// tests/performance/retrieval-latency.test.ts
const THRESHOLDS = {
  p50: 3000,  // ms - NFR1
  p95: 5000,  // ms - NFR1
  p99: 10000  // ms - NFR1
};

it('should meet retrieval latency SLAs', async () => {
  const latencies: number[] = [];

  for (let i = 0; i < 100; i++) {
    const start = performance.now();
    await retrieveUseCase.execute('project-1', `test query ${i}`);
    latencies.push(performance.now() - start);
  }

  latencies.sort((a, b) => a - b);
  const p50 = latencies[Math.floor(latencies.length * 0.5)];
  const p95 = latencies[Math.floor(latencies.length * 0.95)];
  const p99 = latencies[Math.floor(latencies.length * 0.99)];

  expect(p50).toBeLessThan(THRESHOLDS.p50);
  expect(p95).toBeLessThan(THRESHOLDS.p95);
  expect(p99).toBeLessThan(THRESHOLDS.p99);
});
```

**Pourquoi** : NFR1 exige p50 ≤3s end-to-end. Tests de performance détectent régressions critiques avant production.

#### 5. MCP Protocol Compliance (7 Tools)

```typescript
// tests/mcp/tools/retrieve-context.test.ts
import Ajv from 'ajv';

const ajv = new Ajv();

describe('MCP Tool: alexandria_retrieve_context', () => {
  it('should validate input schema', () => {
    const tool = server.getTool('alexandria_retrieve_context');
    const validate = ajv.compile(tool.inputSchema);

    expect(validate({ projectId: 'p1', query: 'test' })).toBe(true);
    expect(validate({ query: 'test' })).toBe(false); // missing projectId
    expect(validate({ projectId: 'p1' })).toBe(false); // missing query
  });

  it('should return isError for business failures, not throw', async () => {
    const result = await server.callTool('alexandria_retrieve_context', {
      projectId: 'non-existent',
      query: 'test'
    });

    // ✅ Tool error visible à Claude
    expect(result.isError).toBe(true);
    expect(result.content[0].text).toContain('Project not found');

    // ❌ PAS throw new Error() - casserait le protocole
  });

  it('should return valid JSON-RPC 2.0 response', async () => {
    const request = {
      jsonrpc: '2.0',
      id: 1,
      method: 'tools/call',
      params: { name: 'alexandria_retrieve_context', arguments: { projectId: 'p1', query: 'q' } }
    };

    const response = await server.handleRequest(request);

    expect(response.jsonrpc).toBe('2.0');
    expect(response.id).toBe(1);
    expect(response).toHaveProperty('result');
  });
});
```

**Pourquoi** : MCP protocol compliance obligatoire (NFR11). `isError: true` permet à Claude de récupérer, `throw` casse complètement l'invocation.

#### 6. Factory Pattern pour Fixtures Déterministes

```typescript
// tests/factories/convention.factory.ts
import { faker } from '@faker-js/faker';
import { createHash } from 'crypto';

export function createConvention(overrides = {}): Convention {
  return Convention.create({
    id: overrides.id ?? faker.string.uuid(),
    content: overrides.content ?? faker.lorem.paragraph(),
    projectId: overrides.projectId ?? 'default-project',
    technologies: overrides.technologies ?? ['typescript'],
    embedding: overrides.embedding ?? generateDeterministicEmbedding(
      overrides.content ?? 'default content'
    ),
  });
}

// Embedding déterministe basé sur hash du texte
function generateDeterministicEmbedding(text: string, dim = 1536): number[] {
  const seed = parseInt(
    createHash('sha256').update(text).digest('hex').slice(0, 8),
    16
  );
  const rng = seededRandom(seed);

  const embedding = Array.from({ length: dim }, () => (rng() - 0.5) * 2);

  // Normalisation L2
  const norm = Math.sqrt(embedding.reduce((sum, v) => sum + v * v, 0));
  return embedding.map(v => v / norm);
}

// Variantes prédéfinies
export const createConventions = (count: number, overrides = {}) =>
  Array.from({ length: count }, () => createConvention(overrides));
```

**Pourquoi** : Tests reproductibles sans flakiness. Évite appels API OpenAI en tests unitaires. Hash garantit même embedding pour même texte.

#### 7. OpenAI Mocking Systématique (Unit Tests)

```typescript
// tests/mocks/openai.mock.ts
import nock from 'nock';

export function mockOpenAIEmbeddings(
  responses: Record<string, number[]> = {}
) {
  nock('https://api.openai.com')
    .post('/v1/embeddings')
    .reply(200, (uri, requestBody: any) => {
      const text = requestBody.input;
      return {
        data: [{
          embedding: responses[text] ?? generateDeterministicEmbedding(text)
        }],
        model: 'text-embedding-3-small',
        usage: { prompt_tokens: 10, total_tokens: 10 }
      };
    });
}

export function mockOpenAIRateLimit() {
  nock('https://api.openai.com')
    .post('/v1/embeddings')
    .reply(429, {
      error: { message: 'Rate limit exceeded', type: 'rate_limit_exceeded' }
    }, { 'retry-after': '20' });
}

// Usage dans tests
describe('OpenAI Adapter', () => {
  afterEach(() => nock.cleanAll());

  it('should generate embeddings', async () => {
    mockOpenAIEmbeddings({ 'test': Array(1536).fill(0.1) });

    const result = await adapter.generate('test');
    expect(result).toHaveLength(1536);
  });

  it('should handle rate limits with retry', async () => {
    mockOpenAIRateLimit();
    mockOpenAIEmbeddings({ 'test': Array(1536).fill(0.1) }); // 2nd call succeeds

    const result = await adapter.generate('test');
    expect(result).toHaveLength(1536);
  });
});
```

**Pourquoi** : Évite coûts API en unit tests. Real API uniquement en integration tests conditionnels (NFR10: pas de storage OpenAI, mais appels nécessaires pour embeddings).

#### 8. Golden Dataset pour Vector Search Quality

```typescript
// tests/fixtures/vector-search-golden.ts
export const goldenDataset = [
  {
    query: 'TypeScript strict mode configuration',
    expectedIds: ['conv-ts-strict', 'conv-ts-config'],
    minRecall: 0.9
  },
  {
    query: 'Error handling patterns',
    expectedIds: ['conv-error-handling', 'conv-try-catch'],
    minRecall: 0.85
  },
  {
    query: 'PostgreSQL transaction management',
    expectedIds: ['conv-pg-tx', 'conv-drizzle-tx'],
    minRecall: 0.9
  }
];

// tests/integration/vector-search-quality.test.ts
describe('Vector Search Quality', () => {
  it('should meet recall thresholds on golden dataset', async () => {
    for (const test of goldenDataset) {
      const results = await vectorSearch.search(test.query, 10);
      const resultIds = results.map(r => r.id);

      const recall = test.expectedIds.filter(
        id => resultIds.includes(id)
      ).length / test.expectedIds.length;

      expect(recall).toBeGreaterThanOrEqual(test.minRecall);
    }
  });
});
```

**Pourquoi** : Validation qualité retrieval Layer 1. Détecte régressions qualité (pas juste performance). Minimum 20 queries pour MVP, 100+ pour production.

#### 9. withTransaction Helper (Clean Tests)

```typescript
// tests/helpers/database.ts
export const withTransaction = async <T>(
  fn: (db: DrizzleDB) => Promise<T>
): Promise<T> => {
  return await testDb.transaction(async (tx) => {
    try {
      const result = await fn(tx);
      tx.rollback(); // Force rollback même si succès
      return result;
    } catch (error) {
      tx.rollback();
      throw error;
    }
  });
};

// Usage
it('should save convention in transaction', async () => {
  await withTransaction(async (tx) => {
    const repo = new DrizzleConventionRepository(tx);
    await repo.save(createConvention({ id: 'test-1' }));

    const found = await repo.findById('test-1');
    expect(found).not.toBeNull();
  });
  // Auto-rollback - DB toujours propre pour prochain test
});
```

**Pourquoi** : Tests isolation (NFR18: data integrity). DB toujours dans état initial entre tests. Évite pollution et flakiness.

#### 10. Property-Based Testing (Domain Invariants)

```typescript
// tests/domain/properties/convention.properties.test.ts
import fc from 'fast-check';

const conventionArbitrary = fc.record({
  id: fc.uuid(),
  content: fc.string({ minLength: 1, maxLength: 10000 }),
  technologies: fc.array(fc.string(), { minLength: 1, maxLength: 5 })
});

describe('Convention Properties', () => {
  it('archived conventions cannot be modified (invariant)', () => {
    fc.assert(
      fc.property(
        conventionArbitrary,
        fc.string({ minLength: 1 }),
        (data, newContent) => {
          const conv = Convention.create(data);
          conv.archive();

          expect(() => conv.updateContent(newContent)).toThrow(
            'Cannot modify archived convention'
          );
        }
      )
    );
  });

  it('content is preserved after save/load cycle', () => {
    fc.assert(
      fc.property(conventionArbitrary, (data) => {
        const conv = Convention.create(data);
        const json = conv.toJSON();
        const restored = Convention.fromJSON(json);

        expect(restored.content).toBe(data.content);
        expect(restored.technologies).toEqual(data.technologies);
      })
    );
  });
});
```

**Pourquoi** : Valide invariants métier avec 100+ cas générés aléatoirement. Trouve edge cases que tests manuels manqueraient. NFR23 exige coverage tests critiques.

#### 11. Pyramide de Tests Alexandria

| Layer | Coverage Cible | Type de Tests | Proportion | Vitesse |
|-------|----------------|---------------|------------|---------|
| **Domain** | 100% | Unit pur (pas de mocks) | 40% | <100ms |
| **Application** | >85% | Unit + Fakes in-memory | 25% | <500ms |
| **Infrastructure** | Happy path + errors | Integration (real DB/API) | 20% | 1-5s |
| **MCP Tools** | Schema + behavior | Contract compliance | 10% | <1s |
| **E2E Pipeline** | Layer 1→2→3 complet | End-to-end | 5% | 3-10s |

**Organisation des Tests:**

```
tests/
├── unit/
│   ├── domain/
│   │   ├── entities/
│   │   ├── value-objects/
│   │   └── properties/      # Property-based tests
│   └── application/
│       └── use-cases/       # Avec fakes in-memory
├── integration/
│   ├── adapters/
│   │   ├── drizzle/         # Real PostgreSQL
│   │   └── openai/          # Mocked sauf tests conditionnels
│   └── vector-search/       # Golden dataset, HNSW validation
├── mcp/
│   ├── tools/               # 7 tools compliance
│   └── protocol/            # JSON-RPC 2.0
├── performance/
│   └── latency.test.ts      # NFR1-6 thresholds
├── contracts/               # Port contracts réutilisables
├── factories/               # Fixtures factories
└── helpers/                 # withTransaction, mocks, etc.
```

**Exécution CI/CD:**

```typescript
// package.json scripts
{
  "test:unit": "bun test tests/unit tests/domain",
  "test:integration": "bun test tests/integration",
  "test:mcp": "bun test tests/mcp",
  "test:performance": "bun test tests/performance",
  "test:all": "bun test",
  "test:coverage": "bun test --coverage"
}
```

**Pourquoi** : Pyramide équilibrée = feedback rapide (unit) + confiance (integration). NFR23 exige tests coverage sur logique critique.

### Code Quality & Style Rules

#### 1. Linting avec Biome (Bun-First)

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

#### 2. Code Organization (Architecture Hexagonale)

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

#### 3. Naming Conventions (Strictes)

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

#### 4. Documentation Requirements (NFR21)

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

#### 5. Import Organization

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

#### 6. File Length Limits

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

#### 7. Error Handling with Custom Errors

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

#### 8. Magic Numbers/Strings Interdits

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

#### 9. Code Coverage Thresholds (NFR23)

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

#### 10. Git Hooks avec Husky + lint-staged

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

#### 11. Code Review Checklist

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

### Development Workflow Rules

#### 1. Git Workflow (Trunk-Based Development)

```bash
# ✅ CORRECT - Branches courtes, merge fréquent
git checkout -b feature/add-validation-tool
# Work, commit, push
git push -u origin feature/add-validation-tool
# Create PR, review, merge to main

# ✅ CORRECT - Branch naming conventions
feature/add-layer3-reformulation
fix/hnsw-index-not-used
refactor/extract-embedding-service
test/add-contract-tests-repositories
docs/update-architecture-diagram
perf/optimize-vector-search

# ❌ INCORRECT - Long-lived feature branches
git checkout -b develop  # ❌ Pas de develop branch
git checkout -b feature/big-refactor  # ❌ Branch vivant 2+ semaines
```

**Pourquoi** : Trunk-based = intégration continue, moins de merge conflicts. Branches courtes (<3 jours) facilitent reviews.

#### 2. Commit Message Format (Gitmoji + Conventional Commits)

```bash
# Format obligatoire
<gitmoji> <type>(<scope>): <description>

<body optionnel>

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
```

**Gitmoji Reference pour Alexandria:**

| Gitmoji | Code | Type | Usage |
|---------|------|------|-------|
| ✨ | `:sparkles:` | `feat` | Nouvelle feature/use case |
| 🐛 | `:bug:` | `fix` | Bug fix |
| ♻️ | `:recycle:` | `refactor` | Refactoring sans changement fonctionnel |
| ✅ | `:white_check_mark:` | `test` | Ajout/modification tests |
| 📝 | `:memo:` | `docs` | Documentation |
| ⚡ | `:zap:` | `perf` | Amélioration performance |
| 🔧 | `:wrench:` | `chore` | Configuration, build, dependencies |
| 🏗️ | `:building_construction:` | `arch` | Changements architecturaux |
| 🔒 | `:lock:` | `security` | Sécurité |
| 🚀 | `:rocket:` | `deploy` | Déploiement |

**Exemples:**

```bash
# Feature
✨ feat(layer1): add vector search with HNSW index

Implements Layer 1 of Active Compliance Filter using pgvector.
- Add DrizzleConventionAdapter.search() method
- Configure HNSW index with m=16, efConstruction=128
- Meet NFR2 performance target (p95 < 1s)

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>

# Bug fix
🐛 fix(mcp): use stderr for logging instead of stdout

MCP protocol requires stdout reserved for JSON-RPC messages.
All console.log() calls moved to console.error().

Fixes #42

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>

# Refactor
♻️ refactor(domain): extract Convention entity to separate file

Improves maintainability by separating Convention from other entities.
No functional changes.

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>

# Performance
⚡ perf(layer1): optimize vector search with parallel embeddings

Use Promise.all for independent embedding generations.
Reduces p50 latency from 4.2s to 2.8s (NFR1 compliant).

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
```

**Pourquoi** : Gitmoji rend l'historique git visuellement scannable. Conventional Commits permet génération CHANGELOG automatique. NFR24 exige commit messages descriptifs.

#### 3. PR Requirements (Quality Gates)

Chaque Pull Request DOIT:

**Automated Checks (CI/CD):**
- [ ] ✅ Biome lint pass (zero warnings)
- [ ] ✅ TypeScript compilation pass (strict mode)
- [ ] ✅ Unit tests pass (Domain + Application)
- [ ] ✅ Integration tests pass (Drizzle + OpenAI mocks)
- [ ] ✅ MCP protocol compliance tests pass
- [ ] ✅ Coverage thresholds maintenus (lines≥80%, functions≥85%)
- [ ] ✅ Performance tests pass (si modification Layer 1-3)

**Manual Reviews:**
- [ ] ✅ CodeRabbit AI review (NFR24)
- [ ] ✅ Au moins 1 human review approve
- [ ] ✅ Architecture hexagonale respectée
- [ ] ✅ Pas de secrets committés (.env, API keys)

**Merge Strategy:**
```bash
# ✅ CORRECT - Squash merge sur main
# Via GitHub UI: "Squash and merge"
# Résultat: 1 commit propre sur main avec tous les gitmojis préservés

# ❌ INCORRECT - Merge commit ou rebase
# Pollue l'historique main avec commits intermédiaires
```

**Pourquoi** : Quality gates garantissent qualité code avant merge. Squash = historique main propre et linéaire.

#### 4. CI/CD Pipeline

```yaml
# .github/workflows/ci.yml
name: CI Pipeline

on: [push, pull_request]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun run biome check

  typecheck:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun run typecheck

  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun test tests/unit tests/domain --coverage
      - uses: codecov/codecov-action@v4
        with:
          files: ./coverage/lcov.info

  integration-tests:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: pgvector/pgvector:pg17
        env:
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
          POSTGRES_DB: alexandria_test
        ports:
          - 5432:5432
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun test tests/integration
        env:
          DATABASE_URL: postgres://test:test@localhost:5432/alexandria_test

  mcp-compliance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun test tests/mcp

  performance:
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun test tests/performance
```

**Pipeline Flow:**
```
Push/PR → Lint → TypeCheck → Unit Tests (parallel) → Integration Tests → MCP Compliance → Performance (main only) → Deploy (main only)
```

**Pourquoi** : NFR23 exige tests automatisés. Pipeline parallèle = feedback rapide (<5 min). Performance tests sur main uniquement = économie temps CI.

#### 5. Environment Management

```bash
# .env.example (commité dans repo)
# Database
DATABASE_URL=postgres://user:password@localhost:5432/alexandria

# OpenAI
OPENAI_API_KEY=sk-...

# Application
DEBUG=false
PORT=3000
LOG_LEVEL=info

# MCP Server
MCP_SERVER_PORT=3001
```

```typescript
// src/config/env.ts - Validation Zod fail fast
import { z } from 'zod';

const envSchema = z.object({
  DATABASE_URL: z.string().url(),
  OPENAI_API_KEY: z.string().min(1),
  DEBUG: z.stringbool().default(false),
  PORT: z.coerce.number().default(3000),
  LOG_LEVEL: z.enum(['debug', 'info', 'warn', 'error']).default('info'),
  MCP_SERVER_PORT: z.coerce.number().default(3001),
});

const result = envSchema.safeParse(process.env);

if (!result.success) {
  console.error('❌ Invalid environment variables:');
  console.error(result.error.flatten().fieldErrors);
  process.exit(1);
}

export const env = result.data;
```

**Security Rules:**
- [ ] ❌ JAMAIS committer `.env` (gitignore strict)
- [ ] ✅ Toujours fournir `.env.example` à jour
- [ ] ✅ Secrets via GitHub Actions secrets en CI/CD
- [ ] ✅ Validation Zod fail fast au startup (NFR7)

**Pourquoi** : NFR7 exige fail fast si credentials manquantes. `.env.example` documente variables requises sans exposer secrets.

#### 6. Code Review Workflow

```bash
# 1. Créer feature branch
git checkout -b feature/add-upload-tool

# 2. Développer avec commits atomiques
git add src/adapters/primary/mcp-server/tools/upload.ts
git commit -m "✨ feat(mcp): add upload tool for conventions

Implements MCP tool for uploading new conventions with embeddings.
- Validates input with Zod schema
- Generates embeddings via OpenAI adapter
- Persists to PostgreSQL in transaction (NFR18)

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

# 3. Push et créer PR
git push -u origin feature/add-upload-tool
gh pr create --title "✨ Add upload tool for conventions" --body "..."

# 4. Attendre reviews (CodeRabbit + human)
# 5. Merge via "Squash and merge" sur GitHub UI
```

**Review Checklist (Reviewer):**
- [ ] Code respecte architecture hexagonale
- [ ] Tests ajoutés et passent
- [ ] JSDoc présent sur fonctions publiques
- [ ] Pas de console.log (uniquement console.error pour logs)
- [ ] MCP protocol respecté (si modification tools)
- [ ] Performance NFRs respectées (si Layer 1-3)
- [ ] Commit message suit Gitmoji + Conventional Commits

**Pourquoi** : NFR24 exige code reviews (CodeRabbit + human). Checklist garantit standards qualité maintenus.

#### 7. Deployment Workflow

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun run build

      # Deploy to production (Docker, VPS, etc.)
      - name: Build Docker image
        run: docker build -t alexandria:latest .

      - name: Push to registry
        run: docker push alexandria:latest

      - name: Deploy to server
        run: |
          # SSH deployment commands
          ssh user@server 'docker pull alexandria:latest && docker-compose up -d'
```

**Deployment Environments:**
- `main` → Production (auto-deploy après tests)
- Tags `v*.*.*` → Release versionnée

**Pourquoi** : Auto-deployment sur main après tests = continuous delivery. Tags permettent rollback si nécessaire.

#### 8. Hotfix Workflow

```bash
# Hotfix critique en production
git checkout main
git pull
git checkout -b fix/critical-mcp-stdout-bug

# Fix rapide
# ... modifications ...

git add .
git commit -m "🐛 fix(mcp): use stderr for all logging

CRITICAL: MCP protocol was broken by console.log() calls.
All logging now uses console.error() per protocol requirement.

Fixes #123

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

# Fast-track PR (skip optional checks si critique)
git push -u origin fix/critical-mcp-stdout-bug
gh pr create --title "🐛 HOTFIX: Fix MCP stdout protocol violation" --label "priority:critical"

# Merge immédiatement après review rapide
# Deploy automatique sur main
```

**Pourquoi** : Hotfixes critiques nécessitent fast-track. Gitmoji 🐛 + label `priority:critical` signale urgence.

### Critical Don't-Miss Rules

#### 🔴 TIER 1: BLOQUANT COMPLET (Production cassée)

##### 1. MCP Protocol: JAMAIS console.log(), TOUJOURS console.error()

```typescript
// ❌ BLOQUANT COMPLET - Casse toute communication MCP
console.log('[INFO] Processing retrieve request');
console.log('Debug:', data);

// ✅ CORRECT
console.error('[INFO] Processing retrieve request');
console.error('Debug:', data);
```

**Impact**: Un seul `console.log()` corrompt le stream JSON-RPC et casse complètement l'intégration Claude Code. **ZERO tolérance.**

##### 2. MCP Protocol: Tool Errors = isError, PAS throw

```typescript
// ❌ BLOQUANT - Casse invocation tool
async function retrieveTool(input) {
  if (!projectExists(input.projectId)) {
    throw new Error('Project not found'); // ❌ Protocol error!
  }
}

// ✅ CORRECT - Claude peut récupérer
async function retrieveTool(input) {
  if (!projectExists(input.projectId)) {
    return {
      isError: true,
      content: [{
        type: 'text',
        text: `Project "${input.projectId}" not found. Available: ${list.join(', ')}`
      }]
    };
  }
}
```

**Impact**: `throw` casse l'invocation complète. `isError: true` permet à Claude de voir l'erreur et retry avec correction.

##### 3. Architecture Hexagonale: Domain JAMAIS import Infrastructure

```typescript
// ❌ BLOQUANT - Viole architecture hexagonale
// src/domain/entities/Convention.ts
import { z } from 'zod'; // ❌ Zod = infrastructure!
import { drizzle } from 'drizzle-orm'; // ❌ Drizzle = infrastructure!

// ✅ CORRECT - Domain pur
// src/domain/entities/Convention.ts
export class Convention {
  private constructor(
    readonly id: string,
    readonly content: string
  ) {}

  static create(data: { id: string; content: string }): Convention {
    if (!data.content) throw new Error('Content required');
    return new Convention(data.id, data.content);
  }
}
```

**Impact**: Viole principe hexagonal. Domain doit être portable sans aucune dépendance infrastructure.

---

#### 🟠 TIER 2: CRITIQUE PERFORMANCE (NFRs non respectées)

##### 4. HNSW Index: Order by Distance Directement

```typescript
// ❌ CRITIQUE PERF - Table scan complet, NFR2 violée
const results = await db
  .select()
  .from(conventions)
  .orderBy(desc(sql`1 - ${cosineDistance(conventions.embedding, queryVector)}`))
  .limit(10);

// ✅ CORRECT - Utilise HNSW index
const results = await db
  .select()
  .from(conventions)
  .orderBy(cosineDistance(conventions.embedding, queryVector))
  .limit(10);
```

**Impact**: Sans HNSW index usage, performance Layer 1 passe de <1s à >30s avec 10k+ embeddings. **NFR2 complètement violée.**

##### 5. Parallélisation: Promise.all pour Opérations Indépendantes

```typescript
// ❌ CRITIQUE PERF - Sequential, NFR1 violée (6s au lieu de 3s)
const embedding = await embeddingGen.generate(query);
const conventions = await conventionRepo.search(projectId, embedding);
const baseDocs = await docRepo.getCommonDocs(projectId);

// ✅ CORRECT - Parallel, NFR1 respectée
const embedding = await embeddingGen.generate(query);
const [conventions, baseDocs] = await Promise.all([
  conventionRepo.search(projectId, embedding),
  docRepo.getCommonDocs(projectId)
]);
```

**Impact**: Sequential operations doubles la latency totale. **NFR1 (p50 ≤3s) violée.**

##### 6. Drizzle Transactions: tx vs db

```typescript
// ❌ CRITIQUE - Perte atomicité, NFR18 violée
await db.transaction(async (tx) => {
  await tx.insert(conventions).values(conv);
  await db.insert(embeddings).values(emb); // ❌ db au lieu de tx!
});

// ✅ CORRECT - Atomicité garantie
await db.transaction(async (tx) => {
  await tx.insert(conventions).values(conv);
  await tx.insert(embeddings).values(emb);
});
```

**Impact**: Utiliser `db` au lieu de `tx` casse atomicité. En cas d'erreur, données orphelines créées. **NFR18 (data integrity) violée.**

---

#### 🟡 TIER 3: IMPORTANT (Type Safety & Maintenabilité)

##### 7. TypeScript: null vs undefined Consistency

```typescript
// ❌ INCONSISTENT - undefined ambigu
findById(id: string): Promise<Convention | undefined>
search(q: string): Promise<Convention[] | undefined>

// ✅ CORRECT - null = absence intentionnelle
findById(id: string): Promise<Convention | null>
search(q: string): Promise<Convention[]> // [] = aucun résultat
```

**Impact**: `undefined` ambigu (absence vs erreur). `null` signifie explicitement "pas trouvé". Arrays vides = zéro résultats.

##### 8. Zod 4.2.1: Nouvelle Syntaxe Error

```typescript
// ❌ BREAKING - Zod 3 syntax ne marche plus
z.string({ message: 'Required' })
z.string({ required_error: 'Required', invalid_type_error: 'Must be string' })

// ✅ CORRECT - Zod 4 syntax
z.string({ error: 'Required' })
z.string({ error: (iss) => iss.input === undefined ? 'Required' : 'Must be string' })
```

**Impact**: Alexandria utilise Zod 4.2.1. Ancienne syntaxe génère erreurs runtime.

##### 9. Drizzle: Identity vs Serial (PostgreSQL 17)

```typescript
// ❌ LEGACY - serial déprécié PostgreSQL 17
export const conventions = pgTable('conventions', {
  id: serial('id').primaryKey(),
});

// ✅ CORRECT - generatedAlwaysAsIdentity moderne
export const conventions = pgTable('conventions', {
  id: integer('id').primaryKey().generatedAlwaysAsIdentity(),
});
```

**Impact**: `serial` est legacy. PostgreSQL 17 recommande `generatedAlwaysAsIdentity()`.

##### 10. TypeScript Strict Interdictions

```typescript
// ❌ INTERDIT - Détruit type safety
function process(data: any) { } // ❌ any
const name = project!.name; // ❌ non-null assertion
// @ts-ignore
someCode(); // ❌ ignore errors

// ✅ CORRECT - Type-safe alternatives
function process(data: unknown) {
  const validated = schema.parse(data);
}
const name = project?.name ?? 'default';
// Fix le problème de typage au lieu de l'ignorer
```

**Impact**: `any` propage absence de types. `!` cache problèmes. `@ts-ignore` masque erreurs légitimes.

---

#### 🔵 TIER 4: EDGE CASES SPÉCIFIQUES ALEXANDRIA

##### 11. Layer 3 Sub-Agent: Pas d'Appel API Direct

```typescript
// ❌ INCORRECT - Appel direct API Claude
import Anthropic from '@anthropic-ai/sdk';
const client = new Anthropic({ apiKey: env.CLAUDE_API_KEY });
const response = await client.messages.create({ /* ... */ });

// ✅ CORRECT - Délégation au système sub-agent Claude Code
// Le sub-agent alexandria-reformulation est géré par Claude Code
// Pas de CLAUDE_API_KEY nécessaire dans Alexandria
```

**Impact**: Layer 3 utilise système sub-agent Claude Code intégré. Pas d'appel API direct depuis Alexandria.

##### 12. Convention Technologies: Multi-Technologie Support

```typescript
// ❌ INCORRECT - Une seule techno par convention
await db.insert(conventions).values({
  content: 'Use strict TypeScript',
  technology: 'typescript' // ❌ Champ unique!
});

// ✅ CORRECT - Array de technologies
await db.insert(conventions).values({
  content: 'Use strict TypeScript with Bun runtime',
  technologies: ['typescript', 'bun'] // ✅ Array
});

// Layer 2 JOIN automatique via convention_technologies
```

**Impact**: Alexandria supporte multi-technologie (ex: convention applicable à TypeScript + React + Next.js). Table pivot `convention_technologies` gère liens.

##### 13. Embedding Dimensions: Toujours 1536

```typescript
// ❌ INCORRECT - Dimension variable
const embedding = await generate(text);
// Pas de validation dimension

// ✅ CORRECT - Valider dimension 1536
const embedding = await generate(text);
if (embedding.length !== 1536) {
  throw new Error(`Invalid embedding dimension: ${embedding.length}, expected 1536`);
}
```

**Impact**: OpenAI text-embedding-3-small/large = 1536 dimensions. pgvector index configuré pour 1536. Autre dimension = index incompatible.

##### 14. NFR Performance Thresholds: Toujours Valider

```typescript
// ❌ INCORRECT - Pas de validation performance
async function retrieve(projectId: string, query: string) {
  const result = await retrieveUseCase.execute(projectId, query);
  return result;
}

// ✅ CORRECT - Log latency, alert si seuils dépassés
async function retrieve(projectId: string, query: string) {
  const start = performance.now();
  const result = await retrieveUseCase.execute(projectId, query);
  const latency = performance.now() - start;

  logger.info('retrieve_latency', { latency, projectId });

  if (latency > 3000) { // NFR1: p50 ≤3s
    logger.warn('retrieve_latency_exceeded', { latency, threshold: 3000 });
  }

  return result;
}
```

**Impact**: NFRs 1-6 définissent thresholds performance stricts. Logging + monitoring nécessaires pour détecter régressions.

##### 15. Zod Validation: Frontières Uniquement

```typescript
// ❌ INCORRECT - Zod dans use case (application layer)
export class RetrieveRawContextUseCase {
  async execute(projectId: string, query: string) {
    const validated = inputSchema.parse({ projectId, query }); // ❌!
    // ...
  }
}

// ✅ CORRECT - Zod dans adapter (frontière)
app.post('/retrieve', async (c) => {
  const input = retrieveInputSchema.parse(await c.req.json()); // ✅ Adapter
  const result = await useCase.execute(input.projectId, input.query);
  return c.json(result);
});
```

**Impact**: Architecture hexagonale exige validation aux frontières (adapters). Domain et Application reçoivent données déjà validées.

---

#### 📋 Checklist Validation Avant Commit

Avant chaque commit, vérifier:

**Architecture:**
- [ ] Pas d'import domain → infrastructure
- [ ] Zod uniquement dans adapters (frontières)
- [ ] Ports abstraits (pas de types Drizzle/Hono)
- [ ] Composition root centralisé dans `src/index.ts`

**MCP Protocol:**
- [ ] Uniquement `console.error()`, JAMAIS `console.log()`
- [ ] Tool errors = `isError: true`, pas `throw`
- [ ] JSON-RPC 2.0 responses conformes
- [ ] Input schemas validés avec Zod

**Performance:**
- [ ] HNSW index utilisé (order by distance directement)
- [ ] Promise.all pour opérations parallèles
- [ ] Transactions avec `tx`, pas `db`
- [ ] NFR thresholds respectés (si Layer 1-3)

**Type Safety:**
- [ ] Pas de `any`, `!`, `@ts-ignore`
- [ ] `null` pour absence, `[]` pour zéro résultats
- [ ] Zod 4 syntax (`{ error: ... }`)
- [ ] TypeScript strict mode pass

**Testing:**
- [ ] Tests unitaires ajoutés (Domain 100%, Application >85%)
- [ ] Contract tests si nouveau port/adapter
- [ ] MCP compliance si modification tools
- [ ] Coverage thresholds maintenus

---

#### 🎯 Résumé: Top 5 Erreurs à JAMAIS Faire

1. **console.log() dans MCP server** → Protocol cassé, ZERO communication
2. **throw au lieu de isError dans MCP tools** → Invocations cassées
3. **Domain importe infrastructure** → Architecture hexagonale violée
4. **ORDER BY 1 - distance au lieu de distance** → HNSW index pas utilisé, perf effondrée
5. **db au lieu de tx dans transaction** → Atomicité perdue, data corruption

**Mémoriser**: Si un agent IA fait UNE de ces 5 erreurs, le système Alexandria est **complètement cassé** ou **inutilisable en production**.

---

## Usage Guidelines

**For AI Agents:**

- **Read this file before implementing any code** - All rules are mandatory
- **Follow ALL rules exactly as documented** - No exceptions without explicit justification
- **When in doubt, prefer the more restrictive option** - Err on the side of strictness
- **Consult specific sections for context** - Each section addresses different concerns
- **Critical sections to never skip:**
  - MCP Protocol rules (TIER 1 - Bloquant complet)
  - HNSW Index usage (TIER 2 - Critique performance)
  - Architecture Hexagonale boundaries (TIER 1 - Bloquant complet)
- **Update this file if new patterns emerge** - Document learnings for future agents

**For Humans:**

- **Keep this file lean and focused on agent needs** - Only unobvious details that LLMs miss
- **Update when technology stack changes** - Version bumps, new frameworks, breaking changes
- **Review quarterly for outdated rules** - Remove rules that become industry standard
- **Test with real AI agent implementation** - Validate rules prevent actual mistakes
- **Prioritize TIER 1 and TIER 2 rules** - These break production if violated

**Integration Points:**

- **BMAD Workflows:** Auto-loaded in `dev-story`, `quick-dev`, `code-review` workflows
- **Custom Skills:** Reference via `@project-context.md` in skill prompts
- **Sub-Agents:** Include in agent system prompts for consistency
- **Claude Code Sessions:** Explicitly include when starting implementation work

**Maintenance Schedule:**

- **After each epic completion:** Review for new patterns discovered
- **Technology upgrades:** Update versions and breaking changes immediately
- **Quarterly review:** Optimize content, remove obvious rules
- **NFR changes:** Update performance thresholds if requirements evolve

Last Updated: 2025-12-26
