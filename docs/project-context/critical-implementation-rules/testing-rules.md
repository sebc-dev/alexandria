# Testing Rules

## 1. Testing par Layer Hexagonal (FONDAMENTAL)

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

## 2. Contract Testing pour Ports (Prevent Regressions)

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

## 3. HNSW Index Usage Validation (NFR2: Layer 1 ≤1s)

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

## 4. Performance Testing avec NFR Thresholds

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

## 5. MCP Protocol Compliance (7 Tools)

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

## 6. Factory Pattern pour Fixtures Déterministes

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

## 7. OpenAI Mocking Systématique (Unit Tests)

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

## 8. Golden Dataset pour Vector Search Quality

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

## 9. withTransaction Helper (Clean Tests)

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

## 10. Property-Based Testing (Domain Invariants)

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

## 11. Pyramide de Tests Alexandria

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
