# Stratégies de résilience pour Alexandria RAG Server

Spring Retry 2.0.11 **seul suffit** pour un serveur MCP mono-utilisateur — l'ajout d'un circuit breaker est superflu dans ce contexte. Voici les recommandations clés : utiliser `@Retryable` avec backoff exponentiel et jitter, implémenter un **mode dégradé explicite** (recherche sans reranking si Infinity down), et retourner des erreurs structurées via `CallToolResult(isError=true)` pour que Claude Code puisse communiquer les échecs intelligemment.

## Spring Retry suffit pour le contexte mono-utilisateur

L'analyse des patterns de résilience confirme que pour un serveur mono-utilisateur avec usage bursty, spring-retry **couvre 100% des besoins**. Le circuit breaker apporte une valeur ajoutée principalement dans les systèmes multi-utilisateurs où il protège contre les défaillances en cascade — ce risque n'existe pas avec un seul utilisateur.

**Configuration Spring Retry optimisée :**

```java
@Configuration
@EnableRetry
public class RetryConfiguration {
    
    @PostConstruct
    public void configureForVirtualThreads() {
        // Obligatoire avec Java 25 + Virtual Threads
        RetrySynchronizationManager.setUseThreadLocal(false);
    }
}
```

```java
@Service
public class InfinityEmbeddingService {

    @Retryable(
        retryFor = {
            ResourceAccessException.class,      // Connexion impossible
            HttpServerErrorException.class,     // 5xx
            SocketTimeoutException.class        // Timeout
        },
        noRetryFor = {
            HttpClientErrorException.BadRequest.class,     // 400 - erreur client
            HttpClientErrorException.Unauthorized.class    // 401 - auth
        },
        maxAttempts = 4,
        backoff = @Backoff(
            delay = 1000,        // 1s initial
            multiplier = 2.0,    // → 2s → 4s
            maxDelay = 8000,     // Cap à 8s
            random = true        // IMPORTANT: jitter anti-thundering herd
        )
    )
    public float[] generateEmbedding(String text) {
        return infinityClient.embed(text);
    }

    @Recover
    public float[] fallbackEmbedding(Exception e, String text) {
        log.error("Embedding service exhausted after retries: {}", e.getMessage());
        throw new EmbeddingServiceUnavailableException(
            "Service d'embeddings temporairement indisponible", e);
    }
}
```

Le paramètre `random = true` ajoute un **jitter** essentiel : sans lui, 4 requêtes qui échouent simultanément réessaient toutes exactement à 1s, 2s, 4s — créant un effet de "thundering herd" qui surcharge le service au moment où il tente de récupérer.

## Messages d'erreur structurés pour MCP et Claude Code

Le protocole MCP distingue deux types d'erreurs avec des comportements radicalement différents. Les erreurs de protocole (JSON-RPC error) ne sont **jamais visibles par le LLM** — elles sont capturées par le client MCP. Les erreurs d'exécution d'outil via `CallToolResult(isError=true)` sont **injectées dans le contexte du LLM**, permettant à Claude Code de comprendre et communiquer l'échec.

**Structure d'erreur recommandée pour Claude Code :**

```java
public enum RagErrorCode {
    // Erreurs base de données
    DATABASE_UNAVAILABLE(-30001, "Base de données vectorielle indisponible", true, 60),
    DATABASE_TIMEOUT(-30002, "Timeout base de données", true, 30),
    
    // Erreurs embedding
    EMBEDDING_SERVICE_DOWN(-30101, "Service d'embeddings indisponible", true, 60),
    EMBEDDING_TIMEOUT(-30102, "Timeout génération embedding", true, 30),
    
    // Erreurs reranker
    RERANKER_SERVICE_DOWN(-30201, "Service de reranking indisponible", true, 60),
    RERANKER_TIMEOUT(-30202, "Timeout reranking", true, 30),
    
    // Erreurs générales
    RATE_LIMIT_EXCEEDED(-30901, "Limite de requêtes atteinte", true, 60),
    NO_RESULTS(-30903, "Aucun résultat trouvé", false, 0);
    
    private final int code;
    private final String message;
    private final boolean retryable;
    private final int retryAfterSeconds;
    
    public String formatForClaudeCode(String details) {
        StringBuilder sb = new StringBuilder();
        sb.append("Erreur [").append(code).append("]: ").append(message);
        if (details != null) {
            sb.append(". Détails: ").append(details);
        }
        if (retryable) {
            sb.append(". Réessayer dans ").append(retryAfterSeconds).append(" secondes.");
        }
        return sb.toString();
    }
}
```

