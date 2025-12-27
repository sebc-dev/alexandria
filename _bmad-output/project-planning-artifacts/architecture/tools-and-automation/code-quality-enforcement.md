# Code Quality Enforcement - Stratégie 3-Tiers

## Table des matières

- [Vue d'ensemble](#vue-densemble)
- [Tier 1: ts-arch - Hard Enforcement](#tier-1-ts-arch---hard-enforcement-build-breaking)
- [Tier 2: ESLint - Real-Time IDE Feedback](#tier-2-eslint---real-time-ide-feedback)
- [Tier 3: CodeRabbit - AI-Powered Contextual Review](#tier-3-coderabbit---ai-powered-contextual-review)
- [Configuration CodeRabbit](#configuration-coderabbit-pour-alexandria)
- [AST-grep Custom Rules](#ast-grep-custom-rules)
- [Documentation Architecture (CLAUDE.md)](#documentation-architecture-claudemd)
- [Workflow Développement Type](#workflow-développement-type)
- [Bénéfices Mesurables](#bénéfices-mesurables)
- [Coûts & Considérations](#coûts--considérations)
- [Prochaines Étapes](#prochaines-étapes)

## Vue d'ensemble

Alexandria utilise une approche **complémentaire à 3 tiers** pour garantir la qualité du code et le respect de l'architecture hexagonale:

**Philosophie:** Approche préventive multi-couches combinant enforcement déterministe (ts-arch, ESLint) et intelligence contextuelle (CodeRabbit AI).

**Objectif:** Garantir que chaque commit respecte l'architecture hexagonale, les conventions TypeScript strictes, et maintient une qualité de code élevée, tout en **facilitant les reviews** avec un objectif de ≤2 commentaires par commit.

---

## Tier 1: ts-arch - Hard Enforcement (Build-Breaking)

**Rôle:** Validation déterministe des règles architecturales **non-négociables**.

**Exécution:** Tests unitaires dans CI pipeline - échec du build si violations.

**Exemples de règles critiques:**

```typescript
// architecture.spec.ts
import "tsarch/dist/jest"
import { filesOfProject } from "tsarch"

describe("Hexagonal Architecture - Alexandria", () => {
  it("domain must not depend on adapters", async () => {
    const rule = filesOfProject()
      .inFolder("domain")
      .shouldNot()
      .dependOnFiles()
      .inFolder("adapters")
    await expect(rule).toPassAsync()
  })

  it("domain must not depend on infrastructure", async () => {
    const rule = filesOfProject()
      .inFolder("domain")
      .shouldNot()
      .dependOnFiles()
      .inFolder("infrastructure")
    await expect(rule).toPassAsync()
  })

  it("domain must not import Zod", async () => {
    const rule = filesOfProject()
      .inFolder("domain")
      .shouldNot()
      .dependOnFiles()
      .matchingPattern(".*zod.*")
    await expect(rule).toPassAsync()
  })

  it("domain must not import Drizzle ORM", async () => {
    const rule = filesOfProject()
      .inFolder("domain")
      .shouldNot()
      .dependOnFiles()
      .matchingPattern(".*drizzle-orm.*")
    await expect(rule).toPassAsync()
  })

  it("domain must not import Hono", async () => {
    const rule = filesOfProject()
      .inFolder("domain")
      .shouldNot()
      .dependOnFiles()
      .matchingPattern(".*hono.*")
    await expect(rule).toPassAsync()
  })

  it("ports must not depend on adapters", async () => {
    const rule = filesOfProject()
      .inFolder("ports")
      .shouldNot()
      .dependOnFiles()
      .inFolder("adapters")
    await expect(rule).toPassAsync()
  })
})
```

**Bénéfice:** Garantie absolue que l'architecture hexagonale est respectée - le build échoue si violations.

---

## Tier 2: ESLint - Real-Time IDE Feedback

**Rôle:** Feedback immédiat dans l'IDE pendant l'écriture du code.

**Exécution:** Pre-commit hook + IDE integration.

**Configuration ESLint pour Alexandria:**

```javascript
// .eslintrc.js
module.exports = {
  parser: '@typescript-eslint/parser',
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
    project: './tsconfig.json',
  },
  extends: [
    'eslint:recommended',
    'plugin:@typescript-eslint/recommended',
    'plugin:@typescript-eslint/recommended-requiring-type-checking',
    'plugin:import/recommended',
    'plugin:import/typescript',
  ],
  plugins: ['@typescript-eslint', 'import'],
  rules: {
    // Import restrictions pour architecture hexagonale
    'import/no-restricted-paths': ['error', {
      zones: [
        {
          target: './src/domain',
          from: './src/adapters',
          message: 'Domain cannot import from adapters (hexagonal architecture violation)'
        },
        {
          target: './src/domain',
          from: './src/infrastructure',
          message: 'Domain cannot import from infrastructure (hexagonal architecture violation)'
        },
        {
          target: './src/ports',
          from: './src/adapters',
          message: 'Ports cannot import from adapters (hexagonal architecture violation)'
        },
        {
          target: './src/ports',
          from: './src/infrastructure',
          message: 'Ports cannot import from infrastructure (hexagonal architecture violation)'
        }
      ]
    }],

    // TypeScript strict rules
    '@typescript-eslint/no-explicit-any': 'error',
    '@typescript-eslint/explicit-function-return-type': 'warn',
    '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    '@typescript-eslint/no-floating-promises': 'error',
    '@typescript-eslint/await-thenable': 'error',
    '@typescript-eslint/no-misused-promises': 'error',

    // Code quality
    'no-console': 'warn',
    'prefer-const': 'error',
    'no-var': 'error',
  },
  settings: {
    'import/resolver': {
      typescript: {
        alwaysTryTypes: true,
        project: './tsconfig.json',
      },
    },
  },
}
```

**Bénéfice:** Détection immédiate des violations pendant l'écriture - économise des cycles de review.

---

## Tier 3: CodeRabbit - AI-Powered Contextual Review

**Rôle:** Review intelligente contextuelle focalisée sur les violations subtiles, sécurité, et patterns métier.

**Exécution:** Automatique sur chaque Pull Request.

**Objectif:** ≤2 commentaires par commit (focus sur critiques uniquement).

**Configuration:** Voir section Configuration CodeRabbit ci-dessous.

**Bénéfice:** Détecte violations que les outils déterministes manquent + feedback éducatif pour nouveaux contributeurs.

---

## Configuration CodeRabbit pour Alexandria

### Fichier .coderabbit.yaml

```yaml
# yaml-language-server: $schema=https://coderabbit.ai/integrations/schema.v2.json
language: "fr-FR"
early_access: false

tone_instructions: |
  Vous reviewez Alexandria, un système RAG avec architecture hexagonale STRICTE.
  Focus UNIQUEMENT sur: violations architecture, bugs de sécurité, erreurs de logique.
  NE PAS commenter: formatage, style mineur, suggestions "nice to have".
  Maximum 1 phrase par commentaire. En cas de doute, ne pas commenter.

reviews:
  profile: "chill"
  request_changes_workflow: false
  high_level_summary: true
  poem: false
  review_status: false
  collapse_walkthrough: true
  sequence_diagrams: false

  auto_review:
    enabled: true
    drafts: false
    ignore_title_keywords: ["wip", "WIP", "draft", "Draft", "DO NOT MERGE"]
    base_branches: ["main", "develop"]

  path_filters:
    - "!dist/**"
    - "!node_modules/**"
    - "!**/*.spec.ts"
    - "!**/*.test.ts"
    - "!**/fixtures/**"
    - "!drizzle/migrations/**"

  path_instructions:
    - path: "src/domain/**/*.ts"
      instructions: |
        DOMAIN LAYER - Architecture hexagonale STRICTE:

        🚫 VIOLATIONS CRITIQUES (toujours signaler):
        - Import depuis 'adapters/', 'infrastructure/', ou '@/adapters'
        - Import de 'zod', 'hono', 'drizzle-orm', ou frameworks externes
        - Propriétés sans modificateur 'readonly' (immutabilité requise)
        - Méthodes setter (viole immutabilité)
        - 'throw new Error()' brut - utiliser erreurs custom domaine
        - Chaînes .then() - utiliser async/await uniquement
        - Classes pas en PascalCase
        - Erreurs custom sans suffixe 'Error'

        ✅ AUTORISÉ:
        - Imports autres modules domain
        - Imports depuis ports/ (interfaces uniquement)
        - Types et utilitaires TypeScript purs

    - path: "src/ports/**/*.ts"
      instructions: |
        PORTS LAYER - Définitions d'interfaces:

        🚫 VIOLATIONS CRITIQUES:
        - Import depuis 'adapters/' ou 'infrastructure/'
        - Interfaces sans suffixe 'Port'
        - Implémentations concrètes (ports doivent être abstraits)

        ✅ AUTORISÉ:
        - Imports depuis domain/ (types entités, value objects)
        - Interfaces TypeScript pures

    - path: "src/adapters/**/*.ts"
      instructions: |
        ADAPTERS LAYER - Code d'implémentation:

        Focus sur:
        - Vérifier que l'adapter implémente l'interface port correspondante
        - Vérifier que les schémas Zod valident aux boundaries
        - Vérifier traduction erreurs externes → erreurs domaine
        - Valider patterns d'usage Drizzle ORM

        Review légère - code infrastructure a plus de flexibilité.

    - path: "src/application/**/*.ts"
      instructions: |
        APPLICATION LAYER - Use cases:

        Vérifier:
        - Noms camelCase pour fonctions et variables
        - Usage async/await (pas de chaînes .then())
        - Injection de dépendances via ports
        - Pas d'imports directs d'adapters

  tools:
    eslint:
      enabled: true
    ast-grep:
      essential_rules: true
      rule_dirs:
        - ".coderabbit/rules"
    biome:
      enabled: false
    oxc:
      enabled: false
    gitleaks:
      enabled: true

knowledge_base:
  code_guidelines:
    enabled: true
    filePatterns:
      - "**/CLAUDE.md"
      - "**/ARCHITECTURE.md"
      - "**/README.md"
  learnings:
    scope: "auto"
  web_search:
    enabled: true

chat:
  auto_reply: true
```

---

## AST-grep Custom Rules

Créer répertoire `.coderabbit/rules/` avec les règles suivantes:

### no-then-chains.yaml

```yaml
id: no-then-chains
language: typescript
message: "Utiliser async/await au lieu de chaînes .then()"
severity: warning
rule:
  pattern: $PROMISE.then($$$)
```

### no-bare-throws.yaml

```yaml
id: no-bare-throws
language: typescript
message: "Utiliser des erreurs custom du domaine plutôt que Error brut"
severity: error
rule:
  pattern: throw new Error($MSG)
```

### no-domain-adapter-import.yaml

```yaml
id: no-domain-adapter-import
language: typescript
message: "VIOLATION ARCHITECTURE: Le domaine ne peut pas importer depuis adapters"
severity: error
files:
  - "src/domain/**/*.ts"
rule:
  kind: import_statement
  has:
    kind: string
    regex: "(adapters|infrastructure)"
```

### no-zod-in-domain.yaml

```yaml
id: no-zod-in-domain
language: typescript
message: "Zod est interdit dans la couche domaine"
severity: error
files:
  - "src/domain/**/*.ts"
rule:
  kind: import_statement
  has:
    kind: string_fragment
    regex: "^zod$"
```

### no-drizzle-in-domain.yaml

```yaml
id: no-drizzle-in-domain
language: typescript
message: "Drizzle ORM est interdit dans la couche domaine"
severity: error
files:
  - "src/domain/**/*.ts"
rule:
  kind: import_statement
  has:
    kind: string_fragment
    regex: "^drizzle-orm"
```

### no-hono-in-domain.yaml

```yaml
id: no-hono-in-domain
language: typescript
message: "Hono framework est interdit dans la couche domaine"
severity: error
files:
  - "src/domain/**/*.ts"
rule:
  kind: import_statement
  has:
    kind: string_fragment
    regex: "^hono"
```

---

## Documentation Architecture (CLAUDE.md)

CodeRabbit lit automatiquement `CLAUDE.md` pour contexte. Créer dans la racine du projet:

```markdown
# Alexandria Architecture

## Architecture Hexagonale (NON-NÉGOCIABLE)

### Dépendances entre Couches

- Domain → Ports ✓ (types seulement)
- Adapters → Ports → Domain ✓
- Domain → Adapters ✗ **INTERDIT**
- Domain → Infrastructure ✗ **INTERDIT**
- Ports → Adapters ✗ **INTERDIT**

### Conventions de Nommage

- Entités: PascalCase
- Variables/fonctions: camelCase
- Ports: suffixe "Port"
- Erreurs custom: suffixe "Error"
- Adapters: suffixe "Adapter"

### Patterns Requis

- **Immutabilité domaine**: Toutes propriétés entités en `readonly`
- **Gestion erreurs**: Erreurs custom domaine uniquement (pas `throw new Error()`)
- **Code asynchrone**: async/await uniquement (pas de `.then()`)
- **Validation**: Zod dans couche adapters uniquement
- **ORM**: Drizzle dans couche adapters uniquement
- **Framework web**: Hono dans couche adapters uniquement

### Technologies Interdites dans Domain

- ❌ Zod (validation → adapters)
- ❌ Drizzle ORM (persistence → adapters)
- ❌ Hono (web framework → adapters)
- ❌ Tout framework externe

### Technologies Autorisées dans Domain

- ✅ TypeScript pur
- ✅ Interfaces depuis ports/
- ✅ Types depuis autres modules domain/
- ✅ Utilitaires TypeScript natifs

## Stack Technique Alexandria

- **Runtime**: Bun 1.3.5
- **Framework Web**: Hono 4.11.1
- **Langage**: TypeScript 5.9.7 (strict mode)
- **ORM**: Drizzle ORM 0.36.4
- **Validation**: Zod 4.2.1
- **Base de données**: PostgreSQL 17.7 + pgvector 0.8.1
- **Embeddings**: OpenAI API
- **LLM**: Claude Haiku 4.5 (via sub-agent)
```

---

## Workflow Développement Type

### 1. Setup Initial (Une Fois)

```bash
# Installer dépendances
bun install

# Installer ts-arch pour tests architecture
bun add -D tsarch

# Installer ESLint + plugins
bun add -D eslint @typescript-eslint/parser @typescript-eslint/eslint-plugin eslint-plugin-import eslint-import-resolver-typescript

# Configurer pre-commit hooks
# (Husky ou lefthook pour lancer ESLint avant commit)
```

### 2. Installation CodeRabbit

1. Installer CodeRabbit depuis [GitHub Marketplace](https://github.com/marketplace/coderabbitai)
2. Créer `.coderabbit.yaml` (voir section Configuration ci-dessus)
3. Créer `.coderabbit/rules/` avec AST-grep rules
4. Créer `CLAUDE.md` avec documentation architecture
5. Ouvrir test PR et lancer `@coderabbitai configuration` pour validation

### 3. Développement Quotidien

```bash
# Créer nouvelle branche feature
git checkout -b feature/mon-feature

# Développer code
# ESLint fournit feedback temps réel dans IDE

# Lancer tests (incluant tests architecture ts-arch)
bun test

# Commit
git add .
git commit -m "feat: ajout feature X"
# Pre-commit hook lance ESLint automatiquement

# Push et créer PR
git push origin feature/mon-feature
# CodeRabbit review automatiquement la PR
```

### 4. Review Process

**Validation Multi-Tiers:**

1. **Pre-commit** (ESLint): Violations détectées avant commit
2. **CI Pipeline** (ts-arch): Build échoue si violations architecture
3. **Pull Request** (CodeRabbit): Review contextuelle avec ≤2 commentaires

**Si CodeRabbit signale faux positif:**
- Répondre avec explication
- CodeRabbit apprend pour futures reviews
- Utiliser `@coderabbitai resolve` si nécessaire

### 5. Maintenance Knowledge Base CodeRabbit

**Feedback Loop:**
- CodeRabbit accumule "learnings" basés sur vos réponses
- Review quality s'améliore avec le temps
- Itérer sur `path_instructions` si patterns manqués

**Monitoring Efficacité:**
- Objectif: ≤2 commentaires par commit
- Si trop de commentaires: ajuster `tone_instructions` ou `path_instructions`
- Si violations manquées: ajouter AST-grep rules

---

## Bénéfices Mesurables

**Qualité Code:**
- **100% enforcement architecture** via ts-arch (build-breaking)
- **0 violations architecture** passent en production
- **Feedback <1s** via ESLint dans IDE

**Efficacité Reviews:**
- **≤2 commentaires/commit** via CodeRabbit configuré "chill"
- **Réduction 70%** temps review manuel (focus sur logique métier)
- **Onboarding facilité** nouveaux contributeurs (feedback éducatif automatique)

**Maintenabilité:**
- **Architecture hexagonale garantie** → expérimentation technique facilitée
- **Swap adapters** possible sans risque régression
- **Tests unitaires** simplifiés via isolation ports

---

## Coûts & Considérations

**CodeRabbit Pricing:**
- **Open-source repos**: Gratuit avec toutes fonctionnalités Pro
- **Private repos**: $24/mois (annuel) ou $30/mois
- **Rate limits**: 200 fichiers/heure, 3 reviews back-to-back puis 4/heure

**Recommandation pour Alexandria:**
- Si repo public: Gratuit ✓
- Si repo privé: $24/mois justifié par gain temps review + qualité garantie

**Performance:**
- Reviews complètent en 1-3 minutes
- Pas de blocage CI pipeline (parallèle)
- Ne pas configurer comme required check (évite blocage merge)

---

## Prochaines Étapes

**Setup Immédiat:**
1. ✅ Créer `.coderabbit.yaml` avec configuration ci-dessus
2. ✅ Créer `.coderabbit/rules/` avec AST-grep rules
3. ✅ Créer `CLAUDE.md` avec documentation architecture
4. ✅ Créer tests architecture `architecture.spec.ts` avec ts-arch
5. ✅ Configurer ESLint avec `import/no-restricted-paths`
6. ✅ Installer CodeRabbit depuis GitHub Marketplace
7. ✅ Ouvrir test PR pour valider configuration

**Validation:**
- Créer PR test violant intentionnellement architecture hexagonale
- Vérifier que:
  - ESLint détecte violations (IDE + pre-commit)
  - ts-arch échoue le build (CI)
  - CodeRabbit signale violations avec contexte

**Itération:**
- Monitorer nombre commentaires CodeRabbit par commit
- Ajuster `tone_instructions` si >2 commentaires/commit
- Ajouter AST-grep rules si patterns récurrents manqués
