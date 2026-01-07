#!/bin/bash
# Script de récupération des commentaires CodeRabbit pour une PR
# Usage: ./fetch-coderabbit-comments.sh <PR_NUMBER>

set -euo pipefail

# Résoudre jq dynamiquement pour portabilité
if command -v jq &>/dev/null; then
    JQ=$(command -v jq)
else
    echo "Error: jq is required but not found in PATH" >&2
    echo "Install with: sudo apt install jq (Debian/Ubuntu) or brew install jq (macOS)" >&2
    exit 1
fi

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

# Récupérer les commentaires CodeRabbit inline (review comments API)
echo "" >&2
echo "=== Fetching CodeRabbit Inline Comments ===" >&2

INLINE_COMMENTS=$(gh api "repos/$REPO/pulls/$PR_NUMBER/comments" --jq '
  [.[] | select(.user.login | contains("coderabbit"))] |
  map({
    id: .id,
    source: "inline",
    review_id: null,
    path: .path,
    line: (.line // .original_line // 0),
    created_at: .created_at,
    body: .body
  })
')

INLINE_COUNT=$(echo "$INLINE_COMMENTS" | $JQ 'length')
echo "Found $INLINE_COUNT inline review comments" >&2

# Récupérer les commentaires depuis les review bodies (outside the diff)
echo "" >&2
echo "=== Fetching CodeRabbit Review Body Comments ===" >&2

REVIEW_BODIES=$(gh api "repos/$REPO/pulls/$PR_NUMBER/reviews" --jq '
  [.[] | select(.user.login | contains("coderabbit")) | select(.body != null and .body != "")] |
  map({review_id: .id, submitted_at: .submitted_at, body: .body})
')

# Parser les review bodies pour extraire les commentaires "outside the diff"
# Format: "In @path/to/file:\n- Around line X: Description..."
REVIEW_BODY_COMMENTS="[]"
REVIEW_BODY_COUNT=0

while read -r review; do
    [ -z "$review" ] && continue
    REVIEW_ID=$(echo "$review" | $JQ -r '.review_id')
    SUBMITTED_AT=$(echo "$review" | $JQ -r '.submitted_at')
    BODY=$(echo "$review" | $JQ -r '.body')

    # Extraire les blocs "In @filepath:" avec leurs commentaires
    # Utilise perl pour un parsing multiline plus robuste
    if command -v perl &>/dev/null; then
        EXTRACTED=$(echo "$BODY" | perl -0777 -ne '
            while (/In @([^\n:]+)[:\n]+((?:- (?:Around )?[Ll]ines? \d+(?:-\d+)?[:\s]+[^\n]+\n?)+)/g) {
                my $file = $1;
                my $comments = $2;
                while ($comments =~ /- (?:Around )?[Ll]ines? (\d+)(?:-\d+)?[:\s]+([^\n]+)/g) {
                    print "$file|$1|$2\n";
                }
            }
        ')

        while IFS='|' read -r filepath line_num description; do
            [ -z "$filepath" ] && continue
            # Générer un ID unique pour ces commentaires (review_id + hash du contenu)
            HASH=$(echo "$filepath$line_num$description" | md5sum | cut -c1-8)
            COMMENT_ID="review-${REVIEW_ID}-${HASH}"

            # Encoder la description en JSON-safe
            DESC_JSON=$(echo "$description" | $JQ -Rs '.[:-1]')

            NEW_COMMENT=$($JQ -n \
                --arg id "$COMMENT_ID" \
                --arg review_id "$REVIEW_ID" \
                --arg path "$filepath" \
                --argjson line "$line_num" \
                --arg created_at "$SUBMITTED_AT" \
                --arg body "$description" \
                '{id: $id, source: "review_body", review_id: $review_id, path: $path, line: $line, created_at: $created_at, body: $body}')

            REVIEW_BODY_COMMENTS=$(echo "$REVIEW_BODY_COMMENTS" | $JQ --argjson new "$NEW_COMMENT" '. + [$new]')
            REVIEW_BODY_COUNT=$((REVIEW_BODY_COUNT + 1))
        done <<< "$EXTRACTED"
    fi
done <<< "$(echo "$REVIEW_BODIES" | $JQ -c '.[]')"

echo "Found $REVIEW_BODY_COUNT comments from review bodies" >&2

# Combiner les deux sources
COMMENTS=$(echo "$INLINE_COMMENTS" "$REVIEW_BODY_COMMENTS" | $JQ -s 'add')
COMMENT_COUNT=$(echo "$COMMENTS" | $JQ 'length')
echo "" >&2
echo "=== Total: $COMMENT_COUNT CodeRabbit comments ===" >&2

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
    SOURCE=$(echo "$comment" | $JQ -r '.source // "inline"')
    REVIEW_ID=$(echo "$comment" | $JQ -r '.review_id // "null"')
    FILEPATH=$(echo "$comment" | $JQ -r '.path')
    LINE=$(echo "$comment" | $JQ -r '.line')
    CREATED=$(echo "$comment" | $JQ -r '.created_at')
    BODY=$(echo "$comment" | $JQ -r '.body')

    # Extraire la sévérité du commentaire avec priorité (critical > potential-issue > nitpick)
    # Utilise les marqueurs CodeRabbit: 🔴 Critical, 🟠 Major, 🟡 Minor, 🔵 Trivial
    SEVERITY="unknown"
    # Priorité haute: critical/blocking
    if echo "$BODY" | grep -Eqi "🔴|critical|blocking|\bMajor\b"; then
        SEVERITY="critical"
    # Priorité moyenne: potential issue/minor
    elif echo "$BODY" | grep -Eqi "🟠|🟡|potential issue|\bminor\b"; then
        SEVERITY="potential-issue"
    # Priorité basse: nitpick/trivial
    elif echo "$BODY" | grep -Eqi "🔵|nitpick|\btrivial\b"; then
        SEVERITY="nitpick"
    fi

    # Extraire le type avec priorité (security > bug > performance > style > documentation)
    TYPE="unknown"
    if echo "$BODY" | grep -Eqi "\bsecurity\b|\bsécurité\b|\bvulnerability\b"; then
        TYPE="security"
    elif echo "$BODY" | grep -Eqi "\bbug\b|\berror\b|\bproblème\b|\bissue\b"; then
        TYPE="bug"
    elif echo "$BODY" | grep -Eqi "\bperformance\b|\boptimiz"; then
        TYPE="performance"
    elif echo "$BODY" | grep -Eqi "\bstyle\b|\bformat\b|\bmarkdown\b|\blint"; then
        TYPE="style"
    elif echo "$BODY" | grep -Eqi "\bdocumentation\b|\bdoc\b|\bcomment"; then
        TYPE="documentation"
    fi

    # Extraire un résumé court (première ligne bold ou première ligne)
    # Utilise jq pour un échappement YAML-safe (JSON est un sous-ensemble valide de YAML pour les strings)
    RAW_SUMMARY=$(echo "$BODY" | grep -E "^\*\*.*\*\*" | head -1 | sed 's/\*\*//g' || echo "$BODY" | head -1)
    # Tronquer à 100 caractères et encoder en JSON-safe string via jq
    SUMMARY=$(echo "$RAW_SUMMARY" | cut -c1-100 | $JQ -Rs '.[:-1]')

    # Écrire l'entrée YAML
    # Note: SUMMARY est déjà encodé JSON-safe avec guillemets par jq
    cat >> "$TRACKING_FILE" << EOF
  - id: "$ID"
    source: "$SOURCE"
    review_id: $REVIEW_ID
    file: "$FILEPATH"
    line: $LINE
    type: "$TYPE"
    severity: "$SEVERITY"
    created_at: "$CREATED"
    summary: $SUMMARY
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

# Afficher un résumé par sévérité (utilise regex pour matcher uniquement les clés YAML au bon niveau)
echo "" >&2
echo "=== Summary by Severity ===" >&2
grep -E "^    severity: " "$TRACKING_FILE" | sed 's/^    severity: "//' | sed 's/"$//' | sort | uniq -c | sort -rn >&2

echo "" >&2
echo "=== Summary by Type ===" >&2
grep -E "^    type: " "$TRACKING_FILE" | sed 's/^    type: "//' | sed 's/"$//' | sort | uniq -c | sort -rn >&2
