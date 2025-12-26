# Port Interface Design

```typescript
// ✅ CORRECT: Port interface bien défini
// src/ports/secondary/ConventionRepositoryPort.ts

/**
 * Repository port for Convention aggregate
 * Responsible for persistence and vector search
 */
export interface ConventionRepositoryPort {
  /**
   * Search conventions by vector similarity using HNSW + cosine
   * @param projectId - Project to search within (isolation)
   * @param embedding - Query embedding vector (1536 dimensions)
   * @param topK - Number of results to return (default: DEFAULT_TOP_K)
   * @returns Conventions sorted by cosine similarity score (descending)
   * @throws RepositoryError if database query fails
   */
  search(
    projectId: string,
    embedding: number[],
    topK?: number
  ): Promise<Convention[]>

  /**
   * Save convention with embedding vector
   * Uses Drizzle transaction internally
   * @param convention - Convention entity to persist
   * @throws RepositoryError if save fails
   */
  save(convention: Convention): Promise<void>

  /**
   * Find convention by ID within project scope
   * @param id - Convention UUID
   * @param projectId - Project scope (application-level filtering)
   * @returns Convention or null if not found
   */
  findById(id: string, projectId: string): Promise<Convention | null>

  /**
   * Delete convention by ID
   * @param id - Convention UUID
   * @param projectId - Project scope
   * @throws ConventionNotFoundError if convention doesn't exist
   * @throws RepositoryError if delete fails
   */
  delete(id: string, projectId: string): Promise<void>

  /**
   * Count conventions for a project
   * @param projectId - Project scope
   * @returns Total count of conventions
   */
  count(projectId: string): Promise<number>
}

// ❌ INCORRECT
export interface ConventionRepositoryPort {
  search(embedding: any): any  // ❌ Pas de types, pas de docs
  save(data: object)           // ❌ Pas domain type
}

export type ConventionRepositoryPort = {  // ❌ Type au lieu d'interface
  // ...
}

export interface IConventionRepository { }  // ❌ Prefix I
export interface ConventionRepository { }   // ❌ Pas de suffix Port
```

**Règles obligatoires:**
- ✅ `interface` keyword (jamais `type`)
- ✅ Suffix `Port` obligatoire
- ✅ JSDoc complet (`@param`, `@returns`, `@throws`)
- ✅ Domain types (ex: `Convention`, pas `ConventionDTO` ou `any`)
- ✅ Async: Toutes méthodes retournent `Promise<T>`

---
