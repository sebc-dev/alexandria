# Resilience4j 2.3.0 pour Spring Boot 3.5.x : validation technique complète

**Status: ✅ VALIDÉ** — Resilience4j 2.3.0 est pleinement compatible avec Spring Boot 3.5.x et constitue le choix recommandé face à spring-retry (désormais en maintenance seule). La combinaison exponential backoff + jitter nécessite une configuration programmatique, mais reste simple à implémenter.

La version **2.3.0** publiée le **3 janvier 2025** apporte des améliorations critiques pour les Virtual Threads (conversion `synchronized` → `ReentrantLock`), rendant cette version particulièrement adaptée au runtime Java 25 prévu pour Alexandria. Spring-retry 2.0.11, bien que fonctionnel, est officiellement en mode "maintenance only" et supplanté par les fonctionnalités de résilience natives de Spring Framework 7.

---

## Artifact Maven exact pour Spring Boot 3.x

L'artifact correct est **`resilience4j-spring-boot3`** (introduit en v2.0.1, décembre 2022). La version GA disponible en janvier 2026 est **2.3.0**.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-bom</artifactId>
            <version>2.3.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

Le module `resilience4j-spring-boot3` inclut transitivement `resilience4j-spring6`, `resilience4j-micrometer` et `slf4j-api`. La dépendance **spring-boot-starter-aop est obligatoire** pour les annotations `@Retry`.

---

## Configuration YAML : exponential backoff avec jitter

### Limitation importante à connaître

Les options `enableExponentialBackoff` et `enableRandomizedWait` **ne peuvent pas être combinées directement en YAML**. Activer les deux simultanément provoque une `IllegalStateException`. Pour obtenir exponential backoff + jitter, une configuration programmatique via `RetryConfigCustomizer` est requise.

### Configuration YAML (exponential backoff seul)

```yaml
resilience4j:
  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 1s
        retryExceptions:
          - java.io.IOException
          - java.net.SocketTimeoutException
    
    instances:
      alexandriaHttpRetry:
        maxAttempts: 4                      # 1 initial + 3 retries
        waitDuration: 1s                    # Intervalle initial
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2     # 1s → 2s → 4s
        exponentialMaxWaitDuration: 10s     # Cap optionnel
        retryExceptions:
          - org.springframework.web.client.HttpServerErrorException
          - java.net.SocketTimeoutException
          - java.io.IOException
        ignoreExceptions:
          - org.springframework.web.client.HttpClientErrorException
```

### Configuration programmatique (exponential backoff + jitter ~100ms)

```java
package com.alexandria.rag.config;

import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.IntervalFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfiguration {

    @Bean
    public RetryConfigCustomizer alexandriaRetryCustomizer() {
        return RetryConfigCustomizer.of("alexandriaHttpRetry", builder ->
            builder.maxAttempts(4)
                   .intervalFunction(
                       IntervalFunction.ofExponentialRandomBackoff(
                           Duration.ofSeconds(1),  // initialInterval: 1s
                           2.0,                     // multiplier: 1s → 2s → 4s
                           0.1                      // randomizationFactor: ~10% = ~100ms jitter
                       )
                   )
                   .retryExceptions(
                       org.springframework.web.client.HttpServerErrorException.class,
                       java.net.SocketTimeoutException.class
                   )
        );
    }
}
```

Le paramètre **`randomizationFactor: 0.1`** applique ±10% de jitter sur chaque intervalle, soit ~100ms à 1s, ~200ms à 2s, etc.

---

## Annotation @Retry : syntaxe et package

L'annotation provient du package **`io.github.resilience4j.retry.annotation`**. Contrairement à spring-retry, aucune annotation `@EnableRetry` n'est nécessaire — l'auto-configuration Spring Boot active automatiquement le support.

```java
package com.alexandria.rag.service;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

@Service
public class EmbeddingService {

    private final RestTemplate restTemplate;

    public EmbeddingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Retry(name = "alexandriaHttpRetry", fallbackMethod = "embeddingFallback")
    public float[] generateEmbedding(String text) {
        return restTemplate.postForObject(
            "https://api.embedding-provider.com/v1/embed",
            new EmbeddingRequest(text),
            float[].class
        );
    }

    public float[] embeddingFallback(String text, Exception e) {
        // Log et retour d'un embedding par défaut ou exception métier
        throw new EmbeddingServiceUnavailableException(
            "Embedding service unavailable after retries", e
        );
    }
}
```

