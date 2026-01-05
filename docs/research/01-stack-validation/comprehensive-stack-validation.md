# Validation exhaustive de la stack Alexandria RAG Server

L'analyse complète de la stack technique révèle **3 composants à mettre à jour immédiatement**, une incompatibilité critique avec Spring Boot 4.x, et plusieurs optimisations recommandées. La majorité des versions spécifiées sont correctes, mais des mises à jour de sécurité récentes nécessitent attention.

## 1. Tableau récapitulatif des versions

| Composant | Version spécifiée | Dernière version | Action | Raison |
|-----------|------------------|------------------|--------|--------|
| **Java 25 LTS** | 25.0.1 | 25.0.2 (20 Jan 2026) | ⬆️ | Patch de sécurité critique imminent |
| **Spring Boot** | 3.5.9 | 3.5.9 | ✅ | Version courante |
| **Spring Framework** | 6.2.x | 6.2.15 | ✅ | Inclus dans Boot 3.5.9 |
| **Spring AI MCP SDK** | 1.1.2 GA | 1.1.2 GA | ✅ | Version courante confirmée |
| **spring-retry** | 2.0.11 | **2.0.12** | ⬆️ | Mise à jour mineure disponible |
| **Langchain4j core** | 1.10.0 | 1.10.0 | ✅ | Version courante |
| **langchain4j-pgvector** | 1.10.0-beta18 | 1.10.0-beta18 | ⚠️ | Toujours en beta, pas de GA |
| **langchain4j-spring-boot-starter** | 1.10.0-beta18 | 1.10.0-beta18 | ⚠️ | Toujours en beta, pas de GA |
| **langchain4j-open-ai** | 1.10.0 | 1.10.0 | ✅ | Version courante |
| **PostgreSQL** | 18.1 | 18.1 | ✅ | Version courante |
| **pgvector** | 0.8.1 | 0.8.1 | ✅ | Version courante |
| **Testcontainers** | 2.0.2 | **2.0.3** | ⬆️ | Mise à jour mineure disponible |
| **WireMock** | 3.10.0 | **3.13.2** | ⬆️ | 3 versions de retard |

## 2. Matrice de compatibilité croisée finale

| Combinaison | Statut | Notes |
|-------------|--------|-------|
| Java 25 + Spring Boot 3.5.9 | ✅ Compatible | `TWENTY_FIVE` ajouté à JavaVersion dans 3.5.7+ |
| Java 25 + Langchain4j 1.10.0 | ✅ Compatible | Tests Java 25 ajoutés depuis v1.6.0 |
| Spring Boot 3.5.9 + Spring AI 1.1.2 | ✅ Compatible | Combinaison officiellement supportée |
| Spring Boot 3.5.9 + Langchain4j 1.10.0 | ✅ Compatible | Minimum requis: Boot 3.2+ |
| Spring Boot 3.5.9 + spring-retry 2.0.12 | ✅ Compatible | @EnableRetry toujours requis |
| langchain4j-pgvector + PostgreSQL 18.1 | ✅ Compatible | Via pgvector 0.8.1 |
| pgvector 0.8.1 + PostgreSQL 18.1 | ✅ Compatible | Image Docker disponible |
| Testcontainers 2.0.2 + Java 25 | ⚠️ Non certifié | Devrait fonctionner mais pas officiellement testé |
| Testcontainers 2.0.2 + PostgreSQL 18 | ✅ Compatible | Via image Docker postgres:18 |
| WireMock 3.10.0+ + Java 25 | ⚠️ Non certifié | Minimum Java 11, devrait fonctionner |
| **Langchain4j 1.10.0 + Spring Boot 4.x** | ❌ **Incompatible** | Pas encore compatible avec Boot 4/Jackson 3 |

## 3. Problèmes détectés classés par criticité

### CRITIQUE 🔴

**1. Langchain4j incompatible avec Spring Boot 4.x**
- Langchain4j 1.10.0 n'est **PAS compatible** avec Spring Boot 4.0
- Impact: Migration vers Boot 4.x bloquée tant que Langchain4j ne supporte pas Jackson 3 et Jakarta EE 11
- Source: InfoQ Java News Roundup du 22 décembre 2025
- **Action**: Rester sur Spring Boot 3.5.x pour ce projet

