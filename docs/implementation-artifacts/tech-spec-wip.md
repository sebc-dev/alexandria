---
title: 'Alexandria RAG Server (Java)'
slug: 'alexandria-rag-java'
created: '2026-01-04'
status: 'in-progress'
stepsCompleted: [1, 2]
tech_stack:
  - Java 25 LTS (25.0.1)
  - Spring Boot 3.5.9 + Spring Framework 6.2.x
  - Spring AI MCP SDK 1.1.2 GA (SSE transport)
  - Langchain4j 1.10.0 (GA) + langchain4j-pgvector 1.10.0-beta18
  - spring-retry 2.0.11 avec @EnableRetry
  - PostgreSQL 18.1
  - pgvector 0.8.1
  - RunPod/Infinity (BGE-M3)
  - bge-reranker-v2-m3
stack_validation: '2026-01-04'
files_to_modify: []
code_patterns:
  - Architecture flat simplifiée (core/ + adapters/ + config/)
  - Virtual Threads for concurrency
  - Spring AI @McpTool annotations for MCP tools
  - Langchain4j EmbeddingStore + ContentRetriever
  - Custom AlexandriaMarkdownSplitter (markdown-aware)
  - SSE transport (/sse + /mcp/message)
  - spring-retry @EnableRetry + @Retryable
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
- **Transport MCP** : Spring Boot 3.5.9 + Spring AI MCP SDK 1.1.2 (SSE via `/sse` + `/mcp/message`)
- **Pipeline RAG** : Langchain4j (embeddings, retrieval, reranking)
- **Embeddings** : BGE-M3 sur RunPod/Infinity
- **Retry** : spring-retry 2.0.11 avec @Retryable (exponential backoff)
- **Architecture** : Flat et simplifiée (YAGNI) - pas d'hexagonal, pas de DDD

### Scope

**In Scope:**
- Serveur MCP Java 25 avec Spring AI MCP SDK 1.1.2 (SSE transport)
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

- **Runtime**: Java 25 LTS (25.0.1) avec Virtual Threads (JEP 491 - no pinning)
- **Framework**: Spring Boot 3.5.9 + Spring Framework 6.2.x
- **MCP Transport**: Spring AI MCP SDK 1.1.2 GA - SSE (`/sse` + `/mcp/message`)
- **RAG Pipeline**: Langchain4j 1.10.0 (embeddings, retrieval) + client HTTP custom pour reranking
- **Chunking**: Custom AlexandriaMarkdownSplitter (préservation code blocks, breadcrumbs)
- **Vector Storage**: pgvector 0.8.1 avec vector(1024) via Langchain4j EmbeddingStore
- **Embedding Model**: BGE-M3 (1024 dimensions, 8K tokens context) via langchain4j-open-ai
- **Reranker**: bge-reranker-v2-m3 via endpoint Infinity `/rerank` (format Cohere, non-OpenAI)
- **Retry**: spring-retry 2.0.11 avec @EnableRetry + @Retryable (exponential backoff 1s→2s→4s, jitter=100ms)

### Architecture Decisions

