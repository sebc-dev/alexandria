#!/bin/bash
# Query the PR Review SQLite database
# Usage: query-db.sh <SQL> [--json|--csv|--table]
# Output: Query results in specified format (default: JSON)

set -euo pipefail

DB_DIR="${HOME}/.local/share/alexandria"
DB_PATH="${DB_DIR}/pr-reviews.db"

# Default format
FORMAT="json"
SQL=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --json)
            FORMAT="json"
            shift
            ;;
        --csv)
            FORMAT="csv"
            shift
            ;;
        --table)
            FORMAT="table"
            shift
            ;;
        -h|--help)
            echo "Usage: $0 <SQL> [--json|--csv|--table]" >&2
            echo "  --json   Output as JSON array (default)" >&2
            echo "  --csv    Output as CSV" >&2
            echo "  --table  Output as formatted table" >&2
            exit 0
            ;;
        *)
            if [[ -z "$SQL" ]]; then
                SQL="$1"
            else
                echo "Unexpected argument: $1" >&2
                exit 1
            fi
            shift
            ;;
    esac
done

# Validate SQL provided
if [[ -z "$SQL" ]]; then
    echo '{"error": "No SQL query provided"}' >&2
    exit 1
fi

# Check database exists
if [[ ! -f "$DB_PATH" ]]; then
    echo '{"error": "Database not found. Run db-init.sh first"}' >&2
    exit 3
fi

# Execute query based on format
case $FORMAT in
    json)
        # Use sqlite3 JSON mode - capture output once to avoid double execution
        OUTPUT=$(sqlite3 -json "$DB_PATH" "$SQL" 2>&1)
        STATUS=$?
        if [[ $STATUS -ne 0 ]]; then
            # Extract first line of error message
            ERROR_MSG=$(echo "$OUTPUT" | head -1)
            echo "{\"error\": \"Query failed: $ERROR_MSG\"}"
            exit 3
        fi
        echo "$OUTPUT"
        ;;
    csv)
        sqlite3 -csv -header "$DB_PATH" "$SQL"
        ;;
    table)
        sqlite3 -column -header "$DB_PATH" "$SQL"
        ;;
esac