**2. Patch de sécurité Java 25.0.2 imminent**
- Oracle publie 25.0.2 le **20 janvier 2026**
- Version 25.0.1 n'est pas recommandée après cette date
- **Action**: Planifier mise à jour immédiate après le 20 janvier

### IMPORTANT 🟠

**3. WireMock 3.10.0 → 3.13.2 (3 versions de retard)**
- Manque des correctifs de sécurité et améliorations
- Nouvelles fonctionnalités: webhook scheduler, admin API améliorée
- **Action**: Mettre à jour vers 3.13.2

**4. Modules Langchain4j toujours en beta**
- `langchain4j-pgvector` et `langchain4j-spring-boot-starter` restent en beta18
- API peut changer avant la GA
- **Action**: Surveiller les releases, tester les mises à jour

**5. Testcontainers 2.x - Breaking changes depuis 1.x**
- JUnit 4 support supprimé
- Préfixes de modules changés (`testcontainers-postgresql`)
- Packages relocalisés
- **Action**: Vérifier migration si upgrade depuis 1.x

### MINEUR 🟡

**6. spring-retry 2.0.11 → 2.0.12**
- Mise à jour mineure disponible
- **Action**: Mettre à jour

**7. Testcontainers 2.0.2 → 2.0.3**
- Mise à jour mineure disponible
- **Action**: Mettre à jour

## 4. Points de vigilance validés

### Spring AI MCP SDK 1.1.2 ✅

| Élément | Statut |
|---------|--------|
| Version 1.1.2 GA | ✅ Confirmée (dernière stable) |
| Version 2.x | ⚠️ 2.0.0-M1 milestone uniquement (pour Boot 4.x) |
| Transport HTTP Streamable | ✅ Stable - `spring.ai.mcp.server.protocol=STREAMABLE` |
| @McpTool annotation | ✅ API stable |
| @McpResource annotation | ✅ API stable |
| @McpPrompt annotation | ✅ API stable |
| Capability: completion | ✅ Via @McpComplete |

### spring-retry 2.0.x ✅

```java
@Retryable(
    maxAttempts = 4,           // ✅ Confirmé
    backoff = @Backoff(
        delay = 100,           // ✅ Confirmé
        multiplier = 2,        // ✅ Confirmé
        maxDelay = 1000        // ✅ Confirmé
    )
)
```

- **@EnableRetry**: ✅ Toujours requis sur Spring Boot 3.5.x
- **Virtual Threads**: ✅ Compatible
- **Note**: Spring Framework 7 (Boot 4.x) intègre @Retryable nativement avec @EnableResilientMethods

### PostgreSQL/pgvector ✅

| Élément | Statut |
|---------|--------|
| pgvector 0.8.1 + PostgreSQL 18.1 | ✅ Compatible |
| Image Docker `pgvector/pgvector:0.8.1-pg18` | ✅ Existe |
| HNSW m=16, ef_construction=128 | ✅ Valide |
| `vector_cosine_ops` | ✅ Opérateur valide |
| `vector(1024)` | ✅ Recommandé pour précision maximale |
| `halfvec(1024)` | ⚠️ Alternative -50% stockage, légère perte recall |

**Configuration HNSW recommandée:**
```sql
CREATE INDEX ON embeddings USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 128);

-- Runtime tuning
SET hnsw.ef_search = 100;
SET hnsw.iterative_scan = on;  -- Nouveau 0.8.x pour filtres
```

### Langchain4j ✅

- `OpenAiEmbeddingModel.builder().baseUrl()` : ✅ Toujours supporté
- `PgVectorEmbeddingStore` API : ✅ Stable
- **Note**: Le module pgvector utilise IVFFlat par défaut, pas HNSW. Créer l'index HNSW manuellement.

## 5. Recommandations finales

