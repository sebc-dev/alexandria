# Phase 11: Quality & Security Tooling - Context

**Gathered:** 2026-02-20
**Status:** Ready for planning

<domain>
## Phase Boundary

Configure build tooling to automatically catch bugs (Error Prone), null safety violations (NullAway), formatting drift (Spotless/google-java-format), and vulnerable dependencies (Trivy, OWASP Dependency-Check). Includes pre-commit hooks and CycloneDX SBOM generation. This phase is infrastructure/tooling — no RAG pipeline changes.

</domain>

<decisions>
## Implementation Decisions

### Strictness & developer workflow
- Build behavior identical between local and CI — `./gradlew build` fails on violations everywhere, no warning-only mode
- Pre-commit hook with Spotless auto-fix (spotlessApply) — formatting corrected automatically before each commit
- Trivy scans run in CI only — not part of the local Gradle build
- OWASP Dependency-Check is part of the standard `./gradlew build` — runs locally and in CI

### NullAway scope
- Annotation library: JSpecify (org.jspecify)
- Scope: all packages under dev.alexandria — no progressive rollout, full coverage from the start
- Existing code: annotate all nullable parameters/returns now in this phase (not deferred)
- Commits: NullAway configuration and code annotations in separate commits (config first, then annotations)

### False positive handling
- Error Prone: centralized configuration file for suppressions (bug-patterns exclusion), not inline @SuppressWarnings
- Trivy: `.trivyignore` file at repo root listing accepted CVE-IDs with justification comments
- OWASP: `owasp-suppressions.xml` dedicated file with justification for each suppressed CVE
- All suppression files committed to repo and treated as code — changes require PR review with justification

### Migration of existing code
- Error Prone: fix all existing ERROR-level bug patterns in this phase, not just detect
- Spotless: big-bang `spotlessApply` on entire codebase in a dedicated commit (not ratchet-only)
- Git blame: configure `.git-blame-ignore-revs` to make the formatting commit transparent in blame
- After big-bang format, ratchet mode continues for ongoing enforcement

### Claude's Discretion
- Handling third-party library nullability (castToNonNull helper vs @SuppressWarnings approach for Spring/LangChain4j returns)
- Error Prone bug pattern selection (which ERROR-level checks to enable beyond defaults)
- Pre-commit hook implementation mechanism (Gradle task, git hooks directory, or pre-commit framework)
- Exact Spotless ratchet configuration details

</decisions>

<specifics>
## Specific Ideas

- quality.sh already exists as the quality gate script — new checks should integrate with or extend it
- The project already has SpotBugs configured — Error Prone complements it (different bug pattern coverage)
- Virtual threads are enabled — NullAway annotations should account for any Spring async patterns

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 11-quality-security-tooling*
*Context gathered: 2026-02-20*
