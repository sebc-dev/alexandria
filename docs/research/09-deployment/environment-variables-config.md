# Configuration par variables d'environnement dans Spring Boot 3.5.x

Pour Alexandria (Spring Boot 3.5.9 + Java 25 LTS), les variables d'environnement offrent une flexibilité maximale grâce au **relaxed binding** de Spring Boot. La règle fondamentale : en propriétés YAML, utilisez `kebab-case` ; en environnement, utilisez `SCREAMING_SNAKE_CASE` avec suppression des tirets (pas de remplacement). Cette approche respecte le Twelve-Factor App et sépare configuration et code.

## Le relaxed binding transforme automatiquement les variables

Spring Boot 3.5.x applique un algorithme de transformation précis. Pour convertir une propriété canonique en variable d'environnement : remplacez les points par des underscores, **supprimez** les tirets (ne les remplacez pas), puis passez en majuscules.

| Propriété Spring (canonical) | Variable d'environnement |
|------------------------------|-------------------------|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.hikari.maximum-pool-size` | `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE` |
| `infinity.base-url` | `INFINITY_BASEURL` |
| `infinity.api-key` | `INFINITY_APIKEY` |

**Attention critique** : `INFINITY_BASE_URL` ne fonctionne **pas** car l'underscore avant URL serait interprété comme un séparateur de niveau. Utilisez `INFINITY_BASEURL` sans underscore entre base et url.

Pour les **listes**, Spring Boot utilise une notation spéciale avec underscores numériques : `MY_LIST_0_VALUE` correspond à `my.list[0].value`.

## Conventions de nommage pour les propriétés applicatives

Spring Boot recommande officiellement le format **lowercase kebab-case** pour les fichiers de configuration (`infinity.base-url`). Pour les variables custom d'Alexandria, deux approches valides existent.

**Approche préfixée recommandée** : groupez vos propriétés sous un préfixe applicatif cohérent. Cela facilite l'identification, évite les collisions avec Spring, et permet l'utilisation de `@ConfigurationProperties` type-safe :

```yaml
# application.yml
alexandria:
  infinity:
    base-url: ${ALEXANDRIA_INFINITY_BASEURL:http://localhost:7997}
    api-key: ${ALEXANDRIA_INFINITY_APIKEY}
    timeout: ${ALEXANDRIA_INFINITY_TIMEOUT:30000}
  database:
    url: ${ALEXANDRIA_DATABASE_URL:jdbc:postgresql://localhost:5432/alexandria}
```

**Approche directe** : pour des services tiers standards, restez proche des conventions Spring existantes. `SPRING_DATASOURCE_URL` est immédiatement reconnaissable par tout développeur Spring.

## @ConfigurationProperties vs @Value : choisissez selon le contexte

La différence majeure réside dans le **relaxed binding complet** de `@ConfigurationProperties` contre le support **limité** de `@Value`.

| Critère | @ConfigurationProperties | @Value |
|---------|-------------------------|--------|
| Relaxed binding | ✅ Complet | ⚠️ Limité (forme canonique requise) |
| Variables d'environnement | ✅ Recommandé | ❌ Déconseillé |
| Type-safety | ✅ Avec validation JSR-303 | ❌ Non |
| SpEL expressions | ❌ Non | ✅ Oui |
| Configuration immutable | ✅ Records Java | ❌ Non |

**Pour Alexandria**, privilégiez `@ConfigurationProperties` avec Records Java 25 pour l'immutabilité :

```java
@ConfigurationProperties(prefix = "alexandria.infinity")
public record InfinityProperties(
    String baseUrl,
    String apiKey,
    @DefaultValue("30000") int timeout
) {}

// Activation
@Configuration
@EnableConfigurationProperties(InfinityProperties.class)
public class AlexandriaConfig {}
```

Réservez `@Value` uniquement pour des valeurs isolées avec expressions SpEL : `@Value("#{${timeout:30} * 1000}")`.

## Spring Boot ne supporte pas nativement les fichiers .env

Contrairement à Node.js ou Python, Spring Boot n'intègre pas de support natif .env. L'issue GitHub #24229 reste en "pending-design-work" sans date prévue. Trois solutions pratiques existent pour le développement local.

**Solution 1 : spring-dotenv (recommandée)**

```xml
<dependency>
    <groupId>me.paulschwarz</groupId>
    <artifactId>springboot3-dotenv</artifactId>
    <version>5.0.1</version>
    <optional>true</optional>
</dependency>
```

Cette librairie s'auto-configure, respecte le relaxed binding Spring, et garantit que les variables d'environnement système ont toujours priorité sur le fichier .env (sécurité production).

**Solution 2 : spring.config.import natif**

```properties
# application.properties
spring.config.import=optional:file:.env[.properties]
```

Le hint `[.properties]` indique à Spring de parser le .env comme un fichier properties standard. Limité mais sans dépendance externe.

**Solution 3 : profils Spring (alternative sans .env)**

Créez `application-local.properties` contenant vos valeurs de développement, ajoutez-le au `.gitignore`, et activez avec `SPRING_PROFILES_ACTIVE=local`. Cette approche reste 100% Spring native.

**Structure .gitignore recommandée** :
```gitignore
# Configuration locale - JAMAIS committée
.env
.env.local
application-local.properties
application-local.yml

# Template documentant les variables - TOUJOURS committée
!.env.example
```

## Gestion des secrets en production mono-serveur self-hosted

Pour un déploiement Alexandria mono-serveur, évitez la complexité de Vault sauf si vous avez déjà l'infrastructure. Voici les options classées par rapport complexité/sécurité.

**systemd EnvironmentFile (★ recommandé pour Linux bare-metal)**

```ini
# /etc/systemd/system/alexandria.service
[Unit]
Description=Alexandria MCP Server
After=network.target postgresql.service

[Service]
User=alexandria
EnvironmentFile=/etc/alexandria/secrets.conf
ExecStart=/usr/bin/java -jar /opt/alexandria/alexandria.jar
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

```bash
# /etc/alexandria/secrets.conf (chmod 600, chown root:root)
ALEXANDRIA_INFINITY_APIKEY=sk-runpod-xxxxx
SPRING_DATASOURCE_PASSWORD=secure-db-password
```

**Docker Compose secrets (★ recommandé pour Docker)**

```yaml
# docker-compose.yml
services:
  alexandria:
    image: alexandria:latest
    environment:
      SPRING_CONFIG_IMPORT: configtree:/run/secrets/
    secrets:
      - db_password
      - infinity_api_key

secrets:
  db_password:
    file: ./secrets/db_password.txt
  infinity_api_key:
    file: ./secrets/infinity_api_key.txt
```

Spring Boot lit automatiquement les fichiers dans `/run/secrets/` grâce à `configtree:`. Un fichier `/run/secrets/spring.datasource.password` devient la propriété `spring.datasource.password`.

**Jasypt pour chiffrement at-rest** : si vous devez versionner des configurations avec secrets (déconseillé mais parfois nécessaire), Jasypt permet de chiffrer les valeurs sensibles avec une clé maître fournie au démarrage.

## Configuration complète pour Alexandria

Voici la structure recommandée intégrant toutes les bonnes pratiques.

**Fichier .env.example (à committer)** :
```bash
# ============================================
# Alexandria MCP Server - Environment Variables
# Copy to .env and fill with actual values
# ============================================

# --- Infinity/RunPod Embeddings ---
ALEXANDRIA_INFINITY_BASEURL=https://api.runpod.ai/v2/your-endpoint
ALEXANDRIA_INFINITY_APIKEY=your_runpod_api_key_here
ALEXANDRIA_INFINITY_TIMEOUT=30000

# --- PostgreSQL 18 + pgvector ---
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/alexandria
SPRING_DATASOURCE_USERNAME=alexandria
SPRING_DATASOURCE_PASSWORD=your_secure_password_here

# --- Application Settings ---
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=local
```

**Fichier application.yml** :
```yaml
spring:
  application:
    name: alexandria
  
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/alexandria}
    username: ${SPRING_DATASOURCE_USERNAME:alexandria}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: ${SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE:10}
  
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

# Configuration Alexandria custom
alexandria:
  infinity:
    base-url: ${ALEXANDRIA_INFINITY_BASEURL:http://localhost:7997}
    api-key: ${ALEXANDRIA_INFINITY_APIKEY}
    timeout: ${ALEXANDRIA_INFINITY_TIMEOUT:30000}
    embedding-model: ${ALEXANDRIA_INFINITY_EMBEDDINGMODEL:BAAI/bge-base-en-v1.5}
    rerank-model: ${ALEXANDRIA_INFINITY_RERANKMODEL:BAAI/bge-reranker-base}
  
  mcp:
    sse:
      enabled: true
      path: /mcp/sse

server:
  port: ${SERVER_PORT:8080}

# Sécurité Actuator - CRITIQUE
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    env:
      enabled: false
    configprops:
      enabled: false
    heapdump:
      enabled: false
  server:
    port: 8081
    address: 127.0.0.1
```

**Classe de configuration type-safe** :
```java
@ConfigurationProperties(prefix = "alexandria.infinity")
@Validated
public record InfinityProperties(
    @NotBlank String baseUrl,
    @NotBlank String apiKey,
    @DefaultValue("30000") @Min(1000) int timeout,
    @DefaultValue("BAAI/bge-base-en-v1.5") String embeddingModel,
    @DefaultValue("BAAI/bge-reranker-base") String rerankModel
) {}
```

## Sécurité : prévenir les fuites de secrets

Spring Boot 3.5.x masque **toutes** les valeurs dans `/actuator/env` par défaut (`show-values=never`). Ne changez jamais ce comportement en production.

**Configuration logging sécurisée** dans `logback-spring.xml` :
```xml
<configuration>
    <appender name="MASKED" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %replace(%msg){'(apiKey|password|secret|token)=\S+', '$1=[REDACTED]'}%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="MASKED"/>
    </root>
</configuration>
```

**Anti-patterns à éviter absolument** :
- Ne jamais logger de `@Value` sur des secrets : `log.info("Key: {}", apiKey)` expose le secret
- Exclure les champs sensibles de `toString()` dans vos classes de configuration
- Ne jamais inclure de secrets dans les messages d'exception
- Désactiver `spring.main.banner-mode` en production pour éviter les fuites d'information de version

## Conclusion

Pour Alexandria en mono-serveur self-hosted, la configuration optimale combine **systemd EnvironmentFile** (ou Docker secrets) pour les secrets production, **spring-dotenv** pour le développement local, et **@ConfigurationProperties avec Records** pour un binding type-safe. Utilisez systématiquement le préfixe `alexandria.` pour vos propriétés custom afin d'éviter les collisions et faciliter l'identification. Le point critique reste le relaxed binding : `INFINITY_BASEURL` fonctionne, `INFINITY_BASE_URL` échoue silencieusement car l'underscore supplémentaire crée un niveau de hiérarchie fantôme.