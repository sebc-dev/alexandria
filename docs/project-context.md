---
project_name: alexandria
user_name: Negus
date: '2026-01-06'
sections_completed: ['technology_stack', 'architecture', 'resilience', 'pgvector', 'mcp_errors', 'testing', 'antipatterns']
status: complete
rule_count: 47
optimized_for_llm: true
---

# Alexandria Project Context

> Ce fichier définit les règles et patterns critiques que tous les agents IA doivent suivre lors de l'implémentation. Focus sur les détails non-évidents que les agents pourraient manquer.

---

## Technology Stack & Versions

| Composant | Version | Notes |
|-----------|---------|-------|
| Java | 25 LTS (25.0.1) | Virtual Threads activés (JEP 491), support jusqu'en 2030 |
| Spring Boot | 3.5.9 | Parent POM, support OSS jusqu'en juin 2026 |
| Spring Framework | 6.2.x | Jakarta EE 10 |
| Spring AI MCP SDK | 1.1.2 GA | HTTP Streamable transport via `/mcp` |
| Langchain4j | 1.10.0 | GA stable pour core |
| Langchain4j Spring | 1.10.0-beta18 | Beta - version explicite requise |
| Langchain4j pgvector | 1.10.0-beta18 | Beta - pas dans BOM GA |
| Resilience4j | 2.3.0 | Via BOM, compatible Virtual Threads |
| PostgreSQL | 18.1 | Avec pgvector 0.8.1 |
| PostgreSQL JDBC | 42.7.4 | Dernière version stable |
| Testcontainers | 2.0.3 | ⚠️ Artifact = `testcontainers-postgresql` (pas `postgresql`) |
| WireMock | 3.13.2 | Tests HTTP |
| Picocli | 4.7.7 | CLI ingestion |
| CommonMark | 0.27.0 | Parsing Markdown |
| Guava | 33.4.0-jre | Utilitaires |

### Contraintes de Version Critiques

- **Langchain4j Spring starters**: Toujours spécifier `${langchain4j-spring.version}` explicitement (pas dans BOM GA)
- **Testcontainers 2.x**: Les préfixes des modules ont changé depuis 1.x — utiliser `testcontainers-postgresql`
- **Spring AI**: Utiliser uniquement 1.1.2 GA (pas les milestones/snapshots)

---

## Architecture

### Principes Fondamentaux

- **Architecture Flat Simplifiée (YAGNI)** — Pas d'hexagonal, pas de DDD, pas de ports/adapters abstraits
- **Adapters Directs** — Pas d'interfaces pour les clients HTTP, implémentations concrètes
- **POJOs Simples** — Pas d'aggregates DDD, records Java pour les DTOs
- **Virtual Threads** — Gérés par Spring Boot (`spring.threads.virtual.enabled=true`), pas de config manuelle

### Structure des Packages

```
dev.alexandria/
├── core/           # Logique métier, entités, services
├── adapters/       # Clients HTTP, MCP tools, formatters
├── config/         # @Configuration, @ConfigurationProperties
├── filter/         # Filtres HTTP (CorrelationId)
├── health/         # HealthIndicators custom
└── cli/            # Commandes Picocli
```

### Patterns Obligatoires

| Pattern | Implémentation |
|---------|----------------|
| Retry | `@Retry(name = "infinityApi")` Resilience4j sur clients HTTP |
| Transactions | `@Transactional(isolation = READ_COMMITTED)` sur DELETE+INSERT |
| Error Handling | `AlexandriaException` avec `ErrorCategory` enum |
| MCP Response | Dual-format: JSON structuré + Markdown lisible (max 8000 tokens) |
| Correlation | `X-Correlation-Id` header → MDC → logs |
| Config | `@ConfigurationProperties` pour groupes de propriétés |

---

## Resilience4j Configuration

### ⚠️ Règle Critique

**NE PAS utiliser YAML pour exponential backoff + jitter combinés** — provoque `IllegalStateException`.

Utiliser `RetryConfigCustomizer` programmatique:

```java
@Bean
public RetryConfigCustomizer infinityApiRetryCustomizer() {
    return RetryConfigCustomizer.of("infinityApi", builder ->
        builder.maxAttempts(4)  // 1 initial + 3 retries
               .intervalFunction(
                   IntervalFunction.ofExponentialRandomBackoff(
                       Duration.ofSeconds(1),  // initialInterval
                       2.0,                     // multiplier: 1s → 2s → 4s
                       0.1                      // jitter ±10%
                   )
               )
    );
}
```

### Retry sur Clients HTTP

```java
@Retry(name = "infinityApi", fallbackMethod = "rerankFallback")
public RerankResult rerank(String query, List<String> documents) { ... }

// Fallback: MÊMES paramètres + Exception en dernier
private RerankResult rerankFallback(String query, List<String> documents, Exception ex) {
    throw new AlexandriaException(ErrorCategory.SERVICE_UNAVAILABLE, "...", ex);
}
```

