#!/bin/bash
# Script de récupération des commentaires CodeRabbit pour une PR
# Usage: ./fetch-coderabbit-comments.sh <PR_NUMBER>

set -euo pipefail

# Définir le chemin de jq explicitement
JQ="/usr/bin/jq"

PR_NUMBER="${1:-}"

if [[ -z "$PR_NUMBER" ]]; then
    echo "Usage: $0 <PR_NUMBER>"
    exit 1
fi

# Récupérer les infos du repo
REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner)

# Récupérer les détails de la PR
echo "=== PR #$PR_NUMBER Details ===" >&2
PR_INFO=$(gh pr view "$PR_NUMBER" --json title,headRefName,baseRefName,state)
PR_TITLE=$(echo "$PR_INFO" | $JQ -r '.title')
PR_BRANCH=$(echo "$PR_INFO" | $JQ -r '.headRefName')
PR_STATE=$(echo "$PR_INFO" | $JQ -r '.state')

echo "Title: $PR_TITLE" >&2
echo "Branch: $PR_BRANCH" >&2
echo "State: $PR_STATE" >&2

# Récupérer les commentaires CodeRabbit
echo "" >&2
echo "=== Fetching CodeRabbit Comments ===" >&2

COMMENTS=$(gh api "repos/$REPO/pulls/$PR_NUMBER/comments" --jq '
  [.[] | select(.user.login | contains("coderabbit"))] |
  map({
    id: .id,
    path: .path,
    line: (.line // .original_line // 0),
    created_at: .created_at,
    body: .body
  })
')

COMMENT_COUNT=$(echo "$COMMENTS" | $JQ 'length')
echo "Found $COMMENT_COUNT CodeRabbit review comments" >&2

# Extraire les infos structurées de chaque commentaire
TIMESTAMP=$(date -Iseconds)
TRACKING_FILE=".claude/pr-reviews/pr-${PR_NUMBER}-tracking.yaml"

# Générer le fichier YAML
cat > "$TRACKING_FILE" << EOF
# Tracking Review PR #$PR_NUMBER
# Generated: $TIMESTAMP
# Script: fetch-coderabbit-comments.sh

pr_number: $PR_NUMBER
pr_title: "$PR_TITLE"
branch: "$PR_BRANCH"
state: "$PR_STATE"
created_at: "$TIMESTAMP"
last_updated: "$TIMESTAMP"
status: in_progress

summary:
  total_comments: $COMMENT_COUNT
  analyzed: 0
  accepted: 0
  rejected: 0
  discussed: 0
  deferred: 0
  applied: 0

comments:
EOF

# Parser chaque commentaire et extraire les métadonnées
echo "$COMMENTS" | $JQ -c '.[]' | while read -r comment; do
    ID=$(echo "$comment" | $JQ -r '.id')
    FILEPATH=$(echo "$comment" | $JQ -r '.path')
    LINE=$(echo "$comment" | $JQ -r '.line')
    CREATED=$(echo "$comment" | $JQ -r '.created_at')
    BODY=$(echo "$comment" | $JQ -r '.body')

    # Extraire la sévérité du commentaire (nitpick, potential issue, etc.)
    SEVERITY="unknown"
    if echo "$BODY" | grep -qi "nitpick\|trivial"; then
        SEVERITY="nitpick"
    elif echo "$BODY" | grep -qi "potential issue\|minor"; then
        SEVERITY="potential-issue"
    elif echo "$BODY" | grep -qi "critical\|blocking"; then
        SEVERITY="critical"
    fi

    # Extraire le type (style, bug, security, etc.)
    TYPE="unknown"
    if echo "$BODY" | grep -qi "style\|format\|markdown\|langage\|language"; then
        TYPE="style"
    elif echo "$BODY" | grep -qi "bug\|error\|problème"; then
        TYPE="bug"
    elif echo "$BODY" | grep -qi "security\|sécurité"; then
        TYPE="security"
    elif echo "$BODY" | grep -qi "documentation\|doc\|comment"; then
        TYPE="documentation"
    elif echo "$BODY" | grep -qi "performance"; then
        TYPE="performance"
    fi

    # Extraire un résumé court (première ligne significative)
    SUMMARY=$(echo "$BODY" | grep -E "^\*\*.*\*\*$" | head -1 | sed 's/\*\*//g' || echo "$BODY" | head -1 | cut -c1-100)
    # Escape pour YAML
    SUMMARY=$(echo "$SUMMARY" | sed 's/"/\\"/g' | sed "s/'/\\'/g")

    # Écrire l'entrée YAML
    cat >> "$TRACKING_FILE" << EOF
  - id: "$ID"
    file: "$FILEPATH"
    line: $LINE
    type: "$TYPE"
    severity: "$SEVERITY"
    created_at: "$CREATED"
    summary: "$SUMMARY"
    analysis:
      decision: null
      confidence: null
      criticality: null
      effort: null
      rationale: null
      code_suggestion: null
    status: pending
    applied_at: null

EOF

    echo "  - [$SEVERITY] $FILEPATH:$LINE - $TYPE" >&2
done

echo "" >&2
echo "=== Tracking file generated ===" >&2
echo "File: $TRACKING_FILE" >&2
echo "Total comments: $COMMENT_COUNT" >&2

# Afficher un résumé par sévérité
echo "" >&2
echo "=== Summary by Severity ===" >&2
grep "severity:" "$TRACKING_FILE" | sort | uniq -c | sort -rn >&2

echo "" >&2
echo "=== Summary by Type ===" >&2
grep "type:" "$TRACKING_FILE" | sort | uniq -c | sort -rn >&2
