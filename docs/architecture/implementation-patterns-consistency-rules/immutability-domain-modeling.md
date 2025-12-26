# Immutability & Domain Modeling

## **Value Objects - Immutables**

```typescript
// ✅ CORRECT: Value Object immutable
// src/domain/value-objects/ProjectId.ts
export class ProjectId {
  readonly value: string  // ✅ readonly

  private constructor(value: string) {  // ✅ private constructor
    if (!value || value.trim().length === 0) {
      throw new InvalidProjectIdError('ProjectId cannot be empty')
    }
    this.value = value
  }

  static create(value: string): ProjectId {  // ✅ static factory
    return new ProjectId(value)
  }

  equals(other: ProjectId): boolean {
    return this.value === other.value
  }
}

// Usage
const projectId = ProjectId.create('proj-123')  // ✅ Via factory
// projectId.value = 'other'  // ❌ Compile error (readonly)

// ❌ INCORRECT
export class ProjectId {
  public value: string  // ❌ Pas readonly

  constructor(value: string) {  // ❌ Public constructor
    this.value = value
  }

  setValue(value: string) {  // ❌ Setter (mutable)
    this.value = value
  }
}
```

## **Entities - Immutables avec Méthodes Métier**

```typescript
// ✅ CORRECT: Entity immutable
// src/domain/entities/Convention.ts
export class Convention {
  readonly id: string
  readonly projectId: string
  readonly contentText: string
  readonly embedding?: number[]
  readonly createdAt: Date

  private constructor(data: ConventionData) {
    this.id = data.id
    this.projectId = data.projectId
    this.contentText = data.contentText
    this.embedding = data.embedding
    this.createdAt = data.createdAt ?? new Date()
  }

  static create(data: ConventionData): Convention {
    // Validation métier
    if (!data.contentText || data.contentText.trim().length === 0) {
      throw new InvalidConventionError('Content cannot be empty')
    }
    if (data.contentText.length > MAX_QUERY_LENGTH) {
      throw new InvalidConventionError(`Content exceeds ${MAX_QUERY_LENGTH} chars`)
    }

    return new Convention(data)
  }

  // Méthode métier (retourne nouvelle instance)
  withEmbedding(embedding: number[]): Convention {
    if (embedding.length !== EMBEDDING_DIMENSIONS) {
      throw new InvalidEmbeddingError(`Expected ${EMBEDDING_DIMENSIONS} dimensions`)
    }

    return new Convention({
      ...this,
      embedding
    })
  }

  hasEmbedding(): boolean {
    return this.embedding !== undefined && this.embedding.length > 0
  }
}

// ❌ INCORRECT
export class Convention {
  public id: string  // ❌ Pas readonly

  setEmbedding(embedding: number[]) {  // ❌ Setter mutable
    this.embedding = embedding
  }
}
```

---