| Decision | Choix | Justification |
|----------|-------|---------------|
| Runtime | Java 25 LTS (25.0.1) | Virtual threads matures (JEP 491), support jusqu'en 2030 |
| Framework | Spring Boot 3.5.9 + Framework 6.2.x | Compatibilité Langchain4j, Jakarta EE 10, support OSS jusqu'en juin 2026 |
| MCP Transport | SSE | Endpoints `/sse` + `/mcp/message`, stable avec Spring AI 1.1.2 GA |
| RAG Library | Langchain4j 1.10.0 | GA stable, EmbeddingStore pgvector (beta18) |
| MCP SDK | Spring AI 1.1.2 GA | @McpTool annotations, version stable pour Boot 3.x |
| Database | PostgreSQL 18.1 + pgvector 0.8.1 | Robuste, SQL standard, HNSW index |
| Embeddings | BGE-M3 via langchain4j-open-ai | 1024D, baseUrl custom vers Infinity |
| Reranker | bge-reranker-v2-m3 | Client HTTP custom (format Cohere, non-OpenAI) |
| Vector type | vector(1024) | Langchain4j natif, halfvec non supporté |
| Retry | spring-retry 2.0.11 | @EnableRetry + @Retryable, intégration Spring mature |
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
│   ├── InfinityEmbeddingModel.java      # OpenAiEmbeddingModel avec baseUrl custom
│   ├── InfinityRerankClient.java        # Client HTTP pour /rerank (format Cohere)
│   ├── PgVectorRepository.java          # Langchain4j EmbeddingStore wrapper
│   └── McpTools.java                    # @McpTool annotations Spring AI
│
├── config/
│   ├── LangchainConfig.java             # Beans Langchain4j
│   ├── McpConfig.java                   # Config MCP SSE
│   └── RetryConfig.java                 # @EnableRetry
│
└── AlexandriaApplication.java
```

**Principes :**
- Pas d'interfaces/ports abstraits - adapters directs
- Pas de DDD aggregates - simples POJOs
- @Retryable spring-retry sur les clients HTTP (pas de circuit breaker)
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

### Infinity API (validated)

Endpoint unique RunPod exposant deux APIs avec formats différents:

**Embeddings** - `POST /v1/embeddings` - Format OpenAI-compatible
- Utilisable via `langchain4j-open-ai` avec `baseUrl` custom
- BGE-M3 produit des vecteurs 1024 dimensions

```java
// Via Langchain4j OpenAI module
EmbeddingModel model = OpenAiEmbeddingModel.builder()
    .baseUrl("http://localhost:7997/v1")
    .apiKey("EMPTY")  // Si pas d'auth configurée
    .modelName("BAAI/bge-m3")
    .build();
```

**Reranking** - `POST /rerank` - Format Cohere (NON OpenAI-compatible)
- Nécessite client HTTP custom (pas de support Langchain4j natif)
- bge-reranker-v2-m3, 512 tokens max par paire query/document

```bash
# Request format (style Cohere)
curl -X POST http://<runpod>/rerank \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "BAAI/bge-reranker-v2-m3",
    "query": "Question de recherche",
    "documents": ["Doc 1", "Doc 2", "Doc 3"]
  }'

# Response format
{
  "results": [
    {"index": 0, "relevance_score": 0.95},
    {"index": 2, "relevance_score": 0.72},
    {"index": 1, "relevance_score": 0.31}
  ]
}
```

### Retry Pattern (spring-retry 2.0.11)

```java
@Configuration
@EnableRetry
public class RetryConfig { }

@Service
public class InfinityRerankClient {

    @Retryable(
        retryFor = {HttpServerErrorException.class, SocketTimeoutException.class},
        maxAttempts = 4,     // 4 tentatives totales (1 initial + 3 retries)
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 4000)
    )
    public RerankResult rerank(String query, List<String> documents) {
        return restClient.post()
            .uri(infinityEndpoint + "/rerank")
            .body(new RerankRequest(query, documents))
            .retrieve()
            .body(RerankResult.class);
    }
}
```

**Note:** `maxAttempts` inclut l'appel initial (4 = 1 initial + 3 retries). Backoff: 1s → 2s → 4s.

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `docs/research/resultats/java/Spring AI MCP SDK - Complete @Tool annotation guide for SSE transport.md` | Guide @McpTool annotations |
| `docs/research/resultats/java/Spring AI MCP Server WebMVC SSE.md` | Config MCP SSE endpoints |
| `docs/research/resultats/java/Langchain4j et Java 25 - compatibilité Spring Boot en janvier 2026.md` | Compatibilité Langchain4j/Boot 3.5 |
| `docs/research/resultats/java/Langchain4j 1.0.1 DocumentSplitter capabilities for Alexandria RAG.md` | Custom splitter pattern |
| `docs/research/resultats/java/Langchain4j works seamlessly with Infinity embedding server.md` | Intégration Infinity |
| `docs/research/resultats/java/API de reranking Infinity - guide technique complet.md` | API reranking détaillée |
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

**Maven dependencies (validées janvier 2026):**
```xml
<!-- Spring Boot 3.5.9 Parent -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.9</version>
</parent>

