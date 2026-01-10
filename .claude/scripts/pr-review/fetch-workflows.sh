#!/bin/bash
# Fetch GitHub Actions workflow runs and checks for a PR
# Usage: fetch-workflows.sh <PR_NUMBER> [--repo REPO] [--head-sha SHA]
# Output: JSON {"workflow_runs": N, "check_runs": N, "failed": N, ...}

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_DIR="${HOME}/.local/share/alexandria"
DB_PATH="${DB_DIR}/pr-reviews.db"

# Load shared utilities
source "${SCRIPT_DIR}/lib/utils.sh"

# Default repo
DEFAULT_REPO=""
if git rev-parse --is-inside-work-tree &>/dev/null 2>&1; then
    DEFAULT_REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo "")
fi

# Parse arguments
PR_NUMBER=""
REPO="${DEFAULT_REPO}"
HEAD_SHA=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --repo)
            REPO="$2"
            shift 2
            ;;
        --head-sha)
            HEAD_SHA="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 <PR_NUMBER> [--repo REPO] [--head-sha SHA]" >&2
            echo "  PR_NUMBER  The PR number to fetch workflows for" >&2
            echo "  --repo     Repository (owner/name), defaults to current repo" >&2
            echo "  --head-sha Only fetch for this commit SHA (defaults to PR head)" >&2
            exit 0
            ;;
        *)
            if [[ -z "$PR_NUMBER" ]]; then
                PR_NUMBER="$1"
            else
                echo "Unexpected argument: $1" >&2
                exit 1
            fi
            shift
            ;;
    esac
done

# Validate arguments
if [[ -z "$PR_NUMBER" ]]; then
    echo '{"error": "PR number required"}' >&2
    exit 1
fi

if [[ -z "$REPO" ]]; then
    echo '{"error": "Could not determine repository. Use --repo owner/name"}' >&2
    exit 1
fi

# Check dependencies
for cmd in gh jq sqlite3; do
    if ! command -v "$cmd" &>/dev/null; then
        echo "{\"error\": \"$cmd not found in PATH\"}" >&2
        exit 3
    fi
done

# Check database exists
if [[ ! -f "$DB_PATH" ]]; then
    echo '{"error": "Database not found. Run db-init.sh first"}' >&2
    exit 3
fi

# Get PR ID and head SHA from database
PR_DATA=$(sqlite3 -json "$DB_PATH" "SELECT id, branch FROM prs WHERE repo='$REPO' AND pr_number=$PR_NUMBER;" 2>/dev/null)
if [[ -z "$PR_DATA" ]] || [[ "$PR_DATA" == "[]" ]]; then
    echo '{"error": "PR not found in database. Run fetch-pr.sh first"}' >&2
    exit 1
fi

PR_ID=$(echo "$PR_DATA" | jq -r '.[0].id')
BRANCH=$(echo "$PR_DATA" | jq -r '.[0].branch')

# Get head SHA if not provided
if [[ -z "$HEAD_SHA" ]]; then
    HEAD_SHA=$(gh api "repos/$REPO/pulls/$PR_NUMBER" --jq '.head.sha' 2>/dev/null || echo "")
fi

if [[ -z "$HEAD_SHA" ]]; then
    echo '{"error": "Could not determine head SHA"}' >&2
    exit 2
fi

echo "Fetching workflows for PR #$PR_NUMBER (sha=$HEAD_SHA)..." >&2

TIMESTAMP=$(date -Iseconds)

# ============================================
# 1. Fetch workflow runs
# ============================================
echo "Fetching workflow runs..." >&2

WORKFLOW_RUNS=$(gh api "repos/$REPO/actions/runs?head_sha=$HEAD_SHA" --jq '.workflow_runs' 2>&1) || {
    echo "Warning: Failed to fetch workflow runs: $WORKFLOW_RUNS" >&2
    WORKFLOW_RUNS="[]"
}

WORKFLOW_COUNT=$(echo "$WORKFLOW_RUNS" | jq 'length')
echo "  Found $WORKFLOW_COUNT workflow runs" >&2

echo "$WORKFLOW_RUNS" | jq -c '.[]' | while read -r run; do
    id=$(echo "$run" | jq -r '.id')
    workflow_name=$(echo "$run" | jq -r '.name // ""')
    workflow_id=$(echo "$run" | jq -r '.workflow_id // null')
    head_sha=$(echo "$run" | jq -r '.head_sha // ""')
    head_branch=$(echo "$run" | jq -r '.head_branch // ""')
    event=$(echo "$run" | jq -r '.event // ""')
    status=$(echo "$run" | jq -r '.status // ""')
    conclusion=$(echo "$run" | jq -r '.conclusion // null')
    run_number=$(echo "$run" | jq -r '.run_number // null')
    run_attempt=$(echo "$run" | jq -r '.run_attempt // 1')
    started_at=$(echo "$run" | jq -r '.run_started_at // ""')
    completed_at=$(echo "$run" | jq -r '.updated_at // ""')
    html_url=$(echo "$run" | jq -r '.html_url // ""')

    workflow_name_esc=$(escape_sql "$workflow_name")
    [[ "$workflow_id" == "null" ]] && workflow_id="NULL"
    [[ "$conclusion" == "null" ]] && conclusion_sql="NULL" || conclusion_sql="'$conclusion'"
    [[ "$run_number" == "null" ]] && run_number="NULL"

    sql="INSERT OR REPLACE INTO workflow_runs (id, pr_id, workflow_name, workflow_id, head_sha, head_branch,
        event, status, conclusion, run_number, run_attempt, started_at, completed_at, html_url, fetched_at)
    VALUES ($id, $PR_ID, '$workflow_name_esc', $workflow_id, '$head_sha', '$head_branch',
        '$event', '$status', $conclusion_sql, $run_number, $run_attempt, '$started_at', '$completed_at', '$html_url', '$TIMESTAMP');"

    sqlite3 "$DB_PATH" "$sql" 2>/dev/null || echo "  Warning: Failed to insert workflow run $id" >&2
