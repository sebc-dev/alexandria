# 09 - Deployment

Research documents for Docker packaging and configuration.

## Documents

| File | Description |
|------|-------------|
| [complete-configuration.md](complete-configuration.md) | Complete configuration for Alexandria RAG Server |
| [environment-variables-config.md](environment-variables-config.md) | Environment variables configuration in Spring Boot 3.5.x |
| [docker-packaging.md](docker-packaging.md) | Docker packaging for Java 25 and Spring Boot 3.5.9 |
| [logging-health-checks.md](logging-health-checks.md) | Logging and health checks for RAG servers |
| [spring-boot-env-files.md](spring-boot-env-files.md) | Spring Boot 3.5.x .env file support |
| [spring-dotenv-guide.md](spring-dotenv-guide.md) | Spring-dotenv 5.1.0 complete guide |
| [docker-compose-health-checks.md](docker-compose-health-checks.md) | Docker Compose v2.x health check syntax |

## Key Findings

- ECS structured logging with Spring Boot 3.4+ native support
- Custom HealthIndicators for Infinity, pgvector, reranking
- MDC propagation for Virtual Threads with TaskDecorator
- Docker Compose v2.x health check dependency syntax
- Dual profiles: dev (console) / prod (JSON ECS)
