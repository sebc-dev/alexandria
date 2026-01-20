---
name: coderabbit-review
description: Code review AI avec CodeRabbit CLI. Utiliser pour analyser le code avant commit, détecter les vulnérabilités, ou quand l'utilisateur dit "review", "code review", "analyse mon code", "coderabbit".
allowed-tools: Read, Grep, Glob, Bash(coderabbit:*), Bash(git:*)
---

# CodeRabbit Code Review Skill

## Prérequis
CodeRabbit CLI doit être installé et authentifié :
```bash
curl -fsSL https://cli.coderabbit.ai/install.sh | sh
coderabbit auth login
```

## Modes de review disponibles

### Staged changes (pré-commit)
```bash
coderabbit --type uncommitted --prompt-only
```

### Commits spécifiques
```bash
# 3 derniers commits
coderabbit --base-commit HEAD~3 --prompt-only

# Range précis
coderabbit --base-commit abc123..def456 --prompt-only
```

### Branche complète vs main
```bash
coderabbit --base origin/main --prompt-only
```

## Interprétation des résultats

Catégoriser les findings par sévérité :
- 🔴 **Critical** : Sécurité, bugs bloquants → action immédiate
- 🟡 **Warning** : Qualité, maintenabilité → à traiter
- 🔵 **Suggestion** : Améliorations → à considérer
- ✅ **Info** : Documentation, style → optionnel

## Gestion d'erreurs

| Erreur | Solution |
|--------|----------|
| "command not found" | Installation : `curl -fsSL https://cli.coderabbit.ai/install.sh \| sh` |
| "rate limit exceeded" | Attendre 1h ou upgrade vers Pro |
| "no changes" | Vérifier git status, stager des fichiers |

## Workflow recommandé
1. Exécuter review avec scope approprié
2. Parser la sortie et catégoriser
3. Générer rapport structuré dans `.claude/reports/`
4. Présenter les findings à l'utilisateur