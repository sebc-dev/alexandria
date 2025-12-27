# 5. Test Environment Requirements

## 5.1 Local Development

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Unit Tests** | Bun test runner | Domain logic + use-cases avec mocks |
| **Integration Tests** | PostgreSQL 17.7 + pgvector 0.8.1 | Docker container (testcontainers pattern) |
| **E2E Tests (limited)** | Claude Code CLI | MCP stdio protocol validation |
| **Performance Tests** | k6 | Load testing Layer 1+2+3 pipeline |

## 5.2 CI Pipeline (GitHub Actions)

```yaml
# Excerpt from Epic 2 CI/CD requirements
jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: oven-sh/setup-bun@v1
      - run: bun test:unit

  integration-tests:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: pgvector/pgvector:pg17
        env:
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
    env:
      OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
    steps:
      - run: bun test:integration

  architecture-compliance:
    runs-on: ubuntu-latest
    steps:
      - run: bun test:architecture  # ts-arch build-breaking tests
```

## 5.3 Required Secrets

- `OPENAI_API_KEY` (GitHub Actions secret) → Integration tests embeddings
- `ALEXANDRIA_DB_URL` (test environment) → PostgreSQL test container

---
