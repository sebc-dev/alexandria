# Resilience4j remporte le match pour Java 25 Virtual Threads

**Resilience4j 2.3.0 est la recommandation claire** pour votre serveur RAG avec Spring Boot 3.5.9 et Java 25 Virtual Threads. Spring Retry, désormais en mode maintenance, nécessite une configuration manuelle pour les Virtual Threads tandis que Resilience4j 2.3.0 (janvier 2025) intègre nativement les correctifs anti-pinning. Pour vos appels HTTP vers Infinity avec cold start potentiel, le Circuit Breaker de Resilience4j offre une protection supplémentaire que Spring Retry ne peut pas fournir.

## Compatibilité Virtual Threads : le critère éliminatoire tranché

Les deux bibliothèques supportent les Virtual Threads, mais avec des approches très différentes. **Spring Retry 2.0.12** exige l'appel explicite `RetrySynchronizationManager.setUseThreadLocal(false)` dans un `@PostConstruct` car par défaut, le `RetryContext` est stocké dans un `ThreadLocal` incompatible avec les Virtual Threads selon le JEP 444. Cette configuration basculce vers un `ConcurrentHashMap` indexé par thread.

**Resilience4j 2.3.0** a corrigé le problème de pinning Virtual Threads directement dans le code source via les issues GitHub #2232 et #2241. Les blocs `synchronized` ont été convertis en `ReentrantLock` et une fenêtre glissante lock-free a été implémentée pour le Circuit Breaker. Aucune configuration spéciale n'est requise — le support est natif. De plus, le projet prévoit un bump vers JDK 25 (issue #2343) pour éliminer totalement les risques de pinning avec les améliorations du JDK 24+.

## Spring Retry est officiellement en fin de vie

Développement critique : **Spring Retry est désormais en mode maintenance uniquement**. Le README officiel GitHub indique que le projet a été supplanté par Spring Framework 7, qui intègre les fonctionnalités de retry directement dans `spring-core` et `spring-context`. Spring Framework 7 (novembre 2025) introduit ses propres annotations `@Retryable` et `@ConcurrencyLimit` natives.

| Critère | Spring Retry 2.0.12 | Resilience4j 2.3.0 |
|---------|--------------------|--------------------|
| Virtual Threads | Configuration manuelle requise | Support natif |
| Dernière release | Mai 2025 (bugfix) | Janvier 2025 (features) |
| Statut maintenance | **Maintenance only** | Actif |
| Circuit Breaker | Non inclus | Inclus |
| Rate Limiter | Non | Oui |
| Métriques Micrometer | Depuis v2.0.8 | Intégré |

## Comparaison fonctionnelle pour vos cas d'usage HTTP

### Configuration Retry avec Backoff exponentiel et Jitter

Pour les appels vers Infinity avec cold start serverless, le jitter est essentiel pour éviter le thundering herd. Voici la syntaxe comparée :

**Spring Retry** :
```java
@Retryable(
    maxAttempts = 5,
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000, random = true)
)
public EmbeddingResponse callInfinity(String text) { ... }
```

**Resilience4j** (via application.yml) :
```yaml
resilience4j:
  retry:
    instances:
      infinityApi:
        maxAttempts: 5
        waitDuration: 1s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2.0
        exponentialMaxWaitDuration: 30s
        randomizedWaitFactor: 0.5
```

L'avantage Resilience4j : la configuration **externalisée dans YAML** sans recompilation, alors que Spring Retry nécessite des expressions SpEL avec placeholders pour atteindre le même résultat.

### Circuit Breaker pour cold start Infinity

Le cold start RunPod serverless peut prendre **30-60 secondes**. Un Circuit Breaker protège votre application pendant ces périodes :

```yaml
resilience4j:
  circuitbreaker:
    instances:
      infinityApi:
        slidingWindowSize: 10
        minimumNumberOfCalls: 3
        failureRateThreshold: 50
        waitDurationInOpenState: 60s  # Temps de cold start
        permittedNumberOfCallsInHalfOpenState: 2
```

**Spring Retry n'offre pas de Circuit Breaker intégré**. Vous devriez ajouter Spring Cloud Circuit Breaker qui… utilise Resilience4j en backend.

## Configuration minimale recommandée pour Alexandria

### Dépendances Maven

```xml
<dependencies>
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
        <version>2.3.0</version>
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

### Configuration application.yml complète

```yaml
spring:
  threads:
    virtual:
      enabled: true

