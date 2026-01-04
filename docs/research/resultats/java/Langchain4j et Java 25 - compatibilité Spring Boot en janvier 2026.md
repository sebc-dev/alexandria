# Langchain4j et Java 25 : compatibilité Spring Boot en janvier 2026

**Langchain4j 1.10.0 ne supporte pas encore Spring Boot 4.0.** Pour un projet RAG avec Java 25, la stack recommandée est **Spring Boot 3.5.x** avec Langchain4j 1.10.0, qui offre une compatibilité complète Java 25 tout en évitant les breaking changes de Jakarta EE 11 et Jackson 3. Les modules `langchain4j-pgvector` et `langchain4j-open-ai` sont compatibles avec PostgreSQL 18 + pgvector 0.8.1 et supportent les endpoints OpenAI-compatible comme Infinity.

---

## État des versions Langchain4j (décembre 2025)

Langchain4j a atteint sa **version 1.10.0 GA** pour les modules core, mais les **Spring Boot starters restent en beta** (1.10.0-beta18). Cette distinction est importante : les modules core (`langchain4j`, `langchain4j-open-ai`) sont considérés stables, tandis que les starters Spring peuvent encore subir des breaking changes.

| Module | Version | Statut |
|--------|---------|--------|
| langchain4j-bom | **1.10.0** | GA |
| langchain4j-core | 1.10.0 | GA |
| langchain4j-open-ai | 1.10.0 | GA |
| langchain4j-pgvector | 1.10.0-beta18 | Beta |
| langchain4j-spring-boot-starter | 1.10.0-beta18 | Beta |
| langchain4j-open-ai-spring-boot-starter | 1.10.0-beta18 | Beta |

Les exigences officielles de Langchain4j sont **Java 17 minimum** et **Spring Boot 3.2 minimum**. Aucune documentation officielle ne mentionne Spring Boot 4.x à ce jour.

---

## Spring Boot 3.5 vs 4.0 : quelle version pour Java 25 ?

Les deux branches Spring Boot supportent Java 25, mais avec des différences architecturales majeures.

**Spring Boot 3.5.9** (dernière version 3.x) maintient la compatibilité avec Jakarta EE 10 et Jackson 2, ce qui garantit une intégration transparente avec Langchain4j. Cette branche bénéficie d'un support OSS jusqu'en juin 2026 et d'un support commercial étendu jusqu'en 2032.

**Spring Boot 4.0.1** (sorti le 30 novembre 2025) introduit plusieurs ruptures incompatibles avec l'écosystème Langchain4j actuel :

| Changement | Spring Boot 3.5 | Spring Boot 4.0 |
|------------|-----------------|-----------------|
| Jakarta EE | 10 | **11** |
| Jackson | 2.x (`com.fasterxml.jackson`) | **3.x** (`tools.jackson`) |
| Spring Framework | 6.x | **7.x** |
| Servlet | 6.0 | **6.1** |
| Undertow | Supporté | **Supprimé** |

Le projet Seed4J (anciennement JHipster Lite) a explicitement **désactivé ses modules LangChain4j** en attendant la compatibilité Spring Boot 4.0, confirmant que cette intégration n'est pas encore validée par la communauté.

---

## Modules pgvector et OpenAI pour votre projet RAG

### langchain4j-pgvector 1.10.0-beta18

Ce module est **compatible avec PostgreSQL 18 et pgvector 0.8.1**. Le client Java `pgvector-java 0.1.6` inclus comme dépendance transitive fonctionne comme une abstraction JDBC sans couplage direct avec la version serveur de pgvector.

Dépendances transitives clés :
- `com.pgvector:pgvector` 0.1.6
- `org.postgresql:postgresql` 42.7.7 (compatible PostgreSQL 18)
- `jackson-databind` 2.20.1

### langchain4j-open-ai 1.10.0

