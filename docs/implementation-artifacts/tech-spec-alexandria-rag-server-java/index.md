---
title: Alexandria RAG Server (Java)
slug: alexandria-rag-server-java
created: 2026-01-06
status: ready-for-dev
stepsCompleted: [1, 2, 3, 4]
methodology: TDD-strict
---

# Tech-Spec: Alexandria RAG Server (Java)

> **Méthodologie** : TDD Strict (Red-Green-Refactor) - voir [TDD Workflow](./context-for-development/tdd-workflow.md)

## Table of Contents

- [Tech-Spec: Alexandria RAG Server (Java)](#table-of-contents)
  - [Overview](./overview.md)
    - [Problem Statement](./overview.md#problem-statement)
    - [Solution](./overview.md#solution)
    - [Scope](./overview.md#scope)
  - [Context for Development](./context-for-development/index.md)
    - [TDD Workflow](./context-for-development/tdd-workflow.md) **(NEW)**
    - [Codebase Patterns](./context-for-development/codebase-patterns.md)
    - [Architecture Decisions](./context-for-development/architecture-decisions.md)
    - [Project Structure](./context-for-development/project-structure.md)
    - [pgvector Configuration (from research + #12)](./context-for-development/pgvector-configuration-from-research-12.md)
    - [Chunking Strategy (from research #15)](./context-for-development/chunking-strategy-from-research-15.md)
    - [Infinity API (validated)](./context-for-development/infinity-api-validated.md)
    - [Retry Pattern (Resilience4j 2.3.0)](./context-for-development/retry-pattern-resilience4j-230.md)
    - [Timeout Budget (from research #19)](./context-for-development/timeout-budget-from-research-19.md)
    - [RAG Pipeline Configuration](./context-for-development/rag-pipeline-configuration.md)
    - [No-Results Handling & Response Schema](./context-for-development/no-results-handling-response-schema.md)
    - [MCP Response Format (from research #6)](./context-for-development/mcp-response-format-from-research-6.md)
    - [Error Handling (from research #7)](./context-for-development/error-handling-from-research-7.md)
    - [Ingestion Strategy (from research #11)](./context-for-development/ingestion-strategy-from-research-11.md)
    - [Document Update Strategy (from research #12)](./context-for-development/document-update-strategy-from-research-12.md)
    - [Files to Reference](./context-for-development/files-to-reference.md)
    - [Hardware Cible (Self-hosted)](./context-for-development/hardware-cible-self-hosted.md)
  - [Implementation Plan (TDD)](./implementation-plan/index.md)
    - [Tasks](./implementation-plan/tasks/index.md)
      - [Phase 1: Project Foundation](./implementation-plan/tasks/phase-1-project-foundation.md) *(Scaffold)*
      - [Phase 2A: Local Dev Tools](./implementation-plan/tasks/phase-2a-local-dev-tools.md) *(DevOps)*
      - [Phase 2B: Quality Plugins](./implementation-plan/tasks/phase-2b-quality-plugins.md) *(DevOps)*
      - [Phase 2C: CI/CD Pipeline](./implementation-plan/tasks/phase-2c-cicd-pipeline.md) *(DevOps)*
      - [Phase 3: Test Infrastructure](./implementation-plan/tasks/phase-3-test-infrastructure.md) *(Prérequis TDD)*
      - [Phase 4: Exception Hierarchy](./implementation-plan/tasks/phase-4-exception-hierarchy.md) *(TDD)*
      - [Phase 5: Core Entities](./implementation-plan/tasks/phase-5-core-entities.md) *(TDD)*
      - [Phase 6: Configuration Classes](./implementation-plan/tasks/phase-6-configuration-classes.md) *(TDD)*
      - [Phase 7: Markdown Processing](./implementation-plan/tasks/phase-7-markdown-processing.md) *(TDD)*
      - [Phase 8: HTTP Clients](./implementation-plan/tasks/phase-8-http-clients.md) *(TDD)*
      - [Phase 9: Core Services](./implementation-plan/tasks/phase-9-core-services.md) *(TDD)*
      - [Phase 10: MCP Adapters](./implementation-plan/tasks/phase-10-mcp-adapters.md) *(TDD)*
      - [Phase 11: Infrastructure](./implementation-plan/tasks/phase-11-infrastructure.md) *(TDD)*
      - [Phase 12: CLI](./implementation-plan/tasks/phase-12-cli.md) *(TDD)*
      - [Phase 13: Database Schema](./implementation-plan/tasks/phase-13-database-schema.md) *(Integration)*
      - [Phase 14: E2E Validation](./implementation-plan/tasks/phase-14-e2e-validation.md) *(Validation)*
      - [Phase 15: Docker & Deployment](./implementation-plan/tasks/phase-15-docker-deployment.md) *(Infra)*
    - [Acceptance Criteria](./implementation-plan/acceptance-criteria.md)
      - [AC 1: MCP Server Startup](./implementation-plan/acceptance-criteria.md#ac-1-mcp-server-startup)
      - [AC 2: Document Ingestion (CLI)](./implementation-plan/acceptance-criteria.md#ac-2-document-ingestion-cli)
      - [AC 3: Document Ingestion (MCP)](./implementation-plan/acceptance-criteria.md#ac-3-document-ingestion-mcp)
      - [AC 4: Semantic Search](./implementation-plan/acceptance-criteria.md#ac-4-semantic-search)
      - [AC 5: Tiered Response](./implementation-plan/acceptance-criteria.md#ac-5-tiered-response)
      - [AC 6: Query Validation](./implementation-plan/acceptance-criteria.md#ac-6-query-validation)
      - [AC 7: Retry Resilience](./implementation-plan/acceptance-criteria.md#ac-7-retry-resilience)
      - [AC 8: Timeout Budget](./implementation-plan/acceptance-criteria.md#ac-8-timeout-budget)
      - [AC 9: Document Updates](./implementation-plan/acceptance-criteria.md#ac-9-document-updates)
      - [AC 10: Health Checks](./implementation-plan/acceptance-criteria.md#ac-10-health-checks)
      - [AC 11: Error Handling](./implementation-plan/acceptance-criteria.md#ac-11-error-handling)
      - [AC 12: Observability](./implementation-plan/acceptance-criteria.md#ac-12-observability)
  - [Additional Context](./additional-context/index.md)
    - [Dependencies](./additional-context/dependencies.md)
    - [Testing Strategy (from research #21-22)](./additional-context/testing-strategy-from-research-21-22.md)
    - [Logging & Observability (from research #22)](./additional-context/logging-observability-from-research-22.md)
    - [Notes](./additional-context/notes.md)
    - [Stack Validation (2026-01-04)](./additional-context/stack-validation-2026-01-04.md)
    - [Profiling Dev avec JFR (Optionnel)](./additional-context/profiling-dev-avec-jfr-optionnel.md)
