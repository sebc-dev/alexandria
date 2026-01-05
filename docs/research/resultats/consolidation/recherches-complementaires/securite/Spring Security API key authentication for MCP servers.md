# Spring Security API key authentication for MCP servers

A dedicated **mcp-server-security** library exists for Spring AI MCP SDK, offering both OAuth 2.0 and API key authentication with minimal configuration. For simpler deployments, a custom `OncePerRequestFilter` provides full control with approximately **50 lines of code**. Both approaches work with Spring Boot 3.5.x and Spring Security 6.x using stateless session management and proper filter chain positioning.

The Spring AI community library (`org.springaicommunity:mcp-server-security:0.0.5`) handles MCP protocol compliance automatically, while a custom filter gives you precise control over authentication logic and error responses. For most MCP server deployments, the **custom filter approach** offers the best balance of simplicity and flexibility unless you need OAuth 2.0 flows.

---

## Complete OncePerRequestFilter implementation

The filter validates the `X-API-Key` header, sets the authentication context, and handles failures with proper HTTP status codes. Position it **before `AnonymousAuthenticationFilter`** to prevent unauthenticated requests from being marked as anonymous.

```java
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

public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private final String validApiKey;

    public ApiKeyAuthFilter(String validApiKey) {
        this.validApiKey = validApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            sendUnauthorized(response, "Missing API Key");
            return;
        }

        // Timing-safe comparison prevents timing attacks
        if (!timingSafeEquals(apiKey, validApiKey)) {
            sendUnauthorized(response, "Invalid API Key");
            return;
        }

        // Three-arg constructor marks authentication as authenticated
        var authentication = new UsernamePasswordAuthenticationToken(
                "api-client",              // principal
                null,                      // credentials (not needed post-auth)
                Collections.emptyList()    // authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
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

For the **Authorization: Bearer** pattern, extract the token after the prefix:

```java
String authHeader = request.getHeader("Authorization");
if (authHeader != null && authHeader.startsWith("Bearer ")) {
    String apiKey = authHeader.substring(7); // "Bearer ".length()
    // validate apiKey...
}
```

---

## SecurityFilterChain configuration for Spring Security 6.x

Spring Security 6.x requires `SecurityFilterChain` beans with lambda DSL configuration. The deprecated `WebSecurityConfigurerAdapter` is no longer available.

```java
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

    @Value("${mcp.security.api-key}")
    private String apiKey;

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
}
```

**Key configuration decisions:**
- **CSRF disabled** for stateless APIs (tokens in headers aren't auto-attached like cookies)
- **SessionCreationPolicy.STATELESS** ensures no HTTP session is created
- **Filter positioned before AnonymousAuthenticationFilter** to authenticate before anonymous assignment
- **Separate filter chains** allow different security rules for actuator vs MCP endpoints

---

## application.yml configuration

```yaml
mcp:
  security:
    api-key: ${MCP_API_KEY}  # Never commit plaintext keys

spring:
  ai:
    mcp:
      server:
        name: my-mcp-server
        transport: STREAMABLE_HTTP

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when_authorized
  health:
    livenessState:
      enabled: true
    readinessState:
      enabled: true
```

For production, store the API key in HashiCorp Vault or AWS Secrets Manager:

```yaml
spring:
  cloud:
    vault:
      uri: ${VAULT_URI:https://vault.example.com:8200}
      authentication: kubernetes
      kv:
        enabled: true
        backend: secret
  config:
    import: vault://
```

---

## mcp-server-security library vs custom filter

A community library exists specifically for Spring AI MCP SDK security. The comparison below helps determine which approach fits your requirements.

| Aspect | Custom OncePerRequestFilter | mcp-server-security library |
|--------|---------------------------|----------------------------|
| **Maven coordinate** | N/A (custom code) | `org.springaicommunity:mcp-server-security:0.0.5` |
| **Setup complexity** | ~50 lines of code | Add dependency + 5 lines config |
| **OAuth 2.0 support** | Must implement manually | Built-in via `McpServerOAuth2Configurer` |
| **API key support** | Full control | Ready via `McpApiKeyConfigurer` |
| **Protected Resource Metadata** | Manual implementation | Automatic (RFC 9728 compliant) |
| **MCP spec compliance** | Requires spec knowledge | Follows MCP 2025-06-18 spec |
| **Client testing** | Extensive debugging | Pre-tested with Claude, Cursor |
| **WebFlux support** | Possible | WebMVC only |
| **Maintenance burden** | You maintain it | Community maintained |

**Using the mcp-server-security library for API key auth:**

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>mcp-server-security</artifactId>
    <version>0.0.5</version>
</dependency>
```

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
        .with(McpApiKeyConfigurer.mcpServerApiKey(), apiKey -> {
            apiKey.apiKeyRepository(myApiKeyRepository());
            apiKey.headerName("X-API-Key");
        })
        .build();
}
```

**Recommendation:** Use the custom filter for simple single-key API authentication. Use `mcp-server-security` if you need OAuth 2.0 flows, Dynamic Client Registration, or guaranteed MCP specification compliance.

---

## Production hardening essentials

### Timing-safe key comparison

Standard `.equals()` short-circuits on first mismatch, leaking timing information. Use `MessageDigest.isEqual()` for constant-time comparison:

```java
private boolean timingSafeEquals(String provided, String expected) {
    if (provided == null || expected == null) return false;
    byte[] a = provided.getBytes(StandardCharsets.UTF_8);
    byte[] b = expected.getBytes(StandardCharsets.UTF_8);
    return MessageDigest.isEqual(a, b);  // Constant-time since Java 6u17
}
```

### Multiple API keys with client identification

Store hashed keys in a database with client metadata:

```java
@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {
    @Id
    private String keyId;
    
    @Column(nullable = false)
    private String keyHash;  // SHA-256 hash, never plaintext
    
    private String clientName;
    private boolean active = true;
    private Instant expiresAt;
    private Long rateLimitPerMinute;
}
```

```java
public boolean validateApiKey(String providedKey) {
    String hash = hashKey(providedKey);
    return apiKeyRepository.findByKeyHashAndActiveTrue(hash)
        .filter(key -> key.getExpiresAt() == null || 
                       Instant.now().isBefore(key.getExpiresAt()))
        .isPresent();
}

