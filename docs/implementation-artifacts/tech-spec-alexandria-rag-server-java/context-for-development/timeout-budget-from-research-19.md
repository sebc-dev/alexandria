# Timeout Budget (from research #19)

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
