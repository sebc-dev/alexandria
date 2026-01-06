# Phase 14: Docker & Deployment

> **Note**: Cette phase n'utilise pas de TDD. Les artefacts Docker sont validés manuellement ou via CI.

- [ ] **Task 39: Create Dockerfile**
  - File: `Dockerfile`
  - Action: Multi-stage build
    ```dockerfile
    # Build stage
    FROM maven:3.9-eclipse-temurin-25 AS builder
    WORKDIR /app
    COPY pom.xml .
    RUN mvn dependency:go-offline -B
    COPY src ./src
    RUN mvn package -DskipTests -B

    # Runtime stage
    FROM eclipse-temurin:25-jre-alpine
    WORKDIR /app
    COPY --from=builder /app/target/*.jar app.jar

    # JVM settings for containers
    ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

    EXPOSE 8080
    HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
      CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

    ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
    ```
  - Notes: Java 25, Alpine for size, healthcheck on actuator

- [ ] **Task 40: Create docker-compose.yml**
  - File: `docker-compose.yml`
  - Action:
    ```yaml
    services:
      alexandria:
        build: .
        ports:
          - "8080:8080"
        environment:
          SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alexandria
          SPRING_DATASOURCE_USERNAME: alexandria
          SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
          ALEXANDRIA_INFINITY_BASE_URL: ${INFINITY_URL}
        depends_on:
          postgres:
            condition: service_healthy
        healthcheck:
          test: ["CMD", "wget", "-q", "--spider", "http://localhost:8080/actuator/health"]
          interval: 30s
          timeout: 3s
          start_period: 60s

      postgres:
        image: pgvector/pgvector:0.8.1-pg18
        environment:
          POSTGRES_DB: alexandria
          POSTGRES_USER: alexandria
          POSTGRES_PASSWORD: ${DB_PASSWORD}
        volumes:
          - pgdata:/var/lib/postgresql/data
        healthcheck:
          test: ["CMD-SHELL", "pg_isready -U alexandria"]
          interval: 10s
          timeout: 5s
          retries: 5

    volumes:
      pgdata:
    ```
  - Notes: Health checks for startup order. Env vars for secrets

- [ ] **Task 41: Create .env.example**
  - File: `.env.example`
  - Action: Template for required environment variables
    ```
    DB_PASSWORD=changeme
    INFINITY_URL=http://your-infinity-host:7997
    ```
  - Notes: Never commit .env with real values
