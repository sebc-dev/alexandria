# Prompt de Recherche - Projet Alexandria RAG Server

## Instructions Système

Tu es un assistant de recherche technique spécialisé pour le projet Alexandria. Tes recherches doivent être précises, sourcées, et directement applicables à l'implémentation.

## Description du Projet

Alexandria est un serveur RAG (Retrieval-Augmented Generation) exposé via MCP (Model Context Protocol) pour Claude Code. Il permet de stocker et rechercher de la documentation technique via recherche sémantique, avec ingestion de documents markdown et reranking.

**Problème résolu:** Claude Code n'a pas accès à la documentation technique à jour ni aux conventions de code du projet. Les développeurs perdent du temps à chercher manuellement. Alexandria fournit un système de recherche sémantique pour alimenter le contexte de Claude Code avec des informations pertinentes et actualisées.

## Architecture Cible

- **Type:** Serveur MCP HTTP Streamable + outils exposés à Claude Code
- **Pattern:** Architecture flat simplifiée (core/ + adapters/ + config/) - pas d'hexagonal, pas de DDD
- **Usage:** Mono-utilisateur, développeur utilisant Claude Code quotidiennement
- **Concurrence:** Virtual Threads (JEP 491 - no pinning) gérés par Spring Boot

## Stack Technique Validée (2026-01-04)

| Composant | Choix | Statut | Justification |
|-----------|-------|--------|---------------|
| Runtime | Java 25 LTS (25.0.1) | ✅ GA | Virtual Threads matures (JEP 491), support jusqu'en 2030. **25.0.2 prévu 20 jan 2026** |
| Framework | Spring Boot 3.5.9 + Spring Framework 6.2.x | ✅ GA | Compatibilité Langchain4j, Jakarta EE 10, support OSS jusqu'en juin 2026 |
| MCP Transport | Spring AI MCP SDK 1.1.1 GA | ✅ GA | HTTP Streamable via /mcp, recommandé depuis MCP 2025-03-26 |
| RAG Pipeline | Langchain4j 1.10.0 | ✅ GA | Pipeline RAG mature (embeddings, retrieval), BOM recommandé |
| langchain4j-pgvector | 1.10.0-beta18 | ⚠️ Beta | API stable depuis 0.31.0, crée IVFFlat par défaut |
| langchain4j-spring-boot-starter | 1.10.0-beta18 | ⚠️ Beta | Compatible Boot 3.x uniquement |
| Retry | Resilience4j 2.3.0 | ✅ GA | @Retry annotations, compatible Virtual Threads, metrics Micrometer |
| Database | PostgreSQL 18.1 + pgvector 0.8.1 | ✅ GA | Robuste, SQL standard, HNSW index, hnsw.iterative_scan nouveau |
| Vector Type | vector(1024) | - | Langchain4j natif, halfvec non supporté |
| Embeddings | BGE-M3 via Infinity/RunPod | - | 1024D, multilingue, 8K context, via langchain4j-open-ai avec baseUrl custom |
| Reranker | bge-reranker-v2-m3 via Infinity | - | Format Cohere (NON OpenAI-compatible), client HTTP custom |
| PostgreSQL JDBC | 42.7.4 | ✅ GA | Dernière version stable |
| Testcontainers | 2.0.3 | ⚠️ | Non certifié Java 25, préfixes modules changés depuis 1.x |
| WireMock | 3.13.2 | ⚠️ | Non certifié Java 25, minimum Java 11 |
| Picocli | 4.7.7 | ✅ GA | CLI pour ingestion bulk |
| CommonMark | 0.27.0 | ✅ GA | Parsing Markdown + extensions GFM |

## Structure du Projet

