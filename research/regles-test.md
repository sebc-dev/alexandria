# Testing Best Practices pour Architecture Hexagonale avec Vector DB et AI

Le projet Alexandria possède déjà une base de testing solide. Cette recherche identifie **12 améliorations clés** qui transformeront vos tests en véritables filets de sécurité, tout en réduisant les coûts d'exécution et la maintenance. Les recommandations prioritaires : adopter le pattern **Fake/Simulator** plutôt que les mocks classiques pour les ports, implémenter des **Contract Tests** entre layers, et structurer un **golden dataset** pour valider la qualité du vector search.

---

## Architecture de testing hexagonale renforcée

Votre stratégie actuelle par layer est correcte, mais peut être optimisée avec des patterns plus robustes. Le Domain layer doit rester **100% pur** sans aucune dépendance injectable - si un test Domain nécessite un mock, c'est un signal architectural.

### Domain Layer: Tests purs avec invariants explicites

```typescript
// tests/domain/entities/convention.test.ts
import { describe, it, expect } from "bun:test";
import { Convention } from "@/domain/entities/convention";
import { ConventionContent } from "@/domain/value-objects/convention-content";

describe("Convention Entity", () => {
  describe("invariants métier", () => {
    it("should reject empty convention content", () => {
      expect(() => ConventionContent.create("")).toThrow("Content cannot be empty");
    });

    it("should emit ConventionCreated event on creation", () => {
      const convention = Convention.create({
        id: "conv-1",
        content: ConventionContent.create("Always use TypeScript strict mode"),
        category: "code-quality"
      });
      expect(convention.domainEvents).toHaveLength(1);
      expect(convention.domainEvents[0].type).toBe("ConventionCreated");
    });

    it("should prevent modification of archived conventions", () => {
      const convention = Convention.create({ /* ... */ });
      convention.archive();
      expect(() => convention.updateContent("New content")).toThrow(
        "Cannot modify archived convention"
      );
    });
  });
});
```

**Pattern clé** : passez les dépendances non-déterministes (UUID, timestamps) en paramètres plutôt que de les générer dans le Domain. Cela permet des tests reproductibles sans mocks.

### Application Layer: Fakes plutôt que Mocks

Les **Fakes** (implémentations simplifiées in-memory) sont supérieurs aux mocks car ils ont un comportement réel et permettent des tests orientés comportement plutôt qu'implémentation.

```typescript
// src/infrastructure/repositories/in-memory-convention-repository.ts
export class InMemoryConventionRepository implements ConventionRepository {
  private conventions: Map<string, Convention> = new Map();

  async save(convention: Convention): Promise<void> {
    this.conventions.set(convention.id, convention);
  }

  async findById(id: string): Promise<Convention | null> {
    return this.conventions.get(id) || null;
  }

  async findByCategory(category: string): Promise<Convention[]> {
    return Array.from(this.conventions.values())
      .filter(c => c.category === category);
  }

  // API de test uniquement
  clear(): void { this.conventions.clear(); }
  getAll(): Convention[] { return Array.from(this.conventions.values()); }
}

// tests/application/use-cases/inject-conventions.test.ts
describe("InjectConventionsUseCase", () => {
  const createUseCase = () => {
    const conventionRepo = new InMemoryConventionRepository();
    const vectorSearch = new InMemoryVectorSearch();
    const useCase = new InjectConventionsUseCase(conventionRepo, vectorSearch);
    return { useCase, conventionRepo, vectorSearch };
  };

  it("should inject relevant conventions based on context", async () => {
    const { useCase, conventionRepo } = createUseCase();
    
    // Given: conventions existantes
    conventionRepo.save(Convention.create({ 
      content: "Use strict TypeScript",
      category: "typescript"
    }));
    
    // When: injection demandée
    const result = await useCase.execute({ 
      context: "Working on TypeScript file",
      maxConventions: 5 
    });
    
    // Then: conventions pertinentes injectées
    expect(result.conventions).toHaveLength(1);
    expect(result.conventions[0].content).toContain("TypeScript");
  });
});
```

---

## Contract Testing entre layers hexagonaux

Le **Contract Testing** garantit que les adapters d'infrastructure respectent les contrats définis par les ports de l'Application layer. Créez une suite de tests abstraite exécutable contre toutes les implémentations.

