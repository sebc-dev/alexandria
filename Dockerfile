# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy Gradle wrapper and build files first for layer caching
COPY gradlew gradlew
COPY gradle/ gradle/
COPY build.gradle.kts settings.gradle.kts ./

# Copy source code
COPY src/ src/
COPY config/ config/

# Build the fat JAR (skip tests -- they run in CI)
RUN chmod +x gradlew && ./gradlew bootJar -x test -x integrationTest --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre

LABEL maintainer="alexandria"
LABEL description="Alexandria - RAG documentation search service"

WORKDIR /app

# Copy the fat JAR from builder
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=web

ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-XX:NativeMemoryTracking=summary", \
    "-jar", "app.jar"]