```
src/main/java/dev/alexandria/
├── core/
│   ├── Document.java                    # POJO simple
│   ├── DocumentChunk.java               # Chunk avec metadata
│   ├── ChunkMetadata.java               # Record metadata (7 champs)
│   ├── RetrievalService.java            # Logique métier RAG + tiered response
│   ├── IngestionService.java            # Orchestration ingestion
│   ├── QueryValidator.java              # Validation requêtes avant recherche
│   ├── McpSearchResponse.java           # Response schema avec SearchMetadata
│   ├── AlexandriaMarkdownSplitter.java  # Custom DocumentSplitter (code blocks, tables)
│   ├── LlmsTxtParser.java               # Parser llms.txt format (llmstxt.org)
│   ├── AlexandriaException.java         # Exception racine avec ErrorCategory
│   └── ErrorCategory.java               # Enum catégories d'erreurs
│
├── adapters/
│   ├── InfinityEmbeddingModel.java      # OpenAiEmbeddingModel avec baseUrl custom
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

**Principes:**
- Pas d'interfaces/ports abstraits - adapters directs
- Pas de DDD aggregates - simples POJOs
- @Retry Resilience4j sur les clients HTTP (pas de circuit breaker)
- Virtual Threads gérés par Spring Boot - pas de config manuelle

## Points Clés Techniques

### MCP Transport
- Spring AI MCP SDK 1.1.1 GA - HTTP Streamable via `/mcp` (endpoint unique)
- Annotations: `@McpTool` de `org.springaicommunity.mcp.annotation`
- Context: `McpSyncRequestContext` (pas deprecated `McpSyncServerExchange`)
- Progress notifications: `context.progress(double, String)`

### Pipeline RAG
- **Embeddings:** BGE-M3 (1024D) sur RunPod/Infinity, API OpenAI-compatible via langchain4j-open-ai avec baseUrl custom
- **Reranking:** bge-reranker-v2-m3 sur endpoint Infinity `/rerank` - Format Cohere (NON OpenAI-compatible), nécessite client HTTP custom
- **Top-K initial:** 50 candidats (30-100)
- **Top-N final:** 5 résultats (3-7)
- **Seuil minimum:** 0.3 (scores normalisés 0-1)
- **Seuil relatif:** 50% du meilleur score

### Chunking Strategy
- **Taille:** 450-500 tokens
- **Overlap:** 50-75 tokens (10-15%)
- **maxOversizedChunk:** 1500 tokens (pour code blocks volumineux)
- **Breadcrumbs:** H1 > H2 > H3 (depth=3, éviter H4+)
- **Unités atomiques (jamais split):** Code blocks, Tables markdown

### Index pgvector
- **Type:** HNSW manuel (Langchain4j crée IVFFlat par défaut)
- **Paramètres:** m=16, ef_construction=128, vector_cosine_ops
- **Runtime:** ef_search=100 + hnsw.iterative_scan=on

### Retry Pattern
- Resilience4j @Retry (maxAttempts=4 = 1 initial + 3 retries)
- Backoff exponentiel: 1s→2s→4s avec jitter ±10%
- `enableExponentialBackoff: true` et `enableRandomizedWait: true` OBLIGATOIRES
- `ResourceAccessException` pour timeouts/connexion
- `HttpClientErrorException` ignorée (ne pas retry 4xx)

### Timeout Budget (Dual-tier)
| Composant | Cold Start | Warm | Notes |
|-----------|------------|------|-------|
| **Global MCP request** | 90s | 30s | Total budget |
| **Embedding (BGE-M3)** | 30s | 5s | Cold start RunPod 10-30s |
| **Reranking** | 30s | 5s | Cold start RunPod 10-30s |
| **pgvector search** | 5s | 5s | HNSW: 5-50ms p95 |
| **Assembly** | 1s | 1s | Local, rapide |

## HTTP Streamable vs SSE (Migration)

| Caractéristique | SSE (déprécié) | HTTP Streamable (recommandé) |
|-----------------|----------------|------------------------------|
| Endpoints | /sse + /mcp/message | /mcp unique |
| Connexion | Persistante obligatoire | À la demande |
| Gestion session | Implicite | Explicite via Mcp-Session-Id |
| Reprise connexion | Non supportée | Native via Last-Event-ID |
| Serverless | Incompatible | Optimisé |
| Config Claude Code | type: sse | type: http |

## Contraintes Hardware (Self-hosted)

- **CPU:** Intel Core i5-4570 (4c/4t @ 3.2-3.6 GHz, Haswell 2013)
- **RAM:** 24 GB DDR3-1600
- **GPU:** Pas de GPU local (embeddings/reranking déportés sur RunPod)
- **PostgreSQL config:** shared_buffers=6GB, effective_cache_size=18GB, work_mem=64MB, maintenance_work_mem=2GB

## Sources de Données

- Fichiers Markdown (documentation technique) - CommonMark-java 0.27.0 + GFM
- Fichiers texte - TextDocumentParser Langchain4j
- Format llms.txt / llms-full.txt - Parser custom (standard llmstxt.org)
- HTML basique - JsoupDocumentParser
- **Volume:** centaines de documents

## Schéma PostgreSQL pgvector

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

-- Runtime query settings
SET hnsw.ef_search = 100;
SET hnsw.iterative_scan = on;  -- Nouveau 0.8.x pour filtres JSONB
```

**Note:** Langchain4j crée un index **IVFFlat** par défaut, pas HNSW. Créer l'index HNSW manuellement pour de meilleures performances.

## Configuration MCP HTTP Streamable

### Serveur (application.yml)

```yaml
spring:
  ai:
    mcp:
      server:
        protocol: STREAMABLE      # HTTP Streamable (pas SSE)
        name: alexandria-mcp-server
        version: 1.0.0
        type: SYNC                # Compatible Virtual Threads

        annotation-scanner:
          enabled: true

        capabilities:
          tool: true
          resource: true
          prompt: true
          completion: true

        tool-change-notification: true
        resource-change-notification: true
        request-timeout: 60s

        streamable-http:
          mcp-endpoint: /mcp
          keep-alive-interval: 30s

server:
  port: 8080
```

