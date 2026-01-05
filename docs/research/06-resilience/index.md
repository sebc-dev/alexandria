# 06 - Resilience

Research documents for retry patterns and resilience strategies.

## Documents

| File | Description |
|------|-------------|
| [resilience4j-technical-guide.md](resilience4j-technical-guide.md) | Resilience4j 2.3.0 technical guide |
| [resilience4j-spring-boot-integration.md](resilience4j-spring-boot-integration.md) | Resilience4j integration with Spring Boot 3.5.x |
| [resilience4j-virtual-threads.md](resilience4j-virtual-threads.md) | Resilience4j compatibility with Virtual Threads |
| [resilience-strategies.md](resilience-strategies.md) | Resilience strategies for Alexandria RAG Server |
| [http-retry-patterns.md](http-retry-patterns.md) | HTTP retry patterns in modern Java |
| [spring-retry-migration.md](spring-retry-migration.md) | Spring Retry migration to Framework 7 |

## Key Findings

- Resilience4j 2.3.0 is recommended (compatible with Virtual Threads)
- @Retry annotations with exponential backoff (1s -> 2s -> 4s)
- enableExponentialBackoff: true and enableRandomizedWait: true are REQUIRED
- Retry on 5xx errors, ignore 4xx errors
- No circuit breaker needed for single-user usage
