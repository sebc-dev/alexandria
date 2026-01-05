---
title: 'Alexandria RAG Server (Java)'
slug: 'alexandria-rag-java'
created: '2026-01-04'
updated: '2026-01-05'
status: 'in-progress'
stepsCompleted: [1, 2]
specProgress: '75%'
tech_stack:
  - Java 25 LTS (25.0.1)
  - Spring Boot 3.5.9 + Spring Framework 6.2.x
  - Spring AI MCP SDK 1.1.2 GA (HTTP Streamable transport)
  - Langchain4j 1.10.0 (GA) + langchain4j-pgvector 1.10.0-beta18
  - Resilience4j 2.3.0 (Retry + decorators)
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
  - HTTP Streamable transport (/mcp endpoint unique)
  - Resilience4j @Retry annotations + programmatic decorators
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
- **Transport MCP** : Spring Boot 3.5.9 + Spring AI MCP SDK 1.1.2 (HTTP Streamable via `/mcp`)
- **Pipeline RAG** : Langchain4j (embeddings, retrieval, reranking)
- **Embeddings** : BGE-M3 sur RunPod/Infinity
- **Retry** : Resilience4j 2.3.0 avec @Retry (exponential backoff)
- **Architecture** : Flat et simplifiée (YAGNI) - pas d'hexagonal, pas de DDD

### Scope

**In Scope:**
- Serveur MCP Java 25 avec Spring AI MCP SDK 1.1.2 (HTTP Streamable transport)
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
- **MCP Transport**: Spring AI MCP SDK 1.1.2 GA - HTTP Streamable (`/mcp` endpoint unique)
- **RAG Pipeline**: Langchain4j 1.10.0 (embeddings, retrieval) + client HTTP custom pour reranking
- **Chunking**: Custom AlexandriaMarkdownSplitter (préservation code blocks, breadcrumbs)
- **Vector Storage**: pgvector 0.8.1 avec vector(1024) via Langchain4j EmbeddingStore
- **Embedding Model**: BGE-M3 (1024 dimensions, 8K tokens context) via langchain4j-open-ai
- **Reranker**: bge-reranker-v2-m3 via endpoint Infinity `/rerank` (format Cohere, non-OpenAI)
- **Retry**: Resilience4j 2.3.0 avec @Retry (exponential backoff 1s→2s→4s, randomizedWaitFactor=0.1)

### Architecture Decisions

| Decision | Choix | Justification |
|----------|-------|---------------|
| Runtime | Java 25 LTS (25.0.1) | Virtual threads matures (JEP 491), support jusqu'en 2030 |
| Framework | Spring Boot 3.5.9 + Framework 6.2.x | Compatibilité Langchain4j, Jakarta EE 10, support OSS jusqu'en juin 2026 |
| MCP Transport | HTTP Streamable | Endpoint unique `/mcp`, recommandé depuis MCP 2025-03-26 |
| RAG Library | Langchain4j 1.10.0 | GA stable, EmbeddingStore pgvector (beta18) |
| MCP SDK | Spring AI 1.1.2 GA | @McpTool annotations, version stable pour Boot 3.x |
| Database | PostgreSQL 18.1 + pgvector 0.8.1 | Robuste, SQL standard, HNSW index |
| Embeddings | BGE-M3 via langchain4j-open-ai | 1024D, baseUrl custom vers Infinity |
| Reranker | bge-reranker-v2-m3 | Client HTTP custom (format Cohere, non-OpenAI) |
| Vector type | vector(1024) | Langchain4j natif, halfvec non supporté |
| Retry | Resilience4j 2.3.0 | @Retry annotations, compatible Virtual Threads, metrics Micrometer |
| Architecture | Flat simplifiée | YAGNI - pas d'hexagonal ni DDD |

### Project Structure

```
src/main/java/dev/alexandria/
├── core/
│   ├── Document.java                    # POJO simple
│   ├── DocumentChunk.java               # Chunk avec metadata
│   ├── ChunkMetadata.java               # Record metadata (8 champs + document_hash)
│   ├── RetrievalService.java            # Logique métier RAG + tiered response
│   ├── IngestionService.java            # Orchestration ingestion
│   ├── DocumentUpdateService.java       # Detection changements + DELETE/INSERT
│   ├── QueryValidator.java              # Validation requêtes avant recherche
│   ├── McpSearchResponse.java           # Response schema avec SearchMetadata
│   ├── AlexandriaMarkdownSplitter.java  # Custom DocumentSplitter (code blocks, tables)
│   ├── LlmsTxtParser.java               # Parser llms.txt format (llmstxt.org)
│   ├── AlexandriaException.java         # Exception racine avec ErrorCategory
│   └── ErrorCategory.java               # Enum catégories d'erreurs
│
├── adapters/
│   ├── InfinityRerankClient.java        # Client HTTP pour /rerank (format Cohere)
│   ├── PgVectorRepository.java          # Langchain4j EmbeddingStore wrapper
│   ├── McpTools.java                    # @McpTool annotations Spring AI
│   └── McpResponseFormatter.java        # Dual-format (JSON + Markdown)
│
├── config/
│   ├── LangchainConfig.java             # Beans Langchain4j
│   ├── McpConfig.java                   # Config MCP HTTP Streamable
│   ├── HttpClientConfig.java            # RestClient beans avec timeouts
│   ├── ResilienceConfig.java            # Resilience4j RetryRegistry
│   ├── RagProperties.java               # @ConfigurationProperties RAG pipeline
│   └── TimeoutProperties.java           # @ConfigurationProperties timeouts
│
├── cli/
│   └── IngestCommand.java               # CLI Picocli pour bulk ingestion
│
└── AlexandriaApplication.java

src/test/java/dev/alexandria/test/
├── PgVectorTestConfiguration.java       # Testcontainers pgvector
├── InfinityStubs.java                   # WireMock stubs Infinity API
├── EmbeddingFixtures.java               # Générateur vecteurs 1024D
└── McpTestSupport.java                  # Helper client MCP
```

**Principes :**
- Pas d'interfaces/ports abstraits - adapters directs
- Pas de DDD aggregates - simples POJOs
- @Retry Resilience4j sur les clients HTTP (pas de circuit breaker)
- Virtual Threads gérés par Spring Boot - pas de config manuelle

### pgvector Configuration (from research + #12)

```sql
-- Table structure (Langchain4j crée automatiquement avec createTable=true)
-- Note: Langchain4j utilise ces noms de colonnes par défaut
CREATE TABLE document_embeddings (
    embedding_id UUID PRIMARY KEY,  -- Langchain4j: configurable via idColumn()
    embedding vector(1024),         -- Langchain4j: configurable via embeddingColumn()
    text TEXT,                      -- Langchain4j: configurable via textColumn()
    metadata JSONB                  -- Langchain4j: configurable via metadataColumn()
);

-- HNSW index optimized for 1024D cosine similarity
CREATE INDEX idx_embeddings_hnsw ON document_embeddings
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 128);

-- B-tree indexes pour opérations de mise à jour (DELETE par document)
CREATE INDEX idx_doc_source_uri ON document_embeddings ((metadata->>'sourceUri'));
CREATE INDEX idx_doc_hash ON document_embeddings ((metadata->>'documentHash'));

-- Index GIN pour requêtes flexibles sur métadonnées (optionnel)
CREATE INDEX idx_metadata_gin ON document_embeddings USING GIN (metadata jsonb_path_ops);

-- Runtime query settings
SET hnsw.ef_search = 100;
SET hnsw.iterative_scan = on;  -- Nouveau 0.8.x pour filtres JSONB
```

**Index B-tree justification:**
- `idx_doc_source_uri`: Lookup rapide pour DELETE par document lors des mises à jour
- `idx_doc_hash`: Détection de doublons par contenu (v2) et vérification de changement

PostgreSQL config for 24GB RAM single-user:
- `shared_buffers = 6GB`
- `effective_cache_size = 18GB`
- `work_mem = 64MB`
- `maintenance_work_mem = 2GB`

### Chunking Strategy (from research #15)

**Paramètres de base:**
- **Taille**: 450-500 tokens
- **Overlap**: 50-75 tokens (10-15%)
- **Stratégie**: Markdown-aware hierarchical (headers + recursive splitting)
- **maxOversizedChunk**: 1500 tokens (pour code blocks volumineux)

**Unités atomiques (jamais split):**

| Élément | Comportement | Fallback si > 1500 tokens |
|---------|--------------|---------------------------|
| Code blocks ` ``` ` | Préservation stricte | Split aux boundaries fonction/classe |
| Tables markdown | Préservation stricte | Reformater en key-value par ligne |
| YAML front matter | Extraire → metadata | N/A (typiquement < 100 tokens) |

**Priorité de split** (du plus au moins prioritaire):
1. **Code blocks** - Toujours préserver
2. **Tables** - Toujours préserver
3. **Headers** - Primary split boundary
4. **Lists** - Split entre items, préserver contexte intro
5. **Paragraphs** - Secondary split boundary
6. **Sentences** - Tertiary (pour paragraphes oversized)

**Breadcrumbs:**
- Depth: H1 > H2 > H3 (éviter H4+ = bruit)
- Format: String délimitée `"Configuration > Database > PostgreSQL"`
- Storage: Metadata `breadcrumbs` sur chaque chunk

```java
package dev.alexandria.core;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.splitter.DocumentSplitter;

