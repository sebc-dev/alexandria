# Phase 14: Docker & Deployment

- [ ] **Task 41: Create Dockerfile**
  - File: `Dockerfile`
  - Action: Multi-stage build: builder with Maven, runtime with eclipse-temurin:25-jre. EXPOSE 8080. HEALTHCHECK on /actuator/health
  - Notes: Use --enable-preview if needed for Java 25 features. Set JVM memory limits

- [ ] **Task 42: Create docker-compose.yml**
  - File: `docker-compose.yml`
  - Action: Services: alexandria (app), postgres (pgvector/pgvector:0.8.1-pg18 with volumes), optional infinity mock for local dev
  - Notes: Include environment variables for connection. Health checks for startup order
