# Pattern Retry HTTP en Java moderne sans dépendances lourdes

L'implémentation d'un retry robuste avec `java.net.http.HttpClient` ne nécessite **aucune dépendance externe** — la JDK ne fournit pas de retry intégré, mais un pattern simple en vanilla Java avec exponential backoff et jitter est parfaitement adapté aux Virtual Threads de Java 21+. Pour Spring Boot 3.4+, **spring-retry standalone** (~50KB) ou les opérateurs natifs de **WebClient** offrent des alternatives légères sans Spring Cloud.

## HttpClient Java n'a pas de retry intégré

Le `java.net.http.HttpClient` ne dispose d'**aucun mécanisme de retry applicatif**. Les seuls retries internes concernent l'authentification (`jdk.httpclient.auth.retrylimit=3`) et les redirections. Vous devez implémenter la logique retry vous-même.

**Pattern idiomatique Java 21+** — une simple boucle `while` avec `Thread.sleep()` est parfaitement compatible avec les Virtual Threads. Le thread virtuel se "démonte" du carrier thread pendant le sleep, libérant la ressource :

```java
public class RetryableHttpClient {
    private final HttpClient client;
    private final int maxRetries;
    private final Duration baseDelay;
    private final double multiplier;

    public <T> HttpResponse<T> send(HttpRequest request, BodyHandler<T> handler) 
            throws IOException, InterruptedException {
        IOException lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<T> response = client.send(request, handler);
                
                if (!isRetryable(response.statusCode()) || attempt == maxRetries) {
                    return response;
                }
                // Status retryable → continuer
            } catch (IOException e) {
                if (attempt == maxRetries) throw e;
                lastException = e;
            }
            
            // Thread.sleep() fonctionne parfaitement avec Virtual Threads
            Thread.sleep(calculateDelayWithJitter(attempt));
        }
        throw lastException;
    }
    
    private boolean isRetryable(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504 || status >= 500;
    }
}
```

## Exponential backoff avec Full Jitter — la formule AWS

Le **Full Jitter** est recommandé par AWS et Google Cloud pour éviter le "thundering herd" où tous les clients retry simultanément. La formule produit un délai aléatoire dans l'intervalle exponentiel :

```java
private long calculateDelayWithJitter(int attempt) {
    // Pattern: 1s → 2s → 4s (avant jitter)
    long exponentialDelay = (long) (baseDelay.toMillis() * Math.pow(multiplier, attempt));
    long cappedDelay = Math.min(exponentialDelay, maxDelay.toMillis());
    
    // Full Jitter: random entre 0 et le délai calculé
    return ThreadLocalRandom.current().nextLong(0, cappedDelay + 1);
}
```

| Stratégie | Formule | Usage |
|-----------|---------|-------|
| **Full Jitter** | `random(0, min(cap, base × 2^n))` | Meilleure performance globale |
| **Equal Jitter** | `delay/2 + random(0, delay/2)` | Garantit un délai minimum |

Pour vos spécifications (**1s → 2s → 4s**), utilisez `baseDelay=1000ms`, `multiplier=2.0`, `maxRetries=3`.

## Codes HTTP retryables vs non-retryables

La distinction clé est **erreur transitoire** (problème temporaire côté serveur/réseau) vs **erreur permanente** (problème avec la requête elle-même).

**✅ RETRYABLES — erreurs transitoires :**
- **429 Too Many Requests** — respecter le header `Retry-After`
- **502 Bad Gateway** — problème de proxy/gateway temporaire
- **503 Service Unavailable** — surcharge serveur
- **504 Gateway Timeout** — timeout upstream
- **500 Internal Server Error** — optionnel, avec prudence
- **Timeouts/IOException** — erreurs réseau transitoires

**❌ NON-RETRYABLES — erreurs permanentes :**
- **400 Bad Request** — payload invalide, corriger les données
- **401 Unauthorized** — token expiré, renouveler l'auth
- **403 Forbidden** — permissions insuffisantes
- **404 Not Found** — ressource inexistante
- **422 Unprocessable Entity** — erreur sémantique

### Parsing du header Retry-After

Le header `Retry-After` peut contenir des **secondes** ou une **date HTTP** :

```java
private Duration parseRetryAfter(HttpResponse<?> response) {
    return response.headers().firstValue("Retry-After")
        .map(value -> {
            // Essayer comme nombre de secondes
            try {
                return Duration.ofSeconds(Long.parseLong(value));
            } catch (NumberFormatException e) {
                // Parser comme date HTTP (RFC 7231)
                ZonedDateTime retryDate = ZonedDateTime.parse(value, 
                    DateTimeFormatter.RFC_1123_DATE_TIME);
                return Duration.between(Instant.now(), retryDate.toInstant());
            }
        })
        .filter(d -> !d.isNegative())
        .orElse(null);
}
```

## Configuration des timeouts pour API embeddings