/**
 * Splitter Markdown-aware avec préservation des unités atomiques.
 * Langchain4j n'a pas de splitter markdown dédié (PR #2418 toujours draft).
 */
public class AlexandriaMarkdownSplitter implements DocumentSplitter {

    private static final int MAX_CHUNK_TOKENS = 500;
    private static final int OVERLAP_TOKENS = 75;
    private static final int MAX_OVERSIZED_TOKENS = 1500;
    private static final int BREADCRUMB_DEPTH = 3;

    private final TokenEstimator tokenEstimator;

    public AlexandriaMarkdownSplitter(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    @Override
    public List<TextSegment> split(Document document) {
        String content = document.text();

        // 1. Extraire YAML front matter → metadata
        var frontMatterResult = extractFrontMatter(content);
        Map<String, Object> docMetadata = frontMatterResult.metadata();
        content = frontMatterResult.content();

        // 2. Identifier et protéger les unités atomiques
        var protectedContent = protectAtomicUnits(content);

        // 3. Split par headers puis récursivement
        List<TextSegment> segments = splitByHeaders(protectedContent, docMetadata);

        // 4. Restaurer les unités protégées et ajouter breadcrumbs
        return segments.stream()
            .map(this::restoreAtomicUnits)
            .map(s -> addBreadcrumbMetadata(s, docMetadata))
            .toList();
    }

    /**
     * Extrait YAML front matter (--- ... ---) en metadata.
     */
    private FrontMatterResult extractFrontMatter(String markdown) {
        if (!markdown.startsWith("---")) {
            return new FrontMatterResult(markdown, Map.of());
        }

        int endIndex = markdown.indexOf("---", 3);
        if (endIndex == -1) {
            return new FrontMatterResult(markdown, Map.of());
        }

        String yamlBlock = markdown.substring(3, endIndex).trim();
        String remaining = markdown.substring(endIndex + 3).trim();

        // Parse YAML basique (ou utiliser SnakeYAML)
        Map<String, Object> metadata = parseSimpleYaml(yamlBlock);
        return new FrontMatterResult(remaining, metadata);
    }

    /**
     * Remplace code blocks et tables par placeholders.
     */
    private ProtectedContent protectAtomicUnits(String content) {
        Map<String, String> placeholders = new HashMap<>();
        String result = content;

        // Protéger code blocks
        Pattern codePattern = Pattern.compile("```[\\s\\S]*?```", Pattern.MULTILINE);
        Matcher codeMatcher = codePattern.matcher(result);
        int codeIndex = 0;
        while (codeMatcher.find()) {
            String placeholder = "{{CODE_BLOCK_" + (codeIndex++) + "}}";
            placeholders.put(placeholder, codeMatcher.group());
            result = result.replace(codeMatcher.group(), placeholder);
        }

        // Protéger tables
        Pattern tablePattern = Pattern.compile("^\\|.*\\|$[\\s\\S]*?(?=\\n\\n|$)", Pattern.MULTILINE);
        Matcher tableMatcher = tablePattern.matcher(result);
        int tableIndex = 0;
        while (tableMatcher.find()) {
            String placeholder = "{{TABLE_" + (tableIndex++) + "}}";
            placeholders.put(placeholder, tableMatcher.group());
            result = result.replace(tableMatcher.group(), placeholder);
        }

        return new ProtectedContent(result, placeholders);
    }

    record FrontMatterResult(String content, Map<String, Object> metadata) {}
    record ProtectedContent(String content, Map<String, String> placeholders) {}
}
```

**Configuration recommandée:**

```yaml
# application.yml
alexandria:
  chunking:
    max-tokens: 500
    overlap-tokens: 75
    max-oversized-tokens: 1500
    breadcrumb-depth: 3
    preserve-code-blocks: true
    preserve-tables: true
    extract-front-matter: true
```

**ChunkMetadata record (from research #10 + #12):**

```java
/**
 * Métadonnées par chunk - 8 champs pour tracking complet.
 * Contrainte Langchain4j: types primitifs (String, int, long, double, boolean).
 */
public record ChunkMetadata(
    String sourceUri,        // URI logique (pas filesystem path) - identifiant document
    String documentHash,     // SHA-256 du document complet - détection changements
    int chunkIndex,          // Position dans le document
    String breadcrumbs,      // "H1 > H2 > H3" (string délimitée)
    String documentTitle,    // Titre du document (du front matter ou H1)
    String contentHash,      // SHA-256 du chunk - déduplication fine
    long createdAt,          // Epoch millis
    String documentType      // "markdown", "llmstxt", "text"
) {
    /**
     * Convertit path filesystem en URI logique sécurisé.
     * Ne jamais exposer /home/user/... à Claude.
     */
    public static String toLogicalUri(Path filePath, Path basePath) {
        Path relative = basePath.relativize(filePath);
        return "docs://" + relative.toString().replace("\\", "/");
    }

    /**
     * Calcule le hash SHA-256 normalisé d'un contenu.
     * Normalisation: NFKC + espaces + fins de ligne.
     */
    public static String computeHash(String content) {
        // Normalisation Unicode NFKC
        String normalized = Normalizer.normalize(content, Normalizer.Form.NFKC);
        // Normaliser espaces et fins de ligne
        normalized = normalized.replaceAll("[\\u00A0\\u2000-\\u200B\\u3000\\t]+", " ")
                               .replace("\r\n", "\n").replace("\r", "\n")
                               .replaceAll(" +", " ").replaceAll("\n+", "\n")
                               .strip();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

**Budget tokens metadata:** 50-80 tokens par chunk (10-15% du budget).

**Champs clés pour update strategy:**
- `sourceUri` = identifiant stable du document (chemin logique)
- `documentHash` = détection de changements (si différent → re-ingestion)

### Infinity API (validated)

Endpoint unique RunPod exposant deux APIs avec formats différents:

**Embeddings** - `POST /v1/embeddings` - Format OpenAI-compatible
- Utilisable via `langchain4j-open-ai` avec `baseUrl` custom
- BGE-M3 produit des vecteurs 1024 dimensions

```java
// Via Langchain4j OpenAI module (pas de wrapper custom nécessaire)
// Configuré comme bean dans LangchainConfig.java
EmbeddingModel model = OpenAiEmbeddingModel.builder()
    .baseUrl("http://localhost:7997/v1")  // Infinity endpoint
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

### Retry Pattern (Resilience4j 2.3.0)

```yaml
# application.yml
resilience4j:
  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 500ms
        retryExceptions:
          - java.io.IOException
          - org.springframework.web.client.HttpServerErrorException

    instances:
      infinityApi:
        baseConfig: default
        maxAttempts: 4
        waitDuration: 1s
        enableExponentialBackoff: true          # OBLIGATOIRE pour exponential backoff
        exponentialBackoffMultiplier: 2
        exponentialMaxWaitDuration: 4s
        enableRandomizedWait: true              # OBLIGATOIRE pour jitter
        randomizedWaitFactor: 0.1               # ±10% = ~100ms à 1s
        retryExceptions:
          - org.springframework.web.client.HttpServerErrorException   # Erreurs 5xx
          - org.springframework.web.client.ResourceAccessException    # Timeouts/connexion
        ignoreExceptions:
          - org.springframework.web.client.HttpClientErrorException   # Ne pas retry 4xx

management:
  endpoints:
    web:
      exposure:
        include: health,info,retries,retryevents,metrics
  health:
    retries:
      enabled: true
```

```java
import io.github.resilience4j.retry.annotation.Retry;  // Package exact

@Service
public class InfinityRerankClient {

    private final RestClient restClient;

    @Retry(name = "infinityApi", fallbackMethod = "rerankFallback")
    public RerankResult rerank(String query, List<String> documents) {
        return restClient.post()
            .uri(infinityEndpoint + "/rerank")
            .body(new RerankRequest(query, documents))
            .retrieve()
            .body(RerankResult.class);
    }

    // Fallback: mêmes paramètres + Exception en dernier
    private RerankResult rerankFallback(String query, List<String> documents, Exception ex) {
        log.warn("Infinity rerank unavailable after retries: {}", ex.getMessage());
        throw new AlexandriaException(ErrorCategory.SERVICE_UNAVAILABLE,
            "Reranking service temporarily unavailable", ex);
    }
}
```

**Notes importantes:**
- `maxAttempts` = 4 tentatives totales (1 initial + 3 retries)
- Backoff exponentiel: 1s → 2s → 4s avec jitter ±10%
- `enableExponentialBackoff: true` et `enableRandomizedWait: true` sont **OBLIGATOIRES**
- `ResourceAccessException` capture les timeouts et erreurs de connexion (encapsule `SocketTimeoutException`)
- Pas besoin de `@EnableRetry` (c'est Spring Retry, pas Resilience4j)
- AOP auto-configuré avec `spring-boot-starter-aop`

### Timeout Budget (from research #19)

**Stratégie dual-tier:** Timeouts adaptés au cold start vs warm.

| Composant | Cold Start | Warm | Notes |
|-----------|------------|------|-------|
| **Global MCP request** | 90s | 30s | Total budget |
| **Embedding (BGE-M3)** | 30s | 5s | Cold start RunPod 10-30s |
| **Reranking** | 30s | 5s | Cold start RunPod 10-30s |
| **pgvector search** | 5s | 5s | HNSW: 5-50ms p95 |
| **Assembly** | 1s | 1s | Local, rapide |

**Formule budget:** `Global = Embed + Search + Rerank + Assembly + Buffer(10%)`

```yaml
# application.yml
alexandria:
  timeouts:
    global-cold-start: 90s      # Première requête après idle
    global-warm: 30s            # Requêtes suivantes
    embedding: 30s
    reranking: 30s
    database: 5s
    assembly: 1s

spring:
  mvc:
    async:
      request-timeout: 90000    # Timeout global Spring MVC (ms)
```

```java
package dev.alexandria.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import java.time.Duration;

/**
 * RestClient beans séparés par service avec timeouts dédiés.
 * JDK HttpClient pour compatibilité Virtual Threads.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClient embeddingRestClient(TimeoutProperties props) {
        return RestClient.builder()
            .baseUrl(props.getEmbeddingBaseUrl())
            .requestFactory(createJdkClientFactory(
                Duration.ofSeconds(5),  // Connect timeout
                props.getEmbedding()    // Read timeout
            ))
            .build();
    }

    @Bean
    public RestClient rerankRestClient(TimeoutProperties props) {
        return RestClient.builder()
            .baseUrl(props.getRerankBaseUrl())
            .requestFactory(createJdkClientFactory(
                Duration.ofSeconds(5),
                props.getReranking()
            ))
            .build();
    }

    /**
     * JDK HttpClient factory avec read timeout.
     * Note: JdkClientHttpRequestFactory supporte readTimeout depuis Spring 6.1.
     */
    private ClientHttpRequestFactory createJdkClientFactory(
            Duration connectTimeout, Duration readTimeout) {
        var httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .build();
        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);  // Spring 6.1+ - timeout par requête
        return factory;
    }
}
```

**Graceful Degradation (skip rerank):**

```java
/**
 * Skip reranking si le budget temps est insuffisant.
 */
public McpSearchResponse search(String query) {
    long startTime = System.currentTimeMillis();

    // 1. Embedding + Vector search (prioritaires)
    var embedding = embedAndSearch(query);
    var candidates = vectorSearch(embedding);

    long elapsed = System.currentTimeMillis() - startTime;
    long remainingBudget = timeoutProps.getGlobalWarm().toMillis() - elapsed;

    // 2. Skip rerank si < 5s restantes
    if (remainingBudget < 5000) {
        log.warn("Skipping rerank - insufficient time budget: {}ms remaining", remainingBudget);
        return buildResponse(candidates, RelevanceLevel.LOW,
            "Results not reranked due to time constraints");
    }

    // 3. Rerank si budget suffisant
    var reranked = rerank(query, candidates);
    return buildResponse(reranked, RelevanceLevel.HIGH, null);
}
```

**Reporté à v2:**
- Worker warming proactif (coût $0.50-2/day sur RunPod)

### RAG Pipeline Configuration

```yaml
# application.yml
rag:
  retrieval:
    top-k-initial: 50              # Candidats avant reranking (30-100)
    top-n-final: 5                 # Résultats après reranking (3-7)
    min-score: 0.3                 # Seuil minimum absolu (scores normalisés 0-1)
    score-threshold-type: relative # relative | absolute
    relative-threshold-ratio: 0.5  # 50% du meilleur score
    min-results-guarantee: 2       # Toujours retourner au moins 2 résultats

