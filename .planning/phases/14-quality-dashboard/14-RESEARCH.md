# Phase 14: Quality Dashboard - Research

**Researched:** 2026-01-23
**Domain:** GitHub Actions badges, shell scripting for quality metrics
**Confidence:** HIGH

## Summary

This phase requires implementing a dynamic coverage badge in README.md and a unified `./quality` script that consolidates JaCoCo and PIT metrics. Research identified two primary approaches for dynamic badges:

1. **JaCoCo Badge Generator** (`cicirello/jacoco-badge-generator@v2`) - Generates SVG badges directly from JaCoCo CSV, commits to repository
2. **Dynamic Badges Action** (`schneegans/dynamic-badges-action@v1.7.0`) - Uploads JSON to a gist, shields.io renders the badge

For this project's constraints (no external service signup, works with existing CI, simple setup), **JaCoCo Badge Generator is recommended** because:
- Parses `jacoco.csv` directly (already generated)
- No external account/gist setup required
- No GitHub Personal Access Token with gist scope needed
- Badge stored in repository (`.github/badges/`)
- Automatic color gradients based on coverage percentage

**Primary recommendation:** Use `cicirello/jacoco-badge-generator@v2` for coverage badge; create `./quality` script that calls existing `./coverage` and `./mutation` scripts sequentially with consolidated output.

## Standard Stack

The established tools for this domain:

### Core
| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| `cicirello/jacoco-badge-generator` | v2 | Generate coverage badge from JaCoCo CSV | 1000+ GitHub stars, purpose-built for JaCoCo, no external dependencies |
| Bash shell scripts | N/A | Quality script consolidation | Matches existing `./coverage` and `./mutation` patterns |

### Supporting
| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| `actions/checkout@v4` | v4 | Checkout with token for push | Required to commit badges back |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| jacoco-badge-generator | schneegans/dynamic-badges-action | Requires gist setup and PAT with gist scope; more flexible for arbitrary badges but overkill for JaCoCo |
| jacoco-badge-generator | codecov.io / coveralls.io | External service, requires account signup, potential costs for private repos |
| Repository commit | Gist-based approach | Gist keeps repo history clean but requires token setup; for simple coverage badge, repo commit is simpler |

## Architecture Patterns

### Recommended CI Workflow Extension

```yaml
# In .github/workflows/ci.yml after existing steps
- name: Generate JaCoCo coverage badge
  id: jacoco
  uses: cicirello/jacoco-badge-generator@v2
  with:
    jacoco-csv-file: target/site/jacoco-merged/jacoco.csv
    badges-directory: .github/badges
    generate-coverage-badge: true
    generate-branches-badge: true
    coverage-label: coverage
    branches-label: branches
    colors: '#4c1 #97ca00 #a4a61d #dfb317 #fe7d37 #e05d44'
    intervals: 100 90 80 70 60 0

- name: Commit coverage badges
  if: github.event_name == 'push' && github.ref == 'refs/heads/main'
  run: |
    if [[ `git status --porcelain .github/badges` ]]; then
      git config --global user.name 'github-actions[bot]'
      git config --global user.email '41898282+github-actions[bot]@users.noreply.github.com'
      git add .github/badges/*.svg
      git commit -m "chore: update coverage badges"
      git push
    fi
```

### Recommended Quality Script Structure

```
./quality
├── Runs ./coverage (unit test coverage)
├── Runs ./mutation (PIT mutation testing - opt-in or always)
├── Parses both outputs
└── Displays consolidated summary
```

### Pattern: Sequential Quality Checks with Consolidated Output

**What:** Run coverage first (fast), then optionally mutation testing (slow), display unified summary
**When to use:** Developer wants full quality picture with single command
**Example:**
```bash
#!/usr/bin/env bash
set -euo pipefail

echo "Running Quality Analysis"
echo "========================"
echo ""

# Run coverage (fast - ~30 seconds)
./coverage 2>&1 | tail -n +3  # Skip "Running..." header

echo ""

# Run mutation (slow - ~2-5 minutes)
if [[ "${1:-}" == "--full" ]]; then
    ./mutation 2>&1 | tail -n +3
else
    echo "Mutation testing: skipped (use --full to include)"
fi

echo ""
echo "Quality Analysis Complete"
```

