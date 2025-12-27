# Pattern Examples

## **Good Examples (Follow These):**

**✅ PERFECT Use-Case Example:**
```typescript
// src/domain/use-cases/RetrieveRawContext.ts
import { Convention } from '@/domain/entities/Convention'
import { Documentation } from '@/domain/entities/Documentation'
import { ConventionRepositoryPort } from '@/ports/secondary/ConventionRepositoryPort'
import { DocumentationRepositoryPort } from '@/ports/secondary/DocumentationRepositoryPort'
import { EmbeddingGeneratorPort } from '@/ports/secondary/EmbeddingGeneratorPort'
import { LoggerPort } from '@/ports/secondary/LoggerPort'
import { DEFAULT_TOP_K } from '@/config/constants'
import { RetrieveContextError } from '@/domain/errors/RetrieveContextError'

export class RetrieveRawContext {
  constructor(
    private conventionRepo: ConventionRepositoryPort,
    private documentationRepo: DocumentationRepositoryPort,
    private embeddingGen: EmbeddingGeneratorPort,
    private logger: LoggerPort
  ) {}

  async execute(
    projectId: string,
    query: string,
    topK: number = DEFAULT_TOP_K
  ): Promise<RawContext> {
    const startTime = performance.now()

    try {
      // Layer 1: Vector Search
      const queryEmbedding = await this.embeddingGen.generate(query)
      const conventions = await this.conventionRepo.search(projectId, queryEmbedding, topK)

      this.logger.info('Layer 1 completed', {
        projectId,
        query,
        layer: 'layer1',
        operation: 'search',
        resultCount: conventions.length,
        latencyMs: Math.round(performance.now() - startTime)
      })

      // Layer 2: Technology Linking
      const technologies = conventions.flatMap(c => c.technologies)
      const linkedDocs = await this.documentationRepo.findByTechnologies(projectId, technologies)

      const totalLatency = Math.round(performance.now() - startTime)

      this.logger.info('Layer 2 completed', {
        projectId,
        layer: 'layer2',
        operation: 'link',
        documentationCount: linkedDocs.length,
        totalLatencyMs: totalLatency
      })

      return {
        conventions,
        linkedDocumentation: linkedDocs
      }
    } catch (error) {
      this.logger.error('Retrieve raw context failed', error, {
        projectId,
        query,
        layer: 'domain',
        operation: 'retrieve_raw_context'
      })
      throw new RetrieveContextError('Failed to retrieve context', { cause: error })
    }
  }
}
```

**✅ PERFECT Entity Example:**
```typescript
// src/domain/entities/Convention.ts
import { InvalidConventionError } from '@/domain/errors/InvalidConventionError'
import { InvalidEmbeddingError } from '@/domain/errors/InvalidEmbeddingError'
import { MAX_QUERY_LENGTH, EMBEDDING_DIMENSIONS } from '@/config/constants'

export interface ConventionData {
  id: string
  projectId: string
  contentText: string
  embedding?: number[]
  technologies?: string[]
  createdAt?: Date
}

export class Convention {
  readonly id: string
  readonly projectId: string
  readonly contentText: string
  readonly embedding?: number[]
  readonly technologies: string[]
  readonly createdAt: Date

  private constructor(data: ConventionData) {
    this.id = data.id
    this.projectId = data.projectId
    this.contentText = data.contentText
    this.embedding = data.embedding
    this.technologies = data.technologies ?? []
    this.createdAt = data.createdAt ?? new Date()
  }

  static create(data: ConventionData): Convention {
    if (!data.contentText?.trim()) {
      throw new InvalidConventionError('Content cannot be empty')
    }
    if (data.contentText.length > MAX_QUERY_LENGTH) {
      throw new InvalidConventionError(`Content exceeds ${MAX_QUERY_LENGTH} chars`)
    }
    return new Convention(data)
  }

  withEmbedding(embedding: number[]): Convention {
    if (embedding.length !== EMBEDDING_DIMENSIONS) {
      throw new InvalidEmbeddingError(`Expected ${EMBEDDING_DIMENSIONS} dimensions`)
    }
    return new Convention({ ...this, embedding })
  }

  hasEmbedding(): boolean {
    return this.embedding !== undefined && this.embedding.length > 0
  }
}
```

