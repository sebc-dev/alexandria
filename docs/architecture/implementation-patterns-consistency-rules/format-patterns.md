# Format Patterns

## **Error Handling Standards**

**Error Class Naming - Suffix `Error` obligatoire:**
```typescript
// ✅ CORRECT
// src/shared/errors/DomainError.ts
export abstract class DomainError extends Error {
  constructor(message: string, options?: ErrorOptions) {
    super(message, options)
    this.name = this.constructor.name
  }
}

// src/domain/errors/ConventionNotFoundError.ts
export class ConventionNotFoundError extends DomainError {
  constructor(conventionId: string) {
    super(`Convention not found: ${conventionId}`)
  }
}

// src/domain/errors/InvalidProjectIdError.ts
export class InvalidProjectIdError extends DomainError { }

// src/shared/errors/InfrastructureError.ts
export abstract class InfrastructureError extends Error { }

// src/adapters/secondary/database/errors/RepositoryError.ts
export class RepositoryError extends InfrastructureError { }

// ❌ INCORRECT
export class ConventionNotFound extends Error { }  // ❌ Pas de suffix "Error"
```

**MCP Error Response Format - JSON-RPC 2.0:**
```typescript
// ✅ CORRECT
{
  "code": -32603,                          // JSON-RPC standard code
  "message": "Internal error",             // User-friendly (English)
  "data": {
    "type": "RepositoryError",             // Error class name
    "details": "Failed to query conventions table"  // Technical (optional, DEBUG only)
  }
}

// ❌ INCORRECT
{
  "error": "Something went wrong",         // ❌ Pas de structure
  "stack": "Error at ..."                  // ❌ Jamais exposer stack trace
}
```

**Error Logging Pattern:**
```typescript
// ✅ CORRECT
async search(projectId: string, query: string): Promise<Convention[]> {
  try {
    const embedding = await this.embeddingGen.generate(query)
    return await this.conventionRepo.search(projectId, embedding)
  } catch (error) {
    // Log avec context
    this.logger.error('Convention search failed', error, {
      projectId,
      query,
      layer: 'layer1',
      operation: 'search'
    })

    // Wrap et rethrow
    throw new SearchError('Failed to search conventions', { cause: error })
  }
}

// ❌ INCORRECT
} catch (error) {
  console.log('Error:', error)             // ❌ Pas de logger.error()
  throw error                              // ❌ Pas de wrap (perd contexte)
}
```

---

## **Data Exchange Formats**

**Date/Time Format - ISO 8601:**
```typescript
// ✅ CORRECT
{
  "createdAt": "2025-12-26T10:30:45.123Z"  // ISO 8601 strict
}

// TypeScript
const createdAt = new Date().toISOString()

// ❌ INCORRECT
{
  "createdAt": "2025-12-26 10:30:45"       // ❌ Pas de timezone
  "createdAt": 1703587845123               // ❌ Timestamp Unix (ambiguïté)
}
```

**Boolean Representation:**
```typescript
// ✅ CORRECT
{
  "isActive": true,                        // JSON boolean
  "hasEmbedding": false
}

// ❌ INCORRECT
{
  "isActive": 1,                           // ❌ 0/1
  "hasEmbedding": "true"                   // ❌ String
}
```

---