### Anti-Patterns to Avoid
- **Committing badges on PRs:** Only commit on main branch pushes to avoid merge conflicts
- **Using `-uall` flag with git status:** Can cause memory issues on large repos
- **Force-pushing badges:** Use normal push, badges are additive changes
- **Running PIT by default in quality script:** PIT is slow (2-5 minutes); make it opt-in with `--full` flag

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Coverage badge generation | Custom SVG template + awk parsing | cicirello/jacoco-badge-generator | Handles color gradients, SVG generation, edge cases |
| Badge color gradients | Manual color calculation | Action's `colors` + `intervals` inputs | Tested, configurable color ranges |
| Shields.io JSON endpoints | Manual JSON generation | Action's `generate-coverage-endpoint` option | Proper schema, validated |

**Key insight:** Badge generation involves color interpolation, SVG formatting, and edge case handling. The cicirello action has been refined over years and handles all this correctly.

## Common Pitfalls

### Pitfall 1: Badge Commits on Pull Requests
**What goes wrong:** Workflow tries to commit badges on PR, fails or creates merge conflicts
**Why it happens:** CI runs on both push and PR events
**How to avoid:** Add condition: `if: github.event_name == 'push' && github.ref == 'refs/heads/main'`
**Warning signs:** Failed workflows on PRs, "nothing to commit" errors

### Pitfall 2: Using Wrong JaCoCo CSV File
**What goes wrong:** Badge shows only unit test or only integration test coverage
**Why it happens:** Project has multiple JaCoCo reports (jacoco/, jacoco-it/, jacoco-merged/)
**How to avoid:** Use `target/site/jacoco-merged/jacoco.csv` for combined coverage
**Warning signs:** Coverage percentage doesn't match expected total

### Pitfall 3: Checkout Without Token for Push
**What goes wrong:** `git push` fails with permission denied
**Why it happens:** Default checkout token doesn't have push permission
**How to avoid:** Use `actions/checkout@v4` with default token (has push rights) or explicit `token: ${{ secrets.GITHUB_TOKEN }}`
**Warning signs:** "Permission denied" or "could not read Username" errors

### Pitfall 4: Badge Directory Not in .gitignore
**What goes wrong:** Generated badges tracked in working directory, constant diffs
**Why it happens:** `.github/badges/` created but not properly committed
**How to avoid:** Ensure initial `.github/badges/` directory exists in repo (can be empty with `.gitkeep`)
**Warning signs:** Local git status shows badge changes after every build

### Pitfall 5: Quality Script Runs Both Tests Sequentially
**What goes wrong:** Quality script takes 10+ minutes, developers avoid using it
**Why it happens:** Running both `mvn test` for coverage AND `mvn test -Ppitest` re-runs all tests
**How to avoid:** PIT already runs tests; consider making mutation testing opt-in (`--full` flag)
**Warning signs:** Script takes >5 minutes for routine use

## Code Examples

Verified patterns from official sources:

### Complete Badge Generation Workflow Step
```yaml
# Source: https://github.com/cicirello/jacoco-badge-generator README
- name: Generate JaCoCo coverage badge
  id: jacoco
  uses: cicirello/jacoco-badge-generator@v2
  with:
    jacoco-csv-file: target/site/jacoco-merged/jacoco.csv
    badges-directory: .github/badges
    generate-coverage-badge: true
    generate-branches-badge: true
    coverage-label: coverage
    branches-label: branches
```

### Conditional Badge Commit (Main Branch Only)
```yaml
# Source: https://github.com/cicirello/examples-jacoco-badge-generator
- name: Commit coverage badges
  if: github.event_name == 'push' && github.ref == 'refs/heads/main'
  run: |
    if [[ `git status --porcelain .github/badges` ]]; then
      git config --global user.name 'github-actions[bot]'
      git config --global user.email '41898282+github-actions[bot]@users.noreply.github.com'
      git add .github/badges/*.svg
      git commit -m "chore: update coverage badges"
      git push
    fi
```

### README Badge Markdown
```markdown
![Coverage](.github/badges/jacoco.svg)
![Branches](.github/badges/branches.svg)
```

