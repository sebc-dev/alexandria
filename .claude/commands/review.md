---
allowed-tools: Bash(coderabbit:*), Bash(git:*), Read, Write, Grep, Glob
argument-hint: [staged | commits:N | branch:NAME]
description: Lance une code review CodeRabbit. Default: branche actuelle vs master.
---

# Code Review CodeRabbit

## Paramètre reçu : $ARGUMENTS

## Contexte Git actuel

- Branche actuelle : !`git branch --show-current`
- Staged files : !`git diff --cached --name-only`

## Interpréter le scope

### Si vide (défaut) → Branche actuelle vs master
```bash
coderabbit --base origin/master --prompt-only
```

### Si "staged"
```bash
coderabbit --type uncommitted --prompt-only
```

### Si "commits:N" (ex: commits:3)
```bash
N=$(echo "$ARGUMENTS" | cut -d: -f2)
coderabbit --base-commit HEAD~$N --prompt-only
```

### Si "branch:NAME" (ex: branch:feature/auth)
```bash
BRANCH=$(echo "$ARGUMENTS" | cut -d: -f2)
git checkout $BRANCH
coderabbit --base origin/master --prompt-only
```

## Workflow d'exécution

1. **Parser le scope** depuis $ARGUMENTS (défaut = branche vs master)
2. **Exécuter** CodeRabbit et capturer la sortie
3. **Parser les findings** en catégories (Critical/Warning/Suggestion)
4. **Écrire le rapport** dans `.claude/reports/coderabbit-latest.md`
5. **Invoquer** l'agent `review-validator` pour validation interactive

## Exemples d'utilisation
```
/review                     → branche actuelle vs master (défaut)
/review staged              → staged changes uniquement
/review commits:5           → 5 derniers commits
/review branch:feature/auth → branche feature/auth vs master
```