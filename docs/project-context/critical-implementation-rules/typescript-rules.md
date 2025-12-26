# TypeScript Rules

## 1. Constructor Injection avec Ports

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

## 2. Immutabilité des Entities et Value Objects

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

## 3. Nullabilité Explicite

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

## 4. Parallélisation avec Promise.all

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

## 5. Early Return Pattern

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

## 6. Constantes avec `as const`

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

## 7. Type Guards pour Unions

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

## 8. Interdictions Strictes

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

## 9. Zod aux Frontières Uniquement

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