### Exceptions Retry

| Retry | Ignore |
|-------|--------|
| `HttpServerErrorException` (5xx) | `HttpClientErrorException` (4xx) |
| `ResourceAccessException` (timeout/connexion) | |

### Notes

- `maxAttempts = 4` = 1 tentative initiale + 3 retries
- `ResourceAccessException` encapsule `SocketTimeoutException`
- Pas besoin de `@EnableRetry` (c'est Spring Retry, pas Resilience4j)
- AOP auto-configuré via `spring-boot-starter-aop`

---

## pgvector Configuration

### ⚠️ Règles Critiques

1. **Type vecteur = `vector(1024)`** — Langchain4j ne supporte PAS `halfvec`
2. **Index HNSW manuel** — Langchain4j crée IVFFlat par défaut, performances dégradées
3. **SET non persistant** — `SET hnsw.ef_search` dans schema.sql ne persiste pas entre connexions

### Configuration HikariCP Obligatoire

```yaml
spring:
  datasource:
    hikari:
      auto-commit: false  # Sécurité transactionnelle
      connection-init-sql: |
        SET hnsw.ef_search = 100;
        SET hnsw.iterative_scan = relaxed_order;
```

### Schema SQL

```sql
-- Extension pgvector
CREATE EXTENSION IF NOT EXISTS vector;

-- Table chunks avec metadata JSONB
CREATE TABLE IF NOT EXISTS document_chunks (
    id UUID PRIMARY KEY,
    content TEXT NOT NULL,
    embedding vector(1024) NOT NULL,
    metadata JSONB NOT NULL
);

-- Index HNSW (OBLIGATOIRE - ne pas laisser Langchain4j créer IVFFlat)
CREATE INDEX IF NOT EXISTS idx_chunks_embedding_hnsw
ON document_chunks USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 200);

-- Index JSONB pour filtrage
CREATE INDEX IF NOT EXISTS idx_chunks_metadata_gin
ON document_chunks USING gin (metadata jsonb_path_ops);
```

### Image Docker Tests

```
pgvector/pgvector:0.8.1-pg18
```

---

## MCP Server Configuration

### Transport HTTP Streamable

- Endpoint unique: `/mcp`
- SDK: `spring-ai-starter-mcp-server-webmvc`
- Annotations: `@McpTool`, `@McpToolParam`

### Response Format Dual

Toujours retourner **deux contenus**:
1. **JSON structuré** — pour parsing programmatique
2. **Markdown lisible** — pour Claude et humains

```java
public static CallToolResult formatSearchResults(McpSearchResponse response) {
    var builder = CallToolResult.builder();

    // 1. JSON
    builder.addTextContent(mapper.writeValueAsString(response));

    // 2. Markdown (max 8000 tokens)
    builder.addTextContent(formatAsMarkdown(response));

    return builder.build();
}
```

### Error Handling Pattern

```java
@McpTool(name = "search_documents", description = "Search documentation")
public CallToolResult searchDocuments(@McpToolParam(description = "Query") String query) {
    try {
        var response = retrievalService.search(query);
        return McpResponseFormatter.formatSearchResults(response);
    } catch (AlexandriaException e) {
        log.warn("Search failed: {}", e.getMessage());
        return McpResponseFormatter.errorResult(e.getMessage(), e.getCategory());
    } catch (Exception e) {
        log.error("Unexpected error", e);
        return McpResponseFormatter.errorResult("Unexpected error", ErrorCategory.DATABASE_ERROR);
    }
}
```

### Error Response avec isError: true

```java
public static CallToolResult errorResult(String message, ErrorCategory category) {
    return CallToolResult.builder()
        .isError(true)  // Important pour signaler l'erreur au LLM
        .addTextContent(String.format("""
            ## Error: %s
            **Problem:** %s
            **Suggested action:** %s
            """, category.title(), message, category.suggestedAction()))
        .build();
}
```

### ErrorCategory Enum

| Catégorie | Titre | Action suggérée |
|-----------|-------|-----------------|
| VALIDATION | Validation Error | Check your query and try again |
| NOT_FOUND | Not Found | The requested resource doesn't exist |
| SERVICE_UNAVAILABLE | Service Unavailable | Retry in a few seconds |
| TIMEOUT | Timeout | Try with a simpler query |

---

## Pratiques de Test

### TDD Obligatoire (Red-Green-Refactor)

1. **RED** — Écrire un test qui échoue AVANT le code de production
2. **GREEN** — Code minimal pour faire passer le test
3. **REFACTOR** — Nettoyer en gardant les tests verts

### Règles Strictes

- ❌ Aucun code de production sans test écrit EN PREMIER
- ❌ Ne jamais commiter du code avec tests qui échouent
- ✅ Tests doivent être exécutables et échouer avant l'implémentation
- ✅ Commits séparés: test (red) → impl (green) → refactor

### Structure des Tests

```
src/test/java/dev/alexandria/
├── test/
│   ├── PgVectorTestConfiguration.java   # Testcontainers pgvector
│   ├── InfinityStubs.java               # WireMock stubs Infinity API
│   ├── EmbeddingFixtures.java           # Générateur vecteurs 1024D
│   └── McpTestSupport.java              # Helper client MCP
├── core/
│   └── *Test.java                       # Tests unitaires
└── integration/
    └── *IT.java                         # Tests d'intégration
```

### Testcontainers Pattern

```java
@TestConfiguration
public class PgVectorTestConfiguration {
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("pgvector/pgvector:0.8.1-pg18")
            .withInitScript("schema.sql");
    }
}
```

### WireMock pour Infinity API

```java
public class InfinityStubs {
    public static void stubEmbedding(WireMockServer server, float[] embedding) {
        server.stubFor(post("/embeddings")
            .willReturn(okJson(embedResponse(embedding))));
    }

    public static void stubRerank(WireMockServer server, List<RerankResult> results) {
        server.stubFor(post("/rerank")
            .willReturn(okJson(rerankResponse(results))));
    }
}
```

### Fixtures Vecteurs 1024D

```java
public class EmbeddingFixtures {
    public static float[] randomEmbedding() {
        float[] embedding = new float[1024];
        // Remplir avec valeurs aléatoires normalisées
        return embedding;
    }
}
```

---

## ⛔ Anti-Patterns à Éviter

### Architecture

- ❌ **Ne PAS créer d'interfaces abstraites** pour les clients HTTP — adapters directs
- ❌ **Ne PAS utiliser d'architecture hexagonale** — flat YAGNI
- ❌ **Ne PAS créer de circuit breaker** — retry seul suffit pour ce projet
- ❌ **Ne PAS configurer manuellement les Virtual Threads** — Spring Boot le gère

### Resilience4j

- ❌ **Ne PAS combiner `enableExponentialBackoff` + `enableRandomizedWait` en YAML** → `IllegalStateException`
- ❌ **Ne PAS utiliser `@EnableRetry`** — c'est Spring Retry, pas Resilience4j
- ❌ **Ne PAS oublier `spring-boot-starter-aop`** — requis pour `@Retry`

### pgvector

- ❌ **Ne PAS utiliser `halfvec`** — Langchain4j ne le supporte pas
- ❌ **Ne PAS laisser Langchain4j créer l'index** — il crée IVFFlat, pas HNSW
- ❌ **Ne PAS mettre les SET dans schema.sql** — non persistant entre connexions

### Testcontainers

- ❌ **Ne PAS utiliser l'artifact `postgresql`** — depuis 2.x c'est `testcontainers-postgresql`
- ❌ **Ne PAS utiliser H2 pour les tests pgvector** — H2 ne supporte pas l'extension vector

### Reranker Infinity

- ❌ **Ne PAS utiliser le format OpenAI** pour `/rerank` — Infinity utilise le format Cohere
- ❌ **Ne PAS oublier le fallback** sur `@Retry` — signature = mêmes params + Exception

---

## ⚠️ Gotchas Spécifiques

| Piège | Solution |
|-------|----------|
| `langchain4j-pgvector` pas dans BOM | Spécifier `${langchain4j-spring.version}` explicitement |
| Retry fallback signature incorrecte | Mêmes paramètres que la méthode + `Exception ex` en dernier |
| MCP response trop longue | Tronquer à 8000 tokens avec message "[Content truncated]" |
| DELETE+INSERT non atomique | `@Transactional(isolation = READ_COMMITTED)` |
| Logs sans correlationId | `logback-spring.xml` avec pattern `%X{correlationId}` |
| Cold start Infinity 90s | Timeout global configuré, health check dédié |

---

## Références

- **Tech Spec**: `docs/implementation-artifacts/tech-spec-alexandria-rag-server-java/`
- **Research**: `docs/research/`
- **Phases d'implémentation**: `docs/implementation-artifacts/tech-spec-alexandria-rag-server-java/implementation-plan/tasks/`

---

## Usage Guidelines

**Pour les Agents IA:**

- Lire ce fichier AVANT d'implémenter du code
- Suivre TOUTES les règles exactement comme documentées
- En cas de doute, préférer l'option la plus restrictive
- Signaler si de nouveaux patterns émergent

**Pour les Humains:**

- Garder ce fichier lean et focalisé sur les besoins agents
- Mettre à jour quand la stack technologique change
- Revoir trimestriellement pour supprimer les règles obsolètes

---

_Dernière mise à jour: 2026-01-06_