  reranking:
    enabled: true
    model: BAAI/bge-reranker-v2-m3
    normalize-scores: true         # OBLIGATOIRE - applique sigmoïde pour scores 0-1
```

```java
package dev.alexandria.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "rag")
@Validated
public class RagProperties {

    @Valid
    private RetrievalConfig retrieval = new RetrievalConfig();

    @Valid
    private RerankingConfig reranking = new RerankingConfig();

    // Getters/Setters...

    public static class RetrievalConfig {
        @Min(10) @Max(200)
        private int topKInitial = 50;

        @Min(1) @Max(20)
        private int topNFinal = 5;

        @DecimalMin("0.0") @DecimalMax("1.0")
        private double minScore = 0.3;

        private ThresholdType thresholdType = ThresholdType.RELATIVE;

        @DecimalMin("0.0") @DecimalMax("1.0")
        private double relativeThresholdRatio = 0.5;

        @Min(1) @Max(10)
        private int minResultsGuarantee = 2;

        // Getters/Setters...
    }

    public static class RerankingConfig {
        private boolean enabled = true;
        private String model = "BAAI/bge-reranker-v2-m3";
        private boolean normalizeScores = true;  // Toujours true pour scores 0-1

        // Getters/Setters...
    }

    public enum ThresholdType { ABSOLUTE, RELATIVE }
}
```

**Paramètres validés par benchmarks:**
- **Top-K=50**: Optimal nDCG@10 selon ZeroEntropy 2025, 90% du gain à 100 docs (Elastic Labs)
- **Top-N=5**: Consensus LlamaIndex/Langchain4j/Cohere, évite "Lost in the Middle"
- **Seuil relatif 50%**: Approche Vectara, plus robuste qu'un seuil absolu fixe
- **Garantie min 2**: UX - toujours retourner quelque chose même si scores faibles
- **normalize=true**: Obligatoire pour scores comparables (sigmoïde sur logits bruts)

### No-Results Handling & Response Schema

**Principe clé:** Ne jamais retourner une réponse vide sans explication. Différencier les 4 scénarios d'échec:

| Scénario | Cause | Message utilisateur | Action |
|----------|-------|---------------------|--------|
| Empty database | `count() == 0` | "Knowledge base not indexed yet" | Status système |
| No vector matches | Tous scores < seuil | "No documents match. Try rephrasing." | Log pour analyse corpus |
| Reranker filters all | Scores rerank < seuil | "Found related but not directly relevant" | Offrir résultats loosely related |
| Malformed query | < 3 chars ou stopwords | "Please provide more details" | Retour immédiat sans recherche |

```java
package dev.alexandria.core;

/**
 * Validation des requêtes avant recherche - évite les appels inutiles.
 */
public class QueryValidator {
    private static final int MIN_CHARS = 3;
    private static final int MIN_MEANINGFUL_TOKENS = 2;
    private static final Set<String> STOPWORDS = Set.of(
        "the", "a", "an", "is", "it", "to", "of", "and", "or", "in", "on", "at"
    );

    public ValidationResult validate(String query) {
        if (query == null || query.trim().length() < MIN_CHARS) {
            return ValidationResult.invalid(QueryProblem.TOO_SHORT,
                "Please provide at least 3 characters.");
        }

        String[] tokens = query.toLowerCase().split("\\s+");
        long meaningfulTokens = Arrays.stream(tokens)
            .filter(t -> t.length() > 2 && !STOPWORDS.contains(t))
            .count();

        if (meaningfulTokens < MIN_MEANINGFUL_TOKENS) {
            return ValidationResult.invalid(QueryProblem.TOO_VAGUE,
                "Your query needs more specific terms.");
        }

        return ValidationResult.valid();
    }

    public record ValidationResult(boolean valid, QueryProblem problem, String message) {
        public static ValidationResult valid() { return new ValidationResult(true, null, null); }
        public static ValidationResult invalid(QueryProblem p, String msg) { return new ValidationResult(false, p, msg); }
    }

    public enum QueryProblem { TOO_SHORT, TOO_VAGUE, EMPTY }
}
```

```java
package dev.alexandria.core;

/**
 * Réponse MCP structurée avec métadonnées de confiance.
 * Permet à Claude Code de différencier les scénarios d'échec.
 */
public record McpSearchResponse(
    List<SearchResult> results,
    SearchMetadata metadata
) {
    public record SearchResult(
        String content,
        String source,
        String section,           // Breadcrumb H1 > H2 > H3
        double relevanceScore,
        RelevanceLevel confidence
    ) {}

    public record SearchMetadata(
        SearchStatus status,
        String message,           // User-facing explanation (null si SUCCESS)
        int totalCandidates,      // Avant filtrage
        int returnedResults       // Après filtrage
    ) {}

    public enum SearchStatus { SUCCESS, PARTIAL, NO_RESULTS, ERROR }
    public enum RelevanceLevel { HIGH, MEDIUM, LOW }

    // Factory methods
    public static McpSearchResponse success(List<SearchResult> results, int candidates) {
        return new McpSearchResponse(results,
            new SearchMetadata(SearchStatus.SUCCESS, null, candidates, results.size()));
    }

    public static McpSearchResponse partial(List<SearchResult> results, int candidates, String caveat) {
        return new McpSearchResponse(results,
            new SearchMetadata(SearchStatus.PARTIAL, caveat, candidates, results.size()));
    }

    public static McpSearchResponse noResults(String message) {
        return new McpSearchResponse(List.of(),
            new SearchMetadata(SearchStatus.NO_RESULTS, message, 0, 0));
    }

    public static McpSearchResponse error(String message) {
        return new McpSearchResponse(List.of(),
            new SearchMetadata(SearchStatus.ERROR, message, 0, 0));
    }
}
```

```java
/**
 * Logique tiered response dans RetrievalService.
 * Seuils de confiance pour classification des résultats.
 */
