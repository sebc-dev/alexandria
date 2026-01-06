# Constats CRITICAL (Must Fix)

---

## F1: No @Transactional on DELETE+INSERT

**ID:** F1
**Severite:** CRITICAL
**Categorie:** Transaction

### Description du Probleme

Le `DocumentUpdateService.ingestDocument()` execute un pattern DELETE puis INSERT pour mettre a jour les documents. Sans `@Transactional`, si l'application crash entre le DELETE et l'INSERT, les chunks du document seront perdus definitivement.

### Impact si Non Corrige

- Perte de donnees en cas de crash ou timeout
- Etat inconsistant de la base vectorielle
- Documents partiellement indexes impossibles a detecter

### Solution Concrete

**Fichier a modifier:** `src/main/java/dev/alexandria/core/DocumentUpdateService.java`

```java
package dev.alexandria.core;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class DocumentUpdateService {

    private final PgVectorEmbeddingStore embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final AlexandriaMarkdownSplitter splitter;

    public DocumentUpdateService(PgVectorEmbeddingStore embeddingStore,
                                  EmbeddingModel embeddingModel,
                                  AlexandriaMarkdownSplitter splitter) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.splitter = splitter;
    }

    /**
     * Mise a jour transactionnelle d'un document.
     * ROLLBACK automatique si echec entre DELETE et INSERT.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UpdateResult ingestDocument(String sourceUri, String content,
                                        String documentHash, boolean isNewDocument) {
        // 1. DELETE tous les anciens chunks (rollback si echec INSERT)
        var documentFilter = metadataKey("sourceUri").isEqualTo(sourceUri);
        embeddingStore.removeAll(documentFilter);

        // 2. Chunking
        Document document = Document.from(content);
        List<TextSegment> segments = splitter.split(document);

        // 3. Enrichir metadata et embeddings
        List<TextSegment> enrichedSegments = enrichWithMetadata(segments, sourceUri, documentHash);

        // 4. Generer embeddings
        var response = embeddingModel.embedAll(enrichedSegments);
        List<Embedding> embeddings = response.content();

        // 5. INSERT nouveaux chunks (si echec, DELETE est rollback)
        embeddingStore.addAll(embeddings, enrichedSegments);

        return isNewDocument ? UpdateResult.CREATED : UpdateResult.UPDATED;
    }

    // ... autres methodes
}
```

**Configuration requise dans `application.yml`:**

```yaml
spring:
  datasource:
    hikari:
      auto-commit: false  # Transactions explicites
```

### Fichiers a Modifier/Creer

- [x] `src/main/java/dev/alexandria/core/DocumentUpdateService.java` - Ajouter `@Transactional`
- [x] `src/main/resources/application.yml` - Verifier `auto-commit: false`

### Criteres d'Acceptation

- [ ] La methode `ingestDocument()` est annotee `@Transactional`
- [ ] Un test d'integration verifie le rollback: simuler une exception apres DELETE, verifier que les anciens chunks existent toujours
- [ ] Le connection pool HikariCP est configure avec `auto-commit: false`

### Statut

- [ ] Corrige

---

## F2: No Rate Limiting for Infinity API

**ID:** F2
**Severite:** CRITICAL
**Categorie:** Resilience

### Description du Probleme

L'API Infinity (RunPod) peut etre surchargee par des rafales de requetes, surtout lors de l'ingestion batch. Sans rate limiting, on risque des erreurs 429 et une degradation du service.

### Impact si Non Corrige

- Erreurs 429 Too Many Requests en rafale
- Bannissement temporaire par RunPod
- Degradation du service pour tous les clients
- Couts supplementaires si facturation par requete

### Solution Concrete

**Fichier a creer:** `src/main/java/dev/alexandria/config/ResilienceConfig.java`

