---
description: 'Show PR review status dashboard with summary statistics'
allowed-tools: Bash(sqlite3:*), Read
argument-hint: <PR_NUMBER>
---

# PR Review Status

Affiche un dashboard resume de l'etat de la review d'une PR.

## Usage

```
/pr-review-status <PR_NUMBER>
```

## Queries SQLite

### Informations PR

```bash
sqlite3 -json ~/.local/share/alexandria/pr-reviews.db "
SELECT pr_number, title, branch, state, review_status, last_synced_at
FROM prs WHERE pr_number = $PR_NUMBER;
"
```

### Resume des commentaires

```bash
sqlite3 -json ~/.local/share/alexandria/pr-reviews.db "
SELECT
    COUNT(*) as total,
    SUM(CASE WHEN is_bot = 1 THEN 1 ELSE 0 END) as bot_comments,
    SUM(CASE WHEN user_login LIKE '%coderabbit%' THEN 1 ELSE 0 END) as coderabbit,
    SUM(CASE WHEN is_bot = 0 THEN 1 ELSE 0 END) as human
FROM comments c
JOIN prs p ON c.pr_id = p.id
WHERE p.pr_number = $PR_NUMBER;
"
```

### Resume des analyses

```bash
sqlite3 -json ~/.local/share/alexandria/pr-reviews.db "
SELECT
    decision,
    COUNT(*) as count,
    AVG(confidence) as avg_confidence
FROM analyses a
JOIN comments c ON a.comment_id = c.id
JOIN prs p ON c.pr_id = p.id
WHERE p.pr_number = $PR_NUMBER
GROUP BY decision
ORDER BY count DESC;
"
```

### Resume des reponses

```bash
sqlite3 -json ~/.local/share/alexandria/pr-reviews.db "
SELECT
    status,
    COUNT(*) as count
FROM replies r
JOIN comments c ON r.comment_id = c.id
JOIN prs p ON c.pr_id = p.id
WHERE p.pr_number = $PR_NUMBER
GROUP BY status;
"
```

### Commentaires addressed par CodeRabbit

```bash
sqlite3 -json ~/.local/share/alexandria/pr-reviews.db "
SELECT COUNT(*) as addressed
FROM comments c
JOIN prs p ON c.pr_id = p.id
WHERE p.pr_number = $PR_NUMBER
  AND c.cr_addressed_sha IS NOT NULL;
"
```

### Items DEFER

```bash
sqlite3 -json ~/.local/share/alexandria/pr-reviews.db "
SELECT d.backlog_status, COUNT(*) as count
FROM deferred_items d
JOIN comments c ON d.comment_id = c.id
JOIN prs p ON c.pr_id = p.id
WHERE p.pr_number = $PR_NUMBER
GROUP BY d.backlog_status;
"
```

## Output Dashboard

```
## PR #11 Review Status

**Title**: feat(ci): add GitHub Actions CI/CD pipeline
**Branch**: feature/phase-2c-cicd-pipeline
**State**: open
**Last Sync**: 2026-01-10T10:30:00Z

---

### Comments Overview

| Source | Count |
|--------|-------|
| Total | 23 |
| CodeRabbit | 18 |
| Human | 5 |

---

### Analysis Summary

| Decision | Count | Avg Confidence |
|----------|-------|----------------|
| ACCEPT | 16 | 0.87 |
| REJECT | 4 | 0.82 |
| DEFER | 3 | 0.75 |
| DUPLICATE | 6 | 0.95 |

**Pending Analysis**: 0

---

### Reply Status

| Status | Count |
|--------|-------|
| Posted | 18 |
| Pending | 0 |
| Failed | 0 |

---

### CodeRabbit Resolution

- Addressed by CodeRabbit: 15
- Pending verification: 3

---

### DEFER Items

| Status | Count |
|--------|-------|
| Already planned | 2 |
| Issue created | 1 |
| Needs issue | 0 |

---

### Pending Actions

- [ ] No pending analyses
- [ ] No pending replies
- [x] All DEFER items tracked

**Review Status**: Completed
```

## Notes

- Le dashboard est une vue read-only
- Pour les actions, utiliser les autres commandes:
  - `/custom:pr-review-fetch` pour rafraichir
  - `/custom:pr-review-analyze` pour analyser
  - `/custom:pr-review-reply` pour poster