public class RetrievalService {
    // Seuils de confiance (scores normalisés 0-1 après reranking)
    private static final double THRESHOLD_HIGH = 0.7;    // HIGH confidence
    private static final double THRESHOLD_MEDIUM = 0.4;  // MEDIUM confidence
    private static final double THRESHOLD_LOW = 0.2;     // LOW (fallback vector)

    public McpSearchResponse search(String query) {
        // 1. Validation
        var validation = queryValidator.validate(query);
        if (!validation.valid()) {
            return McpSearchResponse.error(validation.message());
        }

        // 2. Check empty database
        if (embeddingStore.count() == 0) {
            return McpSearchResponse.noResults(
                "The knowledge base is empty. Documents need to be indexed first.");
        }

        // 3. Vector search + Reranking
        var candidates = vectorSearch(query, ragProperties.getRetrieval().getTopKInitial());
        var reranked = rerank(query, candidates);

        // 4. Tiered response
        var highConfidence = filter(reranked, THRESHOLD_HIGH);
        if (highConfidence.size() >= 2) {
            return McpSearchResponse.success(toResults(highConfidence, RelevanceLevel.HIGH), candidates.size());
        }

        var mediumConfidence = filter(reranked, THRESHOLD_MEDIUM);
        if (!mediumConfidence.isEmpty()) {
            return McpSearchResponse.partial(toResults(mediumConfidence, RelevanceLevel.MEDIUM),
                candidates.size(), "Results may be loosely related to your query.");
        }

        // 5. Fallback: raw vector results if reranker too aggressive
        if (!candidates.isEmpty() && candidates.get(0).score() > 0.6) {
            var fallback = candidates.subList(0, Math.min(3, candidates.size()));
            return McpSearchResponse.partial(toResults(fallback, RelevanceLevel.LOW),
                candidates.size(), "Found related content, though it may not directly answer your question.");
        }

        return McpSearchResponse.noResults(
            "No relevant documentation found. Try rephrasing with different keywords.");
    }
}
```

**Notes:**
- `THRESHOLD_HIGH/MEDIUM/LOW` sont des constantes, pas dans `RagProperties` (rarement modifiées)
- Fallback sur vector brut si `score > 0.6` évite les faux négatifs du reranker
- Status `PARTIAL` permet à Claude de nuancer sa réponse

**Reporté à v2:**
- Hybrid search (BM25 + vector) via `ts_vector` PostgreSQL
- Query suggestions basées sur similarité corpus

### MCP Response Format (from research #6)

**Principe:** Dual-format response - JSON structuré + Markdown lisible. Limite 8000 tokens par réponse (sous le warning 10K de Claude Code).

```java
package dev.alexandria.adapters;

import org.springframework.ai.mcp.spec.McpSchema.CallToolResult;
import org.springframework.ai.mcp.spec.McpSchema.TextContent;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Helper pour formater les réponses MCP avec dual-format.
 * Inclut metadata inline pour Claude + structured pour parsing.
 */
public class McpResponseFormatter {
    private static final int MAX_TOKENS = 8000;
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Format dual: JSON structuré + Markdown lisible.
     */
    public static CallToolResult formatSearchResults(McpSearchResponse response) {
        var builder = CallToolResult.builder();

        // 1. Structured JSON content (pour parsing programmatique)
        try {
            builder.addTextContent(mapper.writeValueAsString(response));
        } catch (Exception e) {
            // Fallback si JSON échoue
        }

        // 2. Markdown content (pour Claude et lisibilité humaine)
        StringBuilder md = new StringBuilder();

        if (response.metadata().status() == SearchStatus.SUCCESS
            || response.metadata().status() == SearchStatus.PARTIAL) {

            md.append("## Search Results\n\n");
            if (response.metadata().message() != null) {
                md.append("_").append(response.metadata().message()).append("_\n\n");
            }

            int tokenBudget = MAX_TOKENS - 500; // Reserve for metadata
            int usedTokens = 0;

            for (var result : response.results()) {
                String entry = formatResultEntry(result);
                int entryTokens = estimateTokens(entry);

                if (usedTokens + entryTokens > tokenBudget) {
                    md.append("\n---\n_[Content truncated. ")
                      .append(response.results().size() - response.results().indexOf(result))
                      .append(" more results available]_\n");
                    break;
                }

                md.append(entry);
                usedTokens += entryTokens;
            }

            md.append("\n---\n")
              .append("_Found ").append(response.metadata().returnedResults())
              .append(" of ").append(response.metadata().totalCandidates())
              .append(" candidates_\n");
        } else {
            md.append("## No Results\n\n")
              .append(response.metadata().message());
        }

        builder.addTextContent(md.toString());
        return builder.build();
    }

    private static String formatResultEntry(SearchResult result) {
        return String.format("""
            ### %s
            **Source:** `%s`
            **Relevance:** %s (%.2f)

            %s

            """,
            result.section(),
            result.source(),
            result.confidence(),
            result.relevanceScore(),
            result.content()
        );
    }

    private static int estimateTokens(String text) {
        // Approximation: ~4 chars per token
        return text.length() / 4;
    }

    /**
     * Format erreur avec pattern isError: true.
     */
    public static CallToolResult errorResult(String message, ErrorCategory category) {
        return CallToolResult.builder()
            .isError(true)
            .addTextContent(String.format("""
                ## Error: %s

                **Problem:** %s

                **Suggested action:** %s
                """,
                category.title(),
                message,
                category.suggestedAction()))
            .build();
    }
}
```

**Notes:**
- `isError: true` → Visible par le LLM, permet de différencier erreurs métier
- Truncation explicite avec compteur de résultats restants
- Metadata inline (score, source) pour contexte immédiat

### Error Handling (from research #7)

**Principe:** Hiérarchie d'exceptions métier avec mapping vers réponses MCP.

```java
package dev.alexandria.core;

/**
 * Exception racine Alexandria avec catégorie et message actionnable.
 */
public class AlexandriaException extends RuntimeException {
    private final ErrorCategory category;

    public AlexandriaException(ErrorCategory category, String message) {
        super(message);
        this.category = category;
    }

    public AlexandriaException(ErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public ErrorCategory getCategory() { return category; }
}

/**
 * Catégories d'erreurs avec messages user-facing.
 */
public enum ErrorCategory {
    VALIDATION("Validation Error", "Check your query and try again"),
    NOT_FOUND("Not Found", "The requested resource doesn't exist"),
    SERVICE_UNAVAILABLE("Service Unavailable", "External service is temporarily unavailable. Retry in a few seconds"),
    INGESTION_FAILED("Ingestion Failed", "Document could not be processed. Check format and try again"),
    DATABASE_ERROR("Database Error", "Storage operation failed. Contact administrator if persists"),
    TIMEOUT("Timeout", "Operation took too long. Try with a simpler query or smaller document");

    private final String title;
    private final String suggestedAction;

    ErrorCategory(String title, String suggestedAction) {
        this.title = title;
        this.suggestedAction = suggestedAction;
    }

