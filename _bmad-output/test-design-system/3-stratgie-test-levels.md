# 3. Stratégie Test Levels

## 3.1 Recommandation Split

**60% Unit / 30% Integration / 10% E2E**

**Justification:**

- **Backend API-heavy:** Pas de UI complexe → Moins de E2E, plus de Unit/Integration
- **Hexagonal Architecture:** Domain layer pur → Excellente testabilité unitaire (60%)
- **External Dependencies:** PostgreSQL + OpenAI API + Sub-agent → Integration tests critiques (30%)
- **MCP Protocol Validation:** stdio transport → E2E avec Claude Code runtime limité (10%)

## 3.2 Test Levels par Composant

| Composant | Unit % | Integration % | E2E % | Justification |
|-----------|--------|---------------|-------|---------------|
| **Domain Entities** (Convention, ProjectId, ConformityScore) | 100% | 0% | 0% | Pure logic, no dependencies |
| **Use-Cases** (SearchConventions, ValidateCode, UploadConvention) | 80% | 20% | 0% | Mock ports for unit, real DB for integration edge cases |
| **Ports Interfaces** (ConventionRepositoryPort, EmbeddingServicePort) | 0% | 100% | 0% | Contract tests with real adapters |
| **Drizzle Repositories** (PostgresConventionRepository) | 0% | 100% | 0% | Test against PostgreSQL test container |
| **OpenAI Embedding Adapter** | 30% | 70% | 0% | Unit: error handling, Integration: real API calls |
| **MCP Server (Hono)** | 50% | 40% | 10% | Unit: request validation, Integration: tools logic, E2E: stdio protocol |
| **Layer 1+2 RAG Pipeline** | 0% | 100% | 0% | Requires PostgreSQL + OpenAI API |
| **Layer 3 Orchestration (Skill Alexandria)** | 0% | 50% | 50% | Integration: mock sub-agent, E2E: real Haiku invocation |
| **Multi-Project Isolation Logic** | 0% | 100% | 0% | SQL query validation with multi-project fixtures |

## 3.3 Éviter Duplication de Coverage

**Anti-Patterns à Éviter:**

- ❌ Tester business logic au niveau E2E (slow, brittle)
- ❌ Tester framework behavior (Drizzle ORM internals) en unit tests
- ❌ Tester third-party libraries (pgvector) directement

**Coverage Overlap Acceptable Uniquement Pour:**

- Critical paths requérant defense-in-depth (multi-project isolation: unit value object + integration SQL queries)
- Regression prevention (bug déjà en production)

---
