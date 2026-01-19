#!/bin/bash
# Healthcheck script for PostgreSQL with pgvector and Apache AGE
# Validates all three components are functional
# Runs commands inside the Docker container

set -e

CONTAINER_NAME="${CONTAINER_NAME:-postgres}"
PGUSER="${PGUSER:-alexandria}"
PGDATABASE="${PGDATABASE:-alexandria}"

# Helper function to run psql in container
run_psql() {
    docker compose exec -T "$CONTAINER_NAME" psql -U "$PGUSER" -d "$PGDATABASE" -t -c "$1"
}

echo "=== PostgreSQL Healthcheck ==="

# Step 1: Wait for PostgreSQL to be ready
echo "1. Checking PostgreSQL availability..."
MAX_ATTEMPTS=30
ATTEMPT=0
while ! docker compose exec -T "$CONTAINER_NAME" pg_isready -U "$PGUSER" -d "$PGDATABASE" -q 2>/dev/null; do
    ATTEMPT=$((ATTEMPT + 1))
    if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
        echo "ERROR: PostgreSQL not ready after $MAX_ATTEMPTS attempts"
        echo "   Is the container running? Try: docker compose ps"
        exit 1
    fi
    echo "   Waiting for PostgreSQL... ($ATTEMPT/$MAX_ATTEMPTS)"
    sleep 2
done
echo "   PostgreSQL is ready!"

# Step 2: Test pgvector extension
echo "2. Testing pgvector extension..."
VECTOR_TEST=$(run_psql "SELECT '[1,2,3]'::vector(3);" 2>&1) || {
    echo "ERROR: pgvector test failed"
    echo "   $VECTOR_TEST"
    exit 1
}
echo "   pgvector is functional!"

# Step 3: Test Apache AGE extension
echo "3. Testing Apache AGE extension..."
AGE_TEST=$(run_psql "LOAD 'age'; SELECT * FROM ag_catalog.ag_graph LIMIT 1;" 2>&1) || {
    echo "ERROR: Apache AGE test failed"
    echo "   $AGE_TEST"
    exit 1
}
echo "   Apache AGE is functional!"

# Step 4: Test vector cosine distance operation
echo "4. Testing vector cosine distance..."
COSINE_TEST=$(run_psql "SELECT '[1,2,3]'::vector <=> '[4,5,6]'::vector;" 2>&1) || {
    echo "ERROR: Vector cosine distance test failed"
    echo "   $COSINE_TEST"
    exit 1
}
echo "   Vector operations working! Distance:$COSINE_TEST"

echo ""
echo "=== All healthchecks passed ==="
exit 0
