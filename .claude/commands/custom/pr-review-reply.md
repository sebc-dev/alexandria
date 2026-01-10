---
description: 'Generate and post replies to analyzed PR comments'
allowed-tools: Task, Bash(scripts/*), Bash(sqlite3:*), Read
argument-hint: <PR_NUMBER> [--all|--comment-id ID] [--dry-run]
---

# PR Review Reply

Genere et poste les reponses GitHub pour les commentaires analyses.

## Usage

```
/pr-review-reply <PR_NUMBER> [options]
```

**Arguments:**
- `PR_NUMBER` - Numero de la PR
- `--all` - Poster toutes les reponses pending
- `--comment-id ID` - Poster pour un seul commentaire
- `--dry-run` - Afficher les reponses sans les poster

## Workflow

### 1. Recuperer les analyses sans reponse

```bash
sqlite3 -json ~/.local/share/alexandria/pr-reviews.db "
SELECT a.comment_id, a.decision, a.criticality, a.type, a.summary, a.rationale,
       a.code_suggestion, a.duplicate_of, d.github_issue_number, d.found_in_reference,
       c.user_login, c.file_path, c.line_number
FROM analyses a
JOIN comments c ON a.comment_id = c.id
JOIN prs p ON c.pr_id = p.id
LEFT JOIN deferred_items d ON a.comment_id = d.comment_id
LEFT JOIN replies r ON a.comment_id = r.comment_id AND r.status = 'posted'
WHERE p.pr_number = $PR_NUMBER
  AND r.id IS NULL
  AND a.decision != 'SKIP';
"
```

### 2. Generer les reponses

Pour chaque analyse, utiliser l'agent:

```
Task(pr-reply-generator, {
  "comment_id": "...",
  "user_login": "...",
  "decision": "ACCEPT",
  "criticality": "IMPORTANT",
  "type": "security",
  "summary": "...",
  "rationale": "...",
  "code_suggestion": "...",
  "duplicate_of": null,
  "github_issue_number": null
}, model: "haiku")
```

L'agent retourne:
```json
{
  "comment_id": "...",
  "reply_body": "Merci @coderabbitai...",
  "template_type": "accept_applied"
}
```

### 3. Poster les reponses

Pour chaque reponse generee:

```bash
.claude/scripts/pr-review/post-reply.sh "$COMMENT_ID" "$REPLY_BODY" [--dry-run]
```

En mode `--dry-run`, affiche ce qui serait poste sans le faire.

### 4. Afficher le resultat

```
## Replies Posted - PR #11

### Summary
- Posted: 18
- Skipped: 5 (already replied or SKIP decision)
- Failed: 0

### Details

| Comment | Decision | Status |
|---------|----------|--------|
| #2673846370 | ACCEPT | Posted |
| #2673846371 | REJECT | Posted |
| #2673846372 | DEFER | Posted (Issue #12) |
| ... | ... | ... |
```

## Mode Dry-Run

En `--dry-run`, afficher un apercu:

```
## Dry Run - PR #11

### Would Post:

**Comment #2673846370** (coderabbitai[bot])
> Risque d'injection shell...

**Reply:**
> Merci @coderabbitai
> **Correction appliquee** - Les inputs passent par des variables d'environnement.

---

**Comment #2673846371** (coderabbitai[bot])
> Utiliser Optional.orElseThrow()...

**Reply:**
> Merci pour la suggestion @coderabbitai.
> **Decision: Ne pas appliquer**
> **Raison**: La validation existe en amont dans le controller.

---

Total: 18 replies would be posted.
Run without --dry-run to post.
```

## Gestion des Erreurs

- Si post echoue: logger l'erreur, continuer avec les autres
- Rate limit GitHub: backoff exponentiel
- Reponse deja postee: skip silencieusement