**Handler MCP avec gestion d'erreurs complète :**

```java
@Component
public class SearchToolHandler {

    public CallToolResult searchDocuments(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");
        
        try {
            SearchResult result = ragService.search(query);
            
            // Inclure métadonnées de dégradation si applicable
            String response = formatResults(result);
            if (result.isDegraded()) {
                response += "\n\n⚠️ Note: " + result.getDegradationMessage();
            }
            
            return new CallToolResult(
                List.of(new TextContent(response)),
                false  // isError = false
            );
            
        } catch (EmbeddingServiceUnavailableException e) {
            return createErrorResult(RagErrorCode.EMBEDDING_SERVICE_DOWN
                .formatForClaudeCode(e.getMessage()));
                
        } catch (RerankerServiceUnavailableException e) {
            return createErrorResult(RagErrorCode.RERANKER_SERVICE_DOWN
                .formatForClaudeCode(e.getMessage()));
                
        } catch (DatabaseConnectionException e) {
            return createErrorResult(RagErrorCode.DATABASE_UNAVAILABLE
                .formatForClaudeCode(e.getMessage()));
                
        } catch (TimeoutException e) {
            return createErrorResult(
                "Timeout: La recherche a dépassé la limite de temps. " +
                "Essayez avec une requête plus courte ou plus spécifique.");
                
        } catch (Exception e) {
            log.error("Erreur inattendue dans RAG search", e);
            return createErrorResult(
                "Erreur interne: Une erreur inattendue s'est produite. " +
                "Veuillez réessayer.");
        }
    }
    
    private CallToolResult createErrorResult(String message) {
        return new CallToolResult(
            List.of(new TextContent(message)),
            true  // isError = true — CRITIQUE pour visibilité LLM
        );
    }
}
```

## Mode dégradé avec fallback progressif

L'implémentation d'un mode dégradé est **fortement recommandée** pour un système RAG. La recherche montre que retourner des résultats partiels avec indication claire de dégradation offre une bien meilleure expérience que l'échec complet.

**Matrice de décision fallback :**

| Composant défaillant | Action | Qualité estimée |
|---------------------|--------|-----------------|
| Reranker down | Retourner résultats pgvector triés par similarité | ~75% de la qualité normale |
| Embedding service down | Fallback vers recherche full-text PostgreSQL | ~50% de la qualité normale |
| PostgreSQL down | Échec complet | 0% - pas de fallback possible |

**Implémentation du service RAG avec dégradation gracieuse :**

```java
@Service
public class ResilientRagService {

    private final EmbeddingService embeddingService;
    private final VectorSearchRepository vectorSearch;
    private final FullTextSearchRepository fullTextSearch;
    private final RerankerService rerankerService;
    private final RagMetrics metrics;

    public SearchResult search(String query) {
        SearchResult.Builder result = SearchResult.builder();
        
        // Phase 1: Obtenir embedding ou fallback vers full-text
        float[] queryVector = null;
        boolean usingKeywordFallback = false;
        
        try {
            var sample = metrics.startEmbedding();
            queryVector = embeddingService.generateEmbedding(query);
            metrics.stopEmbedding(sample);
        } catch (EmbeddingServiceUnavailableException e) {
            log.warn("Embedding service indisponible, fallback vers recherche full-text");
            usingKeywordFallback = true;
            result.addDegradation("embedding_fallback", 
                "Recherche par mots-clés utilisée (service d'embeddings indisponible)");
        }
        
        // Phase 2: Recherche vectorielle ou full-text
        List<Document> candidates;
        var searchSample = metrics.startVectorSearch();
        
        if (usingKeywordFallback) {
            candidates = fullTextSearch.search(query, 20);
            result.searchMode("keyword");
        } else {
            candidates = vectorSearch.findSimilar(queryVector, 20);
            result.searchMode("vector");
        }
        metrics.stopVectorSearch(searchSample);
        
        if (candidates.isEmpty()) {
            return result.documents(Collections.emptyList())
                .message("Aucun document correspondant trouvé")
                .build();
        }
        
        // Phase 3: Reranking ou fallback vers tri par similarité
        List<Document> finalResults;
        
        try {
            var rerankSample = metrics.startReranking();
            finalResults = rerankerService.rerank(query, candidates);
            metrics.stopReranking(rerankSample, candidates.size());
            result.reranked(true);
        } catch (RerankerServiceUnavailableException e) {
            log.warn("Reranker indisponible, utilisation des scores pgvector bruts");
            finalResults = candidates.stream()
                .sorted(Comparator.comparing(Document::getSimilarityScore).reversed())
                .limit(10)
                .toList();
            result.reranked(false)
                .addDegradation("reranker_fallback",
                    "Résultats triés par similarité vectorielle uniquement");
        }
        
        return result.documents(finalResults)
            .qualityScore(calculateQualityScore(usingKeywordFallback, result.isReranked()))
            .build();
    }
    
    private double calculateQualityScore(boolean keywordFallback, boolean reranked) {
        if (keywordFallback) return 0.50;
        if (!reranked) return 0.75;
        return 1.0;
    }
}
```

