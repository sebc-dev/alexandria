# Logging & Observability (from research #22)

**Trois piliers:** Logs structurés ECS, HealthIndicators custom, métriques latence pipeline.

**Fichiers additionnels (voir structure complète dans Project Structure):**
```
src/main/resources/
└── logback-spring.xml                 # ECS structuré + profils dev/prod
```

**logback-spring.xml (ECS natif Spring Boot 3.4+):**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true" scanPeriod="30 seconds">

    <springProperty scope="context" name="APP_NAME" source="spring.application.name" defaultValue="alexandria"/>

    <!-- PROFIL DEV - Console lisible -->
    <springProfile name="dev,local,default">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%clr(%d{HH:mm:ss.SSS}){faint} %clr(%5p) %clr([%X{correlationId:-NO_CID}]){yellow} %clr(%-40.40logger{39}){cyan} : %m%n%wEx</pattern>
            </encoder>
        </appender>

        <root level="INFO"><appender-ref ref="CONSOLE"/></root>
        <logger name="dev.alexandria" level="DEBUG"/>
        <logger name="dev.langchain4j" level="DEBUG"/>
    </springProfile>

    <!-- PROFIL PROD - JSON ECS -->
    <springProfile name="prod,production">
        <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="org.springframework.boot.logging.logback.StructuredLogEncoder">
                <format>ecs</format>
            </encoder>
        </appender>

        <appender name="ASYNC_JSON" class="ch.qos.logback.classic.AsyncAppender">
            <queueSize>1024</queueSize>
            <discardingThreshold>0</discardingThreshold>
            <appender-ref ref="JSON_CONSOLE"/>
        </appender>

        <root level="INFO"><appender-ref ref="ASYNC_JSON"/></root>
        <logger name="dev.alexandria" level="INFO"/>
        <logger name="dev.langchain4j" level="WARN"/>
        <logger name="org.springframework" level="WARN"/>
        <logger name="com.zaxxer.hikari" level="WARN"/>
    </springProfile>
</configuration>
```

**CorrelationIdFilter:**

```java
package dev.alexandria.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        var httpRequest = (HttpServletRequest) request;
        var httpResponse = (HttpServletResponse) response;

        try {
            String correlationId = Optional
                .ofNullable(httpRequest.getHeader(CORRELATION_ID_HEADER))
                .filter(s -> !s.isBlank())
                .orElseGet(() -> "CID-" + UUID.randomUUID().toString().substring(0, 16));

            MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

            chain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }
}
```

**VirtualThreadConfig (MDC propagation):**

```java
package dev.alexandria.config;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.TaskExecutorAdapter;
import java.util.concurrent.Executors;

@Configuration
public class VirtualThreadConfig {

    @Bean(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public AsyncTaskExecutor asyncTaskExecutor() {
        var executor = new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
        executor.setTaskDecorator(mdcPropagatingDecorator());
        return executor;
    }

    @Bean
    public TaskDecorator mdcPropagatingDecorator() {
        return runnable -> {
            var contextMap = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (contextMap != null) MDC.setContextMap(contextMap);
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
```

**HealthIndicators:**

```java
// InfinityEmbeddingHealthIndicator - vérifie /health endpoint
@Component("infinity")
public class InfinityEmbeddingHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            var response = restClient.get().uri("/health").retrieve().toBodilessEntity();
            return response.getStatusCode().is2xxSuccessful()
                ? Health.up().withDetail("service", "Infinity Embedding").build()
                : Health.down().withDetail("status", response.getStatusCode()).build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}

// RerankingHealthIndicator - simplifié (pas de /rerank pour éviter coûts RunPod)
@Component("reranking")
public class RerankingHealthIndicator implements HealthIndicator {
    // Même pattern que ci-dessus, appel /health seulement
}

// PgVectorHealthIndicator - vérifie extension + table + index
@Component("pgvector")
public class PgVectorHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try (Connection conn = dataSource.getConnection()) {
            var builder = Health.up();

            // 1. Vérifier extension pgvector
            try (var stmt = conn.createStatement()) {
                var rs = stmt.executeQuery(
                    "SELECT extversion FROM pg_extension WHERE extname = 'vector'");
                if (rs.next()) {
                    builder.withDetail("pgvectorVersion", rs.getString(1));
                } else {
                    return Health.down()
                        .withDetail("error", "pgvector extension not installed").build();
                }
            }

            // 2. Vérifier table + count
            try (var stmt = conn.createStatement()) {
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM document_embeddings");
                if (rs.next()) builder.withDetail("vectorCount", rs.getLong(1));
            }

            // 3. Vérifier index HNSW
            try (var stmt = conn.createStatement()) {
                var rs = stmt.executeQuery("""
                    SELECT indexname FROM pg_indexes
                    WHERE tablename = 'document_embeddings' AND indexdef LIKE '%hnsw%'
                    """);
                builder.withDetail("hnswIndex", rs.next() ? rs.getString(1) : "NOT FOUND");
            }

            return builder.build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).build();
        }
    }
}
```

**Configuration Actuator:**

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,retries,retryevents
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  health:
    group:
      readiness:
        include: db, diskSpace, infinity, pgvector, reranking
      liveness:
        include: ping

logging:
  level:
    dev.alexandria: INFO
    dev.langchain4j: WARN
    org.springframework: WARN
    org.springframework.ai.mcp: WARN
    com.zaxxer.hikari: WARN
    com.zaxxer.hikari.pool.HikariPool: INFO
```

**Niveaux de log recommandés:**

| Package | Dev | Prod |
|---------|-----|------|
| `dev.alexandria` | DEBUG | INFO |
| `dev.langchain4j` | DEBUG | WARN |
| `org.springframework` | INFO | WARN |
| `org.springframework.ai.mcp` | DEBUG | WARN |
| `com.zaxxer.hikari` | INFO | WARN |

**Reporté à v2:**
- RagStatsInfoContributor (métriques business /actuator/info)
- Port Actuator séparé 8081
