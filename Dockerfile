# PostgreSQL 17 with pgvector 0.8.1 and Apache AGE 1.6.0
# Multi-stage build to keep final image small

# ============================================
# Builder stage: Compile Apache AGE from source
# ============================================
FROM pgvector/pgvector:0.8.1-pg17 AS builder

# Install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    git \
    build-essential \
    postgresql-server-dev-17 \
    libreadline-dev \
    zlib1g-dev \
    flex \
    bison \
    && rm -rf /var/lib/apt/lists/*

# Clone and build Apache AGE
WORKDIR /build
RUN git clone --branch PG17/v1.6.0-rc0 --depth 1 https://github.com/apache/age.git && \
    cd age && \
    make && \
    make install

# ============================================
# Final stage: Production image
# ============================================
FROM pgvector/pgvector:0.8.1-pg17

# Copy AGE extension from builder
COPY --from=builder /usr/share/postgresql/17/extension/age* /usr/share/postgresql/17/extension/
COPY --from=builder /usr/lib/postgresql/17/lib/age.so /usr/lib/postgresql/17/lib/

# Create symlink for non-superuser loading
# This allows LOAD 'age' without superuser privileges
RUN mkdir -p /usr/lib/postgresql/17/lib/plugins && \
    ln -s /usr/lib/postgresql/17/lib/age.so /usr/lib/postgresql/17/lib/plugins/age.so

# Copy custom PostgreSQL configuration
COPY postgresql.conf /etc/postgresql/postgresql.conf

# Use custom config on startup
CMD ["postgres", "-c", "config_file=/etc/postgresql/postgresql.conf"]
