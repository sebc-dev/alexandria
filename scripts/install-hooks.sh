#!/usr/bin/env bash
# Install git hooks from scripts/ into the active git hooks directory.
# Supports both standard repos and git worktrees.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Resolve the git hooks directory (works for worktrees too)
HOOKS_DIR="$(git -C "$REPO_ROOT" rev-parse --git-path hooks)"

mkdir -p "$HOOKS_DIR"
cp "$SCRIPT_DIR/pre-commit" "$HOOKS_DIR/pre-commit"
chmod +x "$HOOKS_DIR/pre-commit"

echo "Installed pre-commit hook to $HOOKS_DIR/pre-commit"
