# Optimal timeout configuration for Alexandria RAG pipeline

A self-hosted semantic search pipeline on modest hardware requires **aggressive timeout budgeting** with cold-start awareness. For Alexandria's four-stage pipeline, the recommended global timeout is **90 seconds** for initial requests (handling RunPod cold starts) dropping to **30 seconds** for warm operations, with individual stage budgets detailed below.

## The cold start problem drives everything

RunPod serverless with FlashBoot delivers **500ms–2 second** cold starts at p95, but first requests or low-traffic periods can see **10–42 seconds** without container caching. This reality demands a two-tier timeout strategy: generous initial timeouts that gracefully handle cold starts, then tighter timeouts for warmed workers.

Your Infinity embedding/reranking server on RunPod represents the critical path. While warm inference completes in **10–50ms** for single queries, cold start scenarios require planning for **30–90 second** delays on the first call after worker scale-down.

| Stage | Warm latency (p95) | Cold start scenario | Recommended timeout |
|-------|-------------------|---------------------|---------------------|
| Query embedding (RunPod) | 50–200ms | 10–60s | **30s** (first), **10s** (subsequent) |
| pgvector HNSW search | 15–50ms | N/A (local) | **5s** |
| Reranking (RunPod) | 100–300ms | 10–60s | **30s** (first), **10s** (subsequent) |
| Response assembly | 5–20ms | N/A (local) | **1s** |

## Spring Boot 3.5.x RestClient configuration

Spring Boot 3.5.x introduces `ClientHttpRequestFactoryBuilder` in the `org.springframework.boot.http.client` package—the recommended approach for timeout configuration with RestClient.

### application.yml baseline configuration

```yaml
spring:
  threads:
    virtual:
      enabled: true  # Essential for Java 25 + blocking I/O
  
  http:
    client:
      factory: jdk   # Best virtual thread compatibility
      connect-timeout: 5s
      read-timeout: 30s
  
  datasource:
    url: jdbc:postgresql://localhost:5432/alexandria
    hikari:
      pool-name: AlexandriaPool
      maximum-pool-size: 10        # (4 cores × 2) + 2 for SSD
      minimum-idle: 10
      connection-timeout: 10000    # 10s pool acquisition
      validation-timeout: 5000
      idle-timeout: 600000         # 10 min idle removal
      max-lifetime: 1800000        # 30 min connection refresh
      connection-init-sql: |
        SET statement_timeout = '10s';
        SET hnsw.ef_search = 100;
      data-source-properties:
        socketTimeout: 30
        connectTimeout: 10
  
  jpa:
    properties:
      javax.persistence.query.timeout: 5000  # 5s default query timeout
  
  ai:
    mcp:
      client:
        request-timeout: 90s  # Global MCP request timeout
```

### Separate RestClient beans with stage-specific timeouts

RestClient doesn't support per-request timeout overrides, so create dedicated clients for each external service:

```java
@Configuration
@EnableRetry
public class RestClientConfiguration {

    @Bean("embeddingRestClient")
    public RestClient embeddingRestClient(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(30));  // Cold start buffer
        
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.jdk()
                .build(settings);
        
        return builder
                .baseUrl("https://api.runpod.ai/v2/your-embedding-endpoint")
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${RUNPOD_API_KEY}")
                .build();
    }

    @Bean("rerankingRestClient")
    public RestClient rerankingRestClient(RestClient.Builder builder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(30));  // Cold start buffer
        
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.jdk()
                .build(settings);
        
        return builder
                .baseUrl("https://api.runpod.ai/v2/your-reranking-endpoint")
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${RUNPOD_API_KEY}")
                .build();
    }
    
    @Bean("warmEmbeddingRestClient")
    public RestClient warmEmbeddingRestClient(RestClient.Builder builder) {
        // Tighter timeout for known-warm workers
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(10));
        
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.jdk()
                .build(settings);
        
        return builder
                .baseUrl("https://api.runpod.ai/v2/your-embedding-endpoint")
                .requestFactory(factory)
                .build();
    }
}
```

