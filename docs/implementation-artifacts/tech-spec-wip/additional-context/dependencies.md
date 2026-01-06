# Dependencies

**Maven dependencies (validées janvier 2026):**
```xml
<!-- Spring Boot 3.5.9 Parent -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.9</version>
</parent>

<properties>
    <java.version>25</java.version>
    <spring-ai.version>1.1.2</spring-ai.version>
    <resilience4j.version>2.3.0</resilience4j.version>
    <langchain4j.version>1.10.0</langchain4j.version>
    <langchain4j-spring.version>1.10.0-beta18</langchain4j-spring.version>
    <postgresql.version>42.7.4</postgresql.version>
    <testcontainers.version>2.0.3</testcontainers.version>
    <wiremock.version>3.13.2</wiremock.version>
    <picocli.version>4.7.7</picocli.version>
    <commonmark.version>0.27.0</commonmark.version>
    <guava.version>33.4.0-jre</guava.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Resilience4j BOM -->
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-bom</artifactId>
            <version>${resilience4j.version}</version>
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
        <!-- Spring AI BOM -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring AI MCP SDK (HTTP Streamable transport) - GA -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
    </dependency>

    <!-- Resilience4j pour @Retry (version via BOM) -->
    <dependency>
        <groupId>io.github.resilience4j</groupId>
        <artifactId>resilience4j-spring-boot3</artifactId>
    </dependency>
    <!-- OBLIGATOIRE pour annotations @Retry -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>
    <!-- Métriques et endpoints /actuator/retries -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Langchain4j Spring Integration -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-spring-boot-starter</artifactId>
        <version>${langchain4j-spring.version}</version>
    </dependency>

    <!-- Langchain4j OpenAI (baseUrl custom pour Infinity) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai-spring-boot-starter</artifactId>
        <version>${langchain4j-spring.version}</version>
    </dependency>

    <!-- Langchain4j pgvector (BETA - version explicite requise, pas dans BOM GA) -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-pgvector</artifactId>
        <version>${langchain4j-spring.version}</version>
    </dependency>

    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>${postgresql.version}</version>
        <scope>runtime</scope>
    </dependency>

    <!-- Picocli pour CLI ingestion -->
    <dependency>
        <groupId>info.picocli</groupId>
        <artifactId>picocli-spring-boot-starter</artifactId>
        <version>${picocli.version}</version>
    </dependency>

    <!-- CommonMark pour parsing Markdown -->
    <dependency>
        <groupId>org.commonmark</groupId>
        <artifactId>commonmark</artifactId>
        <version>${commonmark.version}</version>
    </dependency>
    <dependency>
        <groupId>org.commonmark</groupId>
        <artifactId>commonmark-ext-gfm-tables</artifactId>
        <version>${commonmark.version}</version>
    </dependency>
    <dependency>
        <groupId>org.commonmark</groupId>
        <artifactId>commonmark-ext-yaml-front-matter</artifactId>
        <version>${commonmark.version}</version>
    </dependency>

    <!-- Guava pour utilitaires (Lists.partition dans CLI ingestion) -->
    <dependency>
        <groupId>com.google.guava</groupId>
        <artifactId>guava</artifactId>
        <version>${guava.version}</version>
    </dependency>

    <!-- Test dependencies -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-postgresql</artifactId>
        <version>${testcontainers.version}</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.wiremock</groupId>
        <artifactId>wiremock</artifactId>
        <version>${wiremock.version}</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

**Image Docker pour tests :** `pgvector/pgvector:0.8.1-pg18`

**Note versions:**
- Spring Boot 3.5.9 = Dernière version 3.x, support OSS jusqu'en juin 2026
- Spring AI 1.1.2 = GA, stable pour Boot 3.x
- langchain4j-pgvector = Beta (API stable depuis 0.31.0)
- langchain4j-spring-boot-starter = Beta, nécessite version explicite
- Resilience4j 2.3.0 = Compatible Virtual Threads, @Retry annotations
- PostgreSQL JDBC 42.7.4 = Dernière version stable
- Testcontainers 2.0.3 = Attention: préfixes modules changés depuis 1.x
- WireMock 3.13.2 = Dernière version (3.10.0 avait 3 versions de retard)

**Note importante pgvector:**
- Langchain4j crée un index **IVFFlat** par défaut, pas HNSW
- **Créer l'index HNSW manuellement** pour de meilleures performances (voir section pgvector Configuration)
