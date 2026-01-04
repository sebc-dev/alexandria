# Sécuriser un serveur MCP Spring AI : guide complet pour mono-utilisateur

La sécurisation d'un serveur MCP dépend entièrement du contexte de déploiement. Pour un serveur Alexandria RAG mono-utilisateur self-hosted, **la recommandation varie de "aucune sécurité requise" (localhost) à "authentification API Key obligatoire" (exposition internet)**. Le protocole MCP supporte nativement OAuth 2.1 et les Bearer tokens, mais Spring AI MCP SDK 1.1.x ne fournit aucune sécurité intégrée par défaut — tout passe par le module communautaire `mcp-server-security`.

## Le protocole MCP définit OAuth 2.1 comme standard d'authentification

Le protocole MCP (spécification novembre 2025) intègre un framework d'autorisation complet basé sur **OAuth 2.1**, bien que son implémentation soit optionnelle. Chaque requête HTTP doit inclure le header `Authorization: Bearer <access-token>` selon la spécification. Les serveurs répondent avec un code **401 Unauthorized** et un header `WWW-Authenticate` contenant l'URL de découverte des métadonnées OAuth.

Pour le transport **STDIO** (serveurs locaux), la spécification recommande explicitement de récupérer les credentials depuis les variables d'environnement plutôt que d'utiliser OAuth. Cette approche est pertinente pour les déploiements localhost où l'isolation processus fournit déjà une couche de sécurité.

Points critiques de la spécification sécurité MCP :
- Les tokens doivent être validés pour l'audience spécifique (RFC 8707 Resource Indicators)
- PKCE obligatoire pour le flow OAuth 2.1
- Les API Keys ne sont pas formellement spécifiées pour HTTP, mais restent utilisables via headers custom
- Validation du header `Origin` obligatoire pour prévenir les attaques DNS rebinding

## Spring AI MCP SDK : zéro sécurité par défaut, tout est manuel

Le starter `spring-ai-starter-mcp-server-webmvc` expose les endpoints `/sse` et `/mcp/message` **sans aucune protection**. La sécurité provient exclusivement du module communautaire `org.springaicommunity:mcp-server-security:0.0.5`, compatible uniquement avec Spring AI 1.1.x et WebMVC (pas WebFlux).

Deux mécanismes d'authentification sont disponibles via ce module :

**OAuth 2.0 JWT** utilise `McpServerOAuth2Configurer` :
```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .with(McpServerOAuth2Configurer.mcpServerOAuth2(), mcp -> {
            mcp.authorizationServer("https://auth-server.example.com");
            mcp.validateAudienceClaim(true);
        })
        .build();
}
```

**API Key** utilise `McpServerApiKeyConfigurer` avec le header `X-API-key` au format `<id>.<secret>` :
```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .with(McpServerApiKeyConfigurer.mcpServerApiKey(), apiKey -> {
            apiKey.apiKeyRepository(new InMemoryApiKeyEntityRepository<>(
                List.of(ApiKeyEntityImpl.builder()
                    .id("api01").secret("supersecretkey").build())
            ));
        })
        .build();
}
```

Limitation importante : le transport SSE legacy est **déprécié** et non supporté par mcp-server-security. La configuration `spring.ai.mcp.server.protocol=STREAMABLE` est recommandée.

## Configuration concrète pour Alexandria RAG selon le contexte

### Localhost uniquement : sécurité optionnelle

Pour un serveur bindé exclusivement sur `127.0.0.1`, aucune authentification n'est strictement nécessaire. La sécurité repose sur l'isolation réseau et les permissions système.

```yaml
server:
  address: 127.0.0.1
  port: 8080
spring:
  ai:
    mcp:
      server:
        name: alexandria-rag
        protocol: STREAMABLE
```

Le client Claude Code se configure simplement :
```bash
claude mcp add --transport http alexandria http://localhost:8080/mcp
```

### Exposition LAN : API Key recommandée

Sur réseau local, une API Key simple suffit pour éviter les accès non autorisés depuis d'autres machines du réseau.

```yaml
# application.yml
server:
  port: 8080
spring:
  ai:
    mcp:
      server:
        name: alexandria-rag
        protocol: STREAMABLE
```

Configuration Spring Security minimale :
```java
@Configuration
@EnableWebSecurity
public class McpSecurityConfig {
    
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .with(McpServerApiKeyConfigurer.mcpServerApiKey(), apiKey -> 
                apiKey.apiKeyRepository(new InMemoryApiKeyEntityRepository<>(List.of(
                    ApiKeyEntityImpl.builder()
                        .id("alexandria")
                        .secret("${ALEXANDRIA_API_SECRET}")
                        .build()
                )))
            )
            .build();
    }
}
```

