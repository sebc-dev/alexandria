# Structure Patterns

## **Import Path Organization**

**Absolute Imports avec Path Alias `@/`:**
```typescript
// tsconfig.json
{
  "compilerOptions": {
    "baseUrl": ".",
    "paths": {
      "@/*": ["src/*"]
    }
  }
}

// ✅ CORRECT: Absolute imports
import { Convention } from '@/domain/entities/Convention'
import { ConventionRepositoryPort } from '@/ports/secondary/ConventionRepositoryPort'
import { LoggerPort } from '@/ports/secondary/LoggerPort'
import { DEFAULT_TOP_K } from '@/config/constants'

// ❌ INCORRECT: Relative imports
import { Convention } from '../../../../domain/entities/Convention'  // ❌ Fragile
```

**PAS de Barrel Exports:**
```typescript
// ❌ INCORRECT: index.ts barrel exports
// src/domain/entities/index.ts
export { Convention } from './Convention'
export { Documentation } from './Documentation'

// Import ailleurs
import { Convention, Documentation } from '@/domain/entities'  // ❌ Éviter

// ✅ CORRECT: Imports explicites
import { Convention } from '@/domain/entities/Convention'
import { Documentation } from '@/domain/entities/Documentation'
```

**Rationale:**
- Bun performance (barrel exports ralentissent bundling)
- Tree-shaking optimal
- Clarté (voir exactement quel fichier importé)

---

## **Test File Organization**

**Test File Naming - `.test.ts` suffix:**
```
tests/
├── unit/
│   ├── domain/
│   │   ├── entities/
│   │   │   └── Convention.test.ts           # ✅ .test.ts
│   │   └── use-cases/
│   │       ├── layer1/
│   │       │   └── SearchConventions.test.ts
│   │       └── layer2/
│   │           └── LinkDocumentation.test.ts
│   └── adapters/
│       └── DrizzleConventionRepository.test.ts
├── integration/
│   ├── database/
│   │   └── VectorSearch.test.ts
│   └── mcp-server/
│       └── RetrieveRawContext.test.ts
└── architecture/
    └── hexagonal.arch.test.ts              # Architecture tests
```

**Test Naming Convention:**
```typescript
// ✅ CORRECT
import { describe, it, expect } from 'bun:test'
import { Convention } from '@/domain/entities/Convention'

describe('Convention', () => {              // PascalCase (class name)

  describe('create', () => {                // camelCase (method name)

    it('should create valid convention with all required fields', () => {
      // Arrange
      const data = { id: '123', projectId: 'proj-1', contentText: 'test' }

      // Act
      const convention = Convention.create(data)

      // Assert
      expect(convention.id).toBe('123')
      expect(convention.projectId).toBe('proj-1')
    })

    it('should throw InvalidConventionError when contentText is empty', () => {
      expect(() => {
        Convention.create({ id: '123', projectId: 'proj-1', contentText: '' })
      }).toThrow(InvalidConventionError)
    })
  })
})

// ❌ INCORRECT
describe('convention', () => {              // ❌ camelCase
  it('creates a convention', () => { })     // ❌ Pas de "should"
}
```

---