**Objet de résultat avec métadonnées de dégradation :**

```java
@Builder
public class SearchResult {
    private List<Document> documents;
    private String searchMode;      // "vector" | "keyword"
    private boolean reranked;
    private double qualityScore;    // 0.0-1.0
    private Map<String, String> degradations;
    
    public boolean isDegraded() {
        return qualityScore < 1.0;
    }
    
    public String getDegradationMessage() {
        if (degradations.isEmpty()) return null;
        return degradations.values().stream()
            .collect(Collectors.joining("; "));
    }
}
```

## Configuration logging et métriques structurées

Spring Boot 3.4+ offre un **logging structuré natif** sans dépendance externe. La configuration MDC avec correlation ID est essentielle pour tracer les requêtes à travers les retries.

**Configuration application.yml complète :**

```yaml
spring:
  application:
    name: alexandria-rag-server
  threads:
    virtual:
      enabled: true
  datasource:
    url: jdbc:postgresql://localhost:5432/alexandria?options=-c%20statement_timeout=5000
    hikari:
      pool-name: pgvector-pool
      maximum-pool-size: 20
      minimum-idle: 2
      connection-timeout: 5000
      validation-timeout: 3000
      leak-detection-threshold: 30000

logging:
  structured:
    format:
      console: ecs
    ecs:
      service:
        name: ${spring.application.name}
        version: "@project.version@"
        environment: ${ENVIRONMENT:development}
  level:
    io.github.resilience4j: DEBUG
    org.springframework.retry: DEBUG
    com.alexandria.rag: DEBUG

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        http.client.requests: true
        rag.embedding.latency: true
        rag.reranking.latency: true
        rag.vector_search.latency: true
      percentiles:
        all: 0.5, 0.90, 0.95, 0.99

# Timeouts personnalisés
infinity:
  embedding:
    base-url: ${INFINITY_URL:http://localhost:8080}
    connect-timeout: 500ms
    read-timeout: 3s
  reranking:
    connect-timeout: 500ms
    read-timeout: 5s
```

**Filtre correlation ID pour MCP :**

```java
@Component
public class McpCorrelationFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String correlationId = Optional.ofNullable(request.getHeader(CORRELATION_ID_HEADER))
                .orElse(UUID.randomUUID().toString().substring(0, 8));
            
            MDC.put("correlationId", correlationId);
            MDC.put("mcpEndpoint", request.getRequestURI());
            
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

**Niveaux de log par scénario :**

| Événement | Niveau | Justification |
|-----------|--------|---------------|
| Retry réussi (récupération) | `WARN` | Problème transitoire résolu |
| Retry exhausted + fallback | `WARN` | Dégradé mais fonctionnel |
| Retry exhausted + échec | `ERROR` | Impact utilisateur |
| Timeout externe (en cours de retry) | `WARN` | Information, pas critique |
| PostgreSQL connection pool épuisé | `ERROR` | Ressource critique |
| Rate limit 429 | `WARN` | Comportement attendu |

**Métriques Micrometer pour RAG :**

```java
@Component
public class RagMetrics {

