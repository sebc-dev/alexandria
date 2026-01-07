#!/bin/bash
# .claude/hooks/session-welcome.sh
# Hook SessionStart - Message de bienvenue et reset du tracking

set -euo pipefail

PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
TRACKING_FILE="${PROJECT_ROOT}/.claude/hooks/modified-java-files.json"

# Couleurs
CYAN='\033[0;36m'
GREEN='\033[0;32m'
NC='\033[0m'

# Reset du tracking pour nouvelle session
reset_tracking() {
    local timestamp
    timestamp=$(date -Iseconds)
    mkdir -p "$(dirname "$TRACKING_FILE")"
    echo '{"session_start":"'"$timestamp"'","modified_files":[]}' > "$TRACKING_FILE"
}

# Message de bienvenue
show_welcome() {
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${GREEN}  Alexandria RAG Server - Session Claude Code${NC}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo "  Hooks actifs:"
    echo "    - Formatage automatique (Spotless) sur fichiers .java"
    echo "    - Rappel qualite en fin de reponse"
    echo ""
    echo "  Commandes disponibles:"
    echo "    /quality-check   - PMD + SpotBugs + Checkstyle (~2-5 min)"
    echo "    /full-analysis   - Analyse complete + PIT (~10-15 min)"
    echo "    /pit-test        - Mutation testing seul (~8-15 min)"
    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
}

# === MAIN ===
main() {
    reset_tracking
    show_welcome
}

main