private String hashKey(String plainKey) {
    return Hashing.sha256()
        .hashString(plainKey, StandardCharsets.UTF_8)
        .toString();
}
```

### Key rotation without downtime

Support overlapping validity periods during rotation:

```java
public ApiKeyRotationResult rotateKey(String clientId, Duration gracePeriod) {
    ApiKeyEntity currentKey = repository.findByClientIdAndActiveTrue(clientId)
        .orElseThrow();
    
    // Create new key
    String newSecret = generateSecureSecret();
    ApiKeyEntity newKey = new ApiKeyEntity();
    newKey.setKeyHash(hashKey(newSecret));
    newKey.setClientId(clientId);
    newKey.setActive(true);
    
    // Old key remains valid during grace period
    currentKey.setExpiresAt(Instant.now().plus(gracePeriod));
    
    repository.saveAll(List.of(newKey, currentKey));
    return new ApiKeyRotationResult(newKey.getKeyId(), newSecret);
}
```

### Never log API keys

Configure Logback to mask sensitive headers:

```java
public class MaskingPatternLayout extends PatternLayout {
    private static final Pattern API_KEY_PATTERN = 
        Pattern.compile("(?i)(x-api-key|authorization)[=:]\\s*([^\\s,}]+)");
    
    @Override
    public String doLayout(ILoggingEvent event) {
        String message = super.doLayout(event);
        return API_KEY_PATTERN.matcher(message)
            .replaceAll("$1: ***MASKED***");
    }
}
```

### Rate limiting per client

Integrate Bucket4j for per-key rate limiting:

```java
@Service
public class RateLimitService {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    public boolean tryConsume(String clientId, long rateLimit) {
        Bucket bucket = buckets.computeIfAbsent(clientId, id ->
            Bucket.builder()
                .addLimit(Bandwidth.builder()
                    .capacity(rateLimit)
                    .refillGreedy(rateLimit, Duration.ofMinutes(1))
                    .build())
                .build()
        );
        return bucket.tryConsume(1);
    }
}
```

---

## Filter chain positioning explained

Spring Security processes requests through an ordered filter chain. API key filters must execute **before `AnonymousAuthenticationFilter`** to prevent the security context from being populated with an anonymous authentication token.

| Filter order | Filter name | Purpose |
|-------------|-------------|---------|
| 1 | SecurityContextHolderFilter | Loads security context |
| 2 | HeaderWriterFilter | Adds security headers |
| 3 | CsrfFilter | CSRF protection |
| 4 | LogoutFilter | Logout handling |
| 5 | **Your ApiKeyAuthFilter** | **Place here** |
| 6 | UsernamePasswordAuthenticationFilter | Form login |
| 7 | BasicAuthenticationFilter | HTTP Basic |
| 8 | AnonymousAuthenticationFilter | Assigns anonymous if unauthenticated |
| 9 | ExceptionTranslationFilter | Converts exceptions to responses |
| 10 | AuthorizationFilter | Enforces access rules |

Use `addFilterBefore(filter, AnonymousAuthenticationFilter.class)` to position correctly.

---

## Conclusion

For Spring Boot 3.5.x MCP server API key authentication, **start with the custom `OncePerRequestFilter` approach** shown above—it requires minimal code, provides full control, and integrates cleanly with Spring Security 6.x's `SecurityFilterChain` pattern. The key implementation details are timing-safe comparison using `MessageDigest.isEqual()`, stateless session management, CSRF disabled for the API, and proper filter positioning before `AnonymousAuthenticationFilter`.

If your requirements expand to include OAuth 2.0, Dynamic Client Registration, or you need guaranteed MCP protocol compliance, the `mcp-server-security` library (`org.springaicommunity:mcp-server-security:0.0.5`) provides a production-ready solution with minimal configuration. The library handles Protected Resource Metadata advertising and works with Claude Desktop and Cursor out of the box.

For production deployments, prioritize **secrets management** (Vault/AWS Secrets Manager over environment variables), **timing-safe comparison**, **hashed key storage** for multi-key scenarios, and **never logging actual key values**.