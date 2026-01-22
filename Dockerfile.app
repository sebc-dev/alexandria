# Alexandria Application Container
# Multi-stage build with layered JAR extraction for optimal caching

# ============================================
# Build stage: Compile application
# ============================================
FROM eclipse-temurin:21-jdk-jammy AS builder

# Install Maven
RUN apt-get update && apt-get install -y --no-install-recommends maven && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Copy pom.xml first (dependency layer caching)
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src/ src/
RUN mvn package -DskipTests -B

# Extract layered JAR with launcher for exploded class structure
RUN java -Djarmode=tools -jar target/*.jar extract --layers --launcher --destination extracted

# ============================================
# Runtime stage: Production image
# ============================================
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="Alexandria RAG Project"
LABEL description="Alexandria - RAG system for personal technical documentation"

WORKDIR /application

# Create non-root user with fixed UID/GID for security
RUN groupadd -r -g 10000 alexandria && \
    useradd -r -g alexandria -u 10000 -d /application -s /bin/false alexandria

# Create docs and logs directories for volume mount and logging
RUN mkdir -p /docs /application/logs && \
    chown -R alexandria:alexandria /docs /application/logs

# Copy layers in order of change frequency (least to most)
# This maximizes Docker layer cache hits on rebuilds
COPY --from=builder --chown=alexandria:alexandria /build/extracted/dependencies/ ./
COPY --from=builder --chown=alexandria:alexandria /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=alexandria:alexandria /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=alexandria:alexandria /build/extracted/application/ ./

# Switch to non-root user
USER alexandria

# Expose MCP HTTP/SSE port
EXPOSE 8080

# Default to http,docker profiles for containerized deployment
ENV SPRING_PROFILES_ACTIVE=http,docker

# Exec form ENTRYPOINT for proper SIGTERM handling
# Java process receives signals directly (not via shell)
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
