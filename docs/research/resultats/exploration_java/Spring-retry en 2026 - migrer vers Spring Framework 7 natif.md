# Spring-retry en 2026 : migrer vers Spring Framework 7 natif

**Pour le projet Alexandria (Java 25 + Spring Boot 4.0.1), la recommandation est claire : abandonner spring-retry au profit du support natif de Spring Framework 7.0.** Le projet spring-retry est officiellement en mode maintenance depuis septembre 2025, supplanté par l'intégration des fonctionnalités retry directement dans le cœur de Spring Framework. Cette migration élimine une dépendance externe tout en bénéficiant d'un support Virtual Threads natif — essentiel pour une application mono-utilisateur moderne.

## Statut actuel de spring-retry : fin de vie active

Spring-retry **2.0.12** (GA, 16 mai 2025) est la dernière version stable. Le README GitHub affiche désormais un avertissement explicite : *"⚠️ Project Status: Maintenance Only - This project has been superseded by Spring Framework 7."* Aucune version 3.0 n'est planifiée.

Le BOM Spring Boot 4.0.1 **n'inclut plus spring-retry** dans sa gestion de dépendances. L'utiliser nécessite une déclaration explicite de version, avec un risque de compatibilité : spring-retry 2.0.12 compile contre Spring Framework **6.0.23**, pas 7.0. Les notes de migration Spring Boot 4.0 recommandent explicitement de migrer vers les fonctionnalités natives.

| Version | Date | Statut |
|---------|------|--------|
| spring-retry 2.0.12 | Mai 2025 | GA, dernière version |
| spring-retry 3.0 | — | Non planifiée |
| Support Boot 4.0 BOM | — | **Retiré** |

Côté sécurité, **aucun CVE** n'affecte spring-retry directement. Les alertes parfois remontées par les scanners sont des faux positifs liés à des CVE Spring Framework sans rapport.

## Spring Framework 7.0 : le retry devient natif

Spring Framework 7.0 intègre un nouveau package `org.springframework.core.retry` avec support déclaratif et programmatique complet. L'annotation `@Retryable` supporte nativement l'exponential backoff avec jitter :

```java
@Configuration
@EnableResilientMethods  // Remplace @EnableRetry
public class AlexandriaConfig { }

@Service
public class EmbeddingClient {

    @Retryable(
        includes = {HttpServerErrorException.class, SocketTimeoutException.class},
        maxAttempts = 3,
        delay = 1000,
        multiplier = 2,
        jitter = 100,
        maxDelay = 4000
    )
    public float[] getEmbeddings(String text) {
        return restClient.post()
            .uri(infinityEndpoint)
            .body(new EmbeddingRequest(text))
            .retrieve()
            .body(float[].class);
    }
}
```

L'API programmatique `RetryTemplate` offre un contrôle fin :

```java
var retryPolicy = RetryPolicy.builder()
    .includes(IOException.class)
    .maxAttempts(3)
    .delay(Duration.ofSeconds(1))
    .multiplier(2)
    .jitter(Duration.ofMillis(100))
    .build();

var result = new RetryTemplate(retryPolicy)
    .execute(() -> httpClient.call());
```

**Différence critique** : dans Spring Framework 7, `maxAttempts` compte uniquement les retries (3 retries = 4 tentatives totales), alors que spring-retry comptait l'appel initial.

## Resilience4j : compatibilité Spring Boot 4 absente

Resilience4j **2.3.0** (janvier 2025) reste la référence pour les patterns de résilience avancés, mais présente un obstacle majeur pour Alexandria : **aucun module `resilience4j-spring-boot4` n'existe**. Les issues GitHub #2351 et #2371 documentent des problèmes de compatibilité avec Spring Framework 7, sans date de résolution annoncée.

Pour les cas nécessitant circuit breaker ou rate limiting en complément du retry, utiliser les modules core sans auto-configuration reste possible :

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-retry</artifactId>
    <version>2.3.0</version>