done

# ============================================
# 2. Fetch check runs
# ============================================
echo "Fetching check runs..." >&2

CHECK_RUNS=$(gh api "repos/$REPO/commits/$HEAD_SHA/check-runs" --jq '.check_runs' 2>&1) || {
    echo "Warning: Failed to fetch check runs: $CHECK_RUNS" >&2
    CHECK_RUNS="[]"
}

CHECK_COUNT=$(echo "$CHECK_RUNS" | jq 'length')
echo "  Found $CHECK_COUNT check runs" >&2

echo "$CHECK_RUNS" | jq -c '.[]' | while read -r check; do
    id=$(echo "$check" | jq -r '.id')
    name=$(echo "$check" | jq -r '.name // ""')
    head_sha=$(echo "$check" | jq -r '.head_sha // ""')
    status=$(echo "$check" | jq -r '.status // ""')
    conclusion=$(echo "$check" | jq -r '.conclusion // null')
    details_url=$(echo "$check" | jq -r '.details_url // ""')
    output_title=$(echo "$check" | jq -r '.output.title // ""')
    output_summary=$(echo "$check" | jq -r '.output.summary // ""')
    output_text=$(echo "$check" | jq -r '.output.text // ""')
    annotations_count=$(echo "$check" | jq -r '.output.annotations_count // 0')
    started_at=$(echo "$check" | jq -r '.started_at // ""')
    completed_at=$(echo "$check" | jq -r '.completed_at // ""')

    # Try to match workflow run
    workflow_run_id=$(sqlite3 "$DB_PATH" "SELECT id FROM workflow_runs WHERE pr_id=$PR_ID AND head_sha='$head_sha' LIMIT 1;" 2>/dev/null || echo "")
    [[ -z "$workflow_run_id" ]] && workflow_run_id="NULL"

    name_esc=$(escape_sql "$name")
    output_title_esc=$(escape_sql "$output_title")
    output_summary_esc=$(escape_sql "$output_summary")
    output_text_esc=$(escape_sql "$output_text")
    details_url_esc=$(escape_sql "$details_url")

    [[ "$conclusion" == "null" ]] && conclusion_sql="NULL" || conclusion_sql="'$conclusion'"

    sql="INSERT OR REPLACE INTO check_runs (id, pr_id, workflow_run_id, name, head_sha,
        status, conclusion, details_url, output_title, output_summary, output_text,
        annotations_count, started_at, completed_at, fetched_at)
    VALUES ($id, $PR_ID, $workflow_run_id, '$name_esc', '$head_sha',
        '$status', $conclusion_sql, '$details_url_esc', '$output_title_esc', '$output_summary_esc', '$output_text_esc',
        $annotations_count, '$started_at', '$completed_at', '$TIMESTAMP');"

    sqlite3 "$DB_PATH" "$sql" 2>/dev/null || echo "  Warning: Failed to insert check run $id" >&2
done

# ============================================
# Get summary from database
# ============================================
SUMMARY=$(sqlite3 -json "$DB_PATH" "
SELECT
    (SELECT COUNT(*) FROM workflow_runs WHERE pr_id=$PR_ID) as workflow_runs,
    (SELECT COUNT(*) FROM check_runs WHERE pr_id=$PR_ID) as check_runs,
    (SELECT COUNT(*) FROM check_runs WHERE pr_id=$PR_ID AND conclusion='failure') as failed_checks,
    (SELECT COUNT(*) FROM check_runs WHERE pr_id=$PR_ID AND conclusion='success') as passed_checks,
    (SELECT COUNT(*) FROM check_runs WHERE pr_id=$PR_ID AND status='in_progress') as in_progress_checks;
")

# Get failed check names
FAILED_CHECKS=$(sqlite3 -json "$DB_PATH" "
SELECT name, conclusion, details_url
FROM check_runs
WHERE pr_id=$PR_ID AND conclusion IN ('failure', 'cancelled', 'action_required')
ORDER BY name;
" 2>/dev/null)
# sqlite3 -json returns empty string (not []) when no results
[[ -z "$FAILED_CHECKS" ]] && FAILED_CHECKS="[]"

# Log event
sqlite3 "$DB_PATH" "INSERT INTO events (pr_id, event_type, event_subtype, event_data)
VALUES ($PR_ID, 'fetch', 'fetch_workflows', '{\"workflow_runs\": $WORKFLOW_COUNT, \"check_runs\": $CHECK_COUNT}');" 2>/dev/null || true

# Output result
echo "$SUMMARY" | jq --argjson failed "$FAILED_CHECKS" '.[0] + {status: "ok", failed_details: $failed}'
