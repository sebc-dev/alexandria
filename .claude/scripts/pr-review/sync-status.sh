#!/bin/bash
# Synchronize PR review status with GitHub (detect addressed comments, new replies, update PR state)
# Usage: sync-status.sh <PR_NUMBER> [--repo REPO] [--full]
# Output: JSON with sync results including state changes, new comments, addressed comments

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
PR_NUMBER=""
REPO="${DEFAULT_REPO}"
FULL_SYNC=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --repo)
            REPO="$2"
            shift 2
            ;;
        --full)
            FULL_SYNC=true
            shift
            ;;
        -h|--help)
            cat >&2 <<EOF
Usage: $0 <PR_NUMBER> [OPTIONS]

Synchronize PR data with GitHub.

Options:
  --repo REPO    Repository (owner/name), defaults to current repo
  --full         Force full sync (re-fetch all comments, not just new ones)

Output:
  JSON with sync results including:
  - pr_state_changed: true if PR state changed (open -> merged, etc.)
  - newly_addressed: comments marked as addressed since last sync
  - new_replies: replies to our comments
  - new_comments: entirely new comments
EOF
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

if ! [[ "$PR_NUMBER" =~ ^[0-9]+$ ]]; then
    echo '{"error": "PR number must be numeric"}' >&2
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

# Get PR ID from database
PR_ID=$(sqlite3 "$DB_PATH" "SELECT id FROM prs WHERE repo='$REPO' AND pr_number=$PR_NUMBER;" 2>/dev/null)
if [[ -z "$PR_ID" ]]; then
    echo '{"error": "PR not found in database. Run fetch-pr.sh first"}' >&2
    exit 1
fi

echo "Syncing status for PR #$PR_NUMBER..." >&2

TIMESTAMP=$(date -Iseconds)

# Track changes
NEWLY_ADDRESSED="[]"
NEW_REPLIES="[]"
NEW_COMMENTS="[]"
PR_STATE_CHANGED=false
OLD_STATE=""
NEW_STATE=""

# ============================================
# 0. Update PR metadata (state, title, etc.)
# ============================================
echo "Updating PR metadata..." >&2

# Get current state before update
OLD_STATE=$(sqlite3 "$DB_PATH" "SELECT state FROM prs WHERE id = $PR_ID;" 2>/dev/null || echo "unknown")

# Fetch fresh PR data
PR_UPDATE=$("$SCRIPT_DIR/fetch-pr.sh" "$PR_NUMBER" --repo "$REPO" 2>/dev/null) || {
    echo "Warning: Failed to update PR metadata" >&2
    PR_UPDATE="{}"
}

# Get new state after update
NEW_STATE=$(sqlite3 "$DB_PATH" "SELECT state FROM prs WHERE id = $PR_ID;" 2>/dev/null || echo "unknown")

# Detect state change
if [[ "$OLD_STATE" != "$NEW_STATE" ]]; then
    PR_STATE_CHANGED=true
    echo "  PR state changed: $OLD_STATE -> $NEW_STATE" >&2
fi

# Helper: Parse "Addressed in commit" from body
parse_addressed() {
    local body="$1"
    echo "$body" | grep -oP '(?:Addressed|Fixed|Resolved) in commit [a-f0-9]{7,40}' | grep -oP '[a-f0-9]{7,40}' | head -1 || echo ""
}

# Helper: Escape for SQL
escape_sql() {
    echo "$1" | sed "s/'/''/g"
}

# ============================================
# 1. Check for addressed comments (CodeRabbit)
# ============================================
echo "Checking for addressed comments..." >&2

