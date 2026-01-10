#!/bin/bash
# Shared utility functions for pr-review scripts
# Source this file: source "${SCRIPT_DIR}/lib/utils.sh"

# Prevent multiple sourcing
[[ -n "${_PR_REVIEW_UTILS_LOADED:-}" ]] && return 0
_PR_REVIEW_UTILS_LOADED=1

# ============================================
# SQL Utilities
# ============================================

# Escape single quotes for SQL (prevents SQL injection)
# Usage: escaped=$(escape_sql "$value")
escape_sql() {
    # Use bash native substitution (faster than sed)
    echo "${1//\'/\'\'}"
}

# ============================================
# CodeRabbit Parsing Utilities
# ============================================

# Parse "Addressed in commit <sha>" from comment body
# Usage: sha=$(parse_addressed "$body")
# Returns: commit SHA or empty string
parse_addressed() {
    local body="$1"
    # Match patterns like "Addressed in commit abc1234" or "Fixed in commit abc1234"
    echo "$body" | grep -oP '(?:Addressed|Fixed|Resolved) in commit \K[a-f0-9]{7,40}' | head -1 || echo ""
}
