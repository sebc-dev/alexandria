# Process Patterns

## **Validation Placement (Zod Boundaries)**

**Zod uniquement aux Boundaries:**
```typescript
// ✅ CORRECT: Validation à l'entrée (Adapter Primaire)
// src/adapters/primary/mcp-server/schemas/RetrieveSchema.ts
import { z } from 'zod'

export const retrieveInputSchema = z.object({
  query: z.string().min(1).max(MAX_QUERY_LENGTH),
  projectId: z.string().uuid(),
  topK: z.number().int().positive().max(MAX_TOP_K).optional()
})

// src/adapters/primary/mcp-server/routes/retrieve.ts
app.post('/retrieve_raw_context', async (c) => {
  const input = retrieveInputSchema.parse(await c.req.json())  // ✅ Validation boundary

  const result = await retrieveRawContext.execute(
    input.projectId,
    input.query,
    input.topK
  )
  return c.json(result)
})

// ✅ CORRECT: Domain layer PUR (pas de Zod)
// src/domain/use-cases/RetrieveRawContext.ts
export class RetrieveRawContext {
  // ❌ Pas de: import { z } from 'zod'

  execute(projectId: string, query: string, topK?: number): Promise<Result> {
    // Assume données déjà validées par adapter
    // Logique métier pure TypeScript
  }
}

// ✅ CORRECT: Validation response externe (Adapter Secondaire)
// src/adapters/secondary/openai/schemas/EmbeddingResponseSchema.ts
export const embeddingResponseSchema = z.object({
  data: z.array(z.object({
    embedding: z.array(z.number()).length(EMBEDDING_DIMENSIONS)
  }))
})

// src/adapters/secondary/openai/OpenAIEmbeddingAdapter.ts
async generate(text: string): Promise<number[]> {
  const response = await openai.embeddings.create({ ... })
  const validated = embeddingResponseSchema.parse(response)  // ✅ Validation boundary
  return validated.data[0].embedding
}

// ❌ INCORRECT
// src/domain/use-cases/RetrieveRawContext.ts
import { z } from 'zod'  // ❌ Zod dans Domain = viole hexagonal !

execute(projectId: string) {
  z.string().uuid().parse(projectId)  // ❌ Validation dans domain
}
```

**Config Validation - Fail-Fast Startup:**
```typescript
// ✅ CORRECT
// src/config/env.ts
import { z } from 'zod'

const envSchema = z.object({
  ALEXANDRIA_DB_URL: z.string().url(),
  OPENAI_API_KEY: z.string().min(20),
  LOG_RETENTION_DAYS: z.string().transform(Number).pipe(z.number().int().positive()).default('30'),
  ALEXANDRIA_LOG_LEVEL: z.enum(['debug', 'info', 'warn', 'error']).default('info')
})

// Parse au module load (fail-fast si invalide)
export const env = envSchema.parse(process.env)

// src/index.ts
import { env } from '@/config/env'  // ← Crash immédiatement si .env invalide

console.log(`Starting Alexandria with DB: ${env.ALEXANDRIA_DB_URL}`)
```

---

## **Transaction Management (Drizzle)**

