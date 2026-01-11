---
description: 'Review PR comments with AI analysis, GitHub replies, and workflow tracking. Replaces /review-coderabbit-pr with support for all reviewers.'
allowed-tools: Read, Write, Edit, Bash(scripts/*), Bash(sqlite3:*), Bash(git:*), Bash(make:*), Task, Skill
argument-hint: <PR_NUMBER> [--filter coderabbit|all] [--auto] [--dry-run]
---

# PR Review

Workflow principal de review de PR avec analyse IA, corrections, reponses GitHub, et push.

## Usage

```
/pr-review <PR_NUMBER> [options]
```

**Arguments:**
- `PR_NUMBER` - Numero de la PR ou URL GitHub
- `--filter coderabbit|all` - Filtrer les commentaires (defaut: all)
- `--auto` - Mode automatique: analyse -> corrections -> reponses -> push
- `--dry-run` - Mode simulation (n'effectue aucune action)

## Workflow Principal

```
Analyse -> Corrections -> Reponses -> Push
```

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

Afficher le dashboard des resultats avec les decisions:
- **ACCEPT**: Corrections a appliquer
- **REJECT**: Faux positifs a ignorer (thread sera resolu)
- **DEFER**: Reporter a plus tard (issue a creer si besoin)
- **DUPLICATE**: Doublon d'un autre commentaire (thread sera resolu)
- **DISCUSS**: Necessite discussion avec l'equipe

### Phase 4: Corrections (ACCEPT)

Pour chaque commentaire ACCEPT:

1. **Lire le fichier concerne**
2. **Appliquer la correction** selon `code_suggestion` ou `rationale`
3. **Valider** que la correction compile:
   ```bash
   make dev  # Compilation rapide
   ```
4. **Committer** la correction:
   ```bash
   git add -A
   git commit -m "fix(review): $FILE_PATH - $SUMMARY

   Addresses PR #$PR_NUMBER comment #$COMMENT_ID

   Co-Authored-By: Claude <noreply@anthropic.com>"
   ```
5. **Tracker** la correction dans la DB:
   ```sql
   UPDATE analyses SET applied_at = datetime('now') WHERE comment_id = '...';
   ```

**Important**: Committer apres chaque correction pour un historique granulaire. Le push est fait a la fin.

### Phase 5: Validation

Avant de poster les reponses:

1. **Executer les tests**:
   ```bash
   make test
   ```
2. **Verifier la qualite** (optionnel):
   ```bash
   make analyse
   ```

Si les tests echouent, revenir en arriere et revoir les corrections.

### Phase 6: Reponses GitHub

Une fois les corrections validees:

1. **Generer les reponses** (pr-reply-generator) pour tous les commentaires analyses
2. **Poster sur GitHub** (post-reply.sh)
3. **Resoudre les threads** pour REJECT et DUPLICATE:
   ```bash
   .claude/scripts/pr-review/resolve-thread.sh "$COMMENT_ID"
   ```

### Phase 7: Push

Apres avoir poste les reponses, pusher tous les commits de correction:

```bash
git push
```

Les commits individuels ont deja ete crees dans la Phase 4. Le push envoie tous les commits d'un coup.

### Phase 8: Mode Interactif

Si pas `--auto`, proposer un menu:

```
## Actions Disponibles

1. `analyser` - Relancer l'analyse des commentaires pending
2. `corriger` - Appliquer les corrections ACCEPT
3. `corriger --preview` - Apercu des corrections sans les appliquer
4. `valider` - Executer tests et qualite
5. `repondre` - Poster toutes les reponses
6. `repondre --dry-run` - Apercu des reponses
7. `push` - Pusher les commits
8. `auto` - Executer: corriger -> valider -> repondre -> push
9. `sync` - Synchroniser avec GitHub
10. `status` - Afficher le dashboard
11. `terminer` - Quitter

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

| Decision | Count | Applied | Replied | Resolved |
|----------|-------|---------|---------|----------|
| ACCEPT | 12 | 12 | 12 | - |
| REJECT | 4 | - | 4 | 4 |
| DEFER | 3 | - | 3 | - |
| DUPLICATE | 4 | - | - | 4 |

### DEFER Items
- 2 already planned
- 1 issue created (#12)

---

### Progression

[x] Analyse complete (23/23)
[x] Corrections appliquees (12/12 ACCEPT)
[x] Tests passes
[x] Reponses postees (19/19)
[x] Threads resolus (8/8 REJECT+DUPLICATE)
[ ] Push effectue

**Status**: Pret pour push
```

## Commandes Interactives

### `analyser`
Relance `/custom:pr-review-analyze $PR_NUMBER`

### `corriger [--preview]`
Applique les corrections pour tous les commentaires ACCEPT non encore appliques.

1. Recuperer les ACCEPT sans `applied_at`:
   ```sql
   SELECT a.comment_id, a.code_suggestion, a.rationale, c.file_path, c.line_number, c.body
   FROM analyses a
   JOIN comments c ON a.comment_id = c.id
   WHERE a.decision = 'ACCEPT' AND a.applied_at IS NULL;
   ```
2. Pour chaque correction:
   - Lire le fichier
   - Appliquer la modification (Edit tool)
   - Verifier la compilation (`make dev`)
   - Committer avec message specifique:
     ```
     fix(review): path/to/file.java - description courte

     Addresses PR #11 comment #123456
     ```
3. Mettre a jour `applied_at` dans la DB

Avec `--preview`: affiche les corrections sans les appliquer.

### `valider`
Execute les tests et l'analyse de qualite:

```bash
make test      # Tests unitaires
make analyse   # PMD + SpotBugs + Checkstyle (optionnel)
```

Affiche un resume:
```
## Validation

Tests: 142 passed, 0 failed
Quality: 0 violations

Pret pour repondre et push.
```

### `repondre [--dry-run]`
Execute `/custom:pr-review-reply $PR_NUMBER [--dry-run]`

Poste les reponses GitHub et resout les threads REJECT/DUPLICATE.

### `push`
Pushe tous les commits de correction vers le remote:

```bash
git push
```

Affiche un resume des commits pushes:
```
Pushed 5 commits to origin/feature-branch:
- fix(review): File1.java - Added null check
- fix(review): File2.java - Added validation
- fix(review): File3.java - Fixed exception handling
- fix(review): File4.java - Improved logging
- fix(review): File5.java - Updated documentation
```

### `auto`
Execute la sequence complete:

```
corriger -> valider -> repondre -> push
```

S'arrete si une etape echoue (tests, qualite, etc.).

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