### Différences clés avec @Retryable de spring-retry

| Aspect | Resilience4j `@Retry` | Spring `@Retryable` |
|--------|----------------------|---------------------|
| Package | `io.github.resilience4j.retry.annotation.Retry` | `org.springframework.retry.annotation.Retryable` |
| Activation | Auto-configuré | Requiert `@EnableRetry` |
| Configuration | Référence YAML par `name` | Attributs inline ou `@Backoff` |
| Fallback | `fallbackMethod` attribute | `@Recover` séparé |
| Config YAML | ✅ Native | ❌ Non supporté |

---

## Compatibilité Virtual Threads (Java 21+/25)

**Status: PARTIELLEMENT COMPATIBLE** — Version 2.3.0 apporte des améliorations majeures, compatibilité complète attendue avec Java 25.

### Améliorations dans v2.3.0

La version 2.3.0 a converti les blocs `synchronized` en `ReentrantLock` (Issue #2232), éliminant les problèmes de **pinning** dans le code Resilience4j lui-même. Un **sliding window lock-free** a également été implémenté pour le CircuitBreaker (Issue #2241).

### Points d'attention pour Alexandria

- **Java 25 LTS résout définitivement le pinning** : JEP 491 (JDK 24+) élimine les problèmes de pinning causés par `synchronized`
- **Préférer SemaphoreBulkhead** au ThreadPoolBulkhead avec Virtual Threads
- **Risque résiduel** : les dépendances tierces peuvent encore contenir du code `synchronized`
- **Diagnostic** : utiliser `-Djdk.tracePinnedThreads=full` pour détecter le pinning

### Configuration recommandée pour Java 25

```yaml
resilience4j:
  bulkhead:
    instances:
      alexandriaBulkhead:
        maxConcurrentCalls: 25          # Semaphore-based, VT-friendly
        maxWaitDuration: 500ms
```

Le **Retry module utilise `Thread.sleep()`** pour les délais d'attente — les Virtual Threads gèrent cela efficacement sans bloquer de platform thread.

---

## Comparaison spring-retry vs Resilience4j : recommandation claire

### Spring-retry est en mode maintenance

**Fait critique** : spring-retry est officiellement en mode "maintenance only" et ne reçoit plus d'améliorations. Il est supplanté par les fonctionnalités de résilience intégrées à Spring Framework 7.

### Avantages de Resilience4j pour Alexandria

| Critère | Spring-retry 2.0.11 | Resilience4j 2.3.0 |
|---------|--------------------|--------------------|
| Maintenance | ⚠️ Maintenance only | ✅ Actif |
| Config YAML | ❌ Non | ✅ Native |
| Circuit Breaker | ⚠️ Basique | ✅ Complet |
| Rate Limiter | ❌ | ✅ Intégré |
| Bulkhead | ❌ | ✅ Intégré |
| Métriques Actuator | ⚠️ Basiques | ✅ Riches |
| Virtual Threads | ❓ Non documenté | ✅ Amélioré v2.3.0 |

### Recommandation pour Alexandria RAG Server

**Resilience4j est le choix recommandé** pour les raisons suivantes :

- **Pérennité** : maintenance active vs maintenance-only pour spring-retry
- **Virtual Threads** : optimisations spécifiques dans v2.3.0
- **Évolutivité** : si Alexandria nécessite un circuit breaker ou rate limiting (probable pour les appels API LLM), Resilience4j l'intègre nativement
- **Observabilité** : métriques Actuator riches pour le monitoring

L'overhead additionnel est négligeable — Resilience4j est modulaire et n'ajoute que ce qui est utilisé.

---

## Exemple complet pour Alexandria RAG Server

### application.yml

```yaml
spring:
  application:
    name: alexandria-rag-server
  threads:
    virtual:
      enabled: true  # Java 21+ Virtual Threads

resilience4j:
  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 500ms
        
    instances:
      llmApiRetry:
        maxAttempts: 4
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        exponentialMaxWaitDuration: 8s
        retryExceptions:
          - org.springframework.web.client.HttpServerErrorException
          - org.springframework.web.client.ResourceAccessException
          - java.net.SocketTimeoutException
          - java.net.ConnectException
        ignoreExceptions:
          - org.springframework.web.client.HttpClientErrorException$BadRequest
          - org.springframework.web.client.HttpClientErrorException$Unauthorized
          
  circuitbreaker:
    instances:
      llmApiCircuitBreaker:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowType: COUNT_BASED

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,retry,circuitbreakers
  health:
    circuitbreakers:
      enabled: true
```

### Service avec retry et circuit breaker

```java
package com.alexandria.rag.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

@Service
public class LlmApiService {

    private static final Logger log = LoggerFactory.getLogger(LlmApiService.class);
    private final RestClient restClient;

    public LlmApiService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
            .baseUrl("https://api.llm-provider.com")
            .build();
    }

    @CircuitBreaker(name = "llmApiCircuitBreaker", fallbackMethod = "circuitBreakerFallback")
    @Retry(name = "llmApiRetry", fallbackMethod = "retryFallback")
    public CompletionResponse generateCompletion(CompletionRequest request) {
        log.debug("Calling LLM API for completion");
        return restClient.post()
            .uri("/v1/completions")
            .body(request)
            .retrieve()
            .body(CompletionResponse.class);
    }

    private CompletionResponse retryFallback(CompletionRequest request, Exception e) {
        log.warn("All retries exhausted for LLM API call", e);
        throw new LlmServiceUnavailableException("LLM service temporarily unavailable", e);
    }

    private CompletionResponse circuitBreakerFallback(CompletionRequest request, Exception e) {
        log.error("Circuit breaker open for LLM API", e);
        throw new LlmServiceUnavailableException("LLM service circuit breaker open", e);
    }
}
```

### Configuration programmatique pour jitter (optionnel)

```java
package com.alexandria.rag.config;

import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.IntervalFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AlexandriaResilienceConfig {

    @Bean
    public RetryConfigCustomizer llmApiRetryCustomizer() {
        // Remplace la config YAML pour ajouter le jitter
        return RetryConfigCustomizer.of("llmApiRetry", builder ->
            builder.maxAttempts(4)
                   .intervalFunction(
                       IntervalFunction.ofExponentialRandomBackoff(
                           Duration.ofSeconds(1),   // 1s initial
                           2.0,                      // 1s → 2s → 4s
                           0.1                       // ±10% jitter (~100ms à 1s)
                       )
                   )
        );
    }
}
```

---

## Impact sur le projet Alexandria

| Élément | Décision | Impact |
|---------|----------|--------|
| Artifact | `resilience4j-spring-boot3:2.3.0` | Remplace spring-retry dans le POM |
| Configuration | YAML + `RetryConfigCustomizer` pour jitter | Nouveau fichier de config requis |
| Annotations | Migrer de `@Retryable` vers `@Retry` | Modification des services existants |
| Virtual Threads | Compatible avec Java 25 | Aucune configuration spéciale |
| Monitoring | Endpoints Actuator inclus | Activer dans application.yml |
| Dépendances | Ajouter `spring-boot-starter-aop` | Obligatoire pour AOP |

### Migration depuis spring-retry

Si spring-retry est déjà utilisé :
1. Supprimer `@EnableRetry` (non nécessaire avec Resilience4j)
2. Remplacer `@Retryable` par `@Retry(name = "...")`
3. Remplacer `@Recover` par `fallbackMethod` attribute
4. Migrer la configuration inline vers YAML

---

## Conclusion

Resilience4j 2.3.0 est **validé et recommandé** pour Alexandria RAG Server. L'artifact `resilience4j-spring-boot3` avec le BOM 2.3.0 fournit une intégration native Spring Boot 3.5.x avec des optimisations Virtual Threads. La combinaison exponential backoff + jitter requiert un `RetryConfigCustomizer`, mais cette approche offre un contrôle précis adapté aux appels API LLM. Le passage de spring-retry à Resilience4j est stratégiquement pertinent compte tenu du statut maintenance-only de spring-retry et des fonctionnalités supplémentaires (circuit breaker, rate limiter) utiles pour un serveur RAG en production.