**Adapters gèrent les transactions (pas Use-Cases):**
```typescript
// ✅ CORRECT: Use-case ne connaît pas Drizzle
// src/domain/use-cases/UploadConvention.ts
export class UploadConvention {
  constructor(
    private conventionRepo: ConventionRepositoryPort,  // Port, pas Drizzle !
    private embeddingGen: EmbeddingGeneratorPort,
    private logger: LoggerPort
  ) {}

  async execute(data: ConventionData): Promise<Convention> {
    // Logique métier, pas de transaction ici
    const convention = Convention.create(data)
    const embedding = await this.embeddingGen.generate(convention.contentText)
    const withEmbedding = convention.withEmbedding(embedding)

    // Repository gère la transaction en interne
    await this.conventionRepo.save(withEmbedding)

    this.logger.info('Convention uploaded', {
      projectId: convention.projectId,
      conventionId: convention.id,
      layer: 'domain',
      operation: 'upload'
    })

    return withEmbedding
  }
}

// ✅ CORRECT: Adapter gère transaction
// src/adapters/secondary/database/repositories/DrizzleConventionRepository.ts
export class DrizzleConventionRepository implements ConventionRepositoryPort {
  async save(convention: Convention): Promise<void> {
    // Adapter décide de la stratégie transactionnelle
    await this.db.transaction(async (tx) => {
      await tx.insert(conventions).values({
        id: convention.id,
        projectId: convention.projectId,
        contentText: convention.contentText,
        embeddingVector: convention.embedding,
        createdAt: convention.createdAt
      })
    })
  }
}

// ❌ INCORRECT: Use-case gère transaction
export class UploadConvention {
  constructor(private db: DrizzleDatabase) {}  // ❌ Dépendance Drizzle !

  async execute(data: ConventionData) {
    await this.db.transaction(async (tx) => {  // ❌ Viole hexagonal
      // ...
    })
  }
}
```

---

## **Dependency Injection Pattern**

**Constructor Injection Manuelle:**
```typescript
// ✅ CORRECT: Bootstrap centralisé
// src/index.ts
import { drizzle } from 'drizzle-orm/postgres-js'
import postgres from 'postgres'
import { env } from '@/config/env'

// 1. Créer adapters secondaires
const pgConnection = postgres(env.ALEXANDRIA_DB_URL)
const db = drizzle(pgConnection)

const logger = new BunDualLogger(env.ALEXANDRIA_LOG_LEVEL, './logs')

const conventionRepo = new DrizzleConventionRepository(db, logger)
const documentationRepo = new DrizzleDocumentationRepository(db, logger)
const embeddingGenerator = new OpenAIEmbeddingAdapter(env.OPENAI_API_KEY, logger)

// 2. Créer use-cases avec injection
const retrieveRawContext = new RetrieveRawContext(
  conventionRepo,        // Port: ConventionRepositoryPort
  documentationRepo,     // Port: DocumentationRepositoryPort
  embeddingGenerator,    // Port: EmbeddingGeneratorPort
  logger                 // Port: LoggerPort
)

const uploadConvention = new UploadConvention(
  conventionRepo,
  embeddingGenerator,
  logger
)

// 3. Créer adapters primaires (MCP server)
const mcpServer = new HonoMCPServer(
  retrieveRawContext,
  uploadConvention,
  logger
)

// 4. Start
mcpServer.start()

// ❌ INCORRECT: IoC container (over-engineering pour Alexandria)
const container = new Container()
container.bind(ConventionRepositoryPort).to(DrizzleConventionRepository)
// ... trop complexe pour MVP
```

---

## **Async/Await Patterns**

**async/await uniquement (jamais .then()):**
```typescript
// ✅ CORRECT
async execute(query: string): Promise<Convention[]> {
  try {
    const embedding = await this.embeddingGen.generate(query)
    const conventions = await this.conventionRepo.search(embedding)
    return conventions
  } catch (error) {
    this.logger.error('Search failed', error, { query, layer: 'domain' })
    throw new SearchError('Failed to search conventions', { cause: error })
  }
}

// ✅ CORRECT: Promise.all() pour parallélisation
async execute(projectId: string): Promise<Stats> {
  const [conventionCount, documentationCount, techCount] = await Promise.all([
    this.conventionRepo.count(projectId),
    this.documentationRepo.count(projectId),
    this.technologyRepo.count(projectId)
  ])

  return { conventionCount, documentationCount, techCount }
}

// ❌ INCORRECT: .then()/.catch() chains
execute(query: string) {
  return this.embeddingGen.generate(query)
    .then(embedding => this.conventionRepo.search(embedding))  // ❌ Éviter
    .catch(error => {
      console.log(error)  // ❌ Pas de logger
      throw error
    })
}
```

---
