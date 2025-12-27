# Code Quality Enforcement - Stratégie 3-Tiers

## Table des matières

- [Vue d'ensemble](#vue-densemble)
- [Tier 1: Dependency Cruiser - Hard Enforcement](#tier-1-dependency-cruiser---hard-enforcement-build-breaking-cicd)
- [Tier 2: ESLint Plugin Boundaries - Real-Time IDE Feedback](#tier-2-eslint--eslint-plugin-boundaries---real-time-ide-feedback)
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

**Philosophie:** Approche préventive multi-couches combinant enforcement déterministe (Dependency Cruiser en CI/CD, ESLint Plugin Boundaries en local) et intelligence contextuelle (CodeRabbit AI).

**Objectif:** Garantir que chaque commit respecte l'architecture hexagonale, les conventions TypeScript strictes, et maintient une qualité de code élevée, tout en **facilitant les reviews** avec un objectif de ≤2 commentaires par commit.

---

## Tier 1: Dependency Cruiser - Hard Enforcement (Build-Breaking CI/CD)

**Rôle:** Validation déterministe des règles architecturales **non-négociables** en CI/CD.

**Exécution:** CI pipeline (GitHub Actions) - échec du build si violations.

**Configuration .dependency-cruiser.js - Règles critiques Alexandria:**

```javascript
/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    {
      name: 'no-circular',
      severity: 'error',
      comment: 'Dépendances circulaires détectées',
      from: {},
      to: { circular: true }
    },
    {
      name: 'no-domain-to-adapters',
      severity: 'error',
      comment: 'VIOLATION ARCHITECTURE: Domain ne doit JAMAIS accéder Adapters',
      from: { path: '^src/domain' },
      to: { path: '^src/adapters' }
    },
    {
      name: 'no-domain-to-ports',
      severity: 'error',
      comment: 'Domain ne doit pas dépendre de Ports (inversion de dépendance)',
      from: { path: '^src/domain' },
      to: { path: '^src/ports' }
    },
    {
      name: 'no-domain-external-libs',
      severity: 'error',
      comment: 'Domain ne peut pas importer Zod/Drizzle/Hono',
      from: { path: '^src/domain' },
      to: { path: '(zod|drizzle-orm|hono)' }
    },
    {
      name: 'no-ports-to-adapters',
      severity: 'error',
      comment: 'Ports ne doivent pas accéder Adapters',
      from: { path: '^src/ports' },
      to: { path: '^src/adapters' }
    },
    {
      name: 'not-to-test',
      severity: 'error',
      comment: 'Code prod ne doit pas importer tests',
      from: { pathNot: '^(test|spec)' },
      to: { path: '^(test|spec)' }
    }
  ],
  options: {
    doNotFollow: {
      path: 'node_modules'
    },
    tsPreCompilationDeps: true,
    tsConfig: {
      fileName: './tsconfig.json'
    },
    moduleSystems: ['es6', 'cjs', 'ts'],
    cache: true
  }
};
```

**Scripts package.json:**

```json
{
  "scripts": {
    "arch:check": "depcruise src --include-only '^src' --config .dependency-cruiser.js",
    "arch:graph": "depcruise src --include-only '^src' --output-type dot | dot -T svg > dependency-graph.svg",
    "arch:html": "depcruise src --include-only '^src' --output-type err-html --output-to dependency-report.html"
  }
}
```

**Bénéfice:** Garantie absolue que l'architecture hexagonale est respectée - le build échoue si violations. Génération de graphes de dépendances pour debugging.

---

## Tier 2: ESLint + ESLint Plugin Boundaries - Real-Time IDE Feedback

**Rôle:** Feedback immédiat dans l'IDE pendant l'écriture du code avec validation architecture hexagonale.

**Exécution:** Pre-commit hook + IDE integration + VS Code auto-fix.

**Installation:**

```bash
bun add -d eslint eslint-plugin-boundaries @typescript-eslint/parser @typescript-eslint/eslint-plugin
```

**Configuration eslint.config.js (Flat Config) pour Alexandria:**

```javascript
import boundaries from 'eslint-plugin-boundaries';
import typescriptEslint from '@typescript-eslint/eslint-plugin';
import parser from '@typescript-eslint/parser';

export default [
  {
    files: ['**/*.ts', '**/*.tsx'],
    languageOptions: {
      parser: parser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
        project: './tsconfig.json'
      }
    },
    plugins: {
      boundaries,
      '@typescript-eslint': typescriptEslint
    },
    settings: {
      'boundaries/elements': [
        { type: 'domain', pattern: 'src/domain/**/*', mode: 'full' },
        { type: 'ports', pattern: 'src/ports/**/*', mode: 'full' },
        { type: 'adapters-primary', pattern: 'src/adapters/primary/**/*', mode: 'full' },
        { type: 'adapters-secondary', pattern: 'src/adapters/secondary/**/*', mode: 'full' },
        { type: 'config', pattern: 'src/config/**/*', mode: 'full' },
        { type: 'shared', pattern: 'src/shared/**/*', mode: 'full' }
      ]
    },
    rules: {
      // Règles Boundaries - Architecture Hexagonale Alexandria
      'boundaries/element-types': ['error', {
        default: 'disallow',
        rules: [
          { from: ['domain'], disallow: ['*'] },  // Domain pure, aucune dépendance
          { from: ['ports'], allow: ['domain'] },
          { from: ['adapters-primary'], allow: ['ports', 'domain', 'shared'] },
          { from: ['adapters-secondary'], allow: ['ports', 'domain', 'shared'] },
          { from: ['config'], allow: ['*'] },
          { from: ['shared'], disallow: ['domain', 'ports', 'adapters-primary', 'adapters-secondary'] }
        ]
      }],
      'boundaries/no-private': ['error', { allowUncles: false }],
      'boundaries/external': ['error', {
        default: 'allow',
        rules: [
          { from: ['domain'], disallow: ['zod', 'drizzle-orm', 'hono', 'axios'] }
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
      'no-var': 'error'
    }
  }
];
```

**Configuration VS Code (.vscode/settings.json):**

```json
{
  "eslint.validate": ["javascript", "javascriptreact", "typescript", "typescriptreact"],
  "eslint.format.enable": true,
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": "explicit"
  }
}
```

**Scripts package.json:**

```json
{
  "scripts": {
    "lint": "eslint . --ext .ts,.tsx",
    "lint:fix": "eslint . --ext .ts,.tsx --fix",
    "lint:watch": "esw . --ext .ts,.tsx --watch --color"
  }
}
```

**Bénéfice:** Détection immédiate des violations pendant l'écriture (<1s dans IDE) - économise des cycles de review. Debug mode disponible: `ESLINT_PLUGIN_BOUNDARIES_DEBUG=1 bun run lint`

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

# Installer Dependency Cruiser pour validation architecture CI/CD
bun add -D dependency-cruiser

# Installer ESLint + ESLint Plugin Boundaries
bun add -D eslint eslint-plugin-boundaries @typescript-eslint/parser @typescript-eslint/eslint-plugin

# Configurer pre-commit hooks
bun add -D husky
bunx husky init
# Éditer .husky/pre-commit pour ajouter: bun run validate
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
# ESLint Plugin Boundaries fournit feedback temps réel dans IDE (<1s)

# Lancer tests + validation architecture locale
bun test
bun run lint

# Commit
git add .
git commit -m "feat: ajout feature X"
# Pre-commit hook lance: bun run validate (lint + arch:check locale)

# Push et créer PR
git push origin feature/mon-feature
# CodeRabbit review automatiquement la PR
```

### 4. Review Process

**Validation Multi-Tiers:**

1. **Pre-commit** (ESLint Boundaries + Dependency Cruiser): Violations détectées avant commit
2. **CI Pipeline** (Dependency Cruiser): Build échoue si violations architecture
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
- **100% enforcement architecture** via Dependency Cruiser (build-breaking CI/CD)
- **0 violations architecture** passent en production
- **Feedback <1s** via ESLint Plugin Boundaries dans IDE

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
1. ✅ Créer `.dependency-cruiser.js` avec règles architecture Alexandria
2. ✅ Créer `eslint.config.js` (flat config) avec ESLint Plugin Boundaries
3. ✅ Créer `.vscode/settings.json` pour ESLint auto-fix
4. ✅ Créer `.coderabbit.yaml` avec configuration ci-dessus
5. ✅ Créer `.coderabbit/rules/` avec AST-grep rules
6. ✅ Créer `CLAUDE.md` avec documentation architecture
7. ✅ Configurer `.github/workflows/architecture.yml` avec Dependency Cruiser
8. ✅ Configurer Husky pre-commit hook (`bun run validate`)
9. ✅ Installer CodeRabbit depuis GitHub Marketplace
10. ✅ Ouvrir test PR pour valider configuration

**Validation:**
- Créer PR test violant intentionnellement architecture hexagonale
- Vérifier que:
  - ESLint Plugin Boundaries détecte violations (IDE <1s + pre-commit)
  - Dependency Cruiser échoue le build (CI GitHub Actions)
  - CodeRabbit signale violations avec contexte

**Itération:**
- Monitorer nombre commentaires CodeRabbit par commit
- Ajuster `tone_instructions` si >2 commentaires/commit
- Ajouter AST-grep rules si patterns récurrents manqués