    public String title() { return title; }
    public String suggestedAction() { return suggestedAction; }
}

// Exceptions spécialisées
public class DocumentNotFoundException extends AlexandriaException {
    public DocumentNotFoundException(String documentId) {
        super(ErrorCategory.NOT_FOUND, "Document not found: " + documentId);
    }
}

public class QueryValidationException extends AlexandriaException {
    public QueryValidationException(String message) {
        super(ErrorCategory.VALIDATION, message);
    }
}

public class EmbeddingServiceException extends AlexandriaException {
    public EmbeddingServiceException(String message, Throwable cause) {
        super(ErrorCategory.SERVICE_UNAVAILABLE, message, cause);
    }
}

public class IngestionException extends AlexandriaException {
    public IngestionException(String message, Throwable cause) {
        super(ErrorCategory.INGESTION_FAILED, message, cause);
    }
}
```

**Mapping Exception → Réponse MCP:**

| Exception | isError | Message Pattern |
|-----------|---------|-----------------|
| QueryValidationException | true | "Validation: {message}. Try again." |
| DocumentNotFoundException | true | "Not found: {id}. Check document list." |
| EmbeddingServiceException | true | "Embedding service unavailable. Retry." |
| TimeoutException | true | "Timeout. Simplify query or retry." |
| Other RuntimeException | true | "Unexpected error. Contact admin." |

```java
// Dans McpTools.java
@McpTool(name = "search_documents", description = "Search documentation")
public CallToolResult searchDocuments(@McpToolParam String query) {
    try {
        var response = retrievalService.search(query);
        return McpResponseFormatter.formatSearchResults(response);
    } catch (AlexandriaException e) {
        log.warn("Search failed: {}", e.getMessage());
        return McpResponseFormatter.errorResult(e.getMessage(), e.getCategory());
    } catch (Exception e) {
        log.error("Unexpected error in search", e);
        return McpResponseFormatter.errorResult(
            "An unexpected error occurred", ErrorCategory.DATABASE_ERROR);
    }
}
```

### Ingestion Strategy (from research #11)

**Architecture hybride:** CLI pour bulk ingestion + MCP tool pour usage léger.

| Mode | Usage | Limite | Timeout |
|------|-------|--------|---------|
| **CLI Picocli** | Bulk ingestion (dossiers) | Illimité | N/A |
| **MCP tool** | 1-5 documents à la fois | 5 docs max | 60s |

**Pas de watcher répertoire** - Complexité évitée (race conditions, locks, battery drain).

```java
package dev.alexandria.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.springframework.stereotype.Component;

/**
 * CLI Picocli pour ingestion bulk.
 * Usage: java -jar alexandria.jar ingest /path/to/docs --recursive
 */
@Component
@Command(name = "ingest", mixinStandardHelpOptions = true,
         description = "Ingest documents into the knowledge base")
public class IngestCommand implements Runnable {

    @Parameters(index = "0", description = "Path to file or directory")
    private Path path;

    @Option(names = {"-r", "--recursive"}, description = "Process subdirectories")
    private boolean recursive = false;

    @Option(names = {"-b", "--batch-size"}, description = "Documents per batch")
    private int batchSize = 25;  // Optimal pour hardware limité (4 cores)

    @Option(names = {"--dry-run"}, description = "Show what would be ingested")
    private boolean dryRun = false;

    private final IngestionService ingestionService;

    @Override
    public void run() {
        List<Path> files = collectFiles(path, recursive);

        if (dryRun) {
            files.forEach(f -> System.out.println("Would ingest: " + f));
            return;
        }

        // Batch processing avec progress
        Lists.partition(files, batchSize).forEach(batch -> {
            System.out.printf("Processing batch: %d files...%n", batch.size());
            ingestionService.ingestBatch(batch);
        });

        System.out.printf("Ingested %d documents successfully.%n", files.size());
    }

    private List<Path> collectFiles(Path path, boolean recursive) {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }

        try (var stream = recursive
                ? Files.walk(path)
                : Files.list(path)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isSupportedFormat)
                .toList();
        } catch (IOException e) {
            throw new IngestionException("Failed to scan directory", e);
        }
    }

    private boolean isSupportedFormat(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".txt")
            || name.equals("llms.txt") || name.equals("llms-full.txt")
            || name.endsWith(".html");
    }
}
```

**MCP Tool limité:**

```java
@McpTool(name = "ingest_document",
         description = "Ingest a single document into the knowledge base")
public CallToolResult ingestDocument(
        @McpToolParam(description = "File path") String filePath,
        McpSyncRequestContext context) {

    Path path = Path.of(filePath);

    // Validation: max 5 documents via MCP
    if (Files.isDirectory(path)) {
        try (var files = Files.list(path)) {
            if (files.count() > 5) {
                return McpResponseFormatter.errorResult(
                    "Directory contains more than 5 files. Use CLI for bulk ingestion.",
                    ErrorCategory.VALIDATION);
            }
        }
    }

    try {
        context.progress(0.1, "Validating document...");
        ingestionService.validateDocument(path);

        context.progress(0.3, "Parsing document...");
        var document = parseDocument(path);

        context.progress(0.5, "Chunking...");
        var chunks = splitter.split(document);

        context.progress(0.7, "Generating embeddings...");
        ingestionService.embedAndStore(chunks);

        context.progress(1.0, "Done!");

        return CallToolResult.builder()
            .addTextContent(String.format(
                "Successfully ingested: %s (%d chunks)",
                path.getFileName(), chunks.size()))
            .build();

    } catch (AlexandriaException e) {
        return McpResponseFormatter.errorResult(e.getMessage(), e.getCategory());
    }
}
```

**Document Formats supportés (from research #13):**

| Format | Parser | Extensions |
|--------|--------|------------|
| Markdown | TextDocumentParser + AlexandriaMarkdownSplitter | `.md` |
| Text | TextDocumentParser (Langchain4j) | `.txt` |
| llms.txt | LlmsTxtParser (custom) | `llms.txt`, `llms-full.txt` |
| HTML | JsoupDocumentParser (Langchain4j) | `.html`, `.htm` |

**Note:** CommonMark-java 0.27.0 + GFM est utilisé par `AlexandriaMarkdownSplitter` pour le parsing structurel (headers, code blocks, tables), pas comme DocumentParser Langchain4j.

```java
/**
 * Détection format par extension (simple switch, pas SPI).
 * Note: Langchain4j n'a pas de MarkdownDocumentParser - on utilise TextDocumentParser
 * puis AlexandriaMarkdownSplitter pour le chunking markdown-aware.
 *
 * ATTENTION: llms.txt utilise LlmsTxtParser (classe standalone, pas DocumentParser).
 * Traitement séparé dans IngestionService.ingestLlmsTxt().
 */
public DocumentParser getParser(Path file) {
    String name = file.getFileName().toString().toLowerCase();

    if (name.endsWith(".md")) {
        return new TextDocumentParser();  // Parsing brut, chunking via AlexandriaMarkdownSplitter
    } else if (name.endsWith(".html") || name.endsWith(".htm")) {
        return new JsoupDocumentParser(); // Langchain4j natif
    } else if (name.endsWith(".txt") && !name.startsWith("llms")) {
        return new TextDocumentParser();  // Langchain4j natif
    }

    // llms.txt / llms-full.txt → traitement séparé (pas DocumentParser)
    if (name.equals("llms.txt") || name.equals("llms-full.txt")) {
        return null;  // Signal pour IngestionService d'utiliser LlmsTxtParser
    }

    throw new IngestionException("Unsupported format: " + name, null);
}
```

**LlmsTxtParser (from research #14):**

```java
package dev.alexandria.core;

import java.util.regex.*;

/**
 * Parser pour le format llms.txt (spec llmstxt.org).
 * NOTE: Classe standalone, pas un DocumentParser Langchain4j.
 * Retourne LlmsTxtDocument (structure riche) pour traitement custom dans IngestionService.
 */
public class LlmsTxtParser {

