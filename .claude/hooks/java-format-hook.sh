#!/bin/bash
# .claude/hooks/java-format-hook.sh
# Hook PostToolUse pour formatage automatique des fichiers Java avec Spotless
# Execution cible: < 3 secondes

set -euo pipefail

PROJECT_ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
TRACKING_FILE="${PROJECT_ROOT}/.claude/hooks/modified-java-files.json"
LOCK_FILE="/tmp/alexandria-java-hook.lock"
MAX_ITERATIONS=10

# Couleurs (silencieuses si pas de TTY)
if [ -t 2 ]; then
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    RED='\033[0;31m'
    NC='\033[0m'
else
    GREEN='' YELLOW='' RED='' NC=''
fi

# Fonction de log (vers stderr pour ne pas polluer stdout)
log() { echo -e "${GREEN}[java-hook]${NC} $1" >&2; }
warn() { echo -e "${YELLOW}[java-hook]${NC} $1" >&2; }
error() { echo -e "${RED}[java-hook]${NC} $1" >&2; }

# Verifier si c'est un fichier Java
is_java_file() {
    local file="$1"
    [[ "$file" == *.java ]]
}

# Verifier si le fichier est dans src/ (eviter target/)
is_source_file() {
    local file="$1"
    [[ "$file" == */src/* ]]
}

# Protection contre les boucles infinies via lock file
acquire_lock() {
    local waited=0
    while [ -f "$LOCK_FILE" ]; do
        if [ $waited -ge $MAX_ITERATIONS ]; then
            warn "Lock timeout - skipping to avoid deadlock"
            exit 0
        fi
        sleep 0.5
        waited=$((waited + 1))
    done
    echo $$ > "$LOCK_FILE"
    trap 'rm -f "$LOCK_FILE"' EXIT
}

# Tracker les fichiers modifies pour le rappel Stop
track_modified_file() {
    local file="$1"
    local timestamp
    timestamp=$(date -Iseconds)

    # Initialiser le fichier JSON si necessaire
    if [ ! -f "$TRACKING_FILE" ]; then
        mkdir -p "$(dirname "$TRACKING_FILE")"
        echo '{"session_start":"'"$timestamp"'","modified_files":[]}' > "$TRACKING_FILE"
    fi

    # Verifier si jq est disponible
    if ! command -v jq &> /dev/null; then
        warn "jq not found - skipping tracking"
        return 0
    fi

    # Ajouter le fichier s'il n'est pas deja present
    local existing
    existing=$(jq -r --arg f "$file" '.modified_files | map(select(.file == $f)) | length' "$TRACKING_FILE" 2>/dev/null || echo "0")

    if [ "$existing" = "0" ]; then
        jq --arg f "$file" --arg t "$timestamp" \
           '.modified_files += [{"file": $f, "modified_at": $t}]' \
           "$TRACKING_FILE" > "${TRACKING_FILE}.tmp" && \
        mv "${TRACKING_FILE}.tmp" "$TRACKING_FILE"
    fi
}

# Formater avec Spotless (fichier unique pour rapidite)
run_spotless() {
    local file="$1"
    local relative_path="${file#"$PROJECT_ROOT"/}"

    cd "$PROJECT_ROOT"

    # Spotless sur fichier unique (beaucoup plus rapide que tout le projet)
    if mvn spotless:apply -DspotlessFiles="$relative_path" -q 2>/dev/null; then
        log "Formatted: $relative_path"
        return 0
    else
        warn "Spotless failed on $relative_path (non-blocking)"
        return 0  # Ne pas bloquer en cas d'erreur
    fi
}

# === MAIN ===
main() {
    # Lire le JSON depuis stdin (envoye par Claude Code)
    local input
    input=$(cat)

    # Verifier si jq est disponible
    if ! command -v jq &> /dev/null; then
        exit 0
    fi

    # Extraire le chemin du fichier
    local file_path
    file_path=$(echo "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null || echo "")

    # Verifications rapides
    [ -z "$file_path" ] && exit 0
    ! is_java_file "$file_path" && exit 0
    ! is_source_file "$file_path" && exit 0
    [ ! -f "$file_path" ] && exit 0

    # Eviter les boucles
    acquire_lock

    log "Processing: ${file_path#"$PROJECT_ROOT"/}"

    # Tracker le fichier pour le rappel
    track_modified_file "$file_path"

    # Formater avec Spotless
    run_spotless "$file_path"

    log "Done"
}

main
