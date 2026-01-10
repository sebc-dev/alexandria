#!/bin/bash
# Post a reply to a GitHub PR comment
# Usage: post-reply.sh <COMMENT_ID> <BODY> [--dry-run] [--repo REPO]
# Output: JSON {"status": "posted|dry_run|error", "reply_id": "...", ...}

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DB_DIR="${HOME}/.local/share/alexandria"
DB_PATH="${DB_DIR}/pr-reviews.db"

# Default repo
DEFAULT_REPO=""
if git rev-parse --is-inside-work-tree &>/dev/null 2>&1; then
    DEFAULT_REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo "")
fi

# Parse arguments
COMMENT_ID=""
BODY=""
DRY_RUN=false
REPO="${DEFAULT_REPO}"
TEMPLATE_TYPE=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --repo)
            REPO="$2"
            shift 2
            ;;
        --template)
            TEMPLATE_TYPE="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: $0 <COMMENT_ID> <BODY> [--dry-run] [--repo REPO] [--template TYPE]" >&2
            echo "  COMMENT_ID  The comment ID to reply to" >&2
            echo "  BODY        The reply body text" >&2
            echo "  --dry-run   Don't actually post, just show what would be posted" >&2
            echo "  --repo      Repository (owner/name), defaults to current repo" >&2
            echo "  --template  Template type (accept, reject, defer, discuss, duplicate)" >&2
            exit 0
            ;;
        *)
            if [[ -z "$COMMENT_ID" ]]; then
                COMMENT_ID="$1"
            elif [[ -z "$BODY" ]]; then
                BODY="$1"
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

if [[ -z "$BODY" ]]; then
    echo '{"error": "Reply body required"}' >&2
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