1. **Rester sur Spring Boot 3.5.9** - Ne pas migrer vers 4.x tant que Langchain4j n'est pas compatible
2. **Mettre à jour WireMock** de 3.10.0 vers 3.13.2 immédiatement
3. **Planifier mise à jour Java 25.0.2** pour la semaine du 20 janvier 2026
4. **Créer index HNSW manuellement** plutôt que via Langchain4j pour optimiser les performances
5. **Utiliser `vector(1024)`** sauf si contraintes de stockage importantes
6. **Surveiller Langchain4j** pour la sortie GA des modules beta et compatibilité Boot 4.x

## 6. pom.xml corrigé

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.9</version>
        <relativePath/>
    </parent>
    
    <groupId>com.example</groupId>
    <artifactId>alexandria-rag-server</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>Alexandria RAG Server</name>
    <description>RAG Server with MCP and Langchain4j</description>
    
    <properties>
        <!-- Java -->
        <java.version>25</java.version>
        
        <!-- Spring Ecosystem -->
        <spring-ai.version>1.1.2</spring-ai.version>
        <spring-retry.version>2.0.12</spring-retry.version>
        
        <!-- Langchain4j -->
        <langchain4j.version>1.10.0</langchain4j.version>
        <langchain4j-beta.version>1.10.0-beta18</langchain4j-beta.version>
        
        <!-- Database -->
        <postgresql.version>42.7.4</postgresql.version>
        
        <!-- Testing -->
        <testcontainers.version>2.0.3</testcontainers.version>
        <wiremock.version>3.13.2</wiremock.version>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <!-- Spring AI BOM -->
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            
            <!-- Langchain4j BOM -->
            <dependency>
                <groupId>dev.langchain4j</groupId>
                <artifactId>langchain4j-bom</artifactId>
                <version>${langchain4j.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            
            <!-- Testcontainers BOM -->
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        
        <!-- Spring AI MCP -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
        </dependency>
        
        <!-- Spring Retry -->
        <dependency>
            <groupId>org.springframework.retry</groupId>
            <artifactId>spring-retry</artifactId>
            <version>${spring-retry.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>
        
        <!-- Langchain4j Core (GA) -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
        </dependency>
        
        <!-- Langchain4j PgVector (Beta) -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-pgvector</artifactId>
            <version>${langchain4j-beta.version}</version>
        </dependency>
        
        <!-- Langchain4j Spring Boot Starter (Beta) -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-spring-boot-starter</artifactId>
            <version>${langchain4j-beta.version}</version>
        </dependency>
        
        <!-- PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>${postgresql.version}</version>
            <scope>runtime</scope>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        
        <!-- Testcontainers 2.x (note: module prefix changed) -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        
        <!-- WireMock -->
        <dependency>
            <groupId>org.wiremock</groupId>
            <artifactId>wiremock</artifactId>
            <version>${wiremock.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <release>${java.version}</release>
                    <compilerArgs>
                        <arg>--enable-preview</arg>
                    </compilerArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

## Configuration application.properties recommandée

```properties
# Spring AI MCP Server
spring.ai.mcp.server.protocol=STREAMABLE
spring.ai.mcp.server.streamable-http.keep-alive-interval=30s

# Langchain4j OpenAI
langchain4j.open-ai.embedding-model.base-url=${OPENAI_BASE_URL:https://api.openai.com/v1}
langchain4j.open-ai.embedding-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.embedding-model.model-name=text-embedding-3-large
langchain4j.open-ai.embedding-model.dimensions=1024

# PostgreSQL pgvector
spring.datasource.url=jdbc:postgresql://localhost:5432/alexandria
spring.datasource.driver-class-name=org.postgresql.Driver
```

## Docker Compose pour PostgreSQL + pgvector

```yaml
version: '3.8'
services:
  postgres:
    image: pgvector/pgvector:0.8.1-pg18
    environment:
      POSTGRES_DB: alexandria
      POSTGRES_USER: alexandria
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U alexandria"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

## Conclusion

La stack Alexandria RAG Server est **globalement bien configurée** avec des versions récentes et compatibles. Les **actions prioritaires** sont: mise à jour de WireMock vers 3.13.2, planification du patch Java 25.0.2, et maintien sur Spring Boot 3.5.x en attendant la compatibilité Langchain4j avec Boot 4.x. Les modules Langchain4j en beta fonctionnent de manière stable mais surveillez les releases pour d'éventuels changements d'API avant la GA.