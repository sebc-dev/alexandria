#!/bin/bash
# Generate reports from the PR Review SQLite database
# Usage: report.sh <PR_NUMBER> [--format FORMAT] [--type TYPE]
# Formats: markdown (default), json, table, csv
# Types: summary (default), comments, pending, addressed, analysis

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
FORMAT="markdown"
REPORT_TYPE="summary"

while [[ $# -gt 0 ]]; do
    case $1 in
        --repo)
            REPO="$2"
            shift 2
            ;;
        --format|-f)
            FORMAT="$2"
            shift 2
            ;;
        --type|-t)
            REPORT_TYPE="$2"
            shift 2
            ;;
        -h|--help)
            cat >&2 <<EOF
Usage: $0 <PR_NUMBER> [OPTIONS]

Options:
  --repo REPO      Repository (owner/name), defaults to current repo
  --format, -f     Output format: markdown (default), json, table, csv
  --type, -t       Report type:
                     summary   - Overview with stats (default)
                     comments  - All CodeRabbit comments
                     pending   - Comments without analysis
                     addressed - Comments marked as addressed
                     analysis  - Analysis decisions breakdown
                     replies   - Our posted replies
                     timeline  - Chronological event log

Examples:
  $0 11                          # Summary for PR #11
  $0 11 -t comments -f table     # All comments as table
  $0 11 -t pending               # Pending comments to analyze
  $0 11 -t analysis -f json      # Analysis breakdown as JSON
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
    echo "Error: PR number required" >&2
    exit 1
fi

# Check database exists
if [[ ! -f "$DB_PATH" ]]; then
    echo "Error: Database not found. Run db-init.sh first" >&2
    exit 3
fi

# Get PR ID
PR_ID=$(sqlite3 "$DB_PATH" "SELECT id FROM prs WHERE repo='$REPO' AND pr_number=$PR_NUMBER;" 2>/dev/null)
if [[ -z "$PR_ID" ]]; then
    echo "Error: PR #$PR_NUMBER not found in database. Run fetch-pr.sh first" >&2
    exit 1
fi

# Helper: Format SQL output based on FORMAT
format_output() {
    local sql="$1"
    case $FORMAT in
        json)
            sqlite3 -json "$DB_PATH" "$sql" 2>/dev/null || echo "[]"
            ;;
        csv)
            sqlite3 -csv -header "$DB_PATH" "$sql"
            ;;
        table)
            sqlite3 -column -header "$DB_PATH" "$sql"
            ;;
        markdown)
            # Convert to markdown table
            sqlite3 -csv -header "$DB_PATH" "$sql" | awk -F',' '
                NR==1 {
                    printf "|"
                    for(i=1; i<=NF; i++) printf " %s |", $i
                    printf "\n|"
                    for(i=1; i<=NF; i++) printf "---|"
                    printf "\n"
                }
                NR>1 {
                    printf "|"
                    for(i=1; i<=NF; i++) printf " %s |", $i
                    printf "\n"
                }
            '
            ;;
    esac
}