    private final Timer embeddingLatency;
    private final Timer rerankingLatency;
    private final Timer vectorSearchLatency;
    private final Counter fallbackCounter;
    private final Gauge degradedModeGauge;
    
    public RagMetrics(MeterRegistry registry) {
        this.embeddingLatency = Timer.builder("rag.embedding.latency")
            .description("Temps de génération des embeddings")
            .publishPercentiles(0.5, 0.90, 0.95, 0.99)
            .register(registry);
            
        this.rerankingLatency = Timer.builder("rag.reranking.latency")
            .description("Temps de reranking")
            .publishPercentiles(0.5, 0.90, 0.95, 0.99)
            .register(registry);
            
        this.vectorSearchLatency = Timer.builder("rag.vector_search.latency")
            .description("Temps de recherche pgvector")
            .tag("index_type", "hnsw")
            .publishPercentiles(0.5, 0.90, 0.95, 0.99)
            .register(registry);
            
        this.fallbackCounter = Counter.builder("rag.fallback.total")
            .description("Nombre de fallbacks activés")
            .tag("type", "unknown")
            .register(registry);
    }
    
    public void recordFallback(String type) {
        Counter.builder("rag.fallback.total")
            .tag("type", type)
            .register(registry)
            .increment();
    }
}
```

## Timeouts recommandés pour chaque couche

Les valeurs de timeout doivent être calibrées pour Infinity auto-hébergé (RunPod) avec des latences réseau variables.

| Opération | Connect | Read | Total | Rationale |
|-----------|---------|------|-------|-----------|
| Infinity embedding | 2s | 10s | 15s | P99 cloud API peut atteindre 5s |
| Infinity reranking | 2s | 15s | 20s | Plus intensif (~20 docs) |
| pgvector search | N/A | N/A | 5s | HNSW typiquement 1-10ms |
| Opération MCP totale | - | - | 30s | UX responsive |

**Configuration HttpClient pour Infinity :**

```java
@Configuration
public class InfinityClientConfig {

    @Bean
    public RestClient embeddingRestClient(RestClient.Builder builder,
                                          @Value("${infinity.embedding.base-url}") String baseUrl,
                                          @Value("${infinity.embedding.connect-timeout}") Duration connectTimeout,
                                          @Value("${infinity.embedding.read-timeout}") Duration readTimeout) {
        
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout);
            
        var factory = ClientHttpRequestFactoryBuilder.simple().build(settings);
        
        return builder
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
    
    @Bean
    public RestClient rerankingRestClient(RestClient.Builder builder,
                                          @Value("${infinity.reranking.base-url}") String baseUrl,
                                          @Value("${infinity.reranking.connect-timeout}") Duration connectTimeout,
                                          @Value("${infinity.reranking.read-timeout}") Duration readTimeout) {
        
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(connectTimeout)
            .withReadTimeout(readTimeout);
            
        var factory = ClientHttpRequestFactoryBuilder.simple().build(settings);
        
        return builder
            .baseUrl(baseUrl)
            .requestFactory(factory)
            .build();
    }
}
```

## Comportement détaillé par scénario de défaillance

### Scénario 1: Infinity (RunPod) down après 4 retries

```
Comportement: Retry exhausted → Fallback vers recherche full-text PostgreSQL
Durée totale: ~15s (1s + 2s + 4s + 8s avec jitter)

Log généré:
WARN  [correlationId=abc123] Embedding service failed attempt 1/4: Connection refused
WARN  [correlationId=abc123] Embedding service failed attempt 2/4: Connection refused  
WARN  [correlationId=abc123] Embedding service failed attempt 3/4: Connection refused
WARN  [correlationId=abc123] Embedding service failed attempt 4/4: Connection refused
WARN  [correlationId=abc123] Embedding service exhausted, activating keyword fallback
INFO  [correlationId=abc123] Search completed in degraded mode: keyword_fallback

Message MCP retourné (si fallback réussit):
{
  "content": [{"type": "text", "text": "Résultats trouvés...\n\n⚠️ Note: Recherche par mots-clés utilisée (service d'embeddings indisponible). La pertinence peut être réduite."}],
  "isError": false
}