resilience4j:
  retry:
    configs:
      httpDefault:
        maxAttempts: 4
        waitDuration: 2s
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2.0
        exponentialMaxWaitDuration: 30s
        randomizedWaitFactor: 0.5
        retryExceptions:
          - java.io.IOException
          - java.net.SocketTimeoutException
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
        ignoreExceptions:
          - org.springframework.web.client.HttpClientErrorException
    instances:
      infinityEmbeddings:
        baseConfig: httpDefault
        maxAttempts: 5  # Plus de tentatives pour cold start
      infinityRerank:
        baseConfig: httpDefault
      postgresQuery:
        maxAttempts: 3
        waitDuration: 500ms
        retryExceptions:
          - org.postgresql.util.PSQLException

  circuitbreaker:
    instances:
      infinityEmbeddings:
        slidingWindowSize: 10
        minimumNumberOfCalls: 3
        failureRateThreshold: 50
        waitDurationInOpenState: 45s
        permittedNumberOfCallsInHalfOpenState: 2
        registerHealthIndicator: true
      infinityRerank:
        slidingWindowSize: 5
        minimumNumberOfCalls: 2
        failureRateThreshold: 60
        waitDurationInOpenState: 30s

  bulkhead:
    instances:
      infinityEmbeddings:
        maxConcurrentCalls: 5  # Limiter les appels concurrents
        maxWaitDuration: 100ms
```

### Service HTTP avec annotations

```java
@Service
public class InfinityClient {
    private final RestClient restClient;
    
    public InfinityClient(RestClient.Builder builder, 
                          @Value("${infinity.base-url}") String baseUrl) {
        this.restClient = builder
            .baseUrl(baseUrl)
            .requestInterceptor((request, body, execution) -> {
                // Logging pour debug cold start
                return execution.execute(request, body);
            })
            .build();
    }
    
    @CircuitBreaker(name = "infinityEmbeddings", fallbackMethod = "embeddingsFallback")
    @Retry(name = "infinityEmbeddings")
    @Bulkhead(name = "infinityEmbeddings", type = Bulkhead.Type.SEMAPHORE)
    public EmbeddingResponse getEmbeddings(EmbeddingRequest request) {
        return restClient.post()
            .uri("/embeddings")
            .body(request)
            .retrieve()
            .body(EmbeddingResponse.class);
    }
    
    @CircuitBreaker(name = "infinityRerank", fallbackMethod = "rerankFallback")
    @Retry(name = "infinityRerank")
    public RerankResponse rerank(RerankRequest request) {
        return restClient.post()
            .uri("/rerank")
            .body(request)
            .retrieve()
            .body(RerankResponse.class);
    }
    
    // Fallbacks pour graceful degradation
    public EmbeddingResponse embeddingsFallback(EmbeddingRequest req, Throwable t) {
        log.error("Infinity embeddings unavailable after retries", t);
        throw new ServiceUnavailableException("Embedding service temporarily unavailable");
    }
    
    public RerankResponse rerankFallback(RerankRequest req, Throwable t) {
        log.warn("Reranking unavailable, returning unranked results", t);
        // Retourner les résultats non-rerankés comme fallback
        return RerankResponse.passthrough(req.getDocuments());
    }
}
```

## Ordre d'exécution des aspects Resilience4j

L'ordre par défaut est : `Retry(CircuitBreaker(RateLimiter(TimeLimiter(Bulkhead(Function)))))`. Pour votre cas d'usage, le Bulkhead s'exécute en premier (limite la concurrence), puis le CircuitBreaker évalue l'état, puis le Retry effectue les tentatives. C'est l'ordre recommandé.

## Risques et limitations identifiés

**Risques avec Resilience4j** :
- Issue #2378 : conflit de configuration si `waitDuration` et `enableExponentialBackoff` sont définis simultanément dans la config par défaut
- Le `ThreadPoolBulkhead` est incompatible avec les Virtual Threads — utilisez exclusivement `SemaphoreBulkhead` (type par défaut)
- La migration future vers Resilience4j 3.x nécessitera Java 21 minimum (non problématique avec Java 25)

**Risques avec Spring Retry** (si vous choisissez quand même) :
- Projet en **maintenance only** : aucune nouvelle fonctionnalité
- Migration obligatoire vers Spring Framework 7 à terme
- Pas de Circuit Breaker intégré pour gérer les cold starts prolongés

**Risques projet** :
- Cold start RunPod de **30-60s** : le `waitDurationInOpenState` du Circuit Breaker doit couvrir ce temps
- Mono-utilisateur : le Circuit Breaker pourrait s'ouvrir trop facilement avec un petit `slidingWindowSize` — augmentez `minimumNumberOfCalls` si nécessaire

## Conclusion

Resilience4j 2.3.0 s'impose comme le choix évident pour trois raisons décisives : **support natif Virtual Threads** sans configuration manuelle, **maintenance active** versus Spring Retry en fin de vie, et **Circuit Breaker intégré** essentiel pour gérer les cold starts de votre infrastructure RunPod serverless. La configuration YAML externalisée et les métriques Micrometer automatiques simplifient également l'observabilité.

Pour votre architecture mono-utilisateur self-hosted, la complexité additionnelle de Resilience4j est minimale — quelques annotations et un fichier YAML bien structuré. Le pattern `@CircuitBreaker` + `@Retry` + `@Bulkhead(SEMAPHORE)` couvre parfaitement vos cas d'usage HTTP vers Infinity et protège contre les défaillances en cascade lors des cold starts.