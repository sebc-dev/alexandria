---
description: 'Show GitHub Actions workflows and checks status for a PR'
allowed-tools: Bash(scripts/*), Bash(sqlite3:*), Read
argument-hint: <PR_NUMBER> [--details] [--refresh]
---

# PR Review Workflows

Affiche le status des workflows GitHub Actions et checks pour une PR.

## Usage

```
/pr-review-workflows <PR_NUMBER> [options]
```

**Arguments:**
- `PR_NUMBER` - Numero de la PR
- `--details` - Afficher les details des checks failed
- `--refresh` - Rafraichir les donnees depuis GitHub

## Workflow

### 1. Rafraichir si demande

```bash
.claude/scripts/pr-review/fetch-workflows.sh $PR_NUMBER
```

### 2. Recuperer le resume

```bash
sqlite3 -json ~/.local/share/alexandria/pr-reviews.db "
SELECT
    w.workflow_name,
    w.status,
    w.conclusion,
    w.html_url,
    w.started_at,
    w.completed_at
FROM workflow_runs w
JOIN prs p ON w.pr_id = p.id
WHERE p.pr_number = $PR_NUMBER
ORDER BY w.started_at DESC;
"
```

### 3. Recuperer les checks

```bash
sqlite3 -json ~/.local/share/alexandria/pr-reviews.db "
SELECT
    cr.name,
    cr.status,
    cr.conclusion,
    cr.output_title,
    cr.output_summary,
    cr.annotations_count,
    cr.details_url
FROM check_runs cr
JOIN prs p ON cr.pr_id = p.id
WHERE p.pr_number = $PR_NUMBER
ORDER BY cr.name;
"
```

## Output Standard

```
## Workflows - PR #11

### Workflow Runs

| Workflow | Status | Conclusion | Duration |
|----------|--------|------------|----------|
| CI | completed | success | 5m 23s |
| Code Quality | completed | success | 3m 45s |
| Security Scan | completed | success | 2m 12s |

### Check Runs

| Check | Status | Conclusion | Annotations |
|-------|--------|------------|-------------|
| build | completed | success | 0 |
| test | completed | success | 0 |
| lint | completed | success | 0 |
| security | completed | success | 0 |

**Overall Status**: All checks passed
```

## Output avec --details (si failed)

```
## Workflows - PR #11

### Workflow Runs

| Workflow | Status | Conclusion |
|----------|--------|------------|
| CI | completed | failure |
| Code Quality | completed | success |

### Failed Checks

#### test (failure)

**Summary**: 2 tests failed

**Output**:
```
FAILED: UserServiceTest.testCreateUser
  Expected: 200
  Actual: 500

FAILED: AuthControllerTest.testLogin
  Timeout after 30s
```

**Annotations**:
- `src/test/java/UserServiceTest.java:45`: Test failed
- `src/test/java/AuthControllerTest.java:23`: Timeout

[View Details](https://github.com/owner/repo/actions/runs/123456)

---

### Passing Checks

| Check | Conclusion |
|-------|------------|
| build | success |
| lint | success |
| security | success |

**Overall Status**: 1 check failed
```

## Actions Suggeres

Si des checks echouent, suggerer des actions:

```
### Suggested Actions

1. **test failure**: Review test output above
   - Check if tests are flaky
   - Verify test environment

2. **lint failure**: Run `make format` locally
   - Auto-fix available: `make format && git commit --amend`

3. **security failure**: Review security scan
   - Check CVE details
   - Update dependencies if needed
```

## Notes

- Les donnees sont cachees dans SQLite
- Utiliser `--refresh` pour mettre a jour depuis GitHub
- Les annotations sont limitees a 50 par check (limite GitHub API)
