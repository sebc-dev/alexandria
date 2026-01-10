---
description: 'Synchronize PR data with GitHub - fetch new comments, update PR state, detect resolved issues'
allowed-tools: Bash(scripts/*), Bash(sqlite3:*), Read
argument-hint: <PR_NUMBER> [--full]
---

# PR Review Sync

Synchronise les donnees d'une PR avec GitHub. Recupere les nouveaux commentaires, met a jour l'etat de la PR, et detecte les commentaires resolus.

## Usage

```
/pr-review-sync <PR_NUMBER> [--full]
```

**Arguments:**
- `PR_NUMBER` - Numero de la PR a synchroniser
- `--full` - Forcer une synchronisation complete (re-fetch tous les commentaires)

## Workflow

### 1. Verifier la PR existe en base

```bash
sqlite3 ~/.local/share/alexandria/pr-reviews.db \
  "SELECT id, state, last_synced_at FROM prs WHERE pr_number = $PR_NUMBER;"
```

Si la PR n'existe pas, initialiser avec `fetch-pr.sh`.

### 2. Mettre a jour l'etat de la PR

```bash
.claude/scripts/pr-review/fetch-pr.sh $PR_NUMBER
```

Verifie si la PR est passee de `open` a `closed` ou `merged`.

### 3. Synchroniser les commentaires

Mode **incremental** (par defaut):
```bash
.claude/scripts/pr-review/sync-status.sh $PR_NUMBER
```

Mode **full** (avec `--full`):
```bash
.claude/scripts/pr-review/fetch-comments.sh $PR_NUMBER
```

### 4. Mettre a jour les workflows CI

```bash
.claude/scripts/pr-review/fetch-workflows.sh $PR_NUMBER
```

### 5. Afficher le resume

## Output Attendu

```
## PR #11 Sync Complete

### Etat PR
- State: open -> merged
- Last synced: 2026-01-10T10:30:00Z -> 2026-01-10T14:45:00Z

### Changements Detectes
- **Nouveaux commentaires**: 3
  - @reviewer1 on src/main/java/Service.java
  - @coderabbitai[bot] on src/config/App.java (2 comments)
- **Commentaires resolus**: 2
  - #123456 addressed in commit abc1234
  - #789012 addressed in commit def5678
- **Nouvelles reponses**: 1
  - @reviewer1 replied to our comment on line 42

### Workflows CI
| Check | Status |
|-------|--------|
| CI | success |
| Code Quality | success |

### Actions Requises
- [ ] Analyser 3 nouveaux commentaires (`/custom:pr-review-analyze 11`)
- [ ] 0 reponses pending
```

## Mode Full

Avec `--full`, force une recuperation complete:

1. Re-fetch tous les commentaires (ignore `--since`)
2. Re-parse les champs CodeRabbit (severity, category, type)
3. Met a jour les commentaires modifies

Utile apres:
- Un rebase de la PR
- Des modifications manuelles en base
- Une corruption suspectee des donnees

## Detection des Changements

### Nouveaux Commentaires
- Comparaison avec `last_synced_at` dans la table `prs`
- Insertion des nouveaux commentaires via `fetch-comments.sh`

### Commentaires Resolus (CodeRabbit)
- Detection du pattern "Addressed in commit [SHA]"
- Mise a jour de `cr_addressed_sha` et `cr_addressed_at`

### Nouvelles Reponses
- Detection des reponses a nos commentaires
- Insertion comme nouveaux commentaires avec `in_reply_to_id`

### Changement d'Etat PR
- `open` -> `closed`: PR fermee sans merge
- `open` -> `merged`: PR mergee avec succes
- Update de `review_status` si applicable

## Gestion des Erreurs

### Rate Limit GitHub
```
Warning: Rate limit GitHub atteint.
Attente de 60 secondes...
[Retry automatique]
```

### PR non trouvee en base
```
PR #11 non trouvee en base. Initialisation...
[Auto-execute fetch-pr.sh puis fetch-comments.sh]
```

### Conflit de donnees
```
Warning: Commentaire #123 modifie depuis le dernier sync.
[Update avec donnees GitHub]
```

## Notes

- Cette commande est generalement appelee depuis `/custom:pr-review` via le menu interactif
- Peut aussi etre utilisee standalone pour refresh rapide
- N'analyse pas automatiquement les nouveaux commentaires (utiliser `/custom:pr-review-analyze`)
- Mode incremental par defaut pour des syncs rapides