**✅ PERFECT Adapter Example:**
```typescript
// src/adapters/secondary/database/repositories/DrizzleConventionRepository.ts
import { Convention } from '@/domain/entities/Convention'
import { ConventionRepositoryPort } from '@/ports/secondary/ConventionRepositoryPort'
import { LoggerPort } from '@/ports/secondary/LoggerPort'
import { RepositoryError } from '@/shared/errors/RepositoryError'
import { conventions } from '@/adapters/secondary/database/schema/conventions'
import { eq, sql } from 'drizzle-orm'
import { DEFAULT_TOP_K } from '@/config/constants'
import type { DrizzleDatabase } from '@/adapters/secondary/database/connection'

export class DrizzleConventionRepository implements ConventionRepositoryPort {
  constructor(
    private db: DrizzleDatabase,
    private logger: LoggerPort
  ) {}

  async search(
    projectId: string,
    embedding: number[],
    topK: number = DEFAULT_TOP_K
  ): Promise<Convention[]> {
    try {
      const results = await this.db
        .select()
        .from(conventions)
        .where(eq(conventions.projectId, projectId))
        .orderBy(sql`"embeddingVector" <=> ${embedding}`)
        .limit(topK)

      return results.map(row => Convention.create({
        id: row.id,
        projectId: row.projectId,
        contentText: row.contentText,
        embedding: row.embeddingVector,
        technologies: row.technologies,
        createdAt: row.createdAt
      }))
    } catch (error) {
      this.logger.error('Vector search failed', error, {
        projectId,
        layer: 'adapter',
        operation: 'search'
      })
      throw new RepositoryError('Failed to search conventions', { cause: error })
    }
  }

  async save(convention: Convention): Promise<void> {
    try {
      await this.db.transaction(async (tx) => {
        await tx.insert(conventions).values({
          id: convention.id,
          projectId: convention.projectId,
          contentText: convention.contentText,
          embeddingVector: convention.embedding,
          technologies: convention.technologies,
          createdAt: convention.createdAt
        })
      })

      this.logger.info('Convention saved', {
        projectId: convention.projectId,
        conventionId: convention.id,
        layer: 'adapter',
        operation: 'save'
      })
    } catch (error) {
      this.logger.error('Failed to save convention', error, {
        projectId: convention.projectId,
        conventionId: convention.id,
        layer: 'adapter',
        operation: 'save'
      })
      throw new RepositoryError('Failed to save convention', { cause: error })
    }
  }
}
```

## **Anti-Patterns (Avoid These):**

```typescript
// ❌ WRONG: Domain dépend de Drizzle
import { drizzle } from 'drizzle-orm'  // ❌ Infrastructure dans domain

export class RetrieveRawContext {
  constructor(private db: drizzle) {}  // ❌ Pas de port
}

// ❌ WRONG: Mutable entity
export class Convention {
  public id: string  // ❌ Pas readonly

  setId(id: string) {  // ❌ Setter
    this.id = id
  }
}

// ❌ WRONG: Zod dans domain
import { z } from 'zod'  // ❌ Zod dans domain

export class RetrieveRawContext {
  execute(projectId: string) {
    z.string().uuid().parse(projectId)  // ❌ Validation dans domain
  }
}

// ❌ WRONG: Pas de logging
} catch (error) {
  throw error  // ❌ Pas de logger.error()
}

// ❌ WRONG: snake_case database
export const conventions = pgTable('conventions', {
  project_id: uuid('project_id')  // ❌ snake_case
})

// ❌ WRONG: Relative imports
import { Convention } from '../../../../domain/entities/Convention'  // ❌ Fragile

// ❌ WRONG: Pas de suffix Port
interface ConventionRepository { }  // ❌ Pas de "Port"

// ❌ WRONG: .then() chains
return this.embeddingGen.generate(query)
  .then(emb => this.repo.search(emb))  // ❌ Utiliser async/await
```

---
