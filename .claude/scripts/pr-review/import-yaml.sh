#!/bin/bash
# Import analyses from YAML tracking file into SQLite database
# Usage: import-yaml.sh <YAML_FILE> [--dry-run]
# Output: JSON {"imported": N, "skipped": N, "errors": N}

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_DIR="${HOME}/.local/share/alexandria"
DB_PATH="${DB_DIR}/pr-reviews.db"

# Parse arguments
YAML_FILE=""
DRY_RUN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run|-n)
            DRY_RUN=true
            shift
            ;;
        -h|--help)
            cat >&2 <<EOF
Usage: $0 <YAML_FILE> [--dry-run]

Import analyses from a pr-XX-tracking.yaml file into the SQLite database.

Options:
  --dry-run, -n   Show what would be imported without making changes

Examples:
  $0 .claude/pr-reviews/pr-11-tracking.yaml
  $0 .claude/pr-reviews/pr-11-tracking.yaml --dry-run
EOF
            exit 0
            ;;
        *)
            if [[ -z "$YAML_FILE" ]]; then
                YAML_FILE="$1"
            else
                echo "Unexpected argument: $1" >&2
                exit 1
            fi
            shift
            ;;
    esac
done

# Validate arguments
if [[ -z "$YAML_FILE" ]]; then
    echo '{"error": "YAML file path required"}' >&2
    exit 1
fi

if [[ ! -f "$YAML_FILE" ]]; then
    echo "{\"error\": \"File not found: $YAML_FILE\"}" >&2
    exit 1
fi

# Check dependencies
for cmd in python3 jq sqlite3; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "{\"error\": \"$cmd not found in PATH\"}" >&2
        exit 3
    fi
done

# Check PyYAML is available
if ! python3 -c "import yaml" 2>/dev/null; then
    echo '{"error": "Python yaml module not found. Install with: pip install pyyaml"}' >&2
    exit 3
fi

# Check database exists
if [[ ! -f "$DB_PATH" ]]; then
    echo '{"error": "Database not found. Run db-init.sh first"}' >&2
    exit 3
fi

# Extract PR number from YAML using Python
PR_NUMBER=$(python3 -c "import yaml; print(yaml.safe_load(open('$YAML_FILE'))['pr_number'])" 2>/dev/null)
if [[ -z "$PR_NUMBER" || "$PR_NUMBER" == "null" ]]; then
    echo '{"error": "Could not extract pr_number from YAML"}' >&2
    exit 1
fi

# Get PR ID from database
PR_ID=$(sqlite3 "$DB_PATH" "SELECT id FROM prs WHERE pr_number=$PR_NUMBER;" 2>/dev/null | head -1)
if [[ -z "$PR_ID" ]]; then
    echo "{\"error\": \"PR #$PR_NUMBER not found in database\"}" >&2
    exit 1
fi

echo "Importing analyses from $YAML_FILE for PR #$PR_NUMBER (pr_id=$PR_ID)..." >&2

IMPORTED=0
SKIPPED=0
ERRORS=0
TIMESTAMP=$(date -Iseconds)

# Helper: Escape for SQL
escape_sql() {
    echo "$1" | sed "s/'/''/g"
}

