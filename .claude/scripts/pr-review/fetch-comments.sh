#!/bin/bash
# Fetch ALL PR comments (not just CodeRabbit) and store in database
# Version 2: Fixed counters, improved parsing, better error handling
# Usage: fetch-comments-v2.sh <PR_NUMBER> [--repo REPO] [--since TIMESTAMP] [--filter USER]
# Output: JSON {"fetched": N, "new": N, "updated": N}

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
SINCE=""
FILTER_USER=""
VERBOSE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --repo)
            REPO="$2"
            shift 2
            ;;
        --since)
            SINCE="$2"
            shift 2
            ;;
        --filter)
            FILTER_USER="$2"
            shift 2
            ;;
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            cat >&2 <<EOF
Usage: $0 <PR_NUMBER> [OPTIONS]

Fetch all PR comments and store in SQLite database.

Options:
  --repo REPO      Repository (owner/name), defaults to current repo
  --since TIME     Only fetch comments after this timestamp (ISO 8601)
  --filter USER    Only fetch comments from this user (e.g., coderabbitai)
  --verbose, -v    Show detailed progress

Examples:
  $0 11                              # Fetch all comments for PR #11
  $0 11 --filter coderabbitai        # Only CodeRabbit comments
  $0 11 --since 2026-01-10T00:00:00Z # Comments after date
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

echo "Fetching comments for PR #$PR_NUMBER (pr_id=$PR_ID)..." >&2

TIMESTAMP=$(date -Iseconds)

# Create temp directory for counters (workaround for subshell issue)
TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

echo 0 > "$TMPDIR/fetched"
echo 0 > "$TMPDIR/new"
echo 0 > "$TMPDIR/updated"
echo 0 > "$TMPDIR/errors"

# Helper: Increment counter
inc() {
    local file="$TMPDIR/$1"
    local val=$(cat "$file")
    echo $((val + 1)) > "$file"
}

# Helper: Parse CodeRabbit severity from body
parse_cr_severity() {
    local body="$1"
    if echo "$body" | grep -qEi "🔴|Critical|_Critical_"; then
        echo "critical"
    elif echo "$body" | grep -qEi "🟠|Major|_Major_"; then
        echo "major"
    elif echo "$body" | grep -qEi "🟡|Minor|_Minor_"; then
        echo "minor"
    elif echo "$body" | grep -qEi "🔵|Trivial|Nitpick|_Trivial_"; then
        echo "trivial"
    else
        echo ""
    fi
}

# Helper: Parse CodeRabbit category
parse_cr_category() {
    local body="$1"
    if echo "$body" | grep -qEi "Potential.?issue|_Potential issue_|⚠️"; then
        echo "potential-issue"
    elif echo "$body" | grep -qEi "Nitpick|🧹"; then
        echo "nitpick"
    elif echo "$body" | grep -qEi "suggestion"; then
        echo "suggestion"
    else
        echo ""
    fi
}

# Helper: Parse CodeRabbit type
parse_cr_type() {
    local body="$1"
    if echo "$body" | grep -qEi "security|vulnerab|injection|xss|csrf|secret|token"; then
        echo "security"
    elif echo "$body" | grep -qEi "\bbug\b|error|exception|crash|broken|incorrect"; then
        echo "bug"
    elif echo "$body" | grep -qEi "performance|optimiz|slow|memory|cache"; then
        echo "performance"
    elif echo "$body" | grep -qEi "style|format|naming|convention|indent"; then
        echo "style"
    elif echo "$body" | grep -qEi "best.?practice|pattern|idiom|recommend"; then
        echo "best-practice"
    elif echo "$body" | grep -qEi "documentation|comment|javadoc|readme"; then
        echo "documentation"
    else
        echo ""
    fi
}

# Helper: Parse addressed SHA
parse_addressed() {
    local body="$1"
    echo "$body" | grep -oP '(?:Addressed|Fixed|Resolved) in commit \K[a-f0-9]{7,40}' | head -1 || echo ""
}