## Retry strategy with timeout escalation for cold starts

The key pattern for handling RunPod cold starts: **escalating timeouts across retry attempts** rather than fixed timeouts with simple retries.

```java
@Service
public class EmbeddingService {
    
    private final RestClient embeddingClient;
    private final RetryTemplate coldStartRetryTemplate;
    
    public EmbeddingService(@Qualifier("embeddingRestClient") RestClient embeddingClient) {
        this.embeddingClient = embeddingClient;
        this.coldStartRetryTemplate = buildColdStartRetryTemplate();
    }
    
    private RetryTemplate buildColdStartRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(1000, 2.0, 10000)  // 1s, 2s, 4s backoff
                .retryOn(ResourceAccessException.class)
                .retryOn(HttpServerErrorException.class)
                .build();
    }
    
    public float[] generateEmbedding(String text) {
        return coldStartRetryTemplate.execute(context -> {
            int attempt = context.getRetryCount();
            // Log for cold start detection analysis
            if (attempt > 0) {
                log.info("Embedding retry attempt {} - possible cold start", attempt + 1);
            }
            
            return embeddingClient.post()
                    .uri("/runsync")
                    .body(new EmbeddingRequest(text))
                    .retrieve()
                    .body(EmbeddingResponse.class)
                    .getEmbedding();
        });
    }
    
    @Recover
    public float[] embeddingFallback(Exception e, String text) {
        log.error("All embedding attempts failed for text: {}", 
                  text.substring(0, Math.min(50, text.length())), e);
        throw new EmbeddingServiceException("Embedding service unavailable", e);
    }
}
```

### Annotation-based retry with spring-retry

```java
@Service
public class RerankingService {
    
    private final RestClient rerankingClient;
    
    @Retryable(
        retryFor = {ResourceAccessException.class, HttpServerErrorException.class},
        noRetryFor = {HttpClientErrorException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 8000)
    )
    public List<RankedDocument> rerank(String query, List<Document> documents) {
        return rerankingClient.post()
                .uri("/runsync")
                .body(new RerankRequest(query, documents))
                .retrieve()
                .body(new ParameterizedTypeReference<List<RankedDocument>>() {});
    }
    
    @Recover
    public List<RankedDocument> rerankFallback(Exception e, String query, 
                                                List<Document> documents) {
        log.warn("Reranking failed, returning unranked results", e);
        // Graceful degradation: return documents in original order
        return documents.stream()
                .map(doc -> new RankedDocument(doc, doc.getScore()))
                .toList();
    }
}
```

## PostgreSQL and pgvector timeout tuning

For **100K–1M vectors** on your 4-core i5-4570 with 24GB RAM, pgvector HNSW queries should complete in **5–50ms** at p95. Set conservative timeouts to catch anomalies without blocking normal operations.

### Per-query timeout with native query

```java
@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query(value = """
        SELECT d.* FROM documents d
        ORDER BY d.embedding <=> cast(:queryVector as vector)
        LIMIT :limit
        """, nativeQuery = true)
    @QueryHints({
        @QueryHint(name = "javax.persistence.query.timeout", value = "5000"),
        @QueryHint(name = "org.hibernate.fetchSize", value = "50")
    })
    List<Document> findSimilar(@Param("queryVector") String queryVector,
                               @Param("limit") int limit);

    @Query(value = """
        SELECT d.* FROM documents d
        WHERE d.source = :source
        ORDER BY d.embedding <=> cast(:queryVector as vector)
        LIMIT :limit
        """, nativeQuery = true)
    @QueryHints({
        @QueryHint(name = "javax.persistence.query.timeout", value = "10000")  // Filtered = slower
    })
    List<Document> findSimilarBySource(@Param("source") String source,
                                        @Param("queryVector") String queryVector,
                                        @Param("limit") int limit);
}
```

### Dynamic ef_search for recall/speed tradeoff

