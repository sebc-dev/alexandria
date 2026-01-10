---
description: 'Analyze pending PR comments with AI sub-agents'
allowed-tools: Task, Bash(sqlite3:*), Read
argument-hint: <PR_NUMBER> [--comment-id ID] [--parallel N] [--filter coderabbit|all]
---

# PR Review Analyze

Lance l'analyse des commentaires non analyses par des sub-agents specialises.

## Usage

```
/pr-review-analyze <PR_NUMBER> [options]
```

**Arguments:**
- `PR_NUMBER` - Numero de la PR a analyser
- `--comment-id ID` - Analyser un seul commentaire specifique
- `--parallel N` - Nombre d'agents en parallele (defaut: 5)
- `--filter coderabbit|all` - Filtrer par source (defaut: all)

## Workflow

### 1. Recuperer les commentaires pending

```bash
sqlite3 -json ~/.local/share/alexandria/pr-reviews.db "
SELECT c.id, c.file_path, c.line_number, c.body, c.user_login, c.is_bot,
       c.cr_severity, c.cr_category, c.cr_type, c.cr_has_suggestion,
       p.pr_number
FROM comments c
JOIN prs p ON c.pr_id = p.id
LEFT JOIN analyses a ON c.id = a.comment_id
WHERE p.pr_number = $PR_NUMBER
  AND a.id IS NULL
ORDER BY c.created_at;
"
```

### 2. Detecter les doublons d'abord

Lancer l'agent de detection de doublons:

```
Task(pr-duplicate-detector, {
  "pr_id": N,
  "comments": [...]
})
```

Cela evite d'analyser des doublons inutilement.

### 3. Analyser chaque commentaire

Pour chaque commentaire non-doublon, lancer un agent:

```
Task(pr-comment-analyzer, {
  "comment_id": "...",
  "file_path": "...",
  "line_number": N,
  "body": "...",
  "user_login": "...",
  "is_coderabbit": true/false,
  "cr_severity": "...",
  "cr_category": "...",
  "cr_type": "..."
}, model: "sonnet")
```

**Parallelisation**: Lancer jusqu'a N agents en parallele (defaut 5).

### 4. Collecter les resultats

Chaque agent retourne:
```
OK|COMMENT_ID|DECISION|CRITICALITY|TYPE|CONFIDENCE
```

Compiler les statistiques.

### 5. Analyser les DEFER

Pour chaque commentaire marque DEFER, lancer:

```
Task(pr-defer-analyzer, {
  "comment_id": "...",
  "file_path": "...",
  "summary": "...",
  "type": "...",
  "criticality": "..."
}, model: "haiku")
```

## Output Attendu

```
## Analysis Complete - PR #11

### Results
| Decision | Count | Examples |
|----------|-------|----------|
| ACCEPT   | 16    | Security fix, best practice |
| REJECT   | 4     | False positives |
| DISCUSS  | 2     | Trade-offs to evaluate |
| DEFER    | 3     | Out of scope |
| DUPLICATE| 6     | Repeated comments |
| SKIP     | 1     | Obsolete |

### DEFER Items Status
- 2 already planned in phase docs
- 1 needs new issue

### Next Steps
- Run `/pr-review-reply $PR_NUMBER` to post responses
- Review DISCUSS items manually
```

## Gestion des Erreurs

- Si un agent echoue: logger l'erreur, continuer avec les autres
- Si trop d'echecs (>50%): arreter et signaler le probleme
- Timeout par agent: 60 secondes