# Get comment info from database
COMMENT_DATA=$(sqlite3 -json "$DB_PATH" "
SELECT c.id, c.source, c.pr_id, c.user_login, c.file_path, c.line_number, p.pr_number
FROM comments c
JOIN prs p ON c.pr_id = p.id
WHERE c.id = '$COMMENT_ID';
" 2>/dev/null)

if [[ -z "$COMMENT_DATA" ]] || [[ "$COMMENT_DATA" == "[]" ]]; then
    echo "{\"error\": \"Comment $COMMENT_ID not found in database\"}" >&2
    exit 1
fi

SOURCE=$(echo "$COMMENT_DATA" | jq -r '.[0].source')
PR_ID=$(echo "$COMMENT_DATA" | jq -r '.[0].pr_id')
PR_NUMBER=$(echo "$COMMENT_DATA" | jq -r '.[0].pr_number')
USER_LOGIN=$(echo "$COMMENT_DATA" | jq -r '.[0].user_login')
FILE_PATH=$(echo "$COMMENT_DATA" | jq -r '.[0].file_path // ""')

TIMESTAMP=$(date -Iseconds)

# Helper: Escape for SQL
escape_sql() {
    echo "$1" | sed "s/'/''/g"
}

BODY_ESC=$(escape_sql "$BODY")

# Determine how to reply based on comment source
if [[ "$SOURCE" == "inline" ]]; then
    # Inline comments: reply via review comments API
    # Need numeric ID (strip any prefix)
    NUMERIC_ID=$(echo "$COMMENT_ID" | grep -oE '[0-9]+' | head -1)

    if [[ "$DRY_RUN" == "true" ]]; then
        echo "DRY RUN: Would post reply to inline comment #$NUMERIC_ID on PR #$PR_NUMBER" >&2
        echo "Body:" >&2
        echo "$BODY" >&2

        # Save as draft in database
        sqlite3 "$DB_PATH" "INSERT INTO replies (comment_id, body, template_type, status, dry_run)
            VALUES ('$COMMENT_ID', '$BODY_ESC', '$TEMPLATE_TYPE', 'draft', 1);" 2>/dev/null || true

        jq -n \
            --arg comment_id "$COMMENT_ID" \
            --arg body "$BODY" \
            --arg pr_number "$PR_NUMBER" \
            '{status: "dry_run", comment_id: $comment_id, pr_number: $pr_number, body: $body}'
        exit 0
    fi

    # Post the reply
    REPLY_RESULT=$(gh api "repos/$REPO/pulls/$PR_NUMBER/comments" \
        -X POST \
        -f body="$BODY" \
        -F in_reply_to="$NUMERIC_ID" 2>&1) || {
        ERROR_MSG=$(echo "$REPLY_RESULT" | head -1)

        # Save failed attempt
        sqlite3 "$DB_PATH" "INSERT INTO replies (comment_id, body, template_type, status, error_message)
            VALUES ('$COMMENT_ID', '$BODY_ESC', '$TEMPLATE_TYPE', 'failed', '$(escape_sql "$ERROR_MSG")');" 2>/dev/null || true

        echo "{\"status\": \"error\", \"error\": \"Failed to post reply: $ERROR_MSG\"}" >&2
        exit 2
    }

    REPLY_ID=$(echo "$REPLY_RESULT" | jq -r '.id')
    REPLY_URL=$(echo "$REPLY_RESULT" | jq -r '.html_url // ""')

    # Save successful reply in database
    sqlite3 "$DB_PATH" "INSERT INTO replies (comment_id, github_reply_id, body, template_type, status, posted_at)
        VALUES ('$COMMENT_ID', '$REPLY_ID', '$BODY_ESC', '$TEMPLATE_TYPE', 'posted', '$TIMESTAMP');" 2>/dev/null || true

    # Log event
    sqlite3 "$DB_PATH" "INSERT INTO events (pr_id, comment_id, event_type, event_subtype, event_data)
        VALUES ($PR_ID, '$COMMENT_ID', 'reply', 'post_reply', '{\"reply_id\": \"$REPLY_ID\"}');" 2>/dev/null || true

    jq -n \
        --arg comment_id "$COMMENT_ID" \
        --arg reply_id "$REPLY_ID" \
        --arg reply_url "$REPLY_URL" \
        --arg pr_number "$PR_NUMBER" \
        '{status: "posted", comment_id: $comment_id, reply_id: $reply_id, reply_url: $reply_url, pr_number: $pr_number}'

elif [[ "$SOURCE" == "review_body" ]]; then
    # Review body comments: we can't reply directly, post as issue comment
    echo "Note: Review body comments don't support direct replies. Posting as PR comment." >&2

    if [[ "$DRY_RUN" == "true" ]]; then
        echo "DRY RUN: Would post PR comment on #$PR_NUMBER referencing review body comment" >&2
        echo "Body:" >&2
        echo "$BODY" >&2

        sqlite3 "$DB_PATH" "INSERT INTO replies (comment_id, body, template_type, status, dry_run)
            VALUES ('$COMMENT_ID', '$BODY_ESC', '$TEMPLATE_TYPE', 'draft', 1);" 2>/dev/null || true

        jq -n \
            --arg comment_id "$COMMENT_ID" \
            --arg body "$BODY" \
            --arg pr_number "$PR_NUMBER" \
            '{status: "dry_run", comment_id: $comment_id, pr_number: $pr_number, body: $body, note: "review_body comment - will post as PR comment"}'
        exit 0
    fi

    # Add reference to original comment
    FULL_BODY="Re: $FILE_PATH

$BODY"

    REPLY_RESULT=$(gh api "repos/$REPO/issues/$PR_NUMBER/comments" \
        -X POST \
        -f body="$FULL_BODY" 2>&1) || {
        ERROR_MSG=$(echo "$REPLY_RESULT" | head -1)

        sqlite3 "$DB_PATH" "INSERT INTO replies (comment_id, body, template_type, status, error_message)
            VALUES ('$COMMENT_ID', '$BODY_ESC', '$TEMPLATE_TYPE', 'failed', '$(escape_sql "$ERROR_MSG")');" 2>/dev/null || true

        echo "{\"status\": \"error\", \"error\": \"Failed to post reply: $ERROR_MSG\"}" >&2
        exit 2
    }

    REPLY_ID=$(echo "$REPLY_RESULT" | jq -r '.id')
    REPLY_URL=$(echo "$REPLY_RESULT" | jq -r '.html_url // ""')

    sqlite3 "$DB_PATH" "INSERT INTO replies (comment_id, github_reply_id, body, template_type, status, posted_at)
        VALUES ('$COMMENT_ID', 'issue-$REPLY_ID', '$BODY_ESC', '$TEMPLATE_TYPE', 'posted', '$TIMESTAMP');" 2>/dev/null || true

    sqlite3 "$DB_PATH" "INSERT INTO events (pr_id, comment_id, event_type, event_subtype, event_data)
        VALUES ($PR_ID, '$COMMENT_ID', 'reply', 'post_reply', '{\"reply_id\": \"issue-$REPLY_ID\"}');" 2>/dev/null || true

    jq -n \
        --arg comment_id "$COMMENT_ID" \
        --arg reply_id "issue-$REPLY_ID" \
        --arg reply_url "$REPLY_URL" \
        --arg pr_number "$PR_NUMBER" \
        '{status: "posted", comment_id: $comment_id, reply_id: $reply_id, reply_url: $reply_url, pr_number: $pr_number, note: "Posted as PR comment (review_body source)"}'

elif [[ "$SOURCE" == "issue_comment" ]]; then
    # Issue comments: reply as another issue comment
    if [[ "$DRY_RUN" == "true" ]]; then
        echo "DRY RUN: Would post issue comment on #$PR_NUMBER" >&2
        echo "Body:" >&2
        echo "$BODY" >&2

        sqlite3 "$DB_PATH" "INSERT INTO replies (comment_id, body, template_type, status, dry_run)
            VALUES ('$COMMENT_ID', '$BODY_ESC', '$TEMPLATE_TYPE', 'draft', 1);" 2>/dev/null || true

        jq -n \
            --arg comment_id "$COMMENT_ID" \
            --arg body "$BODY" \
            --arg pr_number "$PR_NUMBER" \
            '{status: "dry_run", comment_id: $comment_id, pr_number: $pr_number, body: $body}'
        exit 0
    fi

    REPLY_RESULT=$(gh api "repos/$REPO/issues/$PR_NUMBER/comments" \
        -X POST \
        -f body="$BODY" 2>&1) || {
        ERROR_MSG=$(echo "$REPLY_RESULT" | head -1)

        sqlite3 "$DB_PATH" "INSERT INTO replies (comment_id, body, template_type, status, error_message)
            VALUES ('$COMMENT_ID', '$BODY_ESC', '$TEMPLATE_TYPE', 'failed', '$(escape_sql "$ERROR_MSG")');" 2>/dev/null || true

        echo "{\"status\": \"error\", \"error\": \"Failed to post reply: $ERROR_MSG\"}" >&2
        exit 2
    }

    REPLY_ID=$(echo "$REPLY_RESULT" | jq -r '.id')
    REPLY_URL=$(echo "$REPLY_RESULT" | jq -r '.html_url // ""')

    sqlite3 "$DB_PATH" "INSERT INTO replies (comment_id, github_reply_id, body, template_type, status, posted_at)
        VALUES ('$COMMENT_ID', 'issue-$REPLY_ID', '$BODY_ESC', '$TEMPLATE_TYPE', 'posted', '$TIMESTAMP');" 2>/dev/null || true

    sqlite3 "$DB_PATH" "INSERT INTO events (pr_id, comment_id, event_type, event_subtype, event_data)
        VALUES ($PR_ID, '$COMMENT_ID', 'reply', 'post_reply', '{\"reply_id\": \"issue-$REPLY_ID\"}');" 2>/dev/null || true

    jq -n \
        --arg comment_id "$COMMENT_ID" \
        --arg reply_id "issue-$REPLY_ID" \
        --arg reply_url "$REPLY_URL" \
        --arg pr_number "$PR_NUMBER" \
        '{status: "posted", comment_id: $comment_id, reply_id: $reply_id, reply_url: $reply_url, pr_number: $pr_number}'

else
    echo "{\"error\": \"Unknown comment source: $SOURCE\"}" >&2
    exit 1
fi
