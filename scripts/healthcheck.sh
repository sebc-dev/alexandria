#!/bin/bash
# Healthcheck script for PostgreSQL with pgvector and Apache AGE
# Validates all three components are functional

set -e

PGUSER="${PGUSER:-alexandria}"
PGPASSWORD="${PGPASSWORD:-alexandria}"
PGDATABASE="${PGDATABASE:-alexandria}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"

export PGPASSWORD

echo "=== PostgreSQL Healthcheck ==="

# Step 1: Wait for PostgreSQL to be ready
echo "1. Checking PostgreSQL availability..."
MAX_ATTEMPTS=30
ATTEMPT=0
while ! pg_isready -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -q; do
    ATTEMPT=$((ATTEMPT + 1))
    if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
        echo "ERROR: PostgreSQL not ready after $MAX_ATTEMPTS attempts"
        exit 1
    fi
    echo "   Waiting for PostgreSQL... ($ATTEMPT/$MAX_ATTEMPTS)"
    sleep 2
done
echo "   PostgreSQL is ready!"

# Step 2: Test pgvector extension
echo "2. Testing pgvector extension..."
VECTOR_TEST=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -t -c \
    "SELECT '[1,2,3]'::vector(3);" 2>&1) || {
    echo "ERROR: pgvector test failed"
    echo "   $VECTOR_TEST"
    exit 1
}
echo "   pgvector is functional!"

# Step 3: Test Apache AGE extension
echo "3. Testing Apache AGE extension..."
AGE_TEST=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -t -c \
    "LOAD 'age'; SELECT * FROM ag_catalog.ag_graph LIMIT 1;" 2>&1) || {
    echo "ERROR: Apache AGE test failed"
    echo "   $AGE_TEST"
    exit 1
}
echo "   Apache AGE is functional!"

# Step 4: Test vector cosine distance operation
echo "4. Testing vector cosine distance..."
COSINE_TEST=$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -t -c \
    "SELECT '[1,2,3]'::vector <=> '[4,5,6]'::vector;" 2>&1) || {
    echo "ERROR: Vector cosine distance test failed"
    echo "   $COSINE_TEST"
    exit 1
}
echo "   Vector operations working! Distance: $COSINE_TEST"

echo ""
echo "=== All healthchecks passed ==="
exit 0
