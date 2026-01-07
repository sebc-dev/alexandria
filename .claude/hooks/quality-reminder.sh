#!/bin/bash
# .claude/hooks/quality-reminder.sh
# Hook Stop - Rappelle les verifications de qualite si des fichiers Java ont ete modifies

set -euo pipefail

PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
TRACKING_FILE="${PROJECT_ROOT}/.claude/hooks/modified-java-files.json"

# Couleurs
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Verifier si des fichiers Java ont ete modifies dans cette session
check_modified_files() {
    if [ ! -f "$TRACKING_FILE" ]; then
        return 1
    fi

    # Verifier si jq est disponible
    if ! command -v jq &> /dev/null; then
        return 1
    fi

    local count
    count=$(jq -r '.modified_files | length' "$TRACKING_FILE" 2>/dev/null || echo "0")

    [ "$count" -gt 0 ]
}

# Afficher le rappel
show_reminder() {
    # Verifier si jq est disponible
    if ! command -v jq &> /dev/null; then
        return 0
    fi

    local count
    count=$(jq -r '.modified_files | length' "$TRACKING_FILE" 2>/dev/null || echo "0")

    local files
    files=$(jq -r '.modified_files[].file' "$TRACKING_FILE" 2>/dev/null | sed "s|$PROJECT_ROOT/||g" | head -5)

    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${YELLOW}  Rappel Qualite - $count fichier(s) Java modifie(s)${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "  Fichiers modifies:"
    echo "$files" | while read -r f; do
        [ -n "$f" ] && echo "    - $f"
    done
    if [ "$count" -gt 5 ]; then
        echo "    ... et $((count - 5)) autres"
    fi
    echo ""
    echo -e "  ${GREEN}Avant push/PR, executez:${NC}"
    echo "    /quality-check    - Analyse rapide (PMD, SpotBugs, Checkstyle)"
    echo "    /full-analysis    - Analyse complete avec PIT (~15 min)"
    echo ""
    echo "  Ou via Make:"
    echo "    make quick-check  - Equivalent a /quality-check"
    echo "    make full-check   - Equivalent a /full-analysis"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

# === MAIN ===
main() {
    if check_modified_files; then
        show_reminder
    fi
}

main
