# PR Review SQLite System

Système de gestion des reviews de PR avec stockage SQLite. Permet de récupérer, analyser et répondre aux commentaires CodeRabbit (et autres reviewers) via des scripts bash.

## Architecture

```
~/.local/share/alexandria/
└── pr-reviews.db          # Base SQLite (WAL mode)

.claude/scripts/pr-review/
├── db-schema.sql          # Schéma de la base
├── db-init.sh             # Initialisation/upgrade
├── fetch-pr.sh            # Récupère infos PR
├── fetch-comments.sh      # Récupère commentaires
├── fetch-workflows.sh     # Récupère workflow runs
├── sync-status.sh         # Synchronise avec GitHub
├── post-reply.sh          # Poste réponses GitHub
├── query-db.sh            # Requêtes SQL
├── report.sh              # Génère rapports
└── import-yaml.sh         # Import depuis YAML
```

## Quickstart

```bash
# 1. Initialiser la base
./db-init.sh

# 2. Récupérer une PR
./fetch-pr.sh 11

# 3. Récupérer les commentaires
./fetch-comments.sh 11

# 4. Voir le rapport
./report.sh 11
```

## Scripts

### db-init.sh

Initialise ou met à jour la base SQLite.

```bash
./db-init.sh              # Créer/vérifier la base
./db-init.sh --force      # Recréer la base (DESTRUCTIF)
```

**Output:** JSON avec status et version du schéma.

### fetch-pr.sh

Récupère les informations d'une PR depuis GitHub.

```bash
./fetch-pr.sh 11                    # PR #11 du repo courant
./fetch-pr.sh 11 --repo owner/repo  # Repo spécifique
```

**Output:** JSON avec les infos de la PR.

### fetch-comments.sh

Récupère tous les commentaires d'une PR (inline, review body, issue comments).

```bash
./fetch-comments.sh 11                        # Tous les commentaires
./fetch-comments.sh 11 --filter coderabbitai  # Seulement CodeRabbit
./fetch-comments.sh 11 --since 2026-01-10     # Depuis une date
./fetch-comments.sh 11 -v                     # Mode verbose
```

**Options:**
- `--repo REPO` : Repository (owner/name)
- `--filter USER` : Filtrer par utilisateur
- `--since TIMESTAMP` : Seulement après cette date
- `-v, --verbose` : Afficher le détail

**Output:** JSON avec compteurs (processed, new, updated, errors).

### sync-status.sh

Synchronise l'état avec GitHub : détecte les commentaires marqués "Addressed", les nouvelles réponses, etc.

```bash
./sync-status.sh 11
```

**Output:** JSON avec newly_addressed, new_replies, new_comments.

### report.sh

Génère des rapports depuis la base SQLite.

```bash
./report.sh 11                      # Résumé complet (markdown)
./report.sh 11 -t pending           # Commentaires non analysés
./report.sh 11 -t analysis -f table # Analyses en tableau
./report.sh 11 -t comments -f json  # Commentaires en JSON
./report.sh 11 -t addressed         # Commentaires résolus
./report.sh 11 -t replies           # Réponses postées
./report.sh 11 -t timeline          # Log des événements
```

**Types de rapport:**
| Type | Description |
|------|-------------|
| `summary` | Vue d'ensemble avec statistiques (défaut) |
| `comments` | Liste des commentaires CodeRabbit |
| `pending` | Commentaires sans analyse |
| `addressed` | Commentaires marqués résolus par CodeRabbit |
| `analysis` | Décisions d'analyse (ACCEPT/REJECT/DEFER) |
| `replies` | Réponses postées sur GitHub |
| `timeline` | Journal des événements |

**Formats:** `markdown` (défaut), `json`, `table`, `csv`

### post-reply.sh

Poste une réponse sur un commentaire GitHub.

```bash
./post-reply.sh 11 2673846370 "Merci, corrigé!"
./post-reply.sh 11 2673846370 --template accept
./post-reply.sh 11 2673846370 --template reject --reason "Faux positif"
./post-reply.sh 11 2673846370 --dry-run  # Prévisualiser sans poster
```

**Templates disponibles:** `accept`, `reject`, `defer`, `discuss`, `duplicate`

### query-db.sh

Exécute une requête SQL arbitraire.

```bash
./query-db.sh "SELECT COUNT(*) FROM comments"
./query-db.sh "SELECT * FROM v_pr_summary" --table
./query-db.sh "SELECT * FROM analyses" --json
./query-db.sh "SELECT * FROM comments WHERE cr_severity='critical'" --csv
```

**Formats:** `json` (défaut), `table`, `csv`

### import-yaml.sh