```java
@Service
@Transactional(readOnly = true)
public class VectorSearchService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    public List<Document> searchWithTuning(float[] queryVector, int limit, 
                                           boolean highRecall) {
        // Adjust ef_search based on quality requirements
        int efSearch = highRecall ? 200 : 64;
        
        entityManager.createNativeQuery("SET LOCAL hnsw.ef_search = " + efSearch)
                     .executeUpdate();
        
        return entityManager.createNativeQuery("""
            SELECT * FROM documents
            ORDER BY embedding <=> :vector
            LIMIT :limit
            """, Document.class)
            .setParameter("vector", arrayToVector(queryVector))
            .setParameter("limit", limit)
            .setHint("javax.persistence.query.timeout", 5000)
            .getResultList();
    }
}
```

## MCP protocol timeout architecture

Spring AI MCP SDK defaults to **20 seconds** request timeout. For Alexandria serving Claude Code, configure generous timeouts since the entire RAG pipeline must complete within MCP bounds.

### MCP server configuration

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: alexandria
        version: 1.0.0
        transport: sse
      client:
        request-timeout: 120s  # Must exceed worst-case pipeline time
```

### Claude Code client configuration

Create or edit `~/.claude/settings.json`:

```json
{
  "mcpServers": {
    "alexandria": {
      "command": "java",
      "args": ["-jar", "/path/to/alexandria.jar"],
      "env": {
        "MCP_SERVER_REQUEST_TIMEOUT": "120"
      }
    }
  },
  "env": {
    "MCP_TIMEOUT": "30000",
    "MCP_TOOL_TIMEOUT": "120000"
  }
}
```

The timeout hierarchy flows: **Claude Code (120s)** → **MCP Protocol (120s)** → **Pipeline stages (cumulative ~90s max)** → **Individual HTTP calls (10–30s each)**.

## Global timeout calculation and budget allocation

### Formula for aggregate timeout

```
Global_Timeout = Σ(Stage_Timeout) + Buffer_Overhead
              = (Embed_p95 + Search_p95 + Rerank_p95 + Assembly) × Safety_Factor

For cold start scenario:
Global = (30s + 5s + 30s + 1s) × 1.3 = ~86s → round to 90s

For warm scenario:  
Global = (10s + 5s + 10s + 1s) × 1.2 = ~31s → round to 30s
```

### Recommended timeout budget

| Scenario | Embedding | Vector search | Reranking | Assembly | **Total** |
|----------|-----------|---------------|-----------|----------|-----------|
| Cold start (first request) | 30s | 5s | 30s | 1s | **90s** |
| Warm operation | 10s | 5s | 10s | 1s | **30s** |
| Degraded (skip rerank) | 10s | 5s | — | 1s | **20s** |

## Graceful degradation strategy

Implement tiered fallbacks when timeouts occur:

```java
@Service
public class SearchOrchestrator {
    
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearch;
    private final RerankingService rerankingService;
    
    public SearchResult search(String query, SearchOptions options) {
        Instant deadline = Instant.now().plus(
            options.isAllowColdStart() ? Duration.ofSeconds(90) : Duration.ofSeconds(30)
        );
        
        // Stage 1: Embedding (required)
        float[] queryVector;
        try {
            queryVector = embeddingService.generateEmbedding(query);
        } catch (Exception e) {
            log.error("Embedding failed - cannot proceed", e);
            throw new SearchException("Search unavailable", e);
        }
        
        // Stage 2: Vector search (required)
        List<Document> candidates = vectorSearch.findSimilar(queryVector, 50);
        
        if (candidates.isEmpty()) {
            return SearchResult.empty();
        }
        
        // Stage 3: Reranking (optional - graceful degradation)
        List<RankedDocument> ranked;
        Duration remaining = Duration.between(Instant.now(), deadline);
        
        if (remaining.toSeconds() > 15 && options.isRerankingEnabled()) {
            try {
                ranked = rerankingService.rerank(query, candidates);
            } catch (Exception e) {
                log.warn("Reranking failed, using vector scores", e);
                ranked = candidates.stream()
                        .map(d -> new RankedDocument(d, d.getScore()))
                        .sorted(Comparator.comparing(RankedDocument::score).reversed())
                        .toList();
            }
        } else {
            log.info("Skipping reranking - {} remaining", remaining);
            ranked = candidates.stream()
                    .map(d -> new RankedDocument(d, d.getScore()))
                    .toList();
        }
        
        // Stage 4: Assembly
        return assembleResult(query, ranked.subList(0, Math.min(10, ranked.size())));
    }
}
```

## Cold start detection and worker warming

Distinguish cold starts from failures by tracking response times:

```java
@Component
public class ColdStartDetector {
    