    private static final Pattern TITLE_PATTERN =
        Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern BLOCKQUOTE_PATTERN =
        Pattern.compile("^>\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern SECTION_PATTERN =
        Pattern.compile("^##\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern LINK_PATTERN =
        Pattern.compile("^-\\s+\\[([^\\]]+)\\]\\(([^)]+)\\)(?::\\s*(.*))?$", Pattern.MULTILINE);

    public LlmsTxtDocument parse(String content) {
        // Extraire titre (premier H1)
        Matcher titleMatcher = TITLE_PATTERN.matcher(content);
        String title = titleMatcher.find() ? titleMatcher.group(1) : "Untitled";

        // Extraire description (premier blockquote)
        Matcher descMatcher = BLOCKQUOTE_PATTERN.matcher(content);
        String description = descMatcher.find() ? descMatcher.group(1) : "";

        // Parser les sections H2
        List<LlmsTxtSection> sections = new ArrayList<>();
        Matcher sectionMatcher = SECTION_PATTERN.matcher(content);

        while (sectionMatcher.find()) {
            String sectionName = sectionMatcher.group(1);
            boolean isOptional = sectionName.toLowerCase().contains("optional");

            // Trouver les liens dans cette section
            int sectionStart = sectionMatcher.end();
            int sectionEnd = findNextSectionOrEnd(content, sectionStart);
            String sectionContent = content.substring(sectionStart, sectionEnd);

            List<LlmsTxtLink> links = parseLinks(sectionContent);
            sections.add(new LlmsTxtSection(sectionName, isOptional, links));
        }

        return new LlmsTxtDocument(title, description, sections);
    }

    private List<LlmsTxtLink> parseLinks(String content) {
        List<LlmsTxtLink> links = new ArrayList<>();
        Matcher linkMatcher = LINK_PATTERN.matcher(content);

        while (linkMatcher.find()) {
            links.add(new LlmsTxtLink(
                linkMatcher.group(1),  // title
                linkMatcher.group(2),  // url
                linkMatcher.group(3)   // description (nullable)
            ));
        }
        return links;
    }

    public record LlmsTxtDocument(
        String title,
        String description,
        List<LlmsTxtSection> sections
    ) {}

    public record LlmsTxtSection(
        String name,
        boolean optional,
        List<LlmsTxtLink> links
    ) {}

    public record LlmsTxtLink(
        String title,
        String url,
        String description
    ) {}
}
```

**Reporté à v2:**
- Détection doublons par content_hash
- Rate limiter Resilience4j pour API externe

### Document Update Strategy (from research #12)

**Principe:** Identification hybride (chemin + hash SHA-256) avec pattern DELETE + INSERT transactionnel.

**Stratégie d'identification:**

| Stratégie | Déduplication | Détection changements | Traçabilité | Renommage |
|-----------|--------------|----------------------|-------------|-----------|
| Chemin seul | ❌ Faible | ❌ Non | ✅ Excellente | ❌ Doublons |
| Hash seul | ✅ Parfaite | ✅ Automatique | ❌ Perdue | ✅ OK |
| **Hybride** | ✅ Parfaite | ✅ Automatique | ✅ Complète | ✅ OK |

**Choix Alexandria:** Hybride avec `sourceUri` (chemin logique) + `documentHash` (SHA-256).

**Pattern DELETE + INSERT transactionnel:**

```java
package dev.alexandria.core;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Service de mise à jour des documents avec détection de changements.
 * Pattern: DELETE anciens chunks → INSERT nouveaux (atomique par document).
 */
@Service
public class DocumentUpdateService {

    private final PgVectorEmbeddingStore embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final AlexandriaMarkdownSplitter splitter;

    /**
     * Algorithme de détection de changement en deux phases.
     * Phase 1: Fast path (mtime + size) - évite recalcul hash si inchangé.
     * Phase 2: Hash SHA-256 pour confirmation.
     */
    public UpdateResult processDocument(Path filePath, Path basePath) throws IOException {
        String sourceUri = ChunkMetadata.toLogicalUri(filePath, basePath);
        String content = Files.readString(filePath);
        String documentHash = ChunkMetadata.computeHash(content);

        // Phase 1: Vérification rapide via métadonnées fichier
        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
        Optional<StoredDocumentInfo> stored = getStoredDocumentInfo(sourceUri);

        if (stored.isPresent()) {
            StoredDocumentInfo info = stored.get();
            // Fast path: si taille et date identiques, probablement inchangé
            if (info.fileSize() == attrs.size()
                && info.fileModifiedAt().equals(attrs.lastModifiedTime().toInstant())) {
                // Phase 2: Validation par hash
                if (documentHash.equals(info.documentHash())) {
                    return UpdateResult.NO_CHANGE;
                }
            }
        }

        // Document nouveau ou modifié : procéder à l'ingestion
        return ingestDocument(sourceUri, content, documentHash, attrs);
    }

    private UpdateResult ingestDocument(String sourceUri, String content,
                                        String documentHash, BasicFileAttributes attrs) {
        // 1. Supprimer tous les anciens chunks du document
        Filter documentFilter = metadataKey("sourceUri").isEqualTo(sourceUri);
        embeddingStore.removeAll(documentFilter);

        // 2. Découper le document en chunks
        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);

        // 3. Préparer les segments avec métadonnées enrichies
        List<TextSegment> enrichedSegments = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);

            Metadata metadata = new Metadata()
                .put("sourceUri", sourceUri)
                .put("documentHash", documentHash)
                .put("chunkIndex", i)
                .put("contentHash", ChunkMetadata.computeHash(segment.text()))
                .put("createdAt", Instant.now().toEpochMilli())
                .put("documentType", detectDocType(sourceUri));

            enrichedSegments.add(TextSegment.from(segment.text(), metadata));
        }

        // 4. Générer embeddings et insérer
        List<Embedding> embeddings = embeddingModel.embedAll(
            enrichedSegments.stream().map(TextSegment::text).toList()
        ).content();

        embeddingStore.addAll(embeddings, enrichedSegments);

        return stored.isPresent() ? UpdateResult.UPDATED : UpdateResult.CREATED;
    }

    /**
     * Suppression explicite d'un document (hard delete).
     */
    public void deleteDocument(String sourceUri) {
        Filter filter = metadataKey("sourceUri").isEqualTo(sourceUri);
        embeddingStore.removeAll(filter);
    }

    public enum UpdateResult { NO_CHANGE, CREATED, UPDATED }

    private record StoredDocumentInfo(String documentHash, long fileSize, Instant fileModifiedAt) {}
}
```

**Scénarios gérés:**

| Scénario | Détection | Action |
|----------|-----------|--------|
| Fichier modifié | Hash différent | DELETE + INSERT |
| Fichier renommé | Même hash, URI différent | Détectable (future v2) |
| Fichier supprimé | URI absent du filesystem | `deleteDocument()` |
| Ré-ingestion accidentelle | Même hash | `NO_CHANGE` (skip) |

**Reporté à v2:**
- Soft delete avec colonne `is_active`
- Garbage collection automatique des chunks orphelins
- Détection de renommage par hash (même contenu, nouveau chemin)

### Files to Reference

| File | Purpose |
| ---- | ------- |
| `docs/research/02-mcp/spring-ai-mcp-tool-annotations.md` | Guide @McpTool annotations |
| `docs/research/02-mcp/spring-ai-mcp-sdk-1.1.2-guide.md` | Config MCP HTTP Streamable |
| `docs/research/01-stack-validation/langchain4j-spring-boot-compatibility.md` | Compatibilité Langchain4j/Boot 3.5 |
| `docs/research/03-rag-pipeline/langchain4j-document-splitter.md` | Custom splitter pattern |
| `docs/research/03-rag-pipeline/langchain4j-infinity-integration.md` | Intégration Infinity |
| `docs/research/03-rag-pipeline/infinity-reranking-api.md` | API reranking détaillée |
| `docs/research/04-database/postgresql-pgvector-schema.md` | Schéma DB complet |
| `docs/research/07-testing/testcontainers-pgvector.md` | Config tests |
| `docs/research/05-ingestion/llms-txt-specification.md` | Spec llms.txt parser |
| `docs/research/02-mcp/claude-code-mcp-configuration.md` | Config client Claude Code (à adapter pour HTTP Streamable) |
| `docs/research/01-stack-validation/comprehensive-stack-validation.md` | Validation complète stack janvier 2026 |

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
    <resilience4j.version>2.3.0</resilience4j.version>
    <langchain4j.version>1.10.0</langchain4j.version>
    <langchain4j-spring.version>1.10.0-beta18</langchain4j-spring.version>
    <postgresql.version>42.7.4</postgresql.version>
    <testcontainers.version>2.0.3</testcontainers.version>
    <wiremock.version>3.13.2</wiremock.version>
    <picocli.version>4.7.7</picocli.version>
    <commonmark.version>0.27.0</commonmark.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Resilience4j BOM -->
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-bom</artifactId>
            <version>${resilience4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
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

    <!-- Spring AI MCP SDK (HTTP Streamable transport) - GA -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>

    <!-- Resilience4j pour @Retry (version via BOM) -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
    </dependency>
    <!-- OBLIGATOIRE pour annotations @Retry -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
    <!-- Métriques et endpoints /actuator/retries -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
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

    <!-- Langchain4j pgvector (BETA - version explicite requise, pas dans BOM GA) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-pgvector</artifactId>
        <version>${langchain4j-spring.version}</version>
    </dependency>

    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>${postgresql.version}</version>
        <scope>runtime</scope>
    </dependency>

    <!-- Picocli pour CLI ingestion -->
    <dependency>
        <groupId>info.picocli</groupId>
        <artifactId>picocli-spring-boot-starter</artifactId>
        <version>${picocli.version}</version>
    </dependency>

    <!-- CommonMark pour parsing Markdown -->
    <dependency>
        <groupId>org.commonmark</groupId>
        <artifactId>commonmark</artifactId>
        <version>${commonmark.version}</version>
    </dependency>
    <dependency>
        <groupId>org.commonmark</groupId>
        <artifactId>commonmark-ext-gfm-tables</artifactId>
        <version>${commonmark.version}</version>
    </dependency>
    <dependency>
        <groupId>org.commonmark</groupId>
        <artifactId>commonmark-ext-yaml-front-matter</artifactId>
        <version>${commonmark.version}</version>
    </dependency>

    <!-- Test dependencies -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-postgresql</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.wiremock</groupId>
        <artifactId>wiremock</artifactId>
        <version>${wiremock.version}</version>
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
- Resilience4j 2.3.0 = Compatible Virtual Threads, @Retry annotations
- PostgreSQL JDBC 42.7.4 = Dernière version stable
- Testcontainers 2.0.3 = Attention: préfixes modules changés depuis 1.x
- WireMock 3.13.2 = Dernière version (3.10.0 avait 3 versions de retard)

**Note importante pgvector:**
- Langchain4j crée un index **IVFFlat** par défaut, pas HNSW
- **Créer l'index HNSW manuellement** pour de meilleures performances (voir section pgvector Configuration)

### Testing Strategy (from research #21-22)

**Pyramide de tests:** 70% unitaires / 20% intégration / 10% E2E

| Niveau | Outils | Couverture |
|--------|--------|------------|
| **Unit** | JUnit 5, Mockito | Services, splitter, parsers |
| **Integration** | Testcontainers, WireMock | pgvector, Infinity API |
| **E2E** | McpSyncClient | Flow MCP complet |

**PgVectorTestConfiguration:**

```java
package dev.alexandria.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
// NOTE: Testcontainers 2.x - package changé (plus org.testcontainers.containers.*)
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Configuration réutilisable pour tests avec pgvector.
 */