<properties>
    <java.version>25</java.version>
    <spring-ai.version>1.1.2</spring-ai.version>
    <langchain4j.version>1.10.0</langchain4j.version>
    <langchain4j-spring.version>1.10.0-beta18</langchain4j-spring.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Langchain4j BOM -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>${langchain4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Spring AI BOM -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring AI MCP SDK (SSE transport) - GA -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>

    <!-- spring-retry pour @Retryable -->
    <dependency>
        <groupId>org.springframework.retry</groupId>
        <artifactId>spring-retry</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>

    <!-- Langchain4j Spring Integration -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-spring-boot-starter</artifactId>
        <version>${langchain4j-spring.version}</version>
    </dependency>

    <!-- Langchain4j OpenAI (baseUrl custom pour Infinity) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
        <version>${langchain4j-spring.version}</version>
    </dependency>

    <!-- Langchain4j pgvector (BETA - 1.10.0-beta18) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-pgvector</artifactId>
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
</dependencies>
```

**Image Docker pour tests :** `pgvector/pgvector:0.8.1-pg18`

**Note versions:**
- Spring Boot 3.5.9 = Dernière version 3.x, support OSS jusqu'en juin 2026
- Spring AI 1.1.2 = GA, stable pour Boot 3.x
- langchain4j-pgvector = Beta (API stable depuis 0.31.0)
- langchain4j-spring-boot-starter = Beta, nécessite version explicite
- spring-retry 2.0.11 = Géré par Spring Boot, @EnableRetry + @Retryable

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

### Stack Validation (2026-01-04)

| Composant | Version | Statut | Notes |
|-----------|---------|--------|-------|
| Java | 25 LTS (25.0.1) | ✅ GA | Support jusqu'en 2030, JEP 491 inclus |
| Spring Boot | 3.5.9 | ✅ GA | Dernière 3.x, support OSS jusqu'en juin 2026 |
| Spring Framework | 6.2.x | ✅ GA | Compatible Langchain4j, Jakarta EE 10 |
| Spring AI MCP SDK | 1.1.2 | ✅ GA | SSE transport, @McpTool annotations |
| Langchain4j core | 1.10.0 | ✅ GA | BOM recommandé |
| langchain4j-pgvector | 1.10.0-beta18 | ⚠️ Beta | API stable depuis 0.31.0 |
| langchain4j-open-ai | 1.10.0 | ✅ GA | baseUrl custom supporté |
| langchain4j-spring-boot-starter | 1.10.0-beta18 | ⚠️ Beta | Compatible Boot 3.x uniquement |
| PostgreSQL | 18.1 | ✅ GA | Depuis 25 sept. 2025 |
| pgvector | 0.8.1 | ✅ GA | Compatible PG13-18 |
| spring-retry | 2.0.11 | ✅ GA | @EnableRetry + @Retryable |
| Testcontainers | 2.0.2 | ✅ GA | Java 17+ |
| WireMock | 3.10.0 | ✅ GA | Java 11+ |

**Risques identifiés:**
1. langchain4j-pgvector en beta - API stable mais possible breaking changes
2. langchain4j-spring-boot-starter en beta - nécessite version explicite

**Pourquoi Spring Boot 3.5.x (pas 4.x):**
- Langchain4j 1.10.0 incompatible avec Boot 4.0 (Jackson 3, Jakarta EE 11)
- Spring AI 1.1.2 GA stable pour Boot 3.x
- Seed4J a désactivé ses modules Langchain4j pour Boot 4.x

**Migration future vers Boot 4.x:**
- Attendre Langchain4j compatible Jackson 3 + Jakarta EE 11
- Suivre github.com/langchain4j/langchain4j-spring pour annonces
