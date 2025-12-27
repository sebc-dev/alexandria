# Enforcement Guidelines

## **All AI Agents MUST:**

1. **Respecter l'architecture hexagonale strictement**:
   - Domain ne dépend JAMAIS d'Adapters
   - Domain ne dépend JAMAIS de Zod, Drizzle, Hono
   - Adapters implémentent Ports
   - Use-cases injectent Ports (pas implémentations concrètes)

2. **Utiliser les conventions de naming**:
   - Database: camelCase
   - Files: PascalCase
   - Variables/Functions: camelCase
   - Constants: UPPER_SNAKE_CASE
   - Ports: Suffix `Port`
   - MCP tools: snake_case
   - JSON fields: camelCase

3. **Valider uniquement aux boundaries**:
   - Zod dans Adapters Primaires (MCP inputs)
   - Zod dans Adapters Secondaires (API responses)
   - Zod dans Config (.env validation)
   - PAS de Zod dans Domain

4. **Logger systématiquement**:
   - Champs obligatoires: `projectId`, `layer`, `operation`
   - Layers valides: `layer1`, `layer2`, `mcp`, `adapter`, `domain`
   - PAS de `layer3` (externe à Alexandria)

5. **Garantir l'immutabilité**:
   - `readonly` properties partout
   - Private constructors + static factories
   - Pas de setters

## **Pattern Enforcement Mechanisms**

**1. Architecture Tests (Dependency Cruiser):**
```typescript
// tests/architecture/hexagonal.arch.test.ts
import { filesOfProject } from 'Dependency Cruiser/dist/core/project-loader'
import { expect, describe, it } from 'bun:test'

describe('Hexagonal Architecture Rules', () => {
  const project = filesOfProject()

  it('Domain should not depend on Adapters', () => {
    expect(
      project.inFolder('domain').shouldNot().dependOnFiles().inFolder('adapters')
    ).toBeTruthy()
  })

  it('Domain should not import Zod', () => {
    expect(
      project.inFolder('domain').shouldNot().dependOnFiles().matchingPattern('.*zod.*')
    ).toBeTruthy()
  })

  it('Domain should not import Drizzle', () => {
    expect(
      project.inFolder('domain').shouldNot().dependOnFiles().matchingPattern('.*drizzle.*')
    ).toBeTruthy()
  })

  it('Adapters should implement Ports', () => {
    expect(
      project.inFolder('adapters').should().dependOnFiles().inFolder('ports')
    ).toBeTruthy()
  })

  it('All port files should end with Port.ts', () => {
    expect(
      project.inFolder('ports').should().matchPattern('.*Port\\.ts$')
    ).toBeTruthy()
  })

  it('Test files should end with .test.ts', () => {
    expect(
      project.inFolder('tests').should().matchPattern('.*\\.test\\.ts$')
    ).toBeTruthy()
  })
})
```

**2. ESLint Configuration:**
```json
// eslint.config.json
{
  "extends": [
    "eslint:recommended",
    "plugin:@typescript-eslint/recommended"
  ],
  "parser": "@typescript-eslint/parser",
  "plugins": ["@typescript-eslint"],
  "rules": {
    "@typescript-eslint/naming-convention": [
      "error",
      {
        "selector": "interface",
        "format": ["PascalCase"],
        "custom": {
          "regex": "^I[A-Z]",
          "match": false
        }
      },
      {
        "selector": "class",
        "format": ["PascalCase"]
      },
      {
        "selector": "variable",
        "modifiers": ["const"],
        "format": ["camelCase", "UPPER_CASE"]
      },
      {
        "selector": "function",
        "format": ["camelCase"]
      }
    ],
    "@typescript-eslint/no-explicit-any": "error",
    "@typescript-eslint/explicit-function-return-type": "warn",
    "@typescript-eslint/no-unused-vars": "error",
    "no-console": "warn"
  }
}
```

**3. Pre-commit Hooks:**
```json
// package.json
{
  "scripts": {
    "lint": "eslint src/**/*.ts",
    "lint:fix": "eslint src/**/*.ts --fix",
    "typecheck": "tsc --noEmit",
    "test:unit": "bun test tests/unit/**/*.test.ts",
    "test:arch": "bun test tests/architecture/**/*.test.ts",
    "pre-commit": "bun run lint && bun run typecheck && bun run test:arch",
    "format": "prettier --write src/**/*.ts"
  }
}
```

**4. Git Hooks (avec Husky ou simple bash):**
```bash
# .git/hooks/pre-commit (chmod +x)
#!/bin/bash

echo "Running pre-commit checks..."

bun run lint
if [ $? -ne 0 ]; then
  echo "❌ Linting failed. Fix errors before committing."
  exit 1
fi

bun run typecheck
if [ $? -ne 0 ]; then
  echo "❌ Type checking failed. Fix errors before committing."
  exit 1
fi

bun run test:arch
if [ $? -ne 0 ]; then
  echo "❌ Architecture tests failed. Code violates hexagonal rules."
  exit 1
fi

echo "✅ All checks passed!"
exit 0
```

---