```typescript
// tests/contracts/convention-repository.contract.ts
export function conventionRepositoryContract(
  createRepository: () => ConventionRepository | Promise<ConventionRepository>,
  cleanup?: () => Promise<void>
) {
  describe("ConventionRepository Contract", () => {
    let repository: ConventionRepository;

    beforeEach(async () => {
      repository = await createRepository();
      if (cleanup) await cleanup();
    });

    it("should save and retrieve a convention by id", async () => {
      const convention = Convention.create({ id: "test-1", content: "Test" });
      await repository.save(convention);
      const found = await repository.findById("test-1");
      
      expect(found).not.toBeNull();
      expect(found!.content).toBe("Test");
    });

    it("should return null for non-existent convention", async () => {
      const found = await repository.findById("non-existent");
      expect(found).toBeNull();
    });

    it("should update existing convention when saved twice", async () => {
      const convention = Convention.create({ id: "test-1", content: "V1" });
      await repository.save(convention);
      
      convention.updateContent("V2");
      await repository.save(convention);
      
      const found = await repository.findById("test-1");
      expect(found!.content).toBe("V2");
    });
  });
}

// Usage - tests/unit/in-memory-repository.test.ts
import { conventionRepositoryContract } from "../contracts/convention-repository.contract";
conventionRepositoryContract(() => new InMemoryConventionRepository());

// Usage - tests/integration/drizzle-repository.test.ts
conventionRepositoryContract(
  () => new DrizzleConventionRepository(testDb),
  () => testDb.delete(conventions)
);
```

---

## Testing pgvector et recherche sémantique

Le testing de vector search requiert une approche spécifique combinant **validation de qualité** (recall/precision) et **tests de performance** (latence HNSW).

### Setup Docker pour tests pgvector

```yaml
# docker-compose.test.yml
services:
  pgvector-test:
    image: pgvector/pgvector:pg17
    environment:
      POSTGRES_USER: test
      POSTGRES_PASSWORD: test
      POSTGRES_DB: alexandria_test
    ports:
      - "5433:5432"
    tmpfs:
      - /var/lib/postgresql/data  # RAM pour performance
    command: >
      postgres
        -c shared_buffers=256MB
        -c maintenance_work_mem=512MB
```

### Golden dataset pour validation de qualité

```typescript
// tests/fixtures/vector-search-golden.ts
export const goldenDataset = {
  queries: [
    {
      queryText: "TypeScript strict mode configuration",
      expectedResults: ["conv-ts-strict", "conv-ts-config"],
      minRecall: 0.9
    },
    {
      queryText: "Error handling patterns",
      expectedResults: ["conv-error-handling", "conv-try-catch"],
      minRecall: 0.85
    }
  ]
};

// tests/integration/vector-search-quality.test.ts
describe("Vector Search Quality", () => {
  it("should meet recall thresholds on golden dataset", async () => {
    for (const query of goldenDataset.queries) {
      const results = await vectorSearch.search(query.queryText, 10);
      const resultIds = results.map(r => r.id);
      
      const recall = query.expectedResults.filter(
        id => resultIds.includes(id)
      ).length / query.expectedResults.length;
      
      expect(recall).toBeGreaterThanOrEqual(query.minRecall);
    }
  });
});
```

### Performance testing HNSW

```typescript
// tests/performance/hnsw-latency.test.ts
import { describe, it, expect } from "bun:test";

const LATENCY_THRESHOLDS = {
  p50: 5,   // ms
  p95: 15,  // ms
  p99: 50   // ms
};

describe("HNSW Performance", () => {
  it("should meet latency SLAs", async () => {
    const latencies: number[] = [];
    
    for (const query of benchmarkQueries) {
      const start = performance.now();
      await vectorSearch.search(query.embedding, 10);
      latencies.push(performance.now() - start);
    }
    
    latencies.sort((a, b) => a - b);
    const p50 = latencies[Math.floor(latencies.length * 0.5)];
    const p95 = latencies[Math.floor(latencies.length * 0.95)];
    
    expect(p50).toBeLessThan(LATENCY_THRESHOLDS.p50);
    expect(p95).toBeLessThan(LATENCY_THRESHOLDS.p95);
  });

  it("should use HNSW index (not sequential scan)", async () => {
    const explainResult = await db.execute(sql`
      EXPLAIN ANALYZE 
      SELECT id FROM conventions 
      ORDER BY embedding <=> ${testEmbedding}::vector 
      LIMIT 10
    `);
    
    const plan = explainResult.rows.map(r => r["QUERY PLAN"]).join("\n");
    expect(plan).toContain("hnsw");
    expect(plan).not.toContain("Seq Scan");
  });
});
```

