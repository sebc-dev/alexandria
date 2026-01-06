# Retry Pattern (Resilience4j 2.3.0)

```yaml
# application.yml
resilience4j:
  retry:
    instances:
      infinityApi:
        # IMPORTANT: Ces valeurs sont OVERRIDDEN par RetryConfigCustomizer (voir ci-dessous)
        # Le customizer Java définit: maxAttempts=4, initialInterval=1s, multiplier=2.0, jitter=10%
        # La config YAML ci-dessous sert uniquement de documentation et de fallback
        maxAttempts: 4                     # 1 initial + 3 retries
        waitDuration: 1s                   # Intervalle initial (overridden par customizer)
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
package dev.alexandria.config;

import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.IntervalFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration programmatique Resilience4j pour exponential backoff + jitter.
 * Note: enableExponentialBackoff + enableRandomizedWait ne peuvent pas être
 * combinés en YAML (provoque IllegalStateException).
 */
@Configuration
public class ResilienceConfig {

    @Bean
    public RetryConfigCustomizer infinityApiRetryCustomizer() {
        return RetryConfigCustomizer.of("infinityApi", builder ->
            builder.maxAttempts(4)  // 1 initial + 3 retries
                   .intervalFunction(
                       IntervalFunction.ofExponentialRandomBackoff(
                           Duration.ofSeconds(1),  // initialInterval: 1s
                           2.0,                     // multiplier: 1s → 2s → 4s
                           0.1                      // randomizationFactor: ±10% jitter
                       )
                   )
        );
    }
}
```

```java
package dev.alexandria.adapters;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Client HTTP pour Infinity /rerank endpoint.
 */
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
- **ATTENTION**: `enableExponentialBackoff` + `enableRandomizedWait` en YAML provoquent `IllegalStateException` - utiliser `RetryConfigCustomizer` programmatique
- `IntervalFunction.ofExponentialRandomBackoff()` combine les deux fonctionnalités
- `ResourceAccessException` capture les timeouts et erreurs de connexion (encapsule `SocketTimeoutException`)
- Pas besoin de `@EnableRetry` (c'est Spring Retry, pas Resilience4j)
- AOP auto-configuré avec `spring-boot-starter-aop`
