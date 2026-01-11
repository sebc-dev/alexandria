#!/bin/bash
# Resolve a GitHub PR review thread via GraphQL API
# Usage: resolve-thread.sh <COMMENT_ID> [--dry-run] [--repo REPO]
# Output: JSON {"status": "resolved|already_resolved|not_resolvable|error", ...}

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
COMMENT_ID=""
DRY_RUN=false
REPO="${DEFAULT_REPO}"

while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --repo)
            if [[ $# -lt 2 || -z "${2:-}" ]]; then
                echo '{"error": "Missing value for --repo"}' >&2
                exit 1
            fi
            REPO="$2"
            shift 2
            ;;
        -h|--help)
            cat >&2 <<EOF
Usage: $0 <COMMENT_ID> [OPTIONS]

Resolve a GitHub PR review thread using GraphQL API.

Options:
  --dry-run    Don't actually resolve, just show what would be done
  --repo REPO  Repository (owner/name), defaults to current repo

Note: Only inline comments (source='inline') can be resolved.
      Review body and issue comments don't have resolvable threads.

Examples:
  $0 1234567890                    # Resolve thread containing comment
  $0 1234567890 --dry-run          # Preview without resolving
EOF
            exit 0
            ;;
        *)
            if [[ -z "$COMMENT_ID" ]]; then
                COMMENT_ID="$1"
            else
                echo "Unexpected argument: $1" >&2
                exit 1
            fi
            shift
            ;;
    esac
done

# Validate arguments
if [[ -z "$COMMENT_ID" ]]; then
    echo '{"error": "Comment ID required"}' >&2
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

# Escape comment ID for SQL (prevents injection from user input)
COMMENT_ID_ESC=$(escape_sql "$COMMENT_ID")

