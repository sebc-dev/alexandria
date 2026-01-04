---
title: 'Alexandria RAG Server (Java)'
slug: 'alexandria-rag-java'
created: '2026-01-04'
status: 'in-progress'
stepsCompleted: [1, 2]
tech_stack:
  - Java 25
  - Spring Boot 3.4+
  - Spring AI 1.1.0-M2 (MCP SDK)
  - Langchain4j 1.7.1
  - PostgreSQL 18
  - pgvector 0.8.1
  - RunPod/Infinity (BGE-M3)
  - bge-reranker-v2-m3
files_to_modify: []
code_patterns:
  - Virtual Threads for concurrency
  - Spring AI @Tool annotations for MCP
  - Langchain4j EmbeddingStore pattern
  - WebMVC SSE transport
test_patterns:
  - JUnit 5
  - Testcontainers for PostgreSQL
  - WireMock for Infinity API mocking
---

# Tech-Spec: Alexandria RAG Server (Java)

**Created:** 2026-01-04

## Overview

### Problem Statement

Claude Code n'a pas accès à la documentation technique à jour ni aux conventions de code du projet. Les développeurs perdent du temps à chercher manuellement dans la documentation ou à reformuler des questions. Besoin d'un système de recherche sémantique pour alimenter le contexte de Claude Code avec des informations pertinentes et actualisées.

### Solution

Serveur MCP en Java 25 (Spring Boot + Spring AI MCP SDK) exposant un RAG basé sur pgvector. Embeddings via BGE-M3 sur RunPod/Infinity. Skills Claude Code pour intégration fluide. Architecture s'appuyant sur Langchain4j pour l'orchestration RAG.

### Scope

**In Scope:**
- Serveur MCP Java 25 avec Spring AI MCP SDK
- Skills Claude Code pour recherche sémantique
- Ingestion de documents (markdown, texte, llms.txt)
- PostgreSQL 18 + pgvector 0.8.1 (halfvec 1024D)
- Embeddings via RunPod/Infinity (BGE-M3)
- Reranking (bge-reranker-v2-m3)
- Usage mono-utilisateur

**Out of Scope:**
- Système de mise à jour automatique des sources
- Système de cache (prévu pour version ultérieure)
- Support multi-utilisateur
- Interface web d'administration

## Context for Development

### Codebase Patterns

- **Runtime**: Java 25 avec Virtual Threads (Project Loom) pour concurrence légère
- **Framework**: Spring Boot 3.4+ avec Spring AI 1.1.0-M2 (MCP SDK)
- **RAG Orchestration**: Langchain4j 1.7.1 pour pipeline RAG (chunking, retrieval, reranking)
- **MCP Transport**: WebMVC SSE (synchrone, Virtual Threads gèrent la concurrence)
- **Vector Storage**: pgvector 0.8.1 avec halfvec pour réduire l'empreinte mémoire
- **Embedding Model**: BGE-M3 (1024 dimensions, 8K tokens context)
- **Reranker**: bge-reranker-v2-m3 via même endpoint Infinity

### Architecture Decisions

| Decision | Choix | Justification |
|----------|-------|---------------|
| Runtime | Java 25 | Virtual threads matures, stabilité, typage fort, pattern matching |
| Framework | Spring Boot 3.4+ | Écosystème mature, Spring AI MCP SDK officiel |
| MCP Transport | WebMVC SSE | Simplicité, Virtual Threads suffisent pour mono-utilisateur |
| RAG Library | Langchain4j | Pipeline RAG mature (chunking, retrieval, reranking intégrés) |
| Database | PostgreSQL 18 + pgvector 0.8.1 | Robuste, SQL standard, halfvec support natif |
| Embeddings | BGE-M3 via Infinity | 1024D, même famille que reranker, API OpenAI-compatible |
| Reranker | bge-reranker-v2-m3 | État de l'art, multilingue, même endpoint Infinity |
| Vector type | halfvec | 50% économie mémoire (2KB vs 4KB par vecteur 1024D) |

### pgvector Configuration (from research)

```sql
-- Table structure
CREATE TABLE documents (
    id bigserial PRIMARY KEY,
    content text,
    embedding halfvec(1024)
);

-- HNSW index optimized for 1024D
CREATE INDEX ON documents USING hnsw (embedding halfvec_cosine_ops)
WITH (m = 24, ef_construction = 100);

-- Runtime query setting
SET hnsw.ef_search = 100;
```

PostgreSQL config for 24GB RAM single-user:
- `shared_buffers = 6GB`
- `effective_cache_size = 18GB`
- `work_mem = 256MB`
- `maintenance_work_mem = 2GB`

### Chunking Strategy (from research)

- **Taille**: 450-500 tokens
- **Overlap**: 50-75 tokens (10-15%)
- **Stratégie**: Markdown-aware hierarchical (headers + recursive splitting)
- **Code blocks**: Préservation stricte des frontières ` ``` `
- **Métadonnées**: Breadcrumb headers (H1 > H2 > H3), fichier source, tags YAML

### Infinity API (from research)

Endpoint unique RunPod exposant:
- `POST /v1/embeddings` - BGE-M3 (OpenAI-compatible)
- `POST /rerank` - bge-reranker-v2-m3

```bash
# Embedding request
curl -X POST http://<runpod>/v1/embeddings \
  -H "Authorization: Bearer $API_KEY" \
  -d '{"model": "BAAI/bge-m3", "input": ["query text"]}'

# Rerank request
curl -X POST http://<runpod>/rerank \
  -H "Authorization: Bearer $API_KEY" \
  -d '{"model": "BAAI/bge-reranker-v2-m3", "query": "...", "documents": [...]}'
```

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `docs/research/resultats/PostgreSQL 18 and pgvector configuration.md` | Config pgvector détaillée |
| `docs/research/resultats/Stratégies de chunking pour RAG sur documentation markdown technique.md` | Stratégie chunking |
| `docs/research/resultats/Infinity Embedding Server.md` | API Infinity/RunPod |
| `docs/research/resultats/BGE-M3 vs Qwen3-Embedding-0.6B.md` | Comparatif embeddings |

### Hardware Cible (Self-hosted)

- CPU: Intel Core i5-4570 (4c/4t @ 3.2-3.6 GHz)
- RAM: 24 GB DDR3-1600
- Pas de GPU local (embeddings déportés sur RunPod)

## Implementation Plan

### Tasks

*(À définir en Step 3 - Generate)*

### Acceptance Criteria

*(À définir en Step 3 - Generate)*

## Additional Context

### Dependencies

**Maven dependencies (principales):**
```xml
<!-- Spring AI MCP -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-webmvc-spring-boot-starter</artifactId>
</dependency>

<!-- Langchain4j -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>1.7.1</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-document-parser-apache-tika</artifactId>
    <version>1.7.1</version>
</dependency>
```

### Testing Strategy

- **Unit tests**: JUnit 5, Mockito
- **Integration tests**: Testcontainers (PostgreSQL + pgvector)
- **API mocking**: WireMock pour simuler Infinity API
- **E2E tests**: Test du flow MCP complet avec Claude Code

### Notes

- Format llms.txt: Standard défini sur https://llmstxt.org/
- Usage prévu: mono-utilisateur, développeur utilisant Claude Code quotidiennement
- Centaines de documents (taille typique de documentation technique)
- Recherches existantes dans `docs/research/resultats/` réutilisables
- Spring AI MCP reference: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
- Langchain4j pgvector: https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/