    private final MeterRegistry meterRegistry;
    private final AtomicReference<Instant> lastSuccessfulCall = new AtomicReference<>();
    
    public boolean isLikelyColdStart(Duration responseTime) {
        Instant lastCall = lastSuccessfulCall.get();
        boolean workerLikelyScaledDown = lastCall == null || 
            Duration.between(lastCall, Instant.now()).toMinutes() > 5;
        
        boolean slowResponse = responseTime.toSeconds() > 5;
        
        return workerLikelyScaledDown && slowResponse;
    }
    
    public void recordSuccess(Duration responseTime) {
        lastSuccessfulCall.set(Instant.now());
        
        if (isLikelyColdStart(responseTime)) {
            meterRegistry.counter("runpod.cold_starts").increment();
        }
        
        meterRegistry.timer("runpod.response_time").record(responseTime);
    }
}
```

### Optional: Proactive warming ping

```java
@Scheduled(fixedRate = 240_000)  // Every 4 minutes (under 5-min idle timeout)
public void keepWorkersWarm() {
    try {
        embeddingService.generateEmbedding("warmup");
        log.debug("Worker keep-alive ping successful");
    } catch (Exception e) {
        log.warn("Worker warming failed - expect cold start on next request", e);
    }
}
```

## Complete configuration reference

### application.yml (production)

```yaml
alexandria:
  search:
    global-timeout: 90s
    warm-timeout: 30s
    enable-reranking: true
    max-results: 10
    
  runpod:
    embedding:
      endpoint: ${RUNPOD_EMBEDDING_ENDPOINT}
      connect-timeout: 5s
      read-timeout: 30s
      retry-attempts: 3
    reranking:
      endpoint: ${RUNPOD_RERANKING_ENDPOINT}
      connect-timeout: 5s
      read-timeout: 30s
      retry-attempts: 2
      skip-threshold: 0.92  # Skip if top result score > threshold
      
  pgvector:
    ef-search: 100
    query-timeout: 5s

spring:
  threads:
    virtual:
      enabled: true
  http:
    client:
      factory: jdk
  datasource:
    hikari:
      maximum-pool-size: 10
      connection-timeout: 10000
      connection-init-sql: |
        SET statement_timeout = '10s';
        SET hnsw.ef_search = 100;
  ai:
    mcp:
      client:
        request-timeout: 120s
```

## Conclusion

The optimal configuration balances **cold start resilience** with **interactive responsiveness**. Key takeaways:

- **Dual-tier timeouts** are essential: 90 seconds for cold-start scenarios, 30 seconds for warm operations
- **JDK HttpClient** (`factory: jdk`) provides best Virtual Thread compatibility—avoid Apache HttpClient pinning issues
- **Skip reranking** under time pressure as the primary graceful degradation path—it saves 10–30 seconds on cold starts
- **pgvector HNSW** is remarkably fast (**5–50ms**) on modest hardware; the 5-second timeout is purely defensive
- **MCP client timeout** must exceed pipeline worst-case; set to **120s** to accommodate double cold starts (embedding + reranking)
- **Worker warming** every 4 minutes eliminates most cold starts but adds ~$0.50–2/day in RunPod costs