# Get comment info from database
COMMENT_DATA=$(sqlite3 -json "$DB_PATH" "
SELECT c.id, c.node_id, c.source, c.pr_id, c.file_path, c.line_number,
       a.decision, a.resolved_at
FROM comments c
JOIN prs p ON c.pr_id = p.id
LEFT JOIN analyses a ON c.id = a.comment_id
WHERE c.id = '$COMMENT_ID_ESC';
" 2>/dev/null)

if [[ -z "$COMMENT_DATA" ]] || [[ "$COMMENT_DATA" == "[]" ]]; then
    echo "{\"error\": \"Comment $COMMENT_ID not found in database\"}" >&2
    exit 1
fi

SOURCE=$(echo "$COMMENT_DATA" | jq -r '.[0].source')
NODE_ID=$(echo "$COMMENT_DATA" | jq -r '.[0].node_id // ""')
PR_ID=$(echo "$COMMENT_DATA" | jq -r '.[0].pr_id')
FILE_PATH=$(echo "$COMMENT_DATA" | jq -r '.[0].file_path // ""')
DECISION=$(echo "$COMMENT_DATA" | jq -r '.[0].decision // ""')
ALREADY_RESOLVED=$(echo "$COMMENT_DATA" | jq -r '.[0].resolved_at // ""')

# Check if already resolved in our DB
if [[ -n "$ALREADY_RESOLVED" ]]; then
    jq -n \
        --arg comment_id "$COMMENT_ID" \
        --arg resolved_at "$ALREADY_RESOLVED" \
        '{status: "already_resolved", comment_id: $comment_id, resolved_at: $resolved_at, message: "Thread already marked as resolved in database"}'
    exit 0
fi

# Check if this comment type can be resolved
if [[ "$SOURCE" != "inline" ]]; then
    jq -n \
        --arg comment_id "$COMMENT_ID" \
        --arg source "$SOURCE" \
        '{status: "not_resolvable", comment_id: $comment_id, source: $source, message: "Only inline comments can be resolved (review threads)"}'
    exit 0
fi

# Check if we have the node_id
if [[ -z "$NODE_ID" || "$NODE_ID" == "null" ]]; then
    echo "Warning: Comment $COMMENT_ID has no node_id. Attempting to fetch from GitHub..." >&2

    # Validate COMMENT_ID is strictly numeric for inline comments (security)
    if [[ ! "$COMMENT_ID" =~ ^[0-9]+$ ]]; then
        jq -n --arg error "Inline comment ID must be numeric" --arg id "$COMMENT_ID" \
            '{error: $error, comment_id: $id}' >&2
        exit 1
    fi

    # Try to fetch node_id from GitHub REST API
    COMMENT_JSON=$(gh api "repos/$REPO/pulls/comments/$COMMENT_ID" 2>/dev/null) || {
        jq -n --arg error "Failed to fetch comment from GitHub" \
            '{error: $error}' >&2
        exit 2
    }

    NODE_ID=$(echo "$COMMENT_JSON" | jq -r '.node_id // ""')

    if [[ -z "$NODE_ID" || "$NODE_ID" == "null" ]]; then
        jq -n --arg error "Could not get node_id for comment" --arg id "$COMMENT_ID" \
            '{error: $error, comment_id: $id}' >&2
        exit 2
    fi

    # Update node_id in database (escape for defense in depth)
    NODE_ID_ESC=$(escape_sql "$NODE_ID")
    sqlite3 "$DB_PATH" "UPDATE comments SET node_id = '$NODE_ID_ESC' WHERE id = '$COMMENT_ID_ESC';" 2>/dev/null || true
    echo "  Updated node_id in database: $NODE_ID" >&2
fi

# GraphQL query to get the thread ID from a comment
# The comment node_id is for PullRequestReviewComment, we need to get the thread
GRAPHQL_QUERY='
query GetThreadFromComment($nodeId: ID!) {
  node(id: $nodeId) {
    ... on PullRequestReviewComment {
      pullRequestReviewThread {
        id
        isResolved
        viewerCanResolve
      }
    }
  }
}
'

echo "Fetching thread for comment $COMMENT_ID (node_id: $NODE_ID)..." >&2

THREAD_RESULT=$(gh api graphql -f query="$GRAPHQL_QUERY" -f nodeId="$NODE_ID" 2>&1) || {
    jq -n --arg error "GraphQL query failed" --arg details "$THREAD_RESULT" \
        '{error: $error, details: $details}' >&2
    exit 2
}

THREAD_ID=$(echo "$THREAD_RESULT" | jq -r '.data.node.pullRequestReviewThread.id // ""')
IS_RESOLVED=$(echo "$THREAD_RESULT" | jq -r '.data.node.pullRequestReviewThread.isResolved // false')
CAN_RESOLVE=$(echo "$THREAD_RESULT" | jq -r '.data.node.pullRequestReviewThread.viewerCanResolve // false')

if [[ -z "$THREAD_ID" || "$THREAD_ID" == "null" ]]; then
    jq -n --arg error "Could not find thread for comment" --arg response "$THREAD_RESULT" \
        '{error: $error, response: $response}' >&2
    exit 2
fi

# Check if already resolved on GitHub
if [[ "$IS_RESOLVED" == "true" ]]; then
    TIMESTAMP=$(date -Iseconds)

    # Update our database to reflect GitHub state (use UPSERT for robustness)
    sqlite3 "$DB_PATH" "
        INSERT INTO analyses (comment_id, decision, resolved_at, resolved_by)
        VALUES ('$COMMENT_ID_ESC', 'SKIP', '$TIMESTAMP', 'github')
        ON CONFLICT(comment_id) DO UPDATE SET
            resolved_at = excluded.resolved_at,
            resolved_by = excluded.resolved_by;
    " 2>/dev/null || true

    jq -n \
        --arg comment_id "$COMMENT_ID" \
        --arg thread_id "$THREAD_ID" \
        '{status: "already_resolved", comment_id: $comment_id, thread_id: $thread_id, message: "Thread already resolved on GitHub"}'
    exit 0
fi

# Check if we can resolve
if [[ "$CAN_RESOLVE" != "true" ]]; then
    jq -n \
        --arg comment_id "$COMMENT_ID" \
        --arg thread_id "$THREAD_ID" \
        '{status: "not_authorized", comment_id: $comment_id, thread_id: $thread_id, message: "User does not have permission to resolve this thread"}'
    exit 0
fi

# Dry run mode
if [[ "$DRY_RUN" == "true" ]]; then
    echo "DRY RUN: Would resolve thread $THREAD_ID for comment $COMMENT_ID" >&2
    echo "  File: $FILE_PATH" >&2
    echo "  Decision: $DECISION" >&2

    jq -n \
        --arg comment_id "$COMMENT_ID" \
        --arg thread_id "$THREAD_ID" \
        --arg file_path "$FILE_PATH" \
        --arg decision "$DECISION" \
        '{status: "dry_run", comment_id: $comment_id, thread_id: $thread_id, file_path: $file_path, decision: $decision}'
    exit 0
fi

# Resolve the thread via GraphQL mutation
RESOLVE_MUTATION='
mutation ResolveThread($threadId: ID!) {
  resolveReviewThread(input: {threadId: $threadId}) {
    thread {
      id
      isResolved
    }
  }
}
'

echo "Resolving thread $THREAD_ID..." >&2

RESOLVE_RESULT=$(gh api graphql -f query="$RESOLVE_MUTATION" -f threadId="$THREAD_ID" 2>&1) || {
    ERROR_MSG=$(echo "$RESOLVE_RESULT" | jq -r '.errors[0].message // "Unknown error"' 2>/dev/null || echo "$RESOLVE_RESULT")

    # Log error event
    sqlite3 "$DB_PATH" "INSERT INTO events (pr_id, comment_id, event_type, event_subtype, error_message)
        VALUES ($PR_ID, '$COMMENT_ID_ESC', 'error', 'resolve_thread', '$(escape_sql "$ERROR_MSG")');" 2>/dev/null || true

    jq -n --arg error "Failed to resolve thread" --arg details "$ERROR_MSG" \
        '{error: $error, details: $details}' >&2
    exit 2
}

NEW_IS_RESOLVED=$(echo "$RESOLVE_RESULT" | jq -r '.data.resolveReviewThread.thread.isResolved // false')

if [[ "$NEW_IS_RESOLVED" == "true" ]]; then
    TIMESTAMP=$(date -Iseconds)

    # Update database (use UPSERT for robustness - row may not exist)
    sqlite3 "$DB_PATH" "
        INSERT INTO analyses (comment_id, decision, resolved_at, resolved_by)
        VALUES ('$COMMENT_ID_ESC', 'SKIP', '$TIMESTAMP', 'auto')
        ON CONFLICT(comment_id) DO UPDATE SET
            resolved_at = excluded.resolved_at,
            resolved_by = excluded.resolved_by;
    " 2>/dev/null || true

    # Log success event
    THREAD_ID_ESC=$(escape_sql "$THREAD_ID")
    sqlite3 "$DB_PATH" "INSERT INTO events (pr_id, comment_id, event_type, event_subtype, event_data)
        VALUES ($PR_ID, '$COMMENT_ID_ESC', 'reply', 'resolve_thread', '{\"thread_id\": \"$THREAD_ID_ESC\"}');" 2>/dev/null || true

    jq -n \
        --arg comment_id "$COMMENT_ID" \
        --arg thread_id "$THREAD_ID" \
        --arg file_path "$FILE_PATH" \
        --arg decision "$DECISION" \
        --arg resolved_at "$TIMESTAMP" \
        '{status: "resolved", comment_id: $comment_id, thread_id: $thread_id, file_path: $file_path, decision: $decision, resolved_at: $resolved_at}'
else
    jq -n \
        --arg comment_id "$COMMENT_ID" \
        --arg thread_id "$THREAD_ID" \
        --arg result "$RESOLVE_RESULT" \
        '{status: "error", comment_id: $comment_id, thread_id: $thread_id, message: "Resolution returned false", response: $result}'
    exit 2
fi