### Fixtures vectorielles déterministes

```typescript
// tests/fixtures/embeddings.factory.ts
import { createHash } from "crypto";

export function generateDeterministicEmbedding(text: string, dimension = 1536): number[] {
  // Génère un embedding reproductible basé sur le hash du texte
  const seed = parseInt(createHash("sha256").update(text).digest("hex").slice(0, 8), 16);
  const rng = seedRandom(seed);
  
  const embedding = Array.from({ length: dimension }, () => (rng() - 0.5) * 2);
  
  // Normalisation L2
  const norm = Math.sqrt(embedding.reduce((sum, v) => sum + v * v, 0));
  return embedding.map(v => v / norm);
}

// Pour tests avec vraies embeddings pré-calculées
export const precomputedEmbeddings = {
  "TypeScript strict mode": [0.023, -0.012, /* ... 1534 values */],
  "Error handling": [0.018, -0.008, /* ... */]
};
```

---

## Testing des intégrations OpenAI

La stratégie recommandée combine **mocking systématique** pour les tests unitaires et **budget tracking** pour les rares tests avec l'API réelle.

### Mocking avec MSW ou nock

```typescript
// tests/mocks/openai.mock.ts
import nock from "nock";

export function mockOpenAIEmbeddings(responses: Record<string, number[]>) {
  nock("https://api.openai.com")
    .post("/v1/embeddings")
    .reply(200, (uri, requestBody: any) => {
      const text = requestBody.input;
      return {
        data: [{ embedding: responses[text] || generateDeterministicEmbedding(text) }],
        model: "text-embedding-ada-002",
        usage: { prompt_tokens: 10, total_tokens: 10 }
      };
    });
}

export function mockOpenAIRateLimit() {
  nock("https://api.openai.com")
    .post("/v1/embeddings")
    .reply(429, {
      error: { message: "Rate limit exceeded", type: "rate_limit_exceeded" }
    }, { "retry-after": "20" });
}

// tests/infrastructure/openai-adapter.test.ts
describe("OpenAI Adapter", () => {
  afterEach(() => nock.cleanAll());

  it("should handle rate limits with exponential backoff", async () => {
    mockOpenAIRateLimit();
    mockOpenAIEmbeddings({ "test": [0.1, 0.2, /* ... */] }); // 2nd call succeeds
    
    const result = await adapter.getEmbedding("test");
    expect(result).toHaveLength(1536);
  });

  it("should respect budget limits", async () => {
    const tracker = new TestBudgetTracker(maxTokens: 1000);
    
    // Simule dépassement
    tracker.track({ prompt_tokens: 500, completion_tokens: 600 });
    
    expect(() => tracker.checkBudget()).toThrow("Budget exceeded");
  });
});
```

### Tests conditionnels pour API réelle

```typescript
// tests/e2e/openai-integration.test.ts
const SKIP_REAL_API = !process.env.OPENAI_API_KEY || process.env.CI === "true";

describe.skipIf(SKIP_REAL_API)("OpenAI Real API", () => {
  it("should generate valid embeddings", async () => {
    const embedding = await realOpenAIAdapter.getEmbedding("Test content");
    
    expect(embedding).toHaveLength(1536);
    expect(embedding.every(v => typeof v === "number")).toBe(true);
  });
});
```

---

## Testing MCP Protocol compliance

Pour les **7 MCP tools** d'Alexandria, structurez les tests autour de la validation de schema et du comportement fonctionnel.

### Structure de test par tool