Importe les analyses depuis un fichier YAML de tracking.

```bash
./import-yaml.sh .claude/pr-reviews/pr-11-tracking.yaml --dry-run
./import-yaml.sh .claude/pr-reviews/pr-11-tracking.yaml
```

**Prérequis:** Python 3 avec le module `pyyaml`.

## Schéma de la Base

### Tables principales

| Table | Description |
|-------|-------------|
| `prs` | Informations des PRs (numéro, titre, branche, état) |
| `comments` | Tous les commentaires (inline, review_body, issue_comment) |
| `analyses` | Décisions d'analyse (ACCEPT, REJECT, DEFER, DISCUSS) |
| `replies` | Réponses postées sur GitHub |
| `deferred_items` | Éléments reportés avec suivi backlog |
| `workflow_runs` | Runs des workflows GitHub Actions |
| `check_runs` | Checks individuels des workflows |
| `events` | Journal des événements pour audit |

### Champs CodeRabbit (table `comments`)

| Champ | Description |
|-------|-------------|
| `cr_severity` | critical, major, minor, trivial |
| `cr_category` | potential-issue, nitpick, suggestion |
| `cr_type` | security, bug, performance, style, best-practice, documentation |
| `cr_has_suggestion` | 1 si contient un "Committable suggestion" |
| `cr_addressed_sha` | SHA du commit si marqué "Addressed" |

### Vues utiles

| Vue | Description |
|-----|-------------|
| `v_pending_comments` | Commentaires sans analyse |
| `v_analyzed_comments` | Commentaires avec leur analyse |
| `v_pending_replies` | Réponses en attente de publication |
| `v_pr_summary` | Statistiques par PR |

## Workflow Typique

### 1. Nouvelle PR

```bash
# Initialiser (une seule fois)
./db-init.sh

# Récupérer la PR et ses commentaires
./fetch-pr.sh 42
./fetch-comments.sh 42

# Voir le rapport
./report.sh 42
```

### 2. Analyser les commentaires

```bash
# Voir les commentaires en attente
./report.sh 42 -t pending

# Après analyse manuelle, importer depuis YAML
./import-yaml.sh .claude/pr-reviews/pr-42-tracking.yaml
```

### 3. Répondre sur GitHub

```bash
# Voir les analyses
./report.sh 42 -t analysis

# Poster les réponses
./post-reply.sh 42 123456 --template accept
./post-reply.sh 42 789012 --template reject --reason "Faux positif car..."
```

### 4. Synchroniser

```bash
# Vérifier les changements sur GitHub
./sync-status.sh 42

# Re-récupérer les nouveaux commentaires
./fetch-comments.sh 42
```

## Requêtes SQL Utiles

```bash
# Commentaires critiques non résolus
./query-db.sh "
  SELECT c.id, c.file_path, c.line_number, SUBSTR(c.body, 1, 100)
  FROM comments c
  LEFT JOIN analyses a ON c.id = a.comment_id
  WHERE c.cr_severity = 'critical'
    AND c.cr_addressed_sha IS NULL
    AND (a.decision IS NULL OR a.decision = 'ACCEPT')
" --table

# Statistiques par sévérité
./query-db.sh "
  SELECT cr_severity, COUNT(*) as count,
         SUM(CASE WHEN cr_addressed_sha IS NOT NULL THEN 1 ELSE 0 END) as addressed
  FROM comments
  WHERE user_login LIKE '%coderabbit%'
  GROUP BY cr_severity
" --table

# Commentaires sans réponse
./query-db.sh "
  SELECT c.id, c.file_path, a.decision
  FROM comments c
  JOIN analyses a ON c.id = a.comment_id
  LEFT JOIN replies r ON c.id = r.comment_id AND r.status = 'posted'
  WHERE r.id IS NULL
" --table
```

## Dépendances

- `gh` - GitHub CLI (authentifié)
- `jq` - Processeur JSON
- `sqlite3` - Client SQLite
- `python3` + `pyyaml` - Pour import-yaml.sh

## Fichiers de Configuration

La base est stockée dans `~/.local/share/alexandria/pr-reviews.db` (mode WAL pour performance).

Pour changer l'emplacement, modifier `DB_DIR` dans les scripts.

## Troubleshooting

### "Database not found"

```bash
./db-init.sh
```

### "PR not found in database"

```bash
./fetch-pr.sh <PR_NUMBER>
```

### Compteurs à 0 après fetch

Vérifier que le repo est correct :
```bash
./fetch-comments.sh 11 --repo owner/repo
```

### Import YAML échoue

Vérifier que PyYAML est installé :
```bash
pip install pyyaml
```
