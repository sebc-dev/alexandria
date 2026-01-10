#!/bin/bash
# Fetch PR metadata and store in database
# Usage: fetch-pr.sh <PR_NUMBER> [--repo REPO]
# Output: JSON {"pr_id": N, "pr_number": N, "title": "...", "state": "open", ...}

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_DIR="${HOME}/.local/share/alexandria"
DB_PATH="${DB_DIR}/pr-reviews.db"

# Load shared utilities
source "${SCRIPT_DIR}/lib/utils.sh"

# Default repo (auto-detect from git)
DEFAULT_REPO=""
if git rev-parse --is-inside-work-tree &>/dev/null 2>&1; then
    DEFAULT_REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo "")
fi

# Parse arguments
PR_NUMBER=""
REPO="${DEFAULT_REPO}"

while [[ $# -gt 0 ]]; do
    case $1 in
        --repo)
            REPO="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 <PR_NUMBER> [--repo REPO]" >&2
            echo "  PR_NUMBER  The PR number to fetch" >&2
            echo "  --repo     Repository (owner/name), defaults to current repo" >&2
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

echo "Fetching PR #$PR_NUMBER from $REPO..." >&2

# Fetch PR data from GitHub
PR_JSON=$(gh api "repos/$REPO/pulls/$PR_NUMBER" 2>&1) || {
    echo "{\"error\": \"Failed to fetch PR: $PR_JSON\"}" >&2
    exit 2
}

# Extract fields
TITLE=$(echo "$PR_JSON" | jq -r '.title // ""')
BODY=$(echo "$PR_JSON" | jq -r '.body // ""')
BRANCH=$(echo "$PR_JSON" | jq -r '.head.ref // ""')
BASE_BRANCH=$(echo "$PR_JSON" | jq -r '.base.ref // "main"')
AUTHOR=$(echo "$PR_JSON" | jq -r '.user.login // ""')
STATE=$(echo "$PR_JSON" | jq -r '.state // "open"')
CREATED_AT=$(echo "$PR_JSON" | jq -r '.created_at // ""')
UPDATED_AT=$(echo "$PR_JSON" | jq -r '.updated_at // ""')
MERGED=$(echo "$PR_JSON" | jq -r '.merged // false')

# Determine state (open, closed, merged)
if [[ "$MERGED" == "true" ]]; then
    STATE="merged"
fi

TIMESTAMP=$(date -Iseconds)

TITLE_ESC=$(escape_sql "$TITLE")
BODY_ESC=$(escape_sql "$BODY")
BRANCH_ESC=$(escape_sql "$BRANCH")
AUTHOR_ESC=$(escape_sql "$AUTHOR")

# Insert or update PR in database
SQL="INSERT INTO prs (pr_number, repo, title, body, branch, base_branch, author, state, created_at, updated_at, last_synced_at)
VALUES ($PR_NUMBER, '$REPO', '$TITLE_ESC', '$BODY_ESC', '$BRANCH_ESC', '$BASE_BRANCH', '$AUTHOR_ESC', '$STATE', '$CREATED_AT', '$UPDATED_AT', '$TIMESTAMP')
ON CONFLICT(repo, pr_number) DO UPDATE SET
    title = excluded.title,
    body = excluded.body,
    branch = excluded.branch,
    base_branch = excluded.base_branch,
    author = excluded.author,
    state = excluded.state,
    updated_at = excluded.updated_at,
    last_synced_at = excluded.last_synced_at
RETURNING id, pr_number, repo, title, branch, base_branch, author, state, review_status, last_synced_at;"

RESULT=$(sqlite3 -json "$DB_PATH" "$SQL" 2>&1) || {
    echo "{\"error\": \"Database error: $RESULT\"}" >&2
    exit 3
}

# Log event
PR_ID=$(echo "$RESULT" | jq -r '.[0].id')
sqlite3 "$DB_PATH" "INSERT INTO events (pr_id, event_type, event_subtype, event_data)
VALUES ($PR_ID, 'fetch', 'fetch_pr', '{\"pr_number\": $PR_NUMBER}');" 2>/dev/null || true

# Output result (first row of array)
echo "$RESULT" | jq '.[0]'