```typescript
// tests/mcp/tools/search-conventions.test.ts
import { describe, it, expect } from "bun:test";
import { MCPServer } from "@/infrastructure/mcp/server";
import Ajv from "ajv";

const ajv = new Ajv();

describe("MCP Tool: search_conventions", () => {
  const server = new MCPServer();
  
  describe("Schema validation", () => {
    it("should have valid input schema", () => {
      const tool = server.getTool("search_conventions");
      const validate = ajv.compile(tool.inputSchema);
      
      expect(validate({ query: "TypeScript", limit: 5 })).toBe(true);
      expect(validate({ query: "" })).toBe(false); // Empty query invalid
      expect(validate({ limit: 5 })).toBe(false);  // Missing required query
    });
  });

  describe("Functional behavior", () => {
    it("should return matching conventions", async () => {
      const result = await server.callTool("search_conventions", {
        query: "TypeScript configuration",
        limit: 5
      });
      
      expect(result.isError).toBe(false);
      expect(result.content[0].type).toBe("text");
      expect(JSON.parse(result.content[0].text)).toHaveProperty("conventions");
    });

    it("should handle empty results gracefully", async () => {
      const result = await server.callTool("search_conventions", {
        query: "xyznonexistent123",
        limit: 5
      });
      
      expect(result.isError).toBe(false);
      const data = JSON.parse(result.content[0].text);
      expect(data.conventions).toHaveLength(0);
    });
  });

  describe("Error handling", () => {
    it("should return isError for invalid queries", async () => {
      const result = await server.callTool("search_conventions", {
        query: "",  // Invalid
        limit: 5
      });
      
      expect(result.isError).toBe(true);
      expect(result.content[0].text).toContain("Query cannot be empty");
    });
  });

  describe("Security", () => {
    it("should sanitize injection attempts", async () => {
      const result = await server.callTool("search_conventions", {
        query: "'; DROP TABLE conventions; --",
        limit: 5
      });
      
      expect(result.isError).toBe(false);
      // Query exécutée sans dommage
    });
  });
});
```

### Test du protocole JSON-RPC 2.0

```typescript
// tests/mcp/protocol-compliance.test.ts
describe("MCP Protocol Compliance", () => {
  it("should return valid JSON-RPC 2.0 responses", async () => {
    const request = {
      jsonrpc: "2.0",
      id: 1,
      method: "tools/call",
      params: { name: "list_categories", arguments: {} }
    };
    
    const response = await server.handleRequest(request);
    
    expect(response.jsonrpc).toBe("2.0");
    expect(response.id).toBe(1);
    expect(response).toHaveProperty("result");
  });

  it("should handle tool errors in result, not protocol error", async () => {
    const response = await server.callTool("failing_tool", {});
    
    // Erreur tool = dans result avec isError: true
    expect(response.result.isError).toBe(true);
    // Pas d'erreur protocol-level
    expect(response.error).toBeUndefined();
  });
});
```

---

## Fixtures et helpers réutilisables

### Factory pattern avec Bun

```typescript
// tests/factories/convention.factory.ts
import { faker } from "@faker-js/faker";

interface ConventionOverrides {
  id?: string;
  content?: string;
  category?: string;
  embedding?: number[];
  active?: boolean;
}

export function createConvention(overrides: ConventionOverrides = {}): Convention {
  return Convention.create({
    id: overrides.id ?? faker.string.uuid(),
    content: overrides.content ?? faker.lorem.paragraph(),
    category: overrides.category ?? faker.helpers.arrayElement([
      "code-quality", "testing", "security", "performance"
    ]),
    embedding: overrides.embedding ?? generateDeterministicEmbedding(
      overrides.content ?? faker.lorem.paragraph()
    ),
    active: overrides.active ?? true
  });
}

// Variantes prédéfinies
export const createActiveConvention = (o?: ConventionOverrides) => 
  createConvention({ active: true, ...o });

export const createArchivedConvention = (o?: ConventionOverrides) => {
  const conv = createConvention({ active: false, ...o });
  conv.archive();
  return conv;
};

export const createConventions = (count: number, o?: ConventionOverrides) =>
  Array.from({ length: count }, () => createConvention(o));
```

### Transaction rollback helper