# Helper: Check if has committable suggestion
has_suggestion() {
    local body="$1"
    if echo "$body" | grep -qEi "committable.?suggestion|📝 Committable"; then
        echo 1
    else
        echo 0
    fi
}

# Helper: Escape for SQL
escape_sql() {
    echo "$1" | sed "s/'/''/g"
}

# Helper: Insert/update comment in database
upsert_comment() {
    local id="$1" source="$2" review_id="$3" user_login="$4" user_type="$5"
    local file_path="$6" line_number="$7" side="$8" diff_hunk="$9"
    local body="${10}" in_reply_to="${11}" created_at="${12}" updated_at="${13}"
    local github_url="${14}"

    local is_bot=0
    if [[ "$user_type" == "Bot" ]] || [[ "$user_login" == *"[bot]"* ]]; then
        is_bot=1
    fi

    # Parse CodeRabbit fields if applicable
    local cr_severity="" cr_category="" cr_type="" cr_has_suggestion=0 cr_addressed_sha=""
    if [[ "$user_login" == *"coderabbit"* ]]; then
        cr_severity=$(parse_cr_severity "$body")
        cr_category=$(parse_cr_category "$body")
        cr_type=$(parse_cr_type "$body")
        cr_has_suggestion=$(has_suggestion "$body")
        cr_addressed_sha=$(parse_addressed "$body")
    fi

    # Escape strings
    local body_esc=$(escape_sql "$body")
    local diff_hunk_esc=$(escape_sql "$diff_hunk")
    local file_path_esc=$(escape_sql "$file_path")
    local github_url_esc=$(escape_sql "$github_url")

    # Handle NULL values
    [[ -z "$review_id" ]] && review_id="NULL" || review_id="'$review_id'"
    [[ -z "$line_number" || "$line_number" == "null" ]] && line_number="NULL"
    [[ -z "$in_reply_to" || "$in_reply_to" == "null" ]] && in_reply_to="NULL" || in_reply_to="'$in_reply_to'"
    [[ -z "$side" || "$side" == "null" ]] && side="NULL" || side="'$side'"
    [[ -z "$cr_severity" ]] && cr_severity_sql="NULL" || cr_severity_sql="'$cr_severity'"
    [[ -z "$cr_category" ]] && cr_category_sql="NULL" || cr_category_sql="'$cr_category'"
    [[ -z "$cr_type" ]] && cr_type_sql="NULL" || cr_type_sql="'$cr_type'"
    [[ -z "$cr_addressed_sha" ]] && cr_addressed_sql="NULL" || cr_addressed_sql="'$cr_addressed_sha'"

    # Check if exists
    local exists
    exists=$(sqlite3 "$DB_PATH" "SELECT 1 FROM comments WHERE id = '$id';" 2>/dev/null || echo "")

    local sql="INSERT INTO comments (id, pr_id, github_url, source, review_id, user_login, user_type, is_bot,
        file_path, line_number, side, diff_hunk, body, in_reply_to_id, created_at, updated_at,
        cr_severity, cr_category, cr_type, cr_has_suggestion, cr_addressed_sha, fetched_at)
    VALUES ('$id', $PR_ID, '$github_url_esc', '$source', $review_id, '$user_login', '$user_type', $is_bot,
        '$file_path_esc', $line_number, $side, '$diff_hunk_esc', '$body_esc', $in_reply_to, '$created_at', '$updated_at',
        $cr_severity_sql, $cr_category_sql, $cr_type_sql, $cr_has_suggestion, $cr_addressed_sql, '$TIMESTAMP')
    ON CONFLICT(id) DO UPDATE SET
        body = excluded.body,
        updated_at = excluded.updated_at,
        cr_severity = COALESCE(excluded.cr_severity, comments.cr_severity),
        cr_category = COALESCE(excluded.cr_category, comments.cr_category),
        cr_type = COALESCE(excluded.cr_type, comments.cr_type),
        cr_has_suggestion = excluded.cr_has_suggestion,
        cr_addressed_sha = COALESCE(excluded.cr_addressed_sha, comments.cr_addressed_sha),
        fetched_at = excluded.fetched_at;"

    if sqlite3 "$DB_PATH" "$sql" 2>/dev/null; then
        inc fetched
        if [[ -z "$exists" ]]; then
            inc new
            [[ "$VERBOSE" == "true" ]] && echo "  + New: $id ($user_login on $file_path)" >&2
        else
            inc updated
            [[ "$VERBOSE" == "true" ]] && echo "  ~ Updated: $id" >&2
        fi
        return 0
    else
        inc errors
        echo "  ! Error inserting comment $id" >&2
        return 1
    fi
}

