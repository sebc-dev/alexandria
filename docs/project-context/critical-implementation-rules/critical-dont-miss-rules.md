# Critical Don't-Miss Rules

## 🔴 TIER 1: BLOQUANT COMPLET (Production cassée)

### 1. MCP Protocol: JAMAIS console.log(), TOUJOURS console.error()

```typescript
// ❌ BLOQUANT COMPLET - Casse toute communication MCP
console.log('[INFO] Processing retrieve request');
console.log('Debug:', data);

// ✅ CORRECT
console.error('[INFO] Processing retrieve request');
console.error('Debug:', data);
```

**Impact**: Un seul `console.log()` corrompt le stream JSON-RPC et casse complètement l'intégration Claude Code. **ZERO tolérance.**

### 2. MCP Protocol: Tool Errors = isError, PAS throw

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

### 3. Architecture Hexagonale: Domain JAMAIS import Infrastructure

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

## 🟠 TIER 2: CRITIQUE PERFORMANCE (NFRs non respectées)

### 4. HNSW Index: Order by Distance Directement

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

### 5. Parallélisation: Promise.all pour Opérations Indépendantes

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

### 6. Drizzle Transactions: tx vs db

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

## 🟡 TIER 3: IMPORTANT (Type Safety & Maintenabilité)

### 7. TypeScript: null vs undefined Consistency

```typescript
// ❌ INCONSISTENT - undefined ambigu
findById(id: string): Promise<Convention | undefined>
search(q: string): Promise<Convention[] | undefined>

// ✅ CORRECT - null = absence intentionnelle
findById(id: string): Promise<Convention | null>
search(q: string): Promise<Convention[]> // [] = aucun résultat
```

**Impact**: `undefined` ambigu (absence vs erreur). `null` signifie explicitement "pas trouvé". Arrays vides = zéro résultats.

### 8. Zod 4.2.1: Nouvelle Syntaxe Error

```typescript
// ❌ BREAKING - Zod 3 syntax ne marche plus
z.string({ message: 'Required' })
z.string({ required_error: 'Required', invalid_type_error: 'Must be string' })

// ✅ CORRECT - Zod 4 syntax
z.string({ error: 'Required' })
z.string({ error: (iss) => iss.input === undefined ? 'Required' : 'Must be string' })
```

**Impact**: Alexandria utilise Zod 4.2.1. Ancienne syntaxe génère erreurs runtime.

### 9. Drizzle: Identity vs Serial (PostgreSQL 17)

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

### 10. TypeScript Strict Interdictions

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

## 🔵 TIER 4: EDGE CASES SPÉCIFIQUES ALEXANDRIA

### 11. Layer 3 Sub-Agent: Pas d'Appel API Direct

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

### 12. Convention Technologies: Multi-Technologie Support

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

### 13. Embedding Dimensions: Toujours 1536

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

### 14. NFR Performance Thresholds: Toujours Valider

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

### 15. Zod Validation: Frontières Uniquement

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

## 📋 Checklist Validation Avant Commit

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

## 🎯 Résumé: Top 5 Erreurs à JAMAIS Faire

1. **console.log() dans MCP server** → Protocol cassé, ZERO communication
2. **throw au lieu de isError dans MCP tools** → Invocations cassées
3. **Domain importe infrastructure** → Architecture hexagonale violée
4. **ORDER BY 1 - distance au lieu de distance** → HNSW index pas utilisé, perf effondrée
5. **db au lieu de tx dans transaction** → Atomicité perdue, data corruption

**Mémoriser**: Si un agent IA fait UNE de ces 5 erreurs, le système Alexandria est **complètement cassé** ou **inutilisable en production**.

---