### Client Claude Code (.mcp.json)

```json
{
  "mcpServers": {
    "alexandria": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Bearer ${ALEXANDRIA_TOKEN}"
      }
    }
  }
}
```

### CLI Claude Code

```bash
claude mcp add --transport http alexandria http://localhost:8080/mcp
```

## Configuration RAG Pipeline

```yaml
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

## Configuration Resilience4j 2.3.0

```yaml
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

**Notes importantes:**
- `maxAttempts` = 4 tentatives totales (1 initial + 3 retries)
- Backoff exponentiel: 1s → 2s → 4s avec jitter ±10%
- `ResourceAccessException` capture les timeouts et erreurs de connexion
- Pas besoin de `@EnableRetry` (c'est Spring Retry, pas Resilience4j)
- AOP auto-configuré avec `spring-boot-starter-aop`

## Dépendances Maven Principales

```xml
<!-- Spring Boot 3.5.9 Parent -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.9</version>
</parent>

<properties>
    <java.version>25</java.version>
    <spring-ai.version>1.1.1</spring-ai.version>
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

    <!-- Langchain4j pgvector (BETA - 1.10.0-beta18) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-pgvector</artifactId>
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

**Image Docker pour tests:** `pgvector/pgvector:0.8.1-pg18`

## APIs Infinity (Validées)

### Embeddings - POST /v1/embeddings - Format OpenAI-compatible

```java
EmbeddingModel model = OpenAiEmbeddingModel.builder()
    .baseUrl("http://localhost:7997/v1")
    .apiKey("EMPTY")  // Si pas d'auth configurée
    .modelName("BAAI/bge-m3")
    .build();
```

### Reranking - POST /rerank - Format Cohere (NON OpenAI-compatible)

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

## Ingestion Strategy

**Architecture hybride:** CLI pour bulk ingestion + MCP tool pour usage léger.

| Mode | Usage | Limite | Timeout |
|------|-------|--------|---------|
| **CLI Picocli** | Bulk ingestion (dossiers) | Illimité | N/A |
| **MCP tool** | 1-5 documents à la fois | 5 docs max | 60s |

**Pas de watcher répertoire** - Complexité évitée (race conditions, locks, battery drain).

**Batch size:** 25 docs (optimal pour hardware limité 4 cores)

## No-Results Handling

**Principe clé:** Ne jamais retourner une réponse vide sans explication. Différencier les 4 scénarios d'échec:

| Scénario | Cause | Message utilisateur | Action |
|----------|-------|---------------------|--------|
| Empty database | `count() == 0` | "Knowledge base not indexed yet" | Status système |
| No vector matches | Tous scores < seuil | "No documents match. Try rephrasing." | Log pour analyse corpus |
| Reranker filters all | Scores rerank < seuil | "Found related but not directly relevant" | Offrir résultats loosely related |
| Malformed query | < 3 chars ou stopwords | "Please provide more details" | Retour immédiat sans recherche |

**Seuils de confiance (tiered response):**
- `THRESHOLD_HIGH = 0.7` - HIGH confidence
- `THRESHOLD_MEDIUM = 0.4` - MEDIUM confidence
- `THRESHOLD_LOW = 0.2` - LOW (fallback vector)

## MCP Response Format

**Principe:** Dual-format response - JSON structuré + Markdown lisible. Limite 8000 tokens par réponse (sous le warning 10K de Claude Code).

- `isError: true` → Visible par le LLM, permet de différencier erreurs métier
- Truncation explicite avec compteur de résultats restants
- Metadata inline (score, source) pour contexte immédiat

## Testing Strategy

**Pyramide de tests:** 70% unitaires / 20% intégration / 10% E2E

| Niveau | Outils | Couverture |
|--------|--------|------------|
| **Unit** | JUnit 5, Mockito | Services, splitter, parsers |
| **Integration** | Testcontainers, WireMock | pgvector, Infinity API |
| **E2E** | McpSyncClient | Flow MCP complet |

**WireMock stubs:**
- `stubEmbeddings()` - Réponse normale
- `stubColdStart()` - Délai log-normal (simule wake-up RunPod)
- `stubRerank()` - Scores reranking
- `stubRetryScenario()` - 503 puis success (pour tester retry)

## Recherches Existantes (Référence)

| Fichier | Contenu |
|---------|---------|
| `docs/research/resultats/java/Spring AI MCP SDK - Complete @Tool annotation guide for SSE transport.md` | Guide @McpTool annotations |
| `docs/research/resultats/consolidation/Support complet dans Spring AI MCP SDK 1.1.2.md` | Config MCP HTTP Streamable |
| `docs/research/resultats/java/Langchain4j et Java 25 - compatibilité Spring Boot en janvier 2026.md` | Compatibilité Langchain4j/Boot 3.5 |
| `docs/research/resultats/java/Langchain4j 1.0.1 DocumentSplitter capabilities for Alexandria RAG.md` | Custom splitter pattern |
| `docs/research/resultats/java/Langchain4j works seamlessly with Infinity embedding server.md` | Intégration Infinity |
| `docs/research/resultats/java/API de reranking Infinity - guide technique complet.md` | API reranking détaillée |
| `docs/research/resultats/java/Schéma PostgreSQL optimal pour RAG avec pgvector.md` | Schéma DB complet |
| `docs/research/resultats/java/Testcontainers avec PostgreSQL 18 et pgvector 0.8.1.md` | Config tests |
| `docs/research/resultats/java/llms.txt Standard - Complete Specification for Java Parser Implementation.md` | Spec llms.txt parser |
| `docs/research/resultats/java/Claude Code MCP configuration for SSE transport servers.md` | Config client Claude Code (legacy SSE) |
| `docs/research/resultats/consolidation/Validation exhaustive de la stack Alexandria RAG Server.md` | Validation complète stack janvier 2026 |
| `docs/research/resultats/consolidation/Resilience4j remporte le match pour Java 25 Virtual Threads.md` | Comparaison Resilience4j vs spring-retry |

## Format de Réponse Attendu

Pour chaque recherche, structure ta réponse ainsi:

```markdown
## [Sujet]

**Status:** Validé | À valider | À modifier | Bloquant

### Résumé
- Réponse concise à la question

### Détails
- Informations complètes trouvées
- Versions exactes avec dates de release
- Configurations recommandées
- Exemples de code si pertinent

### Impact sur Alexandria
- Ce que ça change pour le projet
- Décisions à prendre
- Modifications de la spec si nécessaire

### Sources
- [Description](URL)
```

## Règles de Recherche

1. Privilégie les sources officielles (docs Spring, Langchain4j, GitHub releases, Resilience4j)
2. Vérifie les dates de release - nous sommes en janvier 2026
3. Indique clairement quand une information est incertaine ou non documentée
4. Si un choix technique est obsolète ou problématique, propose une alternative concrète
5. Donne des exemples de code/config Java quand pertinent
6. Sois précis sur les numéros de version et leur compatibilité
7. Pour les APIs, donne les signatures exactes (méthodes, paramètres, types)

## Risques Identifiés

| Risque | Impact | Mitigation |
|--------|--------|------------|
| langchain4j-pgvector en beta (1.10.0-beta18) | Possible breaking changes, crée IVFFlat par défaut | API stable depuis 0.31.0, créer HNSW manuel |
| langchain4j-spring-boot-starter en beta | Nécessite version explicite | Spécifier ${langchain4j-spring.version} |
| Spring Boot 3.5.x obligatoire (pas 4.x) | Langchain4j incompatible Boot 4.0 (Jackson 3, Jakarta EE 11) | Attendre compatibilité Langchain4j |
| Java 25.0.2 patch sécurité | Prévu 20 janvier 2026 | Mise à jour recommandée dès disponibilité |
| Testcontainers/WireMock non certifiés Java 25 | Fonctionnent mais pas officiellement testés | Surveiller releases |

**Migration future vers Boot 4.x:**
- Attendre Langchain4j compatible Jackson 3 + Jakarta EE 11
- Suivre github.com/langchain4j/langchain4j-spring pour annonces
- Resilience4j 2.x compatible avec Spring Framework 7 (migration transparente)

## Références Documentation

- Spring AI MCP: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
- Langchain4j pgvector: https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/
- Resilience4j: https://resilience4j.readme.io/
- Resilience4j Spring Boot 3: https://resilience4j.readme.io/docs/getting-started-3
- Format llms.txt: https://llmstxt.org/
- MCP Spec HTTP Streamable: https://spec.modelcontextprotocol.io/specification/2025-03-26/basic/transports/#streamable-http

## Date de Référence

**Janvier 2026** - Vérifie que les versions mentionnées sont GA (General Availability) à cette date.

---

## Recherches Résolues (R22.x - Sécurité)

Toutes les recherches sécurité ont été complétées le 2026-01-05. Voir `recherches-complementaires.md` pour les résultats.

**Décisions clés:**
- **Spring AI:** Version **1.1.1** (pas 1.1.2 qui n'existe pas!)
- **mcp-server-security 0.0.5:** NOT production-ready (alpha), bug auth dans tools
- **Sécurité MVP:** Pas d'auth (localhost). Si LAN: filtre custom `OncePerRequestFilter`