# ============================================
# 1. Fetch inline review comments
# ============================================
echo "Fetching inline review comments..." >&2

INLINE_COMMENTS=$(gh api "repos/$REPO/pulls/$PR_NUMBER/comments" --paginate 2>&1) || {
    echo "Warning: Failed to fetch inline comments" >&2
    INLINE_COMMENTS="[]"
}

INLINE_COUNT=$(echo "$INLINE_COMMENTS" | jq 'length')
echo "  Found $INLINE_COUNT inline comments" >&2

# Process with process substitution to avoid subshell
while read -r comment; do
    [[ -z "$comment" ]] && continue

    id=$(echo "$comment" | jq -r '.id')
    user_login=$(echo "$comment" | jq -r '.user.login // ""')
    user_type=$(echo "$comment" | jq -r '.user.type // "User"')
    file_path=$(echo "$comment" | jq -r '.path // ""')
    line_number=$(echo "$comment" | jq -r '.line // .original_line // null')
    side=$(echo "$comment" | jq -r '.side // null')
    diff_hunk=$(echo "$comment" | jq -r '.diff_hunk // ""')
    body=$(echo "$comment" | jq -r '.body // ""')
    in_reply_to=$(echo "$comment" | jq -r '.in_reply_to_id // null')
    created_at=$(echo "$comment" | jq -r '.created_at // ""')
    updated_at=$(echo "$comment" | jq -r '.updated_at // ""')
    github_url=$(echo "$comment" | jq -r '.html_url // ""')

    # Apply filters
    if [[ -n "$FILTER_USER" ]] && [[ "$user_login" != *"$FILTER_USER"* ]]; then
        continue
    fi
    if [[ -n "$SINCE" ]] && [[ "$created_at" < "$SINCE" ]]; then
        continue
    fi

    upsert_comment "$id" "inline" "" "$user_login" "$user_type" \
        "$file_path" "$line_number" "$side" "$diff_hunk" \
        "$body" "$in_reply_to" "$created_at" "$updated_at" \
        "$github_url"

done < <(echo "$INLINE_COMMENTS" | jq -c '.[]')

# ============================================
# 2. Fetch review body comments
# ============================================
echo "Fetching review body comments..." >&2

REVIEWS=$(gh api "repos/$REPO/pulls/$PR_NUMBER/reviews" --paginate 2>&1) || {
    echo "Warning: Failed to fetch reviews" >&2
    REVIEWS="[]"
}

REVIEW_COUNT=$(echo "$REVIEWS" | jq '[.[] | select(.body != null and .body != "")] | length')
echo "  Found $REVIEW_COUNT reviews with body" >&2

while read -r review; do
    [[ -z "$review" ]] && continue

    review_id=$(echo "$review" | jq -r '.id')
    user_login=$(echo "$review" | jq -r '.user.login // ""')
    user_type=$(echo "$review" | jq -r '.user.type // "User"')
    body=$(echo "$review" | jq -r '.body // ""')
    submitted_at=$(echo "$review" | jq -r '.submitted_at // ""')
    github_url=$(echo "$review" | jq -r '.html_url // ""')

    # Apply filters
    if [[ -n "$FILTER_USER" ]] && [[ "$user_login" != *"$FILTER_USER"* ]]; then
        continue
    fi

    # Generate unique ID for review body
    hash=$(echo -n "$review_id$body" | sha256sum | cut -c1-8)
    comment_id="review-${review_id}-${hash}"

    upsert_comment "$comment_id" "review_body" "$review_id" "$user_login" "$user_type" \
        "" "" "" "" \
        "$body" "" "$submitted_at" "$submitted_at" \
        "$github_url"