### Quality Script with Opt-in Mutation Testing
```bash
#!/usr/bin/env bash
# Quality script - consolidated quality analysis
set -euo pipefail

JACOCO_CSV="target/site/jacoco/jacoco.csv"
PIT_CSV="target/pit-reports/mutations.csv"

echo "Quality Analysis"
echo "================"
echo ""

# Coverage analysis (always run)
echo "Running unit tests with coverage..."
mvn test -q

if [[ ! -f "$JACOCO_CSV" ]]; then
    echo "Error: Coverage report not found"
    exit 1
fi

# Parse coverage (same logic as ./coverage script)
line_cov=$(awk -F',' 'NR>1{m+=$8;c+=$9}END{if(m+c>0)printf "%d",c*100/(m+c);else print "0"}' "$JACOCO_CSV")
branch_cov=$(awk -F',' 'NR>1{m+=$6;c+=$7}END{if(m+c>0)printf "%d",c*100/(m+c);else print "0"}' "$JACOCO_CSV")

echo "Coverage: ${line_cov}% line, ${branch_cov}% branch"
echo ""

# Mutation testing (opt-in)
if [[ "${1:-}" == "--full" ]] || [[ "${1:-}" == "-f" ]]; then
    echo "Running mutation testing..."
    mvn test -Ppitest -q

    if [[ -f "$PIT_CSV" ]]; then
        mutation_score=$(awk -F',' '{t++;if($6=="KILLED")k++}END{if(t>0)printf "%d",k*100/t;else print "0"}' "$PIT_CSV")
        echo "Mutation Score: ${mutation_score}%"
    fi
else
    echo "Mutation testing: skipped (use --full to include)"
fi

echo ""
echo "Quality Analysis Complete"
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| codecov.io/coveralls.io | Self-hosted badges | 2022+ | No external service dependency, no account needed |
| shields.io JSON endpoint only | Direct SVG generation | jacoco-badge-generator v2 | Faster badge loading, no shields.io dependency |
| Single coverage metric | Instructions + Branches | Standard practice | Better quality signal |

**Deprecated/outdated:**
- shields.io endpoint badges (still work but direct SVG is simpler)
- Third-party coverage services for simple badge use cases (overkill)

## Open Questions

Things that couldn't be fully resolved:

1. **Should mutation score badge be added?**
   - What we know: cicirello/jacoco-badge-generator only handles JaCoCo; would need schneegans/dynamic-badges-action for PIT badge
   - What's unclear: Is mutation badge worth the additional complexity (gist setup)?
   - Recommendation: Start with coverage badges only; add mutation badge later if needed

2. **Should `./quality` reuse existing script output or re-run tests?**
   - What we know: Both approaches work; reusing output is faster but less accurate
   - What's unclear: User preference on speed vs freshness
   - Recommendation: Re-run tests for accuracy; add `--report-only` flag for just displaying existing reports

## Sources

### Primary (HIGH confidence)
- [cicirello/jacoco-badge-generator](https://github.com/cicirello/jacoco-badge-generator) - All badge generation patterns
- [cicirello/examples-jacoco-badge-generator](https://github.com/cicirello/examples-jacoco-badge-generator) - Workflow examples with commit step
- [Schneegans/dynamic-badges-action](https://github.com/Schneegans/dynamic-badges-action) - Alternative gist-based approach

### Secondary (MEDIUM confidence)
- [DEV Community - Coverage Badge with GitHub Actions](https://dev.to/thejaredwilcurt/coverage-badge-with-github-actions-finally-59fa) - Community validation of approaches
- [Simon Schneegans' Blog](http://schneegans.github.io/tutorials/2022/04/18/badges) - Gist-based badge tutorial

### Tertiary (LOW confidence)
- General WebSearch results on badge approaches - used for discovery, verified against primary sources

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Official GitHub Action documentation reviewed
- Architecture: HIGH - Based on official examples and project's existing patterns
- Pitfalls: HIGH - Documented in action repositories and community posts

**Research date:** 2026-01-23
**Valid until:** 2026-03-23 (60 days - stable domain, well-established tools)
