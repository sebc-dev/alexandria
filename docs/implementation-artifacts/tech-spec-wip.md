---
title: 'Alexandria RAG MCP Server'
slug: 'alexandria-rag-mcp'
created: '2026-01-03'
status: 'in-progress'
stepsCompleted: [1, 2]
tech_stack:
  - Bun
  - TypeScript
  - PostgreSQL 18
  - pgvector 0.8.1
  - Drizzle ORM
  - postgres.js
  - Zod (v3 API)
  - Pino
  - RunPod Serverless
  - Infinity Server
  - Qwen3-Embedding-0.6B
  - bge-reranker-v2-m3
  - MCP SDK ^1.22.0
files_to_modify: []
files_to_create:
  - src/domain/document.ts
  - src/domain/search.ts
  - src/domain/errors.ts
  - src/application/ports.ts
  - src/application/ingest.ts
  - src/application/search.ts
  - src/infrastructure/db/schema.ts
  - src/infrastructure/db/index.ts
  - src/infrastructure/embeddings/infinity-client.ts
  - src/infrastructure/repositories/document-repo.ts
  - src/mcp/server.ts
  - src/mcp/tools/search-tool.ts
  - src/mcp/tools/ingest-tool.ts
  - src/mcp/tools/delete-tool.ts
  - src/mcp/tools/list-tool.ts
  - src/mcp/tools/health-tool.ts
  - src/cli/ingest.ts
  - runpod/embedder/handler.py
  - runpod/embedder/Dockerfile
  - runpod/reranker/handler.py
  - runpod/reranker/Dockerfile
code_patterns:
  - Hexagonal Architecture (Domain/Application/Infrastructure)
  - Ports & Adapters
  - OpenAI-compatible API client
test_patterns:
  - Bun test runner
  - Testcontainers for DB integration tests
---

# Tech-Spec: Alexandria RAG MCP Server

**Created:** 2026-01-03

## Overview

### Problem Statement

Claude Code n'a pas accès à la documentation technique et aux conventions de code spécifiques à un projet. Le contexte doit être copié/collé manuellement ou rechargé à chaque session, ce qui crée de la friction et des incohérences dans les réponses.

### Solution

Un serveur MCP exposant une base de connaissances interrogeable par recherche sémantique two-stage (embed + rerank). Les documents sont vectorisés via Qwen3-Embedding-0.6B, rerankés avec bge-reranker-v2-m3 sur RunPod Serverless (via Infinity server), et stockés dans PostgreSQL+pgvector avec `halfvec(1024)`. Le serveur MCP est self-hosted.

### Scope

**In Scope:**

- Serveur MCP (TypeScript + Bun)
- Déploiement RunPod Serverless (Infinity + Qwen3-Embedding-0.6B + bge-reranker-v2-m3)
- Ingestion manuelle de documents (Markdown, llms.txt, llms-full.txt)
- Stockage PostgreSQL 18 + pgvector 0.8.1 avec halfvec(1024)
- Métadonnées: source, date, tags, version
- Recherche sémantique two-stage via MCP tools (search, ingest, delete, list)
- Configuration pour self-hosted (domaine existant)
- Architecture Hexagonale

**Out of Scope:**

- Ingestion automatique (file watchers)
- PDF ou autres formats binaires
- UI/dashboard de gestion
- Multi-tenancy
- Authentification/permissions

## Context for Development

### Codebase Patterns

**Confirmed Clean Slate** - Projet greenfield, aucun code existant.

**Architecture:** Hexagonale (Ports & Adapters)

- `src/domain/` - Entités métier pures, aucune dépendance externe
- `src/application/` - Use cases, ports (interfaces)
- `src/infrastructure/` - Adapters (DB, embeddings, repositories)
- `src/mcp/` - Transport layer MCP

**Conventions:**

- TypeScript strict mode
- Bun runtime & test runner
- ESM modules
- Drizzle ORM pour les migrations et queries
- Structured logging avec Pino
- Zod v3 API obligatoire: `import { z } from "zod/v3"`

### Files to Reference

| File | Purpose |
| ---- | ------- |
| docs/research/new/qwen.md | Specs Qwen3-Embedding-0.6B (1024 dims, instructions) |
| docs/research/new/reranking-bge.md | Stratégie two-stage, N:K ratios |
| docs/research/new/runpod.md | Déploiement serverless, handlers, coûts |
| docs/research/new/infinity.md | Multi-model server, API OpenAI-compatible |
| docs/research/new/infinity2.md | Config pgvector, chunking, pipeline complet |
| docs/research/Validation technique du stack MCP RAG.md | Validation dépendances MCP SDK |
| docs/implementation-artifacts/old/tech-spec-alexandria-rag-mcp/ | Ancien spec (référence architecture) |

### Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Runtime | Bun | Performance, TypeScript natif, test runner |
| Database | PostgreSQL 18 + pgvector 0.8.1 | Une seule DB pour vecteurs et metadata |
| Vector type | `halfvec(1024)` | 50% économie stockage, performance équivalente |
| ORM | Drizzle + postgres.js | Support natif pgvector, type-safe |
| Embedding Model | Qwen3-Embedding-0.6B | 1024 dims, 32K tokens, multilingue, code |
| Reranker Model | bge-reranker-v2-m3 | Best open-source multilingue, Apache 2.0 |
| Inference Server | Infinity | Multi-model single endpoint, OpenAI API |
| GPU Hosting | RunPod Serverless (L4 24GB) | ~$25-35/mois, cold start acceptable |
| Query format | `Instruct: {task}\nQuery:{query}` | Requis par Qwen3 (+1-5% perf) |
| Document format | Raw text (no prefix) | Qwen3 spec |
| Two-stage | N=100 candidats → K=5 résultats | Optimal recall/latency |
| Skip rerank | Si top score > 0.90 | Adaptive reranking |
| HNSW Index | m=24, ef_construction=128, ef_search=100 | Optimal pour 1024D |
| Transport MCP | stdio | Intégration native Claude Code |
| Validation | Zod v3 | Requis par MCP SDK (bug v4) |
| Logging | Pino (structured JSON) | Observabilité, performance |