Message MCP retourné (si fallback échoue aussi):
{
  "content": [{"type": "text", "text": "Erreur [-30101]: Service d'embeddings indisponible. Détails: Connection refused after 4 attempts. Réessayer dans 60 secondes."}],
  "isError": true
}
```

### Scénario 2: PostgreSQL complètement indisponible

```
Comportement: Échec immédiat après 3 tentatives (pas de fallback possible)
Durée totale: ~3.5s (500ms + 750ms + 1125ms avec backoff 1.5x)

Log généré:
ERROR [correlationId=abc123] PostgreSQL connection failed: Connection refused
ERROR [correlationId=abc123] PostgreSQL connection failed after 3 attempts

Message MCP retourné:
{
  "content": [{"type": "text", "text": "Erreur [-30001]: Base de données vectorielle indisponible. Détails: Unable to acquire connection from pool. Réessayer dans 60 secondes."}],
  "isError": true
}
```

### Scénario 3: Rate limit 429 ou quota exceeded

```
Comportement: Retry avec backoff respectant Retry-After header si présent

Log généré:
WARN  [correlationId=abc123] Infinity API rate limited (429), waiting 30s before retry
WARN  [correlationId=abc123] Retry 2/4 after rate limit delay
INFO  [correlationId=abc123] Infinity API call succeeded after rate limit recovery

Configuration spéciale pour 429:
```

```java
@Retryable(
    retryFor = { HttpClientErrorException.TooManyRequests.class },
    maxAttempts = 4,
    backoff = @Backoff(
        delay = 30000,      // 30s minimum pour rate limits
        multiplier = 1.5,
        maxDelay = 120000   // Cap à 2 minutes
    )
)
public float[] generateEmbedding(String text) {
    // ...
}
```

### Scénario 4: Latence excessive (>10s pour une recherche)

```
Comportement: Timeout global déclenché, retour d'erreur avant completion

Configuration avec @Timeout (Java 21+):
```

```java
@Service
public class RagService {
    
    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(10);
    
    public SearchResult search(String query) {
        try {
            return CompletableFuture.supplyAsync(() -> executeSearch(query))
                .orTimeout(SEARCH_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof TimeoutException) {
                log.warn("Search timeout exceeded {}s for query: {}", 
                    SEARCH_TIMEOUT.toSeconds(), query);
                throw new SearchTimeoutException(
                    "Recherche interrompue: dépassement du délai de " + 
                    SEARCH_TIMEOUT.toSeconds() + "s");
            }
            throw e;
        }
    }
}
```

```
Message MCP retourné:
{
  "content": [{"type": "text", "text": "Timeout: La recherche a dépassé la limite de 10 secondes. Essayez avec une requête plus courte ou plus spécifique. Réessayer est recommandé."}],
  "isError": true
}
```

## Circuit breaker: quand l'envisager malgré tout

Bien que non recommandé pour le contexte mono-utilisateur, un circuit breaker pourrait être justifié si :

- **Rate limits stricts** d'Infinity causent des échecs prolongés (>5 min)
- **Coût par requête** élevé où éviter les requêtes inutiles économise de l'argent
- **Évolution vers multi-utilisateur** prévue à court terme

Si vous décidez de l'ajouter ultérieurement, voici la configuration minimale avec Resilience4j :

```yaml
resilience4j:
  circuitbreaker:
    instances:
      infinityEmbedding:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 60
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
```

## Conclusion et points clés

La stratégie de résilience pour Alexandria RAG Server repose sur **trois piliers** : Spring Retry pour les erreurs transitoires, mode dégradé explicite pour maintenir le service, et messages d'erreur structurés pour Claude Code.

- **Spring Retry 2.0.11** avec `random=true` et configuration Virtual Threads suffit pour le contexte mono-utilisateur
- **Fallback hiérarchique** : embedding down → keyword search, reranker down → résultats pgvector bruts
- **`CallToolResult(isError=true)`** est critique pour que Claude Code voie les erreurs
- **Timeouts agressifs** (10s embedding, 15s reranking, 5s pgvector) préviennent les blocages
- **Logging structuré ECS** avec correlation ID permet le diagnostic efficace
- **Métriques Micrometer** sur latences et fallbacks activés pour monitoring proactif

L'ajout d'un circuit breaker peut être différé jusqu'à ce que le système évolue vers un usage multi-utilisateur ou que des problèmes spécifiques de rate limiting prolongé se manifestent.