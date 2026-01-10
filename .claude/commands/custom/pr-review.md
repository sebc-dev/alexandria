---
description: 'Review PR comments with AI analysis, GitHub replies, and workflow tracking. Replaces /review-coderabbit-pr with support for all reviewers.'
allowed-tools: Read, Bash(scripts/*), Bash(sqlite3:*), Task, Skill
argument-hint: <PR_NUMBER> [--filter coderabbit|all] [--auto-reply] [--dry-run]
---

# PR Review

Workflow principal de review de PR avec analyse IA, reponses GitHub automatiques, et suivi des workflows CI.

## Usage

```
/pr-review <PR_NUMBER> [options]
```

**Arguments:**
- `PR_NUMBER` - Numero de la PR ou URL GitHub
- `--filter coderabbit|all` - Filtrer les commentaires (defaut: all)
- `--auto-reply` - Poster automatiquement les reponses apres analyse
- `--dry-run` - Mode simulation (n'effectue aucune action)

## Workflow

### Phase 1: Initialisation

1. **Extraire le numero de PR** depuis l'argument (numero ou URL)
2. **Initialiser la DB** si necessaire:
   ```bash
   .claude/scripts/pr-review/db-init.sh
   ```
3. **Verifier l'authentification GitHub**:
   ```bash
   gh auth status
   ```

### Phase 2: Recuperation des donnees

Executer en parallele:

```bash
# Metadata PR
.claude/scripts/pr-review/fetch-pr.sh $PR_NUMBER

# Commentaires
.claude/scripts/pr-review/fetch-comments.sh $PR_NUMBER

# Workflows CI
.claude/scripts/pr-review/fetch-workflows.sh $PR_NUMBER
```

Afficher un resume initial.

### Phase 3: Analyse

1. **Detection des doublons** (pr-duplicate-detector)
2. **Analyse des commentaires** (pr-comment-analyzer) - en parallele
3. **Analyse des DEFER** (pr-defer-analyzer)

Afficher le dashboard des resultats.

### Phase 4: Reponses (si --auto-reply)

1. **Generer les reponses** (pr-reply-generator)
2. **Poster sur GitHub** (post-reply.sh)

Si `--dry-run`, afficher les reponses sans les poster.

### Phase 5: Mode Interactif

Si pas `--auto-reply`, proposer un menu:

```
## Actions Disponibles

1. `analyser` - Relancer l'analyse des commentaires pending
2. `repondre` - Poster toutes les reponses pending
3. `repondre --dry-run` - Apercu des reponses
4. `sync` - Synchroniser avec GitHub (incremental)
5. `sync --full` - Re-synchroniser completement (tous les commentaires)
6. `workflows` - Voir le status des workflows
7. `status` - Afficher le dashboard
8. `terminer` - Quitter

Votre choix:
```

## Dashboard Principal

```
## PR #11 Review

**feat(ci): add GitHub Actions CI/CD pipeline**
Branch: `feature/phase-2c-cicd-pipeline` | State: open

---

### Workflows CI
| Check | Status |
|-------|--------|
| CI | success |
| Code Quality | success |

---

### Commentaires (23 total)

| Source | Count | Analyzed |
|--------|-------|----------|
| CodeRabbit | 18 | 18 |
| Human | 5 | 5 |

### Decisions

| Decision | Count | Replied |
|----------|-------|---------|
| ACCEPT | 16 | 16 |
| REJECT | 4 | 4 |
| DEFER | 3 | 3 |
| DUPLICATE | 6 | - |

### DEFER Items
- 2 already planned
- 1 issue created (#12)

---

### Pending Actions
- [ ] Aucune action pending

**Status**: Review complete
```

## Commandes Interactives

### `analyser`
Relance `/custom:pr-review-analyze $PR_NUMBER`

### `repondre [--dry-run]`
Execute `/custom:pr-review-reply $PR_NUMBER [--dry-run]`

### `sync [--full]`
Execute `/custom:pr-review-sync $PR_NUMBER [--full]`

Synchronise les donnees avec GitHub:
- Met a jour l'etat de la PR (open/closed/merged)
- Recupere les nouveaux commentaires
- Detecte les commentaires resolus (CodeRabbit)
- Met a jour le status des workflows CI

Avec `--full`: force un re-fetch complet de tous les commentaires.

### `workflows`
Execute `/custom:pr-review-workflows $PR_NUMBER --details`

### `status`
Execute `/custom:pr-review-status $PR_NUMBER`

### `creer-issues`
Pour les DEFER avec `needs_issue`:
```bash
# Proposer les commandes
bd create --title "..." --priority medium
gh issue create --title "..." --body "..."
```

### `terminer`
Marque la review comme complete et quitte.

## Gestion des Erreurs

### GitHub non authentifie
```
Erreur: GitHub CLI non authentifie.
Executez: gh auth login
```

### PR non trouvee
```
Erreur: PR #999 non trouvee dans le repository.
Verifiez le numero de PR.
```

### Base de donnees manquante
```
Initialisation de la base de donnees...
[Auto-execute db-init.sh]
```

### Timeout sur API
```
Warning: Rate limit GitHub atteint.
Attente de 60 secondes...
[Retry automatique]
```

## Notes

- Cette commande remplace `/custom:review-coderabbit-pr`
- Les donnees sont stockees dans `~/.local/share/alexandria/pr-reviews.db`
- Compatible avec tous les reviewers (CodeRabbit, humains, autres bots)
- Les reponses utilisent des templates standardises

## Migration depuis l'ancien workflow

Les anciens fichiers YAML dans `.claude/pr-reviews/` peuvent etre importes:
```bash
# A implementer: import-tracking.sh
.claude/scripts/pr-review/import-tracking.sh .claude/pr-reviews/pr-11-tracking.yaml
```
