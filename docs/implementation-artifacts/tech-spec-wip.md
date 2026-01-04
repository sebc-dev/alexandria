---
title: 'Alexandria RAG Server (Java)'
slug: 'alexandria-rag-java'
created: '2026-01-04'
status: 'in-progress'
stepsCompleted: [1, 2]
tech_stack:
  - Java 25
  - Spring Boot 4.0.1
  - Spring AI MCP SDK (Streamable HTTP transport)
  - Langchain4j 1.0.0 (pipeline RAG complet)
  - spring-retry (gestion des retries)
  - PostgreSQL 18
  - pgvector 0.8.1
  - RunPod/Infinity (BGE-M3)
  - bge-reranker-v2-m3
files_to_modify: []
code_patterns:
  - Architecture flat simplifiée (core/ + adapters/ + config/)
  - Virtual Threads for concurrency
  - Spring AI @McpTool annotations for MCP tools
  - Langchain4j EmbeddingStore + ContentRetriever
  - Custom AlexandriaMarkdownSplitter (markdown-aware)
  - Streamable HTTP transport
  - spring-retry @Retryable for resilience
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

Serveur MCP en Java 25 exposant un RAG basé sur pgvector :
- **Transport MCP** : Spring Boot 4.0.1 + Spring AI MCP SDK (Streamable HTTP)
- **Pipeline RAG** : Langchain4j (embeddings, retrieval, reranking)
- **Embeddings** : BGE-M3 sur RunPod/Infinity
- **Retry** : spring-retry avec @Retryable (exponential backoff)
- **Architecture** : Flat et simplifiée (YAGNI) - pas d'hexagonal, pas de DDD

### Scope

**In Scope:**
- Serveur MCP Java 25 avec Spring AI MCP SDK (Streamable HTTP)
- Skills Claude Code pour recherche sémantique
- Ingestion de documents (markdown, texte, llms.txt)
- PostgreSQL 18 + pgvector 0.8.1 (vector 1024D)
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
- **Framework**: Spring Boot 4.0.1 (web, DI, config)
- **MCP Transport**: Spring AI MCP SDK - Streamable HTTP (endpoint unique `/mcp`)
- **RAG Pipeline**: Langchain4j 1.0.0 (embeddings, retrieval, reranking)
- **Chunking**: Custom AlexandriaMarkdownSplitter (préservation code blocks, breadcrumbs)
- **Vector Storage**: pgvector 0.8.1 avec vector(1024) via Langchain4j EmbeddingStore
- **Embedding Model**: BGE-M3 (1024 dimensions, 8K tokens context)
- **Reranker**: bge-reranker-v2-m3 via même endpoint Infinity (512 tokens max/paire)
- **Retry**: spring-retry avec @Retryable (exponential backoff 1s→2s→4s, jitter)

### Architecture Decisions

| Decision | Choix | Justification |
|----------|-------|---------------|
| Runtime | Java 25 LTS | Virtual threads matures (JEP 491), stabilité, typage fort |
| Framework | Spring Boot 4.0.1 | Spring Framework 7.0, support Java 25 natif |
| MCP Transport | Streamable HTTP | Endpoint unique `/mcp`, remplace SSE deprecated |
| RAG Library | Langchain4j 1.0.0 | Pipeline RAG mature, EmbeddingStore pgvector natif |
| MCP SDK | Spring AI MCP | @McpTool annotations, auto-discovery |
| Database | PostgreSQL 18 + pgvector 0.8.1 | Robuste, SQL standard, HNSW index |
| Embeddings | BGE-M3 via Infinity | 1024D, API OpenAI-compatible |
| Reranker | bge-reranker-v2-m3 | État de l'art, multilingue, 512 tokens max |
| Vector type | vector(1024) | Langchain4j natif, simplicité |
| Retry | spring-retry @Retryable | Déclaratif, exponential backoff + jitter |
| Architecture | Flat simplifiée | YAGNI - pas d'hexagonal ni DDD |

### Project Structure

```
src/main/java/dev/alexandria/
├── core/
│   ├── Document.java                    # POJO simple
│   ├── DocumentChunk.java               # Chunk avec metadata
│   ├── RetrievalService.java            # Logique métier RAG
│   ├── AlexandriaMarkdownSplitter.java  # Custom DocumentSplitter
│   └── LlmsTxtParser.java               # Parser llms.txt format
│
├── adapters/
│   ├── InfinityEmbeddingClient.java     # Client Infinity (embeddings + rerank)
│   ├── PgVectorRepository.java          # Langchain4j EmbeddingStore wrapper
│   └── McpTools.java                    # @McpTool annotations Spring AI
│
├── config/
│   ├── LangchainConfig.java             # Beans Langchain4j
│   ├── McpConfig.java                   # Config MCP Streamable HTTP
│   └── RetryConfig.java                 # @EnableRetry + config
│
└── AlexandriaApplication.java
```

**Principes :**
- Pas d'interfaces/ports abstraits - adapters directs
- Pas de DDD aggregates - simples POJOs
- @Retryable sur InfinityEmbeddingClient - pas de circuit breaker
- Virtual Threads gérés par Spring Boot - pas de config manuelle

### pgvector Configuration (from research)

```sql
-- Table structure (Langchain4j crée automatiquement avec createTable=true)
CREATE TABLE document_embeddings (
    embedding_id UUID PRIMARY KEY,
    embedding vector(1024),
    text TEXT,
    metadata JSONB
);

-- HNSW index optimized for 1024D cosine similarity
CREATE INDEX ON document_embeddings USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 128);

-- Runtime query setting
SET hnsw.ef_search = 100;
```

PostgreSQL config for 24GB RAM single-user:
- `shared_buffers = 6GB`
- `effective_cache_size = 18GB`
- `work_mem = 64MB`
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
| `docs/research/resultats/java/Spring AI MCP SDK - Complete @Tool annotation guide for SSE transport.md` | Guide @McpTool annotations |
| `docs/research/resultats/java/Spring AI MCP Server WebMVC SSE.md` | Config MCP endpoints |
| `docs/research/resultats/java/Langchain4j 1.0.1 DocumentSplitter capabilities for Alexandria RAG.md` | Custom splitter pattern |
| `docs/research/resultats/java/Langchain4j works seamlessly with Infinity embedding server.md` | Intégration Infinity |
| `docs/research/resultats/java/API de reranking Infinity - guide technique complet.md` | API reranking détaillée |
| `docs/research/resultats/java/Pattern Retry HTTP en Java moderne sans dépendances lourdes.md` | Pattern spring-retry |
| `docs/research/resultats/java/Schéma PostgreSQL optimal pour RAG avec pgvector.md` | Schéma DB complet |
| `docs/research/resultats/java/Testcontainers avec PostgreSQL 18 et pgvector 0.8.1.md` | Config tests |
| `docs/research/resultats/java/llms.txt Standard - Complete Specification for Java Parser Implementation.md` | Spec llms.txt parser |
| `docs/research/resultats/java/Claude Code MCP configuration for SSE transport servers.md` | Config client Claude Code |

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
<!-- Spring Boot 4.0.1 Parent -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.1</version>
</parent>

<!-- Spring AI MCP SDK (Streamable HTTP transport) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>

<!-- Spring Retry -->
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- Langchain4j (pipeline RAG complet) -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Test dependencies -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.wiremock</groupId>
    <artifactId>wiremock-standalone</artifactId>
    <version>3.10.0</version>
    <scope>test</scope>
</dependency>
```

**Image Docker pour tests :** `pgvector/pgvector:0.8.1-pg18`

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