Le support **baseUrl custom est pleinement fonctionnel**, ce qui permet l'intégration avec des endpoints OpenAI-compatible comme Infinity pour les embeddings BGE-M3 :

```java
OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
    .baseUrl("https://your-infinity-endpoint/v1")
    .apiKey("your-api-key")
    .modelName("BAAI/bge-m3")
    .build();
```

En configuration Spring Boot via `application.properties` :

```properties
langchain4j.open-ai.embedding-model.base-url=https://your-infinity-endpoint/v1
langchain4j.open-ai.embedding-model.api-key=${EMBEDDING_API_KEY}
langchain4j.open-ai.embedding-model.model-name=BAAI/bge-m3
```

---

## Incompatibilités connues avec Spring Boot 4.x

Trois problèmes majeurs bloquent actuellement l'utilisation de Langchain4j avec Spring Boot 4.0 :

**Migration Jackson 2 → 3** : Spring Boot 4.0 utilise Jackson 3 par défaut avec un changement de package (`com.fasterxml.jackson` → `tools.jackson`). Les sérialiseurs/désérialiseurs de Langchain4j ne sont pas encore adaptés. Un module de compatibilité `spring-boot-jackson2` existe comme solution temporaire.

**Alignement Jakarta EE 11** : Bien que Langchain4j ait migré vers les namespaces `jakarta.*` depuis longtemps, l'alignement complet avec Servlet 6.1 et les autres spécifications Jakarta EE 11 n'est pas confirmé.

**Modularisation Spring Boot 4** : Les nouveaux starters modulaires (`spring-boot-starter-webmvc` au lieu de `spring-boot-starter-web`) peuvent nécessiter des ajustements dans les dépendances.

---

## Stack recommandée pour Java 25

Pour votre serveur RAG avec PostgreSQL 18 + pgvector 0.8.1 et embeddings via Infinity, voici la configuration Maven recommandée :

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.9</version>
</parent>

<properties>
    <java.version>25</java.version>
    <langchain4j.version>1.10.0</langchain4j.version>
    <langchain4j-spring.version>1.10.0-beta18</langchain4j-spring.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-bom</artifactId>
            <version>${langchain4j.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Langchain4j Spring Integration -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-spring-boot-starter</artifactId>
        <version>${langchain4j-spring.version}</version>
    </dependency>
    
    <!-- OpenAI (baseUrl custom pour Infinity) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
        <version>${langchain4j-spring.version}</version>
    </dependency>
    
    <!-- PgVector Embedding Store -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-pgvector</artifactId>
    </dependency>
    
    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
</dependencies>
```

Cette configuration garantit :
- ✅ Compatibilité Java 25 via Spring Boot 3.5.9
- ✅ Intégration stable Langchain4j 1.10.0
- ✅ Support PostgreSQL 18 + pgvector 0.8.1
- ✅ Endpoints OpenAI-compatible via baseUrl custom
- ✅ Jakarta EE 10 (pas de conflit de dépendances)

---

## Conclusion et recommandations

**Pour janvier 2026**, restez sur Spring Boot 3.5.x avec Langchain4j 1.10.0. Cette combinaison offre une stabilité éprouvée et une compatibilité Java 25 complète sans les risques liés à la migration Jakarta EE 11 / Jackson 3.

**Actions à surveiller** :
- Le repository [github.com/langchain4j/langchain4j-spring](https://github.com/langchain4j/langchain4j-spring) pour les annonces de compatibilité Spring Boot 4.x
- Les releases notes Langchain4j pour la sortie des starters en version GA (actuellement beta18)
- L'initiative Jakarta Agentic AI pour la standardisation des intégrations IA dans Jakarta EE

**Migration future vers Spring Boot 4.x** : Attendez que Langchain4j publie officiellement le support. Une migration prématurée nécessiterait des workarounds (module compatibilité Jackson 2, exclusions de dépendances) qui complexifient la maintenance et introduisent des risques en production.