Configuration Claude Code côté client :
```bash
claude mcp add --transport http alexandria http://192.168.1.100:8080/mcp \
  --header "X-API-key: alexandria.${ALEXANDRIA_API_SECRET}"
```

### Exposition internet : sécurité complète obligatoire

L'exposition publique exige HTTPS, authentification robuste, CORS restrictif et rate limiting.

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class McpProductionSecurityConfig {

    @Value("${mcp.oauth.issuer-uri}")
    private String issuerUri;
    
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // HTTPS obligatoire
            .requiresChannel(channel -> channel.anyRequest().requiresSecure())
            
            // CORS restrictif
            .cors(cors -> cors.configurationSource(corsConfig()))
            
            // Pas de session, pas de CSRF
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Endpoints publics vs protégés
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/.well-known/**").permitAll();
                auth.requestMatchers("/actuator/health").permitAll();
                auth.anyRequest().authenticated();
            })
            
            // OAuth 2.0 JWT
            .with(McpServerOAuth2Configurer.mcpServerOAuth2(), mcp -> {
                mcp.authorizationServer(issuerUri);
                mcp.validateAudienceClaim(true);
            })
            
            .build();
    }
    
    @Bean
    CorsConfigurationSource corsConfig() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://claude.ai"));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

## Configuration client Claude Code avec authentification

Claude Code supporte nativement plusieurs méthodes d'authentification pour les serveurs MCP distants.

**Via CLI avec header :**
```bash
# Bearer token
claude mcp add --transport http alexandria https://alexandria.example.com/mcp \
  --header "Authorization: Bearer ${ALEXANDRIA_TOKEN}"

# API Key custom
claude mcp add --transport http alexandria https://alexandria.example.com/mcp \
  --header "X-API-key: alexandria.mysecretkey"
```

**Via fichier .mcp.json (scope projet) :**
```json
{
  "mcpServers": {
    "alexandria": {
      "type": "http",
      "url": "${ALEXANDRIA_URL:-https://alexandria.example.com}/mcp",
      "headers": {
        "Authorization": "Bearer ${ALEXANDRIA_TOKEN}"
      }
    }
  }
}
```

**OAuth interactif** pour les serveurs configurés avec OAuth 2.0 :
```bash
claude mcp add --transport http alexandria https://alexandria.example.com/mcp
# Puis dans Claude Code: /mcp → sélectionner le serveur → suivre le flow OAuth
```

## Dépendances Maven requises

```xml
<dependencies>
    <!-- Spring AI MCP Server -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>
    
    <!-- MCP Security (communautaire) -->
    <dependency>
        <groupId>org.springaicommunity</groupId>
        <artifactId>mcp-server-security</artifactId>
        <version>0.0.5</version>
    </dependency>
    
    <!-- Spring Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- OAuth2 Resource Server (si OAuth requis) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>
</dependencies>
```

## Matrice de décision pour Alexandria RAG

| Contexte | Authentification | HTTPS | CORS | Complexité |
|----------|-----------------|-------|------|------------|
| **Localhost only** | Aucune | Non | Non | Minimale |
| **LAN privé** | API Key | Optionnel | Non | Faible |
| **VPN/Tunnel** | API Key | Recommandé | Non | Moyenne |
| **Internet public** | OAuth 2.0 JWT | Obligatoire | Oui | Élevée |

## Conclusion et recommandation pour Alexandria

Pour un projet mono-utilisateur self-hosted comme Alexandria RAG, **l'API Key authentication via `mcp-server-security` offre le meilleur ratio sécurité/complexité** dès qu'une exposition réseau est envisagée. Cette approche évite la complexité d'un serveur OAuth tout en protégeant efficacement les endpoints MCP.

La configuration recommandée pour démarrer : bindez sur `127.0.0.1` sans sécurité pour le développement, puis ajoutez l'API Key authentication avant toute exposition LAN. Réservez OAuth 2.0 pour un éventuel déploiement internet avec reverse proxy HTTPS.

Points de vigilance : le module `mcp-server-security` reste un projet communautaire non officiellement supporté par Spring AI, et le transport SSE legacy n'est plus supporté — utilisez systématiquement `spring.ai.mcp.server.protocol=STREAMABLE`.