```java
package dev.alexandria.config;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    /**
     * Rate limiter pour l'API Infinity - limite les requetes a 10/seconde.
     * Adapte pour un endpoint RunPod standard.
     */
    @Bean
    public RateLimiter infinityRateLimiter(RateLimiterRegistry registry) {
        RateLimiterConfig config = RateLimiterConfig.custom()
            .limitForPeriod(10)                    // 10 requetes max
            .limitRefreshPeriod(Duration.ofSeconds(1))  // par seconde
            .timeoutDuration(Duration.ofSeconds(30))    // attente max pour obtenir permission
            .build();

        return registry.rateLimiter("infinityApi", config);
    }

    /**
     * Bulkhead pour limiter les appels concurrents a Infinity.
     * Protege contre les surcharges memoire et les timeouts en cascade.
     */
    @Bean
    public Bulkhead infinityBulkhead(BulkheadRegistry registry) {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(5)                      // 5 appels simultanes max
            .maxWaitDuration(Duration.ofSeconds(10))    // attente max pour un slot
            .build();

        return registry.bulkhead("infinityApi", config);
    }

    /**
     * Retry avec exponential backoff + jitter pour Infinity API.
     */
    @Bean
    public RetryConfigCustomizer infinityRetryCustomizer() {
        return RetryConfigCustomizer.of("infinityApi", builder ->
            builder.maxAttempts(4)
                   .intervalFunction(
                       IntervalFunction.ofExponentialRandomBackoff(
                           Duration.ofSeconds(1),   // 1s initial
                           2.0,                      // multiplier
                           0.1                       // 10% jitter
                       )
                   )
                   .retryExceptions(
                       org.springframework.web.client.HttpServerErrorException.class,
                       java.net.SocketTimeoutException.class,
                       java.net.ConnectException.class
                   )
        );
    }
}
```

**Fichier a modifier:** `src/main/java/dev/alexandria/adapters/InfinityRerankClient.java`

```java
package dev.alexandria.adapters;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class InfinityRerankClient {

    private final RestClient rerankRestClient;

    public InfinityRerankClient(RestClient rerankRestClient) {
        this.rerankRestClient = rerankRestClient;
    }

    /**
     * Rerank avec triple protection: rate limit, bulkhead, retry.
     * Ordre d'execution: RateLimiter -> Bulkhead -> Retry -> Method
     */
    @RateLimiter(name = "infinityApi", fallbackMethod = "rateLimitFallback")
    @Bulkhead(name = "infinityApi", fallbackMethod = "bulkheadFallback")
    @Retry(name = "infinityApi", fallbackMethod = "rerankFallback")
    public RerankResponse rerank(String query, List<String> documents, int topN) {
        var request = new RerankRequest(
            "BAAI/bge-reranker-v2-m3",
            query,
            documents,
            topN,
            false  // return_documents
        );

        return rerankRestClient.post()
            .uri("/rerank")
            .body(request)
            .retrieve()
            .body(RerankResponse.class);
    }

    // Fallbacks
    private RerankResponse rateLimitFallback(String query, List<String> documents,
                                              int topN, Exception e) {
        throw new AlexandriaException(ErrorCategory.SERVICE_UNAVAILABLE,
            "Rate limit exceeded for Infinity API. Please retry later.", e);
    }

    private RerankResponse bulkheadFallback(String query, List<String> documents,
                                             int topN, Exception e) {
        throw new AlexandriaException(ErrorCategory.SERVICE_UNAVAILABLE,
            "Too many concurrent requests to Infinity API.", e);
    }

    private RerankResponse rerankFallback(String query, List<String> documents,
                                           int topN, Exception e) {
        throw new AlexandriaException(ErrorCategory.SERVICE_UNAVAILABLE,
            "Infinity reranking service unavailable after retries.", e);
    }
}
```

**Configuration YAML:**

```yaml
resilience4j:
  ratelimiter:
    instances:
      infinityApi:
        limit-for-period: 10
        limit-refresh-period: 1s
        timeout-duration: 30s

  bulkhead:
    instances:
      infinityApi:
        max-concurrent-calls: 5
        max-wait-duration: 10s
```

### Fichiers a Modifier/Creer

- [x] `src/main/java/dev/alexandria/config/ResilienceConfig.java` - Ajouter beans RateLimiter et Bulkhead
- [x] `src/main/java/dev/alexandria/adapters/InfinityRerankClient.java` - Ajouter annotations
- [x] `src/main/resources/application.yml` - Configuration resilience4j

