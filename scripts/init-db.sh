#!/bin/bash
# Initialize database with Liquibase changelogs
# This script runs as part of docker-entrypoint-initdb.d

set -e

echo "Running database initialization scripts..."

# Run extension setup first
if [ -f /docker-entrypoint-initdb.d/changelog/changes/001-extensions.sql ]; then
    echo "Setting up extensions..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
        -f /docker-entrypoint-initdb.d/changelog/changes/001-extensions.sql
fi

# Run schema creation
if [ -f /docker-entrypoint-initdb.d/changelog/changes/002-schema.sql ]; then
    echo "Creating schema..."
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
        -f /docker-entrypoint-initdb.d/changelog/changes/002-schema.sql
fi

echo "Database initialization complete!"