</dependency>
```

L'avantage de Resilience4j reste son `IntervalFunction.ofExponentialRandomBackoff()` pour un contrôle précis du backoff, mais ce cas d'usage est désormais couvert par Spring 7 natif.

## Failsafe : alternative légère mais programmatique

Failsafe **3.3.2** (juin 2023) offre une API fluide sans annotation ni dépendance Spring. Son support exponential backoff + jitter est élégant :

```java
RetryPolicy<Object> policy = RetryPolicy.builder()
    .handle(IOException.class)
    .withBackoff(Duration.ofSeconds(1), Duration.ofSeconds(4), 2.0)
    .withJitter(0.1)
    .withMaxRetries(3)
    .build();

Failsafe.with(policy).get(() -> embeddingClient.call());
```

Pour Alexandria, Failsafe convient si l'équipe préfère un contrôle programmatique explicite. L'absence d'auto-configuration Spring nécessite une configuration manuelle des beans. La cadence de release ralentie (18 mois depuis la dernière version) suggère une maturité stable mais peu d'évolution.

## Support Virtual Threads et Java 25

| Solution | Virtual Threads | Java 25 |
|----------|-----------------|---------|
| Spring Framework 7 natif | ✅ Natif, optimisé | ✅ Support complet |
| Resilience4j 2.3.0 | ⚠️ ReentrantLock (pas de pinning) | ✅ Compatible |
| Failsafe 3.3.2 | ⚠️ Via Executor custom | ✅ Compatible |
| spring-retry 2.0.12 | ❓ Non testé avec Spring 7 | ⚠️ Compile contre Spring 6 |

Spring Framework 7 a été conçu pour Virtual Threads : les blocs `synchronized` ont été remplacés par `ReentrantLock` dans tout le codebase. L'annotation `@ConcurrencyLimit` permet de throttler les appels — particulièrement utile avec Virtual Threads où la concurrence peut exploser :

```java
@ConcurrencyLimit(10)  // Max 10 appels simultanés vers RunPod
@Retryable(maxAttempts = 3, delay = 1000, multiplier = 2)
public RerankResult rerank(Query query, List<Document> docs) { ... }
```

## Matrice de décision pour Alexandria

| Critère | Spring 7 natif | Resilience4j | Failsafe |
|---------|---------------|--------------|----------|
| Compatibilité Boot 4.0.1 | ✅ Natif | ❌ Pas de starter | ⚠️ Manuel |
| @Retryable déclaratif | ✅ Oui | ❌ Boot 4 non supporté | ❌ Programmatique |
| Exponential + jitter | ✅ Natif | ✅ Excellent | ✅ Excellent |
| Virtual Threads | ✅ Optimisé | ⚠️ Partiel | ⚠️ Via Executor |
| Dépendances | ✅ Zéro | ➖ 1 JAR | ✅ Zéro |
| Maintenance active | ✅ Core Spring | ⚠️ ~1 release/an | ⚠️ Stable mais lent |
| Circuit breaker | ❌ Non inclus | ✅ Intégré | ✅ Intégré |

## Recommandation finale pour Alexandria

**Utiliser Spring Framework 7.0 natif** comme solution principale. Pour le pattern actuel (exponential backoff 1s→2s→4s avec jitter sur les appels Infinity/RunPod), la migration depuis spring-retry est minimale :

```java
// AVANT (spring-retry)
@EnableRetry
@Retryable(maxAttempts = 4, backoff = @Backoff(delay = 1000, multiplier = 2))

// APRÈS (Spring Framework 7)
@EnableResilientMethods
@Retryable(maxAttempts = 3, delay = 1000, multiplier = 2, jitter = 100)
```

Si le circuit breaker devient nécessaire (protection contre les pannes prolongées d'Infinity ou RunPod), ajouter le module core Resilience4j sans le starter Spring Boot :

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
    <version>2.3.0</version>
</dependency>
```

Cette approche combine le meilleur des deux mondes : retry natif sans dépendance externe, circuit breaker Resilience4j en option, et compatibilité totale avec Java 25 Virtual Threads.

## Conclusion

Le paysage retry Java a significativement évolué avec Spring Framework 7.0. Pour une application greenfield sur Spring Boot 4.0.1 comme Alexandria, spring-retry représente désormais une dette technique immédiate. La migration vers `@EnableResilientMethods` et le nouveau `@Retryable` natif est la voie recommandée par l'équipe Spring, élimine une dépendance, et garantit une compatibilité optimale avec Virtual Threads — un avantage stratégique pour un serveur RAG mono-utilisateur où la latence des appels HTTP externes est critique.