---
description: 'Fetch or sync PR data from GitHub into local SQLite database'
allowed-tools: Bash(scripts/*), Bash(sqlite3:*), Read
argument-hint: <PR_NUMBER> [--full|--incremental]
---

# PR Review Fetch

Recupere les donnees d'une PR depuis GitHub et les stocke dans la base SQLite locale.

## Usage

```
/pr-review-fetch <PR_NUMBER> [--full|--incremental]
```

**Arguments:**
- `PR_NUMBER` - Numero de la PR a recuperer
- `--full` - Forcer une recuperation complete (ignore le cache)
- `--incremental` - Recuperer uniquement les nouveaux commentaires depuis le dernier sync

## Workflow

### 1. Initialiser la base de donnees

```bash
.claude/scripts/pr-review/db-init.sh
```

Verifie que la DB existe et est a jour.

### 2. Recuperer les metadonnees PR

```bash
.claude/scripts/pr-review/fetch-pr.sh $PR_NUMBER
```

Retourne JSON avec: pr_id, title, branch, state, etc.

### 3. Recuperer les commentaires

```bash
.claude/scripts/pr-review/fetch-comments.sh $PR_NUMBER [--since TIMESTAMP]
```

Recupere TOUS les commentaires (inline, review body, issue comments).
Parse automatiquement les champs CodeRabbit (severity, category, type).

### 4. Recuperer les workflows

```bash
.claude/scripts/pr-review/fetch-workflows.sh $PR_NUMBER
```

Recupere les workflow runs et check runs GitHub Actions.

## Output Attendu

Afficher un resume:

```
## PR #11 Fetched

- **Title**: feat(ci): add GitHub Actions CI/CD pipeline
- **Branch**: feature/phase-2c-cicd-pipeline
- **State**: open

### Comments
- Total: 23
- CodeRabbit: 18
- Human reviewers: 5

### Workflows
- CI: success
- Code Quality: success
- Security Scan: in_progress

**Last synced**: 2026-01-10T10:30:00Z
```

## Gestion des Erreurs

- Si `gh` n'est pas authentifie: afficher message d'aide
- Si la PR n'existe pas: erreur claire
- Si rate limit GitHub: attendre et reessayer

## Notes

Cette commande est generalement appelee par l'orchestrateur `/custom:pr-review`.
Elle peut aussi etre utilisee standalone pour rafraichir les donnees.