Le `HttpClient` distingue **connection timeout** (établissement TCP) et **request timeout** (temps total jusqu'aux headers de réponse). **Attention** : il n'y a pas de read timeout intégré pour le body.

```java
// Client avec connection timeout court (fail fast)
HttpClient client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

// Request avec timeout adapté à l'inférence GPU
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.runpod.ai/v2/endpoint/run"))
    .timeout(Duration.ofSeconds(120))  // Batch embeddings
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString(payload))
    .build();
```

### Valeurs recommandées pour RunPod/Infinity

| Scénario | Connection | Request | Justification |
|----------|------------|---------|---------------|
| Single embedding | 10s | 30-60s | P90 ~600ms, mais pics possibles |
| Batch 10-50 textes | 10s | **120-180s** | Latence ×2-5 vs single |
| RunPod serverless | 10s | **300s** | Cold start possible (2-30s) |

**Règle pratique** : multipliez le timeout par **2-3x** quand vous passez de batch=1 à batch=50. Les benchmarks montrent que la latence P95 d'OpenAI peut atteindre **~1 minute** depuis GCP.

## Options légères dans Spring Boot 3.4+

### Option 1 : spring-retry standalone (recommandé pour blocking)

**spring-retry fonctionne 100% sans Spring Cloud** — c'est une dépendance indépendante (~50KB + AOP) :

```xml
<dependency>
    <groupId>org.springframework.retry</groupId>
    <artifactId>spring-retry</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

```java
@Configuration
@EnableRetry
public class RetryConfig {}

@Service
public class EmbeddingService {
    
    @Retryable(
        retryFor = {RestClientException.class, HttpServerErrorException.class},
        maxAttempts = 4,
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000)
    )
    public EmbeddingResponse getEmbeddings(List<String> texts) {
        return restClient.post()
            .uri("/embeddings")
            .body(new EmbeddingRequest(texts))
            .retrieve()
            .body(EmbeddingResponse.class);
    }
    
    @Recover
    public EmbeddingResponse fallback(RestClientException e) {
        throw new EmbeddingServiceException("Échec après 4 tentatives", e);
    }
}
```

### Option 2 : WebClient avec retry Reactor (zéro dépendance extra)

Si vous utilisez déjà WebFlux, les opérateurs Reactor sont **intégrés** :

```java
public Mono<String> embedAsync(String text) {
    return webClient.post()
        .uri("/embeddings")
        .bodyValue(new EmbeddingRequest(text))
        .retrieve()
        .bodyToMono(String.class)
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(10))
            .jitter(0.5)
            .filter(ex -> ex instanceof WebClientResponseException.ServiceUnavailable));
}
```

## Implémentation complète vanilla Java

Voici une solution **production-ready** sans aucune dépendance externe, compatible Virtual Threads :

```java
public final class RetryableHttpClient {
    private final HttpClient client;
    private final int maxRetries;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final double multiplier;
    private final Set<Integer> retryableStatuses = Set.of(429, 500, 502, 503, 504);

    public RetryableHttpClient(HttpClient client, int maxRetries, 
                               Duration baseDelay, Duration maxDelay) {
        this.client = client;
        this.maxRetries = maxRetries;
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
        this.multiplier = 2.0;
    }

    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        
        IOException lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<T> response = client.send(request, handler);
                int status = response.statusCode();
                
                // Succès ou erreur non-retryable
                if (status < 400 || !retryableStatuses.contains(status) || attempt == maxRetries) {
                    return response;
                }
                
                // Respecter Retry-After si présent
                Duration retryAfter = parseRetryAfter(response);
                if (retryAfter != null) {
                    Thread.sleep(retryAfter.toMillis());
                } else {
                    Thread.sleep(calculateDelayWithJitter(attempt));
                }
                
            } catch (HttpTimeoutException | HttpConnectTimeoutException e) {
                lastException = e;
                if (attempt == maxRetries) throw e;
                Thread.sleep(calculateDelayWithJitter(attempt));
            }
        }
        
        throw lastException != null ? lastException : new IOException("Max retries exceeded");
    }

    private long calculateDelayWithJitter(int attempt) {
        long exponential = (long) (baseDelay.toMillis() * Math.pow(multiplier, attempt));
        long capped = Math.min(exponential, maxDelay.toMillis());
        return ThreadLocalRandom.current().nextLong(capped / 2, capped + 1); // Equal jitter
    }

    private Duration parseRetryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After")
            .flatMap(v -> {
                try { return Optional.of(Duration.ofSeconds(Long.parseLong(v))); }
                catch (NumberFormatException e) { return Optional.empty(); }
            })
            .orElse(null);
    }
}

// Usage
var client = new RetryableHttpClient(
    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
    3,                           // maxRetries
    Duration.ofSeconds(1),       // baseDelay (1s → 2s → 4s)
    Duration.ofSeconds(30)       // maxDelay
);

var request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.runpod.ai/v2/infinity/embeddings"))
    .timeout(Duration.ofSeconds(120))
    .header("Authorization", "Bearer " + apiKey)
    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
    .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
```

## Conclusion

Pour votre contexte (Spring Boot 3.4+, mono-utilisateur, pas de circuit breaker), **deux approches optimales** se dégagent :

- **Si vous préférez zéro dépendance** : l'implémentation vanilla Java ci-dessus est robuste, testable, et parfaitement compatible Virtual Threads. Elle couvre tous vos besoins (exponential backoff 1s→2s→4s, jitter, Retry-After, codes 5xx/429).

- **Si vous voulez la simplicité déclarative** : spring-retry standalone avec `@Retryable` ajoute ~2MB mais offre une configuration externalisable et des métriques intégrées.

Dans les deux cas, configurez un **connection timeout court** (10s) pour fail-fast, un **request timeout généreux** (120s pour batch embeddings), et limitez à **3 retries** maximum pour éviter les cascades de timeouts.