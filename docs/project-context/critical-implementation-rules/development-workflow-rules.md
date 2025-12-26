# Development Workflow Rules

## 1. Git Workflow (Trunk-Based Development)

```bash
# ✅ CORRECT - Branches courtes, merge fréquent
git checkout -b feature/add-validation-tool
# Work, commit, push
git push -u origin feature/add-validation-tool
# Create PR, review, merge to main

# ✅ CORRECT - Branch naming conventions
feature/add-layer3-reformulation
fix/hnsw-index-not-used
refactor/extract-embedding-service
test/add-contract-tests-repositories
docs/update-architecture-diagram
perf/optimize-vector-search

# ❌ INCORRECT - Long-lived feature branches
git checkout -b develop  # ❌ Pas de develop branch
git checkout -b feature/big-refactor  # ❌ Branch vivant 2+ semaines
```

**Pourquoi** : Trunk-based = intégration continue, moins de merge conflicts. Branches courtes (<3 jours) facilitent reviews.

## 2. Commit Message Format (Gitmoji + Conventional Commits)

```bash
# Format obligatoire
<gitmoji> <type>(<scope>): <description>

<body optionnel>

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
```

**Gitmoji Reference pour Alexandria:**

| Gitmoji | Code | Type | Usage |
|---------|------|------|-------|
| ✨ | `:sparkles:` | `feat` | Nouvelle feature/use case |
| 🐛 | `:bug:` | `fix` | Bug fix |
| ♻️ | `:recycle:` | `refactor` | Refactoring sans changement fonctionnel |
| ✅ | `:white_check_mark:` | `test` | Ajout/modification tests |
| 📝 | `:memo:` | `docs` | Documentation |
| ⚡ | `:zap:` | `perf` | Amélioration performance |
| 🔧 | `:wrench:` | `chore` | Configuration, build, dependencies |
| 🏗️ | `:building_construction:` | `arch` | Changements architecturaux |
| 🔒 | `:lock:` | `security` | Sécurité |
| 🚀 | `:rocket:` | `deploy` | Déploiement |

**Exemples:**

```bash
# Feature
✨ feat(layer1): add vector search with HNSW index

Implements Layer 1 of Active Compliance Filter using pgvector.
- Add DrizzleConventionAdapter.search() method
- Configure HNSW index with m=16, efConstruction=128
- Meet NFR2 performance target (p95 < 1s)

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>

# Bug fix
🐛 fix(mcp): use stderr for logging instead of stdout

MCP protocol requires stdout reserved for JSON-RPC messages.
All console.log() calls moved to console.error().

Fixes #42

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>

# Refactor
♻️ refactor(domain): extract Convention entity to separate file

Improves maintainability by separating Convention from other entities.
No functional changes.

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>

# Performance
⚡ perf(layer1): optimize vector search with parallel embeddings

Use Promise.all for independent embedding generations.
Reduces p50 latency from 4.2s to 2.8s (NFR1 compliant).

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
```

**Pourquoi** : Gitmoji rend l'historique git visuellement scannable. Conventional Commits permet génération CHANGELOG automatique. NFR24 exige commit messages descriptifs.

## 3. PR Requirements (Quality Gates)

Chaque Pull Request DOIT:

**Automated Checks (CI/CD):**
- [ ] ✅ Biome lint pass (zero warnings)
- [ ] ✅ TypeScript compilation pass (strict mode)
- [ ] ✅ Unit tests pass (Domain + Application)
- [ ] ✅ Integration tests pass (Drizzle + OpenAI mocks)
- [ ] ✅ MCP protocol compliance tests pass
- [ ] ✅ Coverage thresholds maintenus (lines≥80%, functions≥85%)
- [ ] ✅ Performance tests pass (si modification Layer 1-3)

**Manual Reviews:**
- [ ] ✅ CodeRabbit AI review (NFR24)
- [ ] ✅ Au moins 1 human review approve
- [ ] ✅ Architecture hexagonale respectée
- [ ] ✅ Pas de secrets committés (.env, API keys)

**Merge Strategy:**
```bash
# ✅ CORRECT - Squash merge sur main
# Via GitHub UI: "Squash and merge"
# Résultat: 1 commit propre sur main avec tous les gitmojis préservés

# ❌ INCORRECT - Merge commit ou rebase
# Pollue l'historique main avec commits intermédiaires
```

**Pourquoi** : Quality gates garantissent qualité code avant merge. Squash = historique main propre et linéaire.

## 4. CI/CD Pipeline

```yaml
# .github/workflows/ci.yml
name: CI Pipeline

on: [push, pull_request]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun run biome check

  typecheck:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun run typecheck

  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun test tests/unit tests/domain --coverage
      - uses: codecov/codecov-action@v4
        with:
          files: ./coverage/lcov.info

  integration-tests:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: pgvector/pgvector:pg17
        env:
          POSTGRES_USER: test
          POSTGRES_PASSWORD: test
          POSTGRES_DB: alexandria_test
        ports:
          - 5432:5432
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun test tests/integration
        env:
          DATABASE_URL: postgres://test:test@localhost:5432/alexandria_test

  mcp-compliance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun test tests/mcp

  performance:
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun test tests/performance
```

**Pipeline Flow:**
```
Push/PR → Lint → TypeCheck → Unit Tests (parallel) → Integration Tests → MCP Compliance → Performance (main only) → Deploy (main only)
```

**Pourquoi** : NFR23 exige tests automatisés. Pipeline parallèle = feedback rapide (<5 min). Performance tests sur main uniquement = économie temps CI.

## 5. Environment Management

```bash
# .env.example (commité dans repo)
# Database
DATABASE_URL=postgres://user:password@localhost:5432/alexandria

# OpenAI
OPENAI_API_KEY=sk-...

# Application
DEBUG=false
PORT=3000
LOG_LEVEL=info

# MCP Server
MCP_SERVER_PORT=3001
```

```typescript
// src/config/env.ts - Validation Zod fail fast
import { z } from 'zod';

const envSchema = z.object({
  DATABASE_URL: z.string().url(),
  OPENAI_API_KEY: z.string().min(1),
  DEBUG: z.stringbool().default(false),
  PORT: z.coerce.number().default(3000),
  LOG_LEVEL: z.enum(['debug', 'info', 'warn', 'error']).default('info'),
  MCP_SERVER_PORT: z.coerce.number().default(3001),
});

const result = envSchema.safeParse(process.env);

if (!result.success) {
  console.error('❌ Invalid environment variables:');
  console.error(result.error.flatten().fieldErrors);
  process.exit(1);
}

export const env = result.data;
```

**Security Rules:**
- [ ] ❌ JAMAIS committer `.env` (gitignore strict)
- [ ] ✅ Toujours fournir `.env.example` à jour
- [ ] ✅ Secrets via GitHub Actions secrets en CI/CD
- [ ] ✅ Validation Zod fail fast au startup (NFR7)

**Pourquoi** : NFR7 exige fail fast si credentials manquantes. `.env.example` documente variables requises sans exposer secrets.

## 6. Code Review Workflow

```bash
# 1. Créer feature branch
git checkout -b feature/add-upload-tool

# 2. Développer avec commits atomiques
git add src/adapters/primary/mcp-server/tools/upload.ts
git commit -m "✨ feat(mcp): add upload tool for conventions

Implements MCP tool for uploading new conventions with embeddings.
- Validates input with Zod schema
- Generates embeddings via OpenAI adapter
- Persists to PostgreSQL in transaction (NFR18)

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

# 3. Push et créer PR
git push -u origin feature/add-upload-tool
gh pr create --title "✨ Add upload tool for conventions" --body "..."

# 4. Attendre reviews (CodeRabbit + human)
# 5. Merge via "Squash and merge" sur GitHub UI
```

**Review Checklist (Reviewer):**
- [ ] Code respecte architecture hexagonale
- [ ] Tests ajoutés et passent
- [ ] JSDoc présent sur fonctions publiques
- [ ] Pas de console.log (uniquement console.error pour logs)
- [ ] MCP protocol respecté (si modification tools)
- [ ] Performance NFRs respectées (si Layer 1-3)
- [ ] Commit message suit Gitmoji + Conventional Commits

**Pourquoi** : NFR24 exige code reviews (CodeRabbit + human). Checklist garantit standards qualité maintenus.

## 7. Deployment Workflow

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: oven-sh/setup-bun@v2
      - run: bun install --frozen-lockfile
      - run: bun run build

      # Deploy to production (Docker, VPS, etc.)
      - name: Build Docker image
        run: docker build -t alexandria:latest .

      - name: Push to registry
        run: docker push alexandria:latest

      - name: Deploy to server
        run: |
          # SSH deployment commands
          ssh user@server 'docker pull alexandria:latest && docker-compose up -d'
```

**Deployment Environments:**
- `main` → Production (auto-deploy après tests)
- Tags `v*.*.*` → Release versionnée

**Pourquoi** : Auto-deployment sur main après tests = continuous delivery. Tags permettent rollback si nécessaire.

## 8. Hotfix Workflow

```bash
# Hotfix critique en production
git checkout main
git pull
git checkout -b fix/critical-mcp-stdout-bug

# Fix rapide
# ... modifications ...

git add .
git commit -m "🐛 fix(mcp): use stderr for all logging

CRITICAL: MCP protocol was broken by console.log() calls.
All logging now uses console.error() per protocol requirement.

Fixes #123

🤖 Generated with Claude Code
Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"

# Fast-track PR (skip optional checks si critique)
git push -u origin fix/critical-mcp-stdout-bug
gh pr create --title "🐛 HOTFIX: Fix MCP stdout protocol violation" --label "priority:critical"

# Merge immédiatement après review rapide
# Deploy automatique sur main
```

**Pourquoi** : Hotfixes critiques nécessitent fast-track. Gitmoji 🐛 + label `priority:critical` signale urgence.
