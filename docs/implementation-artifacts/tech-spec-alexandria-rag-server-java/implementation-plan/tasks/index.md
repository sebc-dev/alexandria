# Tasks (TDD)

> **Méthodologie TDD Strict** : Chaque phase suit le cycle Red-Green-Refactor. Voir [TDD Workflow](../../context-for-development/tdd-workflow.md) pour les détails.

## Table of Contents

| Phase | Nom | Type | Tasks |
|-------|-----|------|-------|
| 1 | [Project Foundation](./phase-1-project-foundation.md) | Scaffold | 1-3b |
| 2A | [Local Dev Tools](./phase-2a-local-dev-tools.md) | DevOps | 1-6 |
| 2B | [Quality Plugins](./phase-2b-quality-plugins.md) | DevOps | 1-13 |
| 2C | [CI/CD Pipeline](./phase-2c-cicd-pipeline.md) | DevOps | 1-5 |
| 3 | [Test Infrastructure](./phase-3-test-infrastructure.md) | Prérequis TDD | 4-7 |
| 4 | [Exception Hierarchy](./phase-4-exception-hierarchy.md) | TDD | 8-10 |
| 5 | [Core Entities](./phase-5-core-entities.md) | TDD | 11-13 |
| 6 | [Configuration Classes](./phase-6-configuration-classes.md) | TDD | 14-20 |
| 7 | [Markdown Processing](./phase-7-markdown-processing.md) | TDD | 21-22 |
| 8 | [HTTP Clients](./phase-8-http-clients.md) | TDD | 23 |
| 9 | [Core Services](./phase-9-core-services.md) | TDD | 24-26 |
| 10 | [MCP Adapters](./phase-10-mcp-adapters.md) | TDD | 27-28 |
| 11 | [Infrastructure](./phase-11-infrastructure.md) | TDD | 29-32 |
| 12 | [CLI](./phase-12-cli.md) | TDD | 33 |
| 13 | [Database Schema](./phase-13-database-schema.md) | Integration | 34-35 |
| 14 | [E2E Validation](./phase-14-e2e-validation.md) | Validation | 36-38 |
| 15 | [Docker & Deployment](./phase-15-docker-deployment.md) | Infra | 39-41 |

## Ordre d'exécution

```
Phase 1 (Scaffold) ──► Phase 2A (Dev Tools) ──► Phase 2B (Quality Plugins) ──► Phase 2C (CI/CD) ──► Phase 3 (Test Infra) ──► Phases 4-12 (TDD) ──► Phase 13 (DB) ──► Phase 14 (E2E) ──► Phase 15 (Docker)
```

## Résumé des Tasks

- **Total Tasks**: 65 (41 + 24 DevOps)
- **Tasks DevOps (Phase 2A/2B/2C)**: 24 (6 + 13 + 5)
- **Tasks TDD (Red-Green-Refactor)**: 26 (Tasks 8-33)
- **Tasks Infrastructure/Validation**: 15 (Tasks 1-7, 34-41)
