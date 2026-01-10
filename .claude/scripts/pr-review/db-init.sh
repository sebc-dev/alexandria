#!/bin/bash
# Initialize or upgrade the PR Review SQLite database
# Usage: db-init.sh [--force]
# Output: JSON {"status": "ok|created|upgraded", "db_path": "...", "version": N}

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_DIR="${HOME}/.local/share/alexandria"
DB_PATH="${DB_DIR}/pr-reviews.db"
SCHEMA_FILE="${SCRIPT_DIR}/db-schema.sql"

# Parse arguments
FORCE=false
while [[ $# -gt 0 ]]; do
    case $1 in
        --force)
            FORCE=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [--force]" >&2
            echo "  --force  Delete and recreate database" >&2
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            exit 1
            ;;
    esac
done

# Check dependencies
if ! command -v sqlite3 &>/dev/null; then
    echo '{"status": "error", "error": "sqlite3 not found in PATH"}'
    exit 3
fi

if ! command -v jq &>/dev/null; then
    echo '{"status": "error", "error": "jq not found in PATH"}'
    exit 3
fi

# Check schema file exists
if [[ ! -f "$SCHEMA_FILE" ]]; then
    echo "{\"status\": \"error\", \"error\": \"Schema file not found: $SCHEMA_FILE\"}"
    exit 1
fi

# Create directory if needed
if [[ ! -d "$DB_DIR" ]]; then
    mkdir -p "$DB_DIR"
    echo "Created directory: $DB_DIR" >&2
fi

# Handle --force
if [[ "$FORCE" == "true" ]] && [[ -f "$DB_PATH" ]]; then
    echo "Removing existing database (--force)" >&2
    rm -f "$DB_PATH" "$DB_PATH-wal" "$DB_PATH-shm"
fi

# Determine action
ACTION="ok"
if [[ ! -f "$DB_PATH" ]]; then
    ACTION="created"
    echo "Creating new database: $DB_PATH" >&2
else
    # Check if upgrade needed
    CURRENT_VERSION=$(sqlite3 "$DB_PATH" "SELECT COALESCE(MAX(version), 0) FROM schema_version;" 2>/dev/null || echo "0")
    # Extract version from schema_version INSERT statement (portable, works on BSD and GNU grep)
    SCHEMA_VERSION=$(grep -E "INSERT.*INTO.*schema_version.*VALUES" "$SCHEMA_FILE" | grep -oE '[0-9]+' | head -1 || echo "1")

    if [[ "$CURRENT_VERSION" -lt "$SCHEMA_VERSION" ]]; then
        ACTION="upgraded"
        echo "Upgrading database from v$CURRENT_VERSION to v$SCHEMA_VERSION" >&2
    else
        echo "Database up to date (v$CURRENT_VERSION)" >&2
    fi
fi

# Apply schema (idempotent with IF NOT EXISTS)
if ! sqlite3 "$DB_PATH" < "$SCHEMA_FILE" 2>&1; then
    echo "{\"status\": \"error\", \"error\": \"Failed to apply schema\"}"
    exit 3
fi

# Get final version
FINAL_VERSION=$(sqlite3 "$DB_PATH" "SELECT MAX(version) FROM schema_version;")

# Output JSON result
jq -n \
    --arg status "$ACTION" \
    --arg db_path "$DB_PATH" \
    --argjson version "$FINAL_VERSION" \
    '{status: $status, db_path: $db_path, version: $version}'