@TestConfiguration
public class PgVectorTestConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("pgvector/pgvector:0.8.1-pg18")
            .withDatabaseName("alexandria_test")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres",
                "-c", "shared_preload_libraries=vector",
                "-c", "max_connections=20");
    }
}
```

**WireMock stubs Infinity (embeddings + rerank):**

```java
package dev.alexandria.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 * Stubs WireMock pour Infinity API avec simulation cold start.
 */
public class InfinityStubs {

    /**
     * Stub embeddings avec réponse normale.
     */
    public static void stubEmbeddings(WireMockServer server, float[] embedding) {
        server.stubFor(post(urlPathEqualTo("/v1/embeddings"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(embeddingsResponse(embedding))));
    }

    /**
     * Stub cold start avec délai log-normal (simule wake-up RunPod).
     */
    public static void stubColdStart(WireMockServer server, float[] embedding) {
        server.stubFor(post(urlPathEqualTo("/v1/embeddings"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withLogNormalRandomDelay(2000, 0.5)  // ~2s median, variance 0.5
                .withBody(embeddingsResponse(embedding))));
    }

    /**
     * Stub rerank.
     */
    public static void stubRerank(WireMockServer server, double... scores) {
        server.stubFor(post(urlPathEqualTo("/rerank"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(rerankResponse(scores))));
    }

    /**
     * Stub erreur 503 puis success (pour tester retry).
     */
    public static void stubRetryScenario(WireMockServer server, float[] embedding) {
        // Premier appel: 503
        server.stubFor(post(urlPathEqualTo("/v1/embeddings"))
            .inScenario("retry")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("failed_once"));

        // Deuxième appel: success
        server.stubFor(post(urlPathEqualTo("/v1/embeddings"))
            .inScenario("retry")
            .whenScenarioStateIs("failed_once")
            .willReturn(aResponse()
                .withStatus(200)
                .withBody(embeddingsResponse(embedding))));
    }

    private static String embeddingsResponse(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");

        return String.format("""
            {
              "data": [{"embedding": %s, "index": 0}],
              "model": "BAAI/bge-m3",
              "usage": {"prompt_tokens": 10, "total_tokens": 10}
            }
            """, sb);
    }

    private static String rerankResponse(double... scores) {
        StringBuilder results = new StringBuilder();
        for (int i = 0; i < scores.length; i++) {
            if (i > 0) results.append(",");
            results.append(String.format(
                "{\"index\": %d, \"relevance_score\": %f}", i, scores[i]));
        }
        return String.format("{\"results\": [%s]}", results);
    }
}
```

**EmbeddingFixtures (vecteurs normalisés 1024D):**

```java
/**
 * Génère des embeddings 1024D déterministes pour tests.
 */
public class EmbeddingFixtures {

    public static float[] generate(long seed) {
        Random random = new Random(seed);
        float[] embedding = new float[1024];

        double sumSquares = 0;
        for (int i = 0; i < 1024; i++) {
            embedding[i] = (float) random.nextGaussian();
            sumSquares += embedding[i] * embedding[i];
        }

        // Normaliser (L2 norm = 1)
        float norm = (float) Math.sqrt(sumSquares);
        for (int i = 0; i < 1024; i++) {
            embedding[i] /= norm;
        }

        return embedding;
    }

    public static float[] similar(float[] base, float similarity) {
        // Génère un vecteur avec cosine similarity ~= similarity
        float[] result = base.clone();
        Random random = new Random();

        float noise = 1 - similarity;
        for (int i = 0; i < result.length; i++) {
            result[i] += (float) (random.nextGaussian() * noise);
        }

        // Re-normaliser
        return normalize(result);
    }
}
```

**Test Resilience4j retry:**

```java
@SpringBootTest
@AutoConfigureWireMock(port = 0)
class InfinityClientRetryTest {

    @Autowired
    private InfinityEmbeddingModel embeddingModel;

    @Autowired
    private WireMockServer wireMock;

    @Test
    @DisplayName("Should retry on 503 and succeed on second attempt")
    void shouldRetryOnServiceUnavailable() {
        float[] embedding = EmbeddingFixtures.generate(42L);
        InfinityStubs.stubRetryScenario(wireMock, embedding);

        var result = embeddingModel.embed("test query");

        assertThat(result).hasSize(1024);
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/v1/embeddings")));
    }
}
```

**Test McpSyncClient E2E:**

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class McpToolsE2ETest {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("Should search documents via MCP")
    void shouldSearchViaMcp() {
        McpSyncClient client = McpTestSupport.createClient(port);

        try {
            client.initialize();

            var tools = client.listTools();
            assertThat(tools.tools())
                .extracting(Tool::name)
                .contains("search_documents", "ingest_document");

            var result = client.callTool(new CallToolRequest(
                "search_documents",
                Map.of("query", "how to configure PostgreSQL")));

            assertThat(result.isError()).isFalse();
            assertThat(result.content()).isNotEmpty();

        } finally {
            client.closeGracefully();
        }
    }
}

/**
 * Helper pour créer un client MCP de test (HTTP Streamable).
 * Utilise MCP Java SDK 0.17.0 (inclus via Spring AI 1.1.2).
 */
public class McpTestSupport {

    public static McpSyncClient createClient(int port) {
        // HTTP Streamable transport - classe du MCP Java SDK
        // Package: io.modelcontextprotocol.client.transport
        var transport = HttpClientStreamableHttpTransport
            .builder("http://localhost:" + port)
            .endpoint("/mcp")
            .build();

        return McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(30))
            .clientInfo(new McpSchema.Implementation("alexandria-test", "1.0.0"))
            .build();
    }
}
```

**Reporté à v2:**
- Retrieval quality metrics CI/CD (P@5, R@10, MRR)
- Claude Code headless E2E tests
- MCP Inspector validation

### Logging & Observability (from research #22)

**Trois piliers:** Logs structurés ECS, HealthIndicators custom, métriques latence pipeline.

**Structure projet (ajouts):**
```
src/main/java/dev/alexandria/
├── config/
│   └── VirtualThreadConfig.java       # TaskDecorator MDC propagation
├── filter/
│   └── CorrelationIdFilter.java       # X-Correlation-Id + MDC
├── health/
│   ├── InfinityEmbeddingHealthIndicator.java
│   ├── RerankingHealthIndicator.java  # Simplifié: /health (pas /rerank)
│   └── PgVectorHealthIndicator.java
└── ...

src/main/resources/
└── logback-spring.xml                 # ECS structuré + profils dev/prod
```

**logback-spring.xml (ECS natif Spring Boot 3.4+):**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true" scanPeriod="30 seconds">

    <springProperty scope="context" name="APP_NAME" source="spring.application.name" defaultValue="alexandria"/>

    <!-- PROFIL DEV - Console lisible -->
    <springProfile name="dev,local,default">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%clr(%d{HH:mm:ss.SSS}){faint} %clr(%5p) %clr([%X{correlationId:-NO_CID}]){yellow} %clr(%-40.40logger{39}){cyan} : %m%n%wEx</pattern>
            </encoder>
        </appender>

        <root level="INFO"><appender-ref ref="CONSOLE"/></root>
        <logger name="dev.alexandria" level="DEBUG"/>
        <logger name="dev.langchain4j" level="DEBUG"/>
    </springProfile>

    <!-- PROFIL PROD - JSON ECS -->
    <springProfile name="prod,production">
        <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="org.springframework.boot.logging.logback.StructuredLogEncoder">
                <format>ecs</format>
            </encoder>
        </appender>

        <appender name="ASYNC_JSON" class="ch.qos.logback.classic.AsyncAppender">
            <queueSize>1024</queueSize>
            <discardingThreshold>0</discardingThreshold>
            <appender-ref ref="JSON_CONSOLE"/>
        </appender>

        <root level="INFO"><appender-ref ref="ASYNC_JSON"/></root>
        <logger name="dev.alexandria" level="INFO"/>
        <logger name="dev.langchain4j" level="WARN"/>
        <logger name="org.springframework" level="WARN"/>
        <logger name="com.zaxxer.hikari" level="WARN"/>
    </springProfile>
</configuration>
```

**CorrelationIdFilter:**

```java
package dev.alexandria.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        var httpRequest = (HttpServletRequest) request;
        var httpResponse = (HttpServletResponse) response;

        try {
            String correlationId = Optional
                .ofNullable(httpRequest.getHeader(CORRELATION_ID_HEADER))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> "CID-" + UUID.randomUUID().toString().substring(0, 16));

            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}
```

**VirtualThreadConfig (MDC propagation):**

```java
package dev.alexandria.config;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.TaskExecutorAdapter;
import java.util.concurrent.Executors;

@Configuration
public class VirtualThreadConfig {

    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor asyncTaskExecutor() {
        var executor = new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
        executor.setTaskDecorator(mdcPropagatingDecorator());
        return executor;
    }

    @Bean
    public TaskDecorator mdcPropagatingDecorator() {
        return runnable -> {
            var contextMap = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (contextMap != null) MDC.setContextMap(contextMap);
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
```

**HealthIndicators:**

```java
// InfinityEmbeddingHealthIndicator - vérifie /health endpoint
@Component("infinity")
public class InfinityEmbeddingHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            var response = restClient.get().uri("/health").retrieve().toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful()
                ? Health.up().withDetail("service", "Infinity Embedding").build()
                : Health.down().withDetail("status", response.getStatusCode()).build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}

// RerankingHealthIndicator - simplifié (pas de /rerank pour éviter coûts RunPod)
@Component("reranking")
public class RerankingHealthIndicator implements HealthIndicator {
    // Même pattern que ci-dessus, appel /health seulement
}

// PgVectorHealthIndicator - vérifie extension + table + index
@Component("pgvector")
public class PgVectorHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            var builder = Health.up();

            // 1. Vérifier extension pgvector
            try (var stmt = conn.createStatement()) {
                var rs = stmt.executeQuery(
                    "SELECT extversion FROM pg_extension WHERE extname = 'vector'");
                if (rs.next()) {
                    builder.withDetail("pgvectorVersion", rs.getString(1));
                } else {
                    return Health.down()
                        .withDetail("error", "pgvector extension not installed").build();
                }
            }

            // 2. Vérifier table + count
            try (var stmt = conn.createStatement()) {
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM document_embeddings");
                if (rs.next()) builder.withDetail("vectorCount", rs.getLong(1));
            }

            // 3. Vérifier index HNSW
            try (var stmt = conn.createStatement()) {
                var rs = stmt.executeQuery("""
                    SELECT indexname FROM pg_indexes
                    WHERE tablename = 'document_embeddings' AND indexdef LIKE '%hnsw%'
                    """);
                builder.withDetail("hnswIndex", rs.next() ? rs.getString(1) : "NOT FOUND");
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
```

**Configuration Actuator:**

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,retries,retryevents
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:
    group:
      readiness:
        include: db, diskSpace, infinity, pgvector, reranking
      liveness:
        include: ping

logging:
  level:
    dev.alexandria: INFO
    dev.langchain4j: WARN
    org.springframework: WARN
    org.springframework.ai.mcp: WARN
    com.zaxxer.hikari: WARN
    com.zaxxer.hikari.pool.HikariPool: INFO
```

**Niveaux de log recommandés:**

| Package | Dev | Prod |
|---------|-----|------|
| `dev.alexandria` | DEBUG | INFO |
| `dev.langchain4j` | DEBUG | WARN |
| `org.springframework` | INFO | WARN |
| `org.springframework.ai.mcp` | DEBUG | WARN |
| `com.zaxxer.hikari` | INFO | WARN |

**Reporté à v2:**
- RagStatsInfoContributor (métriques business /actuator/info)
- Port Actuator séparé 8081

### Notes

- Format llms.txt: Standard défini sur https://llmstxt.org/
- Usage prévu: mono-utilisateur, développeur utilisant Claude Code quotidiennement
- Centaines de documents (taille typique de documentation technique)
- Recherches existantes dans `docs/research/` réutilisables (organisées par thème)
- Spring AI MCP reference: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
- Langchain4j pgvector: https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/

### Stack Validation (2026-01-04)

| Composant | Version | Statut | Notes |
|-----------|---------|--------|-------|
| Java | 25 LTS (25.0.1) | ✅ GA | Support jusqu'en 2030, JEP 491 inclus. **25.0.2 prévu 20 jan 2026** |
| Spring Boot | 3.5.9 | ✅ GA | Dernière 3.x, support OSS jusqu'en juin 2026 |
| Spring Framework | 6.2.15 | ✅ GA | Inclus dans Boot 3.5.9, compatible Langchain4j |
| Spring AI MCP SDK | 1.1.2 | ✅ GA | HTTP Streamable transport, @McpTool annotations |
| Resilience4j | 2.3.0 | ✅ GA | @Retry, compatible Virtual Threads, metrics Micrometer |
| Langchain4j core | 1.10.0 | ✅ GA | BOM recommandé |
| langchain4j-pgvector | 1.10.0-beta18 | ⚠️ Beta | API stable depuis 0.31.0, crée IVFFlat par défaut |
| langchain4j-open-ai | 1.10.0 | ✅ GA | baseUrl custom supporté |
| langchain4j-spring-boot-starter | 1.10.0-beta18 | ⚠️ Beta | Compatible Boot 3.x uniquement |
| PostgreSQL | 18.1 | ✅ GA | Depuis 25 sept. 2025 |
| PostgreSQL JDBC | 42.7.4 | ✅ GA | Dernière version stable |
| pgvector | 0.8.1 | ✅ GA | Compatible PG13-18, hnsw.iterative_scan nouveau |
| Testcontainers | 2.0.3 | ⚠️ Non certifié Java 25 | Devrait fonctionner, préfixes modules changés |
| WireMock | 3.13.2 | ⚠️ Non certifié Java 25 | Devrait fonctionner, minimum Java 11 |

**Risques identifiés:**
1. langchain4j-pgvector en beta - API stable mais possible breaking changes
2. langchain4j-spring-boot-starter en beta - nécessite version explicite
3. **Java 25.0.2 patch sécurité** - Prévu 20 janvier 2026, mise à jour recommandée
4. Testcontainers/WireMock non certifiés Java 25 - Fonctionnent mais pas officiellement testés

**Pourquoi Spring Boot 3.5.x (pas 4.x):**
- Langchain4j 1.10.0 **incompatible** avec Boot 4.0 (Jackson 3, Jakarta EE 11)
- Spring AI 1.1.1 GA stable pour Boot 3.x (2.0.0-M1 pour Boot 4.x est milestone)
- Migration vers Boot 4.x bloquée tant que Langchain4j ne supporte pas Jackson 3

**Migration future vers Boot 4.x:**
- Attendre Langchain4j compatible Jackson 3 + Jakarta EE 11
- Suivre github.com/langchain4j/langchain4j-spring pour annonces
- Resilience4j 2.x compatible avec Spring Framework 7 (migration transparente)

### Profiling Dev avec JFR (Optionnel)

Java Flight Recorder (JFR) est intégré au JDK 25 - zéro dépendance, overhead <1%, activable à la demande.

**Activation via profil Spring (recommandé):**

```yaml
# application-dev.yml
spring:
  jfr:
    enabled: true  # Spring Boot 3.4+ expose des événements JFR custom
```

**Activation via JVM args (docker-compose ou IDE):**

```bash
# Profiling continu léger (overhead ~1%)
java -XX:StartFlightRecording=filename=alexandria.jfr,dumponexit=true,settings=profile \
     -jar alexandria.jar

# Profiling à la demande (démarrage différé)
java -XX:+FlightRecorder -jar alexandria.jar
# Puis: jcmd <pid> JFR.start name=debug settings=profile duration=60s filename=debug.jfr
```

**Configuration JFR custom pour RAG (optionnel):**

```
# jfr-alexandria.jfc - Activer uniquement les événements pertinents
<?xml version="1.0" encoding="UTF-8"?>
<configuration version="2.0">
  <!-- HTTP/Network - latence Infinity -->
  <event name="jdk.SocketRead"><setting name="enabled">true</setting><setting name="threshold">1 ms</setting></event>
  <event name="jdk.SocketWrite"><setting name="enabled">true</setting><setting name="threshold">1 ms</setting></event>

  <!-- Virtual Threads -->
  <event name="jdk.VirtualThreadStart"><setting name="enabled">true</setting></event>
  <event name="jdk.VirtualThreadEnd"><setting name="enabled">true</setting></event>
  <event name="jdk.VirtualThreadPinned"><setting name="enabled">true</setting><setting name="threshold">20 ms</setting></event>

  <!-- JDBC/PostgreSQL -->
  <event name="jdk.JavaMonitorWait"><setting name="enabled">true</setting><setting name="threshold">10 ms</setting></event>

  <!-- GC (utile pour batch ingestion) -->
  <event name="jdk.GCPhasePause"><setting name="enabled">true</setting></event>
  <event name="jdk.GarbageCollection"><setting name="enabled">true</setting></event>
</configuration>
```

**Analyse des enregistrements:**

```bash
# JDK Mission Control (GUI)
jmc alexandria.jfr

# CLI - dump des événements
jfr print --events jdk.VirtualThreadPinned alexandria.jfr

# CLI - résumé
jfr summary alexandria.jfr
```

**Intérêt pour Alexandria:**
- Détecter les **Virtual Thread pinning** (JEP 491 les élimine mais vérification utile)
- Profiler la **latence réseau** vers Infinity (embedding + reranking)
- Identifier les **pauses GC** pendant l'ingestion de gros batches
- Zero overhead quand désactivé - pas de code à maintenir