# Get all CodeRabbit comments that we've replied to
CODERABBIT_COMMENTS=$(sqlite3 -json "$DB_PATH" "
SELECT c.id, c.body, c.cr_addressed_sha
FROM comments c
WHERE c.pr_id = $PR_ID
  AND c.user_login LIKE '%coderabbit%'
  AND c.cr_addressed_sha IS NULL;
" 2>/dev/null || echo "[]")

# Re-fetch these comments to check for updates
INLINE_COMMENTS=$(gh api "repos/$REPO/pulls/$PR_NUMBER/comments" --paginate 2>&1) || INLINE_COMMENTS="[]"

# Check each CodeRabbit comment for "Addressed" marker
# Use process substitution to avoid subshell variable loss
while read -r comment; do
    [[ -z "$comment" ]] && continue
    COMMENT_ID=$(echo "$comment" | jq -r '.id')

    # Get fresh body from GitHub
    FRESH_BODY=$(echo "$INLINE_COMMENTS" | jq -r --arg id "$COMMENT_ID" '.[] | select(.id == ($id | tonumber)) | .body // ""' 2>/dev/null || echo "")

    if [[ -n "$FRESH_BODY" ]]; then
        ADDRESSED_SHA=$(parse_addressed "$FRESH_BODY")

        if [[ -n "$ADDRESSED_SHA" ]]; then
            echo "  Comment $COMMENT_ID addressed in commit $ADDRESSED_SHA" >&2

            # Update database
            sqlite3 "$DB_PATH" "UPDATE comments
                SET cr_addressed_sha = '$ADDRESSED_SHA',
                    cr_addressed_at = '$TIMESTAMP',
                    body = '$(escape_sql "$FRESH_BODY")'
                WHERE id = '$COMMENT_ID';" 2>/dev/null || true

            NEWLY_ADDRESSED=$(echo "$NEWLY_ADDRESSED" | jq --arg id "$COMMENT_ID" --arg sha "$ADDRESSED_SHA" '. + [{id: $id, sha: $sha}]')
        fi
    fi
done < <(echo "$CODERABBIT_COMMENTS" | jq -c '.[]' 2>/dev/null)

# ============================================
# 2. Check for new replies to our comments
# ============================================
echo "Checking for new replies..." >&2

# Get comment IDs we've replied to
OUR_REPLY_TARGETS=$(sqlite3 "$DB_PATH" "SELECT DISTINCT comment_id FROM replies WHERE status = 'posted';" 2>/dev/null || echo "")

for COMMENT_ID in $OUR_REPLY_TARGETS; do
    # Get numeric ID for API query
    NUMERIC_ID=$(echo "$COMMENT_ID" | grep -oE '[0-9]+' | head -1)

    # Skip if not a valid numeric ID (review_body comments)
    [[ -z "$NUMERIC_ID" ]] && continue

    # Get all replies in this thread
    THREAD=$(echo "$INLINE_COMMENTS" | jq --arg id "$NUMERIC_ID" '[.[] | select(.in_reply_to_id == ($id | tonumber))]' 2>/dev/null || echo "[]")

    # Check for new replies (from others, not us)
    # Use process substitution to avoid subshell variable loss
    while read -r reply; do
        [[ -z "$reply" ]] && continue
        REPLY_ID=$(echo "$reply" | jq -r '.id')
        REPLY_USER=$(echo "$reply" | jq -r '.user.login')
        REPLY_BODY=$(echo "$reply" | jq -r '.body')
        REPLY_CREATED=$(echo "$reply" | jq -r '.created_at')

        # Check if we've already recorded this reply
        EXISTS=$(sqlite3 "$DB_PATH" "SELECT 1 FROM comments WHERE id = '$REPLY_ID';" 2>/dev/null || echo "")

        if [[ -z "$EXISTS" ]]; then
            echo "  New reply from $REPLY_USER on comment $COMMENT_ID" >&2

            # Insert new reply as a comment
            sqlite3 "$DB_PATH" "INSERT INTO comments (id, pr_id, source, user_login, is_bot, body, in_reply_to_id, created_at, fetched_at)
                VALUES ('$REPLY_ID', $PR_ID, 'inline', '$REPLY_USER', $(if [[ "$REPLY_USER" == *"[bot]"* ]]; then echo 1; else echo 0; fi), '$(escape_sql "$REPLY_BODY")', '$COMMENT_ID', '$REPLY_CREATED', '$TIMESTAMP');" 2>/dev/null || true

            NEW_REPLIES=$(echo "$NEW_REPLIES" | jq --arg id "$REPLY_ID" --arg user "$REPLY_USER" --arg parent "$COMMENT_ID" '. + [{id: $id, user: $user, parent_comment: $parent}]')
        fi
    done < <(echo "$THREAD" | jq -c '.[]' 2>/dev/null)
done

# ============================================
# 3. Check for entirely new comments
# ============================================
echo "Checking for new comments..." >&2

# Get last sync timestamp
LAST_SYNC=$(sqlite3 "$DB_PATH" "SELECT last_synced_at FROM prs WHERE id = $PR_ID;" 2>/dev/null || echo "")

# Count existing comments
EXISTING_COUNT=$(sqlite3 "$DB_PATH" "SELECT COUNT(*) FROM comments WHERE pr_id = $PR_ID;" 2>/dev/null || echo "0")

# Check current count from GitHub
GITHUB_COUNT=$(echo "$INLINE_COMMENTS" | jq 'length')

# Determine if we need to fetch new comments
NEED_FETCH=false

if [[ "$FULL_SYNC" == "true" ]]; then
    echo "  Full sync requested - re-fetching all comments..." >&2
    NEED_FETCH=true
elif [[ "$GITHUB_COUNT" -gt "$EXISTING_COUNT" ]]; then
    echo "  Found potential new comments (GitHub: $GITHUB_COUNT, DB: $EXISTING_COUNT)" >&2
    NEED_FETCH=true

    # Find comments not in our database
    EXISTING_IDS=$(sqlite3 "$DB_PATH" "SELECT id FROM comments WHERE pr_id = $PR_ID AND source = 'inline';" 2>/dev/null | tr '\n' '|')

    # Use process substitution to avoid subshell variable loss
    while read -r comment; do
        [[ -z "$comment" ]] && continue
        COMMENT_ID=$(echo "$comment" | jq -r '.id')

        # Check if we have this comment
        if ! echo "$EXISTING_IDS" | grep -q "$COMMENT_ID"; then
            USER_LOGIN=$(echo "$comment" | jq -r '.user.login')
            FILE_PATH=$(echo "$comment" | jq -r '.path // ""')

            echo "  New comment $COMMENT_ID from $USER_LOGIN on $FILE_PATH" >&2

            NEW_COMMENTS=$(echo "$NEW_COMMENTS" | jq --arg id "$COMMENT_ID" --arg user "$USER_LOGIN" --arg file "$FILE_PATH" '. + [{id: $id, user: $user, file: $file}]')
        fi
    done < <(echo "$INLINE_COMMENTS" | jq -c '.[]')
fi

# Fetch comments if needed
if [[ "$NEED_FETCH" == "true" ]]; then
    if [[ "$FULL_SYNC" == "true" ]]; then
        echo "  Re-fetching all comments..." >&2
        "$SCRIPT_DIR/fetch-comments.sh" "$PR_NUMBER" --repo "$REPO" >/dev/null 2>&1 || true
    else
        echo "  Re-fetching comments since $LAST_SYNC..." >&2
        "$SCRIPT_DIR/fetch-comments.sh" "$PR_NUMBER" --repo "$REPO" --since "$LAST_SYNC" >/dev/null 2>&1 || true
    fi
fi

# ============================================
# 3b. Update workflows status
# ============================================
echo "Updating workflow status..." >&2
"$SCRIPT_DIR/fetch-workflows.sh" "$PR_NUMBER" --repo "$REPO" >/dev/null 2>&1 || {
    echo "  Warning: Failed to update workflow status" >&2
}

# ============================================
# 4. Update PR last_synced_at
# ============================================
sqlite3 "$DB_PATH" "UPDATE prs SET last_synced_at = '$TIMESTAMP' WHERE id = $PR_ID;" 2>/dev/null || true

# Log event
sqlite3 "$DB_PATH" "INSERT INTO events (pr_id, event_type, event_subtype, event_data)
    VALUES ($PR_ID, 'sync', 'sync_status', '{\"timestamp\": \"$TIMESTAMP\"}');" 2>/dev/null || true

# ============================================
# 5. Get summary
# ============================================
SUMMARY=$(sqlite3 -json "$DB_PATH" "
SELECT
    (SELECT COUNT(*) FROM comments WHERE pr_id = $PR_ID) as total_comments,
    (SELECT COUNT(*) FROM comments WHERE pr_id = $PR_ID AND cr_addressed_sha IS NOT NULL) as addressed_comments,
    (SELECT COUNT(*) FROM analyses WHERE comment_id IN (SELECT id FROM comments WHERE pr_id = $PR_ID)) as analyzed_comments,
    (SELECT COUNT(*) FROM replies WHERE comment_id IN (SELECT id FROM comments WHERE pr_id = $PR_ID) AND status = 'posted') as posted_replies;
" 2>/dev/null || echo '[{}]')

# Count pending analyses
PENDING_ANALYSIS=$(sqlite3 "$DB_PATH" "
SELECT COUNT(*) FROM comments c
WHERE c.pr_id = $PR_ID
  AND c.id NOT IN (SELECT comment_id FROM analyses WHERE comment_id = c.id)
  AND c.in_reply_to_id IS NULL;
" 2>/dev/null || echo "0")

# Output result
echo "$SUMMARY" | jq --argjson newly_addressed "$NEWLY_ADDRESSED" \
    --argjson new_replies "$NEW_REPLIES" \
    --argjson new_comments "$NEW_COMMENTS" \
    --argjson pr_state_changed "$PR_STATE_CHANGED" \
    --arg old_state "$OLD_STATE" \
    --arg new_state "$NEW_STATE" \
    --argjson pending_analysis "$PENDING_ANALYSIS" \
    --argjson full_sync "$FULL_SYNC" \
    --arg timestamp "$TIMESTAMP" \
    '.[0] + {
        status: "ok",
        synced_at: $timestamp,
        full_sync: $full_sync,
        pr_state_changed: $pr_state_changed,
        old_state: $old_state,
        new_state: $new_state,
        pending_analysis: $pending_analysis,
        newly_addressed: $newly_addressed,
        new_replies: $new_replies,
        new_comments: $new_comments
    }'