### Criteres d'Acceptation

- [ ] RateLimiter configure a 10 req/sec pour `infinityApi`
- [ ] Bulkhead limite a 5 appels concurrents
- [ ] Tests verifiant le comportement sous charge: 20 requetes en 1 seconde, max 10 passent immediatement
- [ ] Fallbacks retournent `AlexandriaException(SERVICE_UNAVAILABLE)`

### Statut

- [ ] Corrige

---

## F3: No Auth on /mcp Endpoint

**ID:** F3
**Severite:** CRITICAL
**Categorie:** Security

### Description du Probleme

L'endpoint MCP `/mcp` est expose sans authentification. N'importe qui avec acces reseau peut executer des recherches et ingerer des documents.

### Impact si Non Corrige

- Acces non autorise aux donnees indexees
- Ingestion de contenu malveillant
- Denial of Service via requetes massives
- Exposition de donnees sensibles dans les chunks

### Solution Concrete

**Fichier a creer:** `src/main/java/dev/alexandria/security/ApiKeyAuthFilter.java`

```java
package dev.alexandria.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

/**
 * Filtre d'authentification par API Key pour les endpoints MCP.
 * Utilise une comparaison timing-safe pour prevenir les timing attacks.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String validApiKey;

    public ApiKeyAuthFilter(String validApiKey) {
        this.validApiKey = validApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = extractApiKey(request);

        if (apiKey == null || apiKey.isBlank()) {
            sendUnauthorized(response, "Missing API Key");
            return;
        }

        // Comparaison timing-safe
        if (!timingSafeEquals(apiKey, validApiKey)) {
            sendUnauthorized(response, "Invalid API Key");
            return;
        }

        // Creer l'authentication
        var authentication = new UsernamePasswordAuthenticationToken(
            "mcp-client",
            null,
            Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private String extractApiKey(HttpServletRequest request) {
        // Essayer X-API-Key header
        String apiKey = request.getHeader(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }

        // Essayer Authorization: Bearer <key>
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Ne pas filtrer les endpoints de sante
        return path.startsWith("/actuator/health") || path.equals("/error");
    }

    private boolean timingSafeEquals(String provided, String expected) {
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(providedBytes, expectedBytes);
    }

    private void sendUnauthorized(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
            "{\"error\": \"Unauthorized\", \"message\": \"" + message + "\"}"
        );
    }
}
```

**Fichier a creer:** `src/main/java/dev/alexandria/security/SecurityConfig.java`

```java
package dev.alexandria.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${alexandria.security.api-key}")
    private String apiKey;

    /**
     * Securite pour les endpoints Actuator.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").permitAll()
                .anyRequest().denyAll()
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    /**
     * Securite pour les endpoints MCP - API Key requise.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/mcp/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(
                new ApiKeyAuthFilter(apiKey),
                AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"error\":\"Unauthorized\",\"message\":\"" +
                        authException.getMessage() + "\"}"
                    );
                })
            );

        return http.build();
    }

    /**
     * Securite par defaut pour les autres endpoints.
     */
    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll()
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
```

**Configuration YAML:**

```yaml
alexandria:
  security:
    api-key: ${ALEXANDRIA_API_KEY}  # Obligatoire via env var
```

**Dependance Maven a ajouter:**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### Fichiers a Modifier/Creer

- [x] `src/main/java/dev/alexandria/security/ApiKeyAuthFilter.java` - Filtre d'authentification
- [x] `src/main/java/dev/alexandria/security/SecurityConfig.java` - Configuration Spring Security
- [x] `src/main/resources/application.yml` - Propriete `alexandria.security.api-key`
- [x] `pom.xml` - Dependance spring-boot-starter-security

### Criteres d'Acceptation

- [ ] Requete sans `X-API-Key` retourne 401 Unauthorized
- [ ] Requete avec mauvaise cle retourne 401 Unauthorized
- [ ] Requete avec bonne cle passe et execute l'outil MCP
- [ ] `/actuator/health` reste accessible sans authentification
- [ ] Configuration Claude Desktop documentee avec `X-API-Key`

### Statut

- [ ] Corrige

---