### Embedding Specifications (Qwen3-Embedding-0.6B)

| Parameter | Value |
|-----------|-------|
| Dimensions | **1024** (flexible 32-1024 via MRL) |
| Max tokens | **32,768** |
| Query format | `Instruct: {task}\nQuery:{query}` |
| Document format | Raw text, **no prefix** |
| Normalization | L2-normalized |
| Distance | Cosine (`<=>`) ou Inner Product (`<#>`) |
| VRAM | ~2GB (FP16) |
| Task instruction | `"Given a technical documentation search query, retrieve relevant passages that answer the query"` |

### Reranker Specifications (bge-reranker-v2-m3)

| Parameter | Value |
|-----------|-------|
| Max tokens | **512 combined** (query + document) |
| Score output | Raw logits (-10 to +10), normalize with sigmoid |
| VRAM | ~1GB (FP16) |
| N (candidates) | 100 (from pgvector) |
| K (final) | 5 (after rerank) |
| Skip condition | Top embedding score > 0.90 with clear separation |

### pgvector Configuration

```sql
-- Type de vecteur
embedding halfvec(1024)

-- Index HNSW optimisé pour 1024D
CREATE INDEX documents_embedding_idx ON documents
USING hnsw (embedding halfvec_cosine_ops)
WITH (m = 24, ef_construction = 128);

-- Configuration de recherche
SET hnsw.ef_search = 100;
SET hnsw.iterative_scan = 'relaxed_order';
```

### RunPod/Infinity Configuration

| Parameter | Value |
|-----------|-------|
| GPU | L4 (24GB) ou A4000 (16GB) |
| Image | `michaelf34/infinity:latest` ou custom |
| Models | `Qwen/Qwen3-Embedding-0.6B;BAAI/bge-reranker-v2-m3` |
| Batch sizes | `16;8` |
| Idle timeout | 30s |
| Cold start | 10-20s (FlashBoot) |
| Cost estimate | ~$25-35/mois (500 req/jour) |

## Implementation Plan

### Tasks

*À générer dans Step 3*

### Acceptance Criteria

*À générer dans Step 3*

## Additional Context

### Dependencies

```json
{
  "dependencies": {
    "@modelcontextprotocol/sdk": "^1.22.0",
    "openai": "^4.0.0",
    "postgres": "^3.4.0",
    "drizzle-orm": "^0.44.0",
    "zod": "^3.25.0",
    "pino": "^9.0.0"
  },
  "devDependencies": {
    "@types/bun": "latest",
    "drizzle-kit": "^0.30.0",
    "pino-pretty": "^11.0.0",
    "typescript": "^5.7.0",
    "@testcontainers/postgresql": "^10.0.0"
  }
}
```

### Database Schema (Drizzle)

```typescript
import { pgTable, bigserial, text, timestamp, jsonb, index } from 'drizzle-orm/pg-core';
import { customType } from 'drizzle-orm/pg-core';

// Custom halfvec type for pgvector
const halfvec = customType<{ data: number[]; driverData: string }>({
  dataType() { return 'halfvec(1024)'; },
  toDriver(value: number[]): string { return `[${value.join(',')}]`; },
  fromDriver(value: string): number[] { return JSON.parse(value.replace('[', '[').replace(']', ']')); }
});

export const documents = pgTable('documents', {
  id: bigserial('id').primaryKey(),
  source: text('source').notNull().unique(),
  title: text('title').notNull(),
  content: text('content').notNull(),
  tags: text('tags').array().default([]),
  version: text('version'),
  createdAt: timestamp('created_at').defaultNow(),
  updatedAt: timestamp('updated_at').defaultNow(),
});

export const chunks = pgTable('chunks', {
  id: bigserial('id').primaryKey(),
  documentId: bigserial('document_id').references(() => documents.id, { onDelete: 'cascade' }),
  chunkIndex: integer('chunk_index').notNull(),
  content: text('content').notNull(),
  heading: text('heading'),
  embedding: halfvec('embedding'),
  createdAt: timestamp('created_at').defaultNow(),
}, (table) => ({
  embeddingIdx: index('chunks_embedding_idx').using('hnsw', table.embedding, 'halfvec_cosine_ops')
    .with({ m: 24, ef_construction: 128 }),
}));
```

### Testing Strategy

- **Unit tests**: Domain entities, use cases (mocked ports)
- **Integration tests**: Database operations with Testcontainers (pgvector/pgvector:pg18)
- **E2E tests**: MCP tools avec mock Infinity server

### Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `DATABASE_URL` | PostgreSQL connection string | Yes |
| `RUNPOD_API_KEY` | RunPod API key | Yes |
| `RUNPOD_ENDPOINT_ID` | Infinity endpoint ID | Yes |
| `LOG_LEVEL` | Pino log level (debug/info/warn/error) | No (default: info) |

### Notes

- **Zod v3**: Import OBLIGATOIRE via `import { z } from "zod/v3"` (bug MCP SDK avec v4)
- **postgres.js**: Utiliser npm package, PAS `bun:sql` (bugs production)
- **Qwen3**: Requiert `transformers>=4.51.0` pour Infinity (KeyError: 'qwen3' sinon)
- **Instructions en anglais**: Même pour queries FR, écrire l'instruction Qwen3 en anglais