done < <(echo "$REVIEWS" | jq -c '.[] | select(.body != null and .body != "")')

# ============================================
# 3. Fetch issue comments (PR conversation)
# ============================================
echo "Fetching issue comments (PR conversation)..." >&2

ISSUE_COMMENTS=$(gh api "repos/$REPO/issues/$PR_NUMBER/comments" --paginate 2>&1) || {
    echo "Warning: Failed to fetch issue comments" >&2
    ISSUE_COMMENTS="[]"
}

ISSUE_COUNT=$(echo "$ISSUE_COMMENTS" | jq 'length')
echo "  Found $ISSUE_COUNT issue comments" >&2

while read -r comment; do
    [[ -z "$comment" ]] && continue

    id=$(echo "$comment" | jq -r '.id')
    user_login=$(echo "$comment" | jq -r '.user.login // ""')
    user_type=$(echo "$comment" | jq -r '.user.type // "User"')
    body=$(echo "$comment" | jq -r '.body // ""')
    created_at=$(echo "$comment" | jq -r '.created_at // ""')
    updated_at=$(echo "$comment" | jq -r '.updated_at // ""')
    github_url=$(echo "$comment" | jq -r '.html_url // ""')

    # Apply filters
    if [[ -n "$FILTER_USER" ]] && [[ "$user_login" != *"$FILTER_USER"* ]]; then
        continue
    fi
    if [[ -n "$SINCE" ]] && [[ "$created_at" < "$SINCE" ]]; then
        continue
    fi

    upsert_comment "issue-$id" "issue_comment" "" "$user_login" "$user_type" \
        "" "" "" "" \
        "$body" "" "$created_at" "$updated_at" \
        "$github_url"

done < <(echo "$ISSUE_COMMENTS" | jq -c '.[]')

# ============================================
# Get final counts
# ============================================
FETCHED=$(cat "$TMPDIR/fetched")
NEW=$(cat "$TMPDIR/new")
UPDATED=$(cat "$TMPDIR/updated")
ERRORS=$(cat "$TMPDIR/errors")

TOTAL_COMMENTS=$(sqlite3 "$DB_PATH" "SELECT COUNT(*) FROM comments WHERE pr_id=$PR_ID;")
CODERABBIT_COMMENTS=$(sqlite3 "$DB_PATH" "SELECT COUNT(*) FROM comments WHERE pr_id=$PR_ID AND user_login LIKE '%coderabbit%';")

# Update PR last_synced_at
sqlite3 "$DB_PATH" "UPDATE prs SET last_synced_at = '$TIMESTAMP' WHERE id = $PR_ID;" 2>/dev/null || true

# Log event
sqlite3 "$DB_PATH" "INSERT INTO events (pr_id, event_type, event_subtype, event_data)
VALUES ($PR_ID, 'fetch', 'fetch_comments', '{\"fetched\": $FETCHED, \"new\": $NEW, \"updated\": $UPDATED, \"errors\": $ERRORS}');" 2>/dev/null || true

echo "" >&2
echo "Summary: $FETCHED processed ($NEW new, $UPDATED updated, $ERRORS errors)" >&2
echo "Database: $TOTAL_COMMENTS total comments, $CODERABBIT_COMMENTS from CodeRabbit" >&2

# Output result
jq -n \
    --argjson fetched "$FETCHED" \
    --argjson new "$NEW" \
    --argjson updated "$UPDATED" \
    --argjson errors "$ERRORS" \
    --argjson total "$TOTAL_COMMENTS" \
    --argjson coderabbit "$CODERABBIT_COMMENTS" \
    --argjson pr_id "$PR_ID" \
    --arg timestamp "$TIMESTAMP" \
    '{
        status: "ok",
        pr_id: $pr_id,
        processed: $fetched,
        new: $new,
        updated: $updated,
        errors: $errors,
        total_in_db: $total,
        coderabbit_in_db: $coderabbit,
        fetched_at: $timestamp
    }'