# Convert YAML to JSON for processing
YAML_AS_JSON=$(python3 -c "
import yaml, json, sys
with open('$YAML_FILE', 'r') as f:
    data = yaml.safe_load(f)
    for comment in data.get('comments', []):
        print(json.dumps(comment))
" 2>/dev/null)

# Process each comment
echo "$YAML_AS_JSON" | while read -r comment_json; do
    COMMENT_ID=$(echo "$comment_json" | jq -r '.id // empty')

    if [[ -z "$COMMENT_ID" ]]; then
        ((ERRORS++)) || true
        continue
    fi

    # Check if analysis exists
    HAS_ANALYSIS=$(echo "$comment_json" | jq -r '.analysis // empty')
    if [[ -z "$HAS_ANALYSIS" || "$HAS_ANALYSIS" == "null" ]]; then
        echo "  Skip $COMMENT_ID: no analysis block" >&2
        ((SKIPPED++)) || true
        continue
    fi

    # Extract analysis fields
    DECISION=$(echo "$comment_json" | jq -r '.analysis.decision // empty')
    if [[ -z "$DECISION" || "$DECISION" == "null" ]]; then
        echo "  Skip $COMMENT_ID: no decision" >&2
        ((SKIPPED++)) || true
        continue
    fi

    CONFIDENCE=$(echo "$comment_json" | jq -r '.analysis.confidence // 0.5')
    TYPE=$(echo "$comment_json" | jq -r '.analysis.type // empty')
    CRITICALITY=$(echo "$comment_json" | jq -r '.analysis.criticality // empty')
    EFFORT=$(echo "$comment_json" | jq -r '.analysis.effort // empty')
    REGRESSION_RISK=$(echo "$comment_json" | jq -r '.analysis.regression_risk // false')
    SUMMARY=$(echo "$comment_json" | jq -r '.analysis.summary // empty')
    RATIONALE=$(echo "$comment_json" | jq -r '.analysis.rationale // empty')
    CODE_SUGGESTION=$(echo "$comment_json" | jq -r '.analysis.code_suggestion // empty')
    RESEARCH_NEEDED=$(echo "$comment_json" | jq -r '.analysis.research_needed // empty')

    # Convert boolean to integer
    if [[ "$REGRESSION_RISK" == "true" ]]; then
        REGRESSION_RISK=1
    else
        REGRESSION_RISK=0
    fi

    # Handle NULL values for SQL
    [[ -z "$TYPE" || "$TYPE" == "null" ]] && TYPE_SQL="NULL" || TYPE_SQL="'$(escape_sql "$TYPE")'"
    [[ -z "$CRITICALITY" || "$CRITICALITY" == "null" || "$CRITICALITY" == "N/A" ]] && CRITICALITY_SQL="NULL" || CRITICALITY_SQL="'$CRITICALITY'"
    [[ -z "$EFFORT" || "$EFFORT" == "null" || "$EFFORT" == "N/A" ]] && EFFORT_SQL="NULL" || EFFORT_SQL="'$EFFORT'"
    [[ -z "$SUMMARY" || "$SUMMARY" == "null" ]] && SUMMARY_SQL="NULL" || SUMMARY_SQL="'$(escape_sql "$SUMMARY")'"
    [[ -z "$RATIONALE" || "$RATIONALE" == "null" ]] && RATIONALE_SQL="NULL" || RATIONALE_SQL="'$(escape_sql "$RATIONALE")'"
    [[ -z "$CODE_SUGGESTION" || "$CODE_SUGGESTION" == "null" ]] && CODE_SQL="NULL" || CODE_SQL="'$(escape_sql "$CODE_SUGGESTION")'"
    [[ -z "$RESEARCH_NEEDED" || "$RESEARCH_NEEDED" == "null" ]] && RESEARCH_SQL="NULL" || RESEARCH_SQL="'$(escape_sql "$RESEARCH_NEEDED")'"

    # Check for duplicate_of in rationale/summary
    DUPLICATE_OF=""
    if [[ "$DECISION" == "ACCEPT" || "$DECISION" == "REJECT" ]]; then
        DUPLICATE_OF=$(echo "$SUMMARY $RATIONALE" | grep -oP 'Duplicata de #?\K[0-9]+' | head -1 || echo "")
        if [[ -n "$DUPLICATE_OF" ]]; then
            DECISION="DUPLICATE"
        fi
    fi
    [[ -z "$DUPLICATE_OF" ]] && DUPLICATE_SQL="NULL" || DUPLICATE_SQL="'$DUPLICATE_OF'"

    if [[ "$DRY_RUN" == "true" ]]; then
        echo "  [DRY-RUN] Would import: $COMMENT_ID -> $DECISION ($CRITICALITY)" >&2
        ((IMPORTED++)) || true
    else
        # Check if comment exists in database
        COMMENT_EXISTS=$(sqlite3 "$DB_PATH" "SELECT 1 FROM comments WHERE id = '$COMMENT_ID';" 2>/dev/null || echo "")

        if [[ -z "$COMMENT_EXISTS" ]]; then
            echo "  Warning: Comment $COMMENT_ID not in database, skipping" >&2
            ((SKIPPED++)) || true
            continue
        fi

        # Upsert analysis
        SQL="INSERT INTO analyses (comment_id, decision, confidence, type, criticality, effort, regression_risk, summary, rationale, code_suggestion, research_needed, duplicate_of, analyzed_at, analyzer_model)
        VALUES ('$COMMENT_ID', '$DECISION', $CONFIDENCE, $TYPE_SQL, $CRITICALITY_SQL, $EFFORT_SQL, $REGRESSION_RISK, $SUMMARY_SQL, $RATIONALE_SQL, $CODE_SQL, $RESEARCH_SQL, $DUPLICATE_SQL, '$TIMESTAMP', 'yaml-import')
        ON CONFLICT(comment_id) DO UPDATE SET
            decision = excluded.decision,
            confidence = excluded.confidence,
            type = excluded.type,
            criticality = excluded.criticality,
            effort = excluded.effort,
            regression_risk = excluded.regression_risk,
            summary = excluded.summary,
            rationale = excluded.rationale,
            code_suggestion = excluded.code_suggestion,
            research_needed = excluded.research_needed,
            duplicate_of = excluded.duplicate_of,
            analyzed_at = excluded.analyzed_at;"

        if sqlite3 "$DB_PATH" "$SQL" 2>/dev/null; then
            echo "  Imported: $COMMENT_ID -> $DECISION" >&2
            ((IMPORTED++)) || true
        else
            echo "  Error importing $COMMENT_ID" >&2
            ((ERRORS++)) || true
        fi
    fi

    # Import replies if present
    REPLIES=$(echo "$comment_json" | jq -r '.our_replies // [] | .[] | @json' 2>/dev/null)
    if [[ -n "$REPLIES" ]]; then
        echo "$REPLIES" | while read -r reply_json; do
            REPLY_ID=$(echo "$reply_json" | jq -r '.id // empty')
            REPLY_BODY=$(echo "$reply_json" | jq -r '.body // empty')
            POSTED_AT=$(echo "$reply_json" | jq -r '.posted_at // empty')

            if [[ -z "$REPLY_BODY" || "$REPLY_BODY" == "null" ]]; then
                continue
            fi

            if [[ "$DRY_RUN" == "true" ]]; then
                echo "    [DRY-RUN] Would import reply for $COMMENT_ID" >&2
            else
                # Determine template type from decision
                TEMPLATE_TYPE="accept"
                case "$DECISION" in
                    REJECT) TEMPLATE_TYPE="reject" ;;
                    DEFER) TEMPLATE_TYPE="defer" ;;
                    DISCUSS) TEMPLATE_TYPE="discuss" ;;
                    DUPLICATE) TEMPLATE_TYPE="duplicate" ;;
                esac

                REPLY_SQL="INSERT OR IGNORE INTO replies (comment_id, github_reply_id, body, template_type, status, posted_at)
                VALUES ('$COMMENT_ID', '$REPLY_ID', '$(escape_sql "$REPLY_BODY")', '$TEMPLATE_TYPE', 'posted', '$POSTED_AT');"

                sqlite3 "$DB_PATH" "$REPLY_SQL" 2>/dev/null || true
            fi
        done
    fi

    # Import deferred item if applicable
    if [[ "$DECISION" == "DEFER" ]]; then
        GITHUB_ISSUE=$(echo "$comment_json" | jq -r '.github_issue // empty')
        TITLE=$(echo "$comment_json" | jq -r '.analysis.summary // empty')
        ACTION_REQUIRED=$(echo "$comment_json" | jq -r '.analysis.research_needed // empty')

        if [[ "$DRY_RUN" == "false" ]]; then
            [[ -z "$GITHUB_ISSUE" || "$GITHUB_ISSUE" == "null" ]] && ISSUE_SQL="NULL" || ISSUE_SQL="$GITHUB_ISSUE"

            DEFER_SQL="INSERT OR IGNORE INTO deferred_items (comment_id, title, action_required, github_issue_number, resolution_status, analyzed_at)
            VALUES ('$COMMENT_ID', '$(escape_sql "$TITLE")', '$(escape_sql "$ACTION_REQUIRED")', $ISSUE_SQL, 'pending', '$TIMESTAMP');"

            sqlite3 "$DB_PATH" "$DEFER_SQL" 2>/dev/null || true
        fi
    fi
done

# Log event
if [[ "$DRY_RUN" == "false" ]]; then
    sqlite3 "$DB_PATH" "INSERT INTO events (pr_id, event_type, event_subtype, event_data)
    VALUES ($PR_ID, 'import', 'yaml_import', '{\"file\": \"$YAML_FILE\", \"timestamp\": \"$TIMESTAMP\"}');" 2>/dev/null || true
fi

# Get actual counts from database
if [[ "$DRY_RUN" == "false" ]]; then
    ACTUAL_IMPORTED=$(sqlite3 "$DB_PATH" "SELECT COUNT(*) FROM analyses WHERE comment_id IN (SELECT id FROM comments WHERE pr_id = $PR_ID);")
else
    ACTUAL_IMPORTED="(dry-run)"
fi

# Output result
jq -n \
    --arg status "ok" \
    --arg imported "$ACTUAL_IMPORTED" \
    --arg dry_run "$DRY_RUN" \
    --arg yaml_file "$YAML_FILE" \
    '{
        status: $status,
        analyses_in_db: $imported,
        dry_run: ($dry_run == "true"),
        source_file: $yaml_file
    }'
