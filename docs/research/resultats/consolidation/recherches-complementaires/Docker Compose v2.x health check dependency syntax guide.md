# Docker Compose v2.x health check dependency syntax guide

**The `depends_on: { service: { condition: service_healthy } }` syntax is fully supported and remains the official recommended approach.** Docker Compose v2.x implements the unified Compose Specification, and this long-form syntax with conditions is the standard way to ensure services wait for dependencies to be healthy before starting. No deprecations affect this syntax—Docker actually enhanced it with additional options in recent versions.

## Valid condition values for depends_on

Docker Compose v2.x supports exactly **three condition values**:

| Condition | Behavior |
|-----------|----------|
| `service_started` | Default behavior—waits only until container is running (not ready) |
| `service_healthy` | Waits for the dependency's healthcheck to pass |
| `service_completed_successfully` | Waits for the dependency to exit with code 0 |

Two additional attributes were added in recent versions: **`restart: true`** (v2.17.0) automatically restarts the dependent service when the dependency restarts, and **`required: false`** (v2.20.0) makes a dependency optional, only issuing a warning if unavailable.

## Current recommended syntax

The long-form syntax is required when using conditions. Modern Compose files no longer need the `version` key—the specification is unified:

```yaml
services:
  app:
    build: .
    depends_on:
      db:
        condition: service_healthy
        restart: true          # Optional: auto-restart when db restarts
```

The short-form syntax `depends_on: [db]` only ensures startup order without waiting for health:

```yaml
services:
  app:
    depends_on:
      - db  # Only waits for container to start, NOT for it to be ready
```

## Healthcheck configuration syntax

The `healthcheck` directive supports these attributes:

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U postgres"]
  interval: 10s        # Time between checks
  timeout: 5s          # Max time for check to complete
  retries: 5           # Failures before marked unhealthy
  start_period: 30s    # Grace period before counting failures
  start_interval: 5s   # Check interval during start_period (v2.20.2+)
```

The `test` command accepts three formats: a list with `CMD` prefix `["CMD", "pg_isready"]`, a list with `CMD-SHELL` for shell features `["CMD-SHELL", "pg_isready || exit 1"]`, or a plain string that runs through the shell.

## Complete working example for Spring Boot with PostgreSQL/pgvector

This example shows your Alexandria RAG Server waiting for PostgreSQL with pgvector to be fully healthy:

```yaml
services:
  alexandria-rag-server:
    build: .
    container_name: alexandria-rag-server
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alexandria
      SPRING_DATASOURCE_USERNAME: alexandria
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
    depends_on:
      postgres:
        condition: service_healthy
        restart: true
    restart: unless-stopped

  postgres:
    image: pgvector/pgvector:pg16
    container_name: alexandria-postgres
    environment:
      POSTGRES_USER: alexandria
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_DB: alexandria
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U alexandria -d alexandria"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    restart: unless-stopped

volumes:
  postgres_data:
```

The **`pg_isready`** command returns exit code 0 when PostgreSQL accepts connections. For Spring Boot applications with longer startup times, the `start_period: 30s` gives PostgreSQL time to initialize before health check failures count against the retry limit.

## Recent version changes affecting this syntax

Docker Compose jumped from v2.x directly to **v5.x** in December 2025 to avoid confusion with legacy Compose file format versions "v2" and "v3". The syntax itself remained unchanged—this was purely a version numbering clarification.

Key historical additions to `depends_on`:
- **v2.17.0**: Added `condition` attribute and `restart: true` option
- **v2.20.0**: Added `required: false` for optional dependencies  
- **v2.20.2**: Added `start_interval` to healthcheck configuration
- **v2.33.1+**: Fixed edge cases with unnecessary container recreation

**No breaking changes or deprecations affect the health check dependency syntax.** The long-form `depends_on` with `condition: service_healthy` is the stable, documented approach and will continue to work.

## Best practices for your use case

For a Spring Boot application depending on PostgreSQL, use **`start_period: 30s`** or higher to allow database initialization. Set **`interval: 10s`** with **`retries: 5`** for a reasonable total wait time of about 80 seconds. Adding **`restart: true`** ensures your application automatically restarts if the database container is recreated during development. The `pg_isready` command is lightweight and preferred over running actual SQL queries for basic connectivity checks.