# ============================================
# Report: Summary
# ============================================
report_summary() {
    local pr_info
    pr_info=$(sqlite3 -json "$DB_PATH" "
        SELECT pr_number, title, branch, state, review_status, last_synced_at
        FROM prs WHERE id = $PR_ID;
    " | jq '.[0]')

    local stats
    stats=$(sqlite3 -json "$DB_PATH" "
        SELECT
            (SELECT COUNT(*) FROM comments WHERE pr_id = $PR_ID) as total_comments,
            (SELECT COUNT(*) FROM comments WHERE pr_id = $PR_ID AND user_login LIKE '%coderabbit%') as coderabbit_comments,
            (SELECT COUNT(*) FROM comments WHERE pr_id = $PR_ID AND user_login LIKE '%coderabbit%' AND in_reply_to_id IS NULL) as coderabbit_original,
            (SELECT COUNT(*) FROM comments WHERE pr_id = $PR_ID AND cr_addressed_sha IS NOT NULL) as addressed,
            (SELECT COUNT(*) FROM analyses WHERE comment_id IN (SELECT id FROM comments WHERE pr_id = $PR_ID)) as analyzed,
            (SELECT COUNT(*) FROM analyses WHERE comment_id IN (SELECT id FROM comments WHERE pr_id = $PR_ID) AND decision = 'ACCEPT') as accepted,
            (SELECT COUNT(*) FROM analyses WHERE comment_id IN (SELECT id FROM comments WHERE pr_id = $PR_ID) AND decision = 'REJECT') as rejected,
            (SELECT COUNT(*) FROM analyses WHERE comment_id IN (SELECT id FROM comments WHERE pr_id = $PR_ID) AND decision = 'DEFER') as deferred,
            (SELECT COUNT(*) FROM analyses WHERE comment_id IN (SELECT id FROM comments WHERE pr_id = $PR_ID) AND decision = 'DISCUSS') as discussed,
            (SELECT COUNT(*) FROM replies WHERE comment_id IN (SELECT id FROM comments WHERE pr_id = $PR_ID) AND status = 'posted') as replied;
    " | jq '.[0]')

    local severity_stats
    severity_stats=$(sqlite3 -json "$DB_PATH" "
        SELECT
            COALESCE(cr_severity, 'unknown') as severity,
            COUNT(*) as count
        FROM comments
        WHERE pr_id = $PR_ID AND user_login LIKE '%coderabbit%' AND in_reply_to_id IS NULL
        GROUP BY cr_severity
        ORDER BY CASE cr_severity
            WHEN 'critical' THEN 1
            WHEN 'major' THEN 2
            WHEN 'minor' THEN 3
            WHEN 'trivial' THEN 4
            ELSE 5
        END;
    ")

    local type_stats
    type_stats=$(sqlite3 -json "$DB_PATH" "
        SELECT
            COALESCE(cr_type, 'unknown') as type,
            COUNT(*) as count
        FROM comments
        WHERE pr_id = $PR_ID AND user_login LIKE '%coderabbit%' AND in_reply_to_id IS NULL
        GROUP BY cr_type
        ORDER BY count DESC;
    ")

    if [[ "$FORMAT" == "json" ]]; then
        jq -n \
            --argjson pr "$pr_info" \
            --argjson stats "$stats" \
            --argjson severity "$severity_stats" \
            --argjson types "$type_stats" \
            '{pr: $pr, stats: $stats, by_severity: $severity, by_type: $types}'
    else
        local title=$(echo "$pr_info" | jq -r '.title')
        local branch=$(echo "$pr_info" | jq -r '.branch')
        local state=$(echo "$pr_info" | jq -r '.state')
        local synced=$(echo "$pr_info" | jq -r '.last_synced_at // "never"')

        local total=$(echo "$stats" | jq -r '.total_comments')
        local cr_total=$(echo "$stats" | jq -r '.coderabbit_comments')
        local cr_orig=$(echo "$stats" | jq -r '.coderabbit_original')
        local addressed=$(echo "$stats" | jq -r '.addressed')
        local analyzed=$(echo "$stats" | jq -r '.analyzed')
        local accepted=$(echo "$stats" | jq -r '.accepted')
        local rejected=$(echo "$stats" | jq -r '.rejected')
        local deferred=$(echo "$stats" | jq -r '.deferred')
        local discussed=$(echo "$stats" | jq -r '.discussed')
        local replied=$(echo "$stats" | jq -r '.replied')

        cat <<EOF
## PR #$PR_NUMBER Review Report

**Title:** $title
**Branch:** $branch
**State:** $state
**Last Synced:** $synced

### Overview

| Metric | Count |
|--------|-------|
| Total Comments | $total |
| CodeRabbit Comments | $cr_total |
| CodeRabbit Original (non-reply) | $cr_orig |
| Addressed by CodeRabbit | $addressed |

### Analysis Status

| Decision | Count |
|----------|-------|
| Analyzed | $analyzed |
| ACCEPT | $accepted |
| REJECT | $rejected |
| DEFER | $deferred |
| DISCUSS | $discussed |
| Replied on GitHub | $replied |

### By Severity

$(echo "$severity_stats" | jq -r '.[] | "| \(.severity) | \(.count) |"' | sed '1i| Severity | Count |\n|----------|-------|')

### By Type

$(echo "$type_stats" | jq -r '.[] | "| \(.type) | \(.count) |"' | sed '1i| Type | Count |\n|------|-------|')
EOF
    fi
}

# ============================================
# Report: Comments
# ============================================
report_comments() {
    local sql="
        SELECT
            c.id,
            COALESCE(c.file_path, '-') as file,
            COALESCE(c.line_number, 0) as line,
            COALESCE(c.cr_severity, '-') as severity,
            COALESCE(c.cr_type, '-') as type,
            CASE WHEN c.cr_addressed_sha IS NOT NULL THEN 'Yes' ELSE 'No' END as addressed,
            SUBSTR(c.body, 1, 80) as excerpt
        FROM comments c
        WHERE c.pr_id = $PR_ID
          AND c.user_login LIKE '%coderabbit%'
          AND c.in_reply_to_id IS NULL
        ORDER BY
            CASE c.cr_severity
                WHEN 'critical' THEN 1
                WHEN 'major' THEN 2
                WHEN 'minor' THEN 3
                WHEN 'trivial' THEN 4
                ELSE 5
            END,
            c.file_path, c.line_number;
    "
    format_output "$sql"
}

# ============================================
# Report: Pending (not analyzed)
# ============================================
report_pending() {
    local sql="
        SELECT
            c.id,
            COALESCE(c.file_path, '-') as file,
            COALESCE(c.line_number, 0) as line,
            COALESCE(c.cr_severity, '-') as severity,
            COALESCE(c.cr_category, '-') as category,
            SUBSTR(c.body, 1, 100) as excerpt
        FROM comments c
        LEFT JOIN analyses a ON c.id = a.comment_id
        WHERE c.pr_id = $PR_ID
          AND c.user_login LIKE '%coderabbit%'
          AND c.in_reply_to_id IS NULL
          AND a.id IS NULL
        ORDER BY
            CASE c.cr_severity
                WHEN 'critical' THEN 1
                WHEN 'major' THEN 2
                WHEN 'minor' THEN 3
                WHEN 'trivial' THEN 4
                ELSE 5
            END;
    "

    if [[ "$FORMAT" == "markdown" ]]; then
        local count
        count=$(sqlite3 "$DB_PATH" "
            SELECT COUNT(*)
            FROM comments c
            LEFT JOIN analyses a ON c.id = a.comment_id
            WHERE c.pr_id = $PR_ID
              AND c.user_login LIKE '%coderabbit%'
              AND c.in_reply_to_id IS NULL
              AND a.id IS NULL;
        ")
        echo "## Pending Comments ($count)"
        echo ""
        format_output "$sql"
    else
        format_output "$sql"
    fi
}

# ============================================
# Report: Addressed
# ============================================
report_addressed() {
    local sql="
        SELECT
            c.id,
            COALESCE(c.file_path, '-') as file,
            COALESCE(c.line_number, 0) as line,
            c.cr_addressed_sha as commit_sha,
            c.cr_addressed_at as addressed_at,
            COALESCE(a.decision, '-') as decision
        FROM comments c
        LEFT JOIN analyses a ON c.id = a.comment_id
        WHERE c.pr_id = $PR_ID
          AND c.user_login LIKE '%coderabbit%'
          AND c.cr_addressed_sha IS NOT NULL
        ORDER BY c.cr_addressed_at DESC;
    "
    format_output "$sql"
}

# ============================================
# Report: Analysis breakdown
# ============================================
report_analysis() {
    local sql="
        SELECT
            a.decision,
            COALESCE(a.criticality, '-') as criticality,
            c.id as comment_id,
            COALESCE(c.file_path, '-') as file,
            COALESCE(c.line_number, 0) as line,
            a.summary
        FROM analyses a
        JOIN comments c ON a.comment_id = c.id
        WHERE c.pr_id = $PR_ID
        ORDER BY
            CASE a.decision
                WHEN 'ACCEPT' THEN 1
                WHEN 'REJECT' THEN 2
                WHEN 'DEFER' THEN 3
                WHEN 'DISCUSS' THEN 4
                ELSE 5
            END,
            CASE a.criticality
                WHEN 'BLOCKING' THEN 1
                WHEN 'IMPORTANT' THEN 2
                WHEN 'MINOR' THEN 3
                WHEN 'COSMETIC' THEN 4
                ELSE 5
            END;
    "
    format_output "$sql"
}

# ============================================
# Report: Replies
# ============================================
report_replies() {
    local sql="
        SELECT
            r.id as reply_id,
            r.comment_id,
            r.template_type,
            r.status,
            r.posted_at,
            c.file_path as file,
            SUBSTR(r.body, 1, 60) as excerpt
        FROM replies r
        JOIN comments c ON r.comment_id = c.id
        WHERE c.pr_id = $PR_ID
        ORDER BY r.posted_at DESC;
    "
    format_output "$sql"
}

# ============================================
# Report: Timeline
# ============================================
report_timeline() {
    local sql="
        SELECT
            created_at as timestamp,
            event_type,
            event_subtype,
            SUBSTR(COALESCE(event_data, ''), 1, 50) as data
        FROM events
        WHERE pr_id = $PR_ID
        ORDER BY created_at DESC
        LIMIT 50;
    "
    format_output "$sql"
}

# ============================================
# Main: Route to appropriate report
# ============================================
case $REPORT_TYPE in
    summary)
        report_summary
        ;;
    comments)
        report_comments
        ;;
    pending)
        report_pending
        ;;
    addressed)
        report_addressed
        ;;
    analysis)
        report_analysis
        ;;
    replies)
        report_replies
        ;;
    timeline)
        report_timeline
        ;;
    *)
        echo "Unknown report type: $REPORT_TYPE" >&2
        echo "Valid types: summary, comments, pending, addressed, analysis, replies, timeline" >&2
        exit 1
        ;;
esac