```typescript
// tests/helpers/database.ts
import { Pool, PoolClient } from "pg";

export const withTransaction = async <T>(
  fn: (client: PoolClient) => Promise<T>
): Promise<T> => {
  const client = await testPool.connect();
  await client.query("BEGIN ISOLATION LEVEL SERIALIZABLE;");
  
  try {
    return await fn(client);
  } finally {
    await client.query("ROLLBACK;");
    client.release();
  }
};

// Usage
it("should create convention in transaction", async () => {
  await withTransaction(async (client) => {
    const repo = new DrizzleConventionRepository(client);
    await repo.save(createConvention());
    
    const found = await repo.findAll();
    expect(found).toHaveLength(1);
  });
  // Transaction rollback - DB propre pour le prochain test
});
```

---

## Property-based testing pour le Domain

Le **property-based testing** avec fast-check est particulièrement utile pour valider les invariants du Domain layer.

```typescript
// tests/domain/properties/convention.properties.test.ts
import fc from "fast-check";
import { Convention } from "@/domain/entities/convention";

// Arbitrary pour Convention
const conventionArbitrary = fc.record({
  id: fc.uuid(),
  content: fc.string({ minLength: 1, maxLength: 10000 }),
  category: fc.constantFrom("code-quality", "testing", "security")
});

describe("Convention Properties", () => {
  it("content is always preserved after save/load cycle", () => {
    fc.assert(
      fc.property(conventionArbitrary, (data) => {
        const convention = Convention.create(data);
        const serialized = convention.toJSON();
        const restored = Convention.fromJSON(serialized);
        
        expect(restored.content).toBe(data.content);
      })
    );
  });

  it("archived conventions cannot be modified (invariant)", () => {
    fc.assert(
      fc.property(
        conventionArbitrary,
        fc.string({ minLength: 1 }),
        (data, newContent) => {
          const convention = Convention.create(data);
          convention.archive();
          
          expect(() => convention.updateContent(newContent)).toThrow();
        }
      )
    );
  });

  it("embedding dimension is always 1536", () => {
    fc.assert(
      fc.property(fc.string({ minLength: 1 }), async (text) => {
        const embedding = await embeddingService.generate(text);
        expect(embedding).toHaveLength(1536);
      })
    );
  });
});
```

---

## Configuration CI/CD avec Bun

### GitHub Actions optimisé

```yaml
# .github/workflows/test.yml
name: Tests
on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      
      - name: Cache dependencies
        uses: actions/cache@v4
        with:
          path: ~/.bun/install/cache
          key: ${{ runner.os }}-bun-${{ hashFiles('bun.lockb') }}
      
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
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun run db:migrate
        env:
          DATABASE_URL: postgres://test:test@localhost:5432/alexandria_test
      - run: bun test tests/integration
        env:
          DATABASE_URL: postgres://test:test@localhost:5432/alexandria_test

  mcp-compliance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun test tests/mcp --reporter=junit --reporter-outfile=mcp-results.xml
      - uses: EnricoMi/publish-unit-test-result-action@v2
        with:
          files: mcp-results.xml
```

### bunfig.toml recommandé

```toml
[test]
root = "."
preload = ["./tests/setup.ts"]
timeout = 15000
coverage = true
coverageReporter = ["text", "lcov"]
coverageThreshold = { lines = 0.80, functions = 0.85 }
coverageSkipTestFiles = true
```

---

## Pyramide de tests recommandée pour Alexandria

| Layer | Coverage cible | Type | Proportion |
|-------|----------------|------|------------|
| **Domain** | 100% | Unit, Property-based | 40% |
| **Application** | >85% | Unit avec Fakes | 25% |
| **Infrastructure** | Happy path + errors | Integration | 20% |
| **MCP Tools** | Schema + comportement | Contract | 10% |
| **E2E Pipeline** | Flux critiques | End-to-end | 5% |

## Checklist d'implémentation

1. **Immédiat** : Migrer les mocks de ports vers des Fakes in-memory
2. **Semaine 1** : Créer les Contract Tests pour tous les repositories
3. **Semaine 2** : Établir le golden dataset pour vector search (50-100 queries)
4. **Semaine 3** : Implémenter les tests de performance HNSW avec seuils
5. **Semaine 4** : Compléter la suite MCP avec validation de schema
6. **Continu** : Ajouter des property-based tests pour chaque nouvel invariant Domain

Cette stratégie de testing renforcée garantira la fiabilité du système Active Compliance Filter tout en maintenant des cycles de feedback rapides grâce à la séparation claire entre tests unitaires (millisecondes) et tests d'intégration (secondes).