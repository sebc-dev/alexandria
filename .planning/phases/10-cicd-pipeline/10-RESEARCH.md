# Phase 10: CI/CD Pipeline - Research

**Researched:** 2026-01-22
**Domain:** GitHub Actions, Docker, GitHub Container Registry (GHCR)
**Confidence:** HIGH

## Summary

This research covers the implementation of a CI/CD pipeline using GitHub Actions to automatically build and publish Docker images to GitHub Container Registry (GHCR) when version tags are pushed. The ecosystem is mature with official Docker actions providing well-documented, battle-tested patterns.

The standard approach uses four official Docker actions: `docker/login-action` for GHCR authentication, `docker/metadata-action` for semantic version tag extraction, `docker/setup-buildx-action` for build capabilities, and `docker/build-push-action` for building and pushing. Authentication uses the automatic `GITHUB_TOKEN` with `packages: write` permission.

**Primary recommendation:** Use the official Docker GitHub Actions (v5/v6) with `type=semver` tag patterns and GitHub Actions cache (`type=gha`) for optimal build performance.

## Standard Stack

The established libraries/tools for this domain:

### Core
| Action | Version | Purpose | Why Standard |
|--------|---------|---------|--------------|
| docker/login-action | v3 | Authenticate to GHCR | Official Docker action, supports all registries |
| docker/metadata-action | v5 | Extract semver tags from git tags | Automatic version extraction, OCI labels |
| docker/build-push-action | v6 | Build and push Docker images | Full Buildx support, caching, attestations |
| docker/setup-buildx-action | v3 | Setup Docker Buildx | Required for advanced caching |

### Supporting
| Action | Version | Purpose | When to Use |
|--------|---------|---------|-------------|
| actions/checkout | v4/v5 | Checkout repository | Always needed for path context |
| actions/attest-build-provenance | v2 | Supply chain attestation | Public repos for provenance |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| docker/* actions | Manual docker commands | More verbose, no caching integration |
| GITHUB_TOKEN | Personal Access Token (PAT) | PAT requires manual management, security risk |
| GHCR | Docker Hub | GHCR is free for public repos, integrated with GitHub |

**Installation:** N/A - GitHub Actions are referenced directly in workflows

## Architecture Patterns

### Recommended Workflow Structure
```
.github/
└── workflows/
    └── release.yml      # Tag-triggered release workflow
```

### Pattern 1: Tag-Triggered Release Workflow
**What:** Workflow that triggers on semantic version tag pushes (v*.*.*)
**When to use:** When releases should publish Docker images automatically
**Example:**
```yaml
# Source: GitHub Actions Workflow Syntax Documentation
name: Release

on:
  push:
    tags:
      - 'v*.*.*'

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
      attestations: write
      id-token: write
```

### Pattern 2: Semver Tag Extraction
**What:** Extract semantic version from git tag (v1.2.3 -> 1.2.3, 1.2, 1)
**When to use:** To generate multiple Docker tags from a single git tag
**Example:**
```yaml
# Source: docker/metadata-action README
- name: Extract Docker metadata
  id: meta
  uses: docker/metadata-action@v5
  with:
    images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
    tags: |
      type=semver,pattern={{version}}
      type=semver,pattern={{major}}.{{minor}}
```

### Pattern 3: GHA Cache for Multi-Stage Builds
**What:** Use GitHub Actions native cache for Docker layer caching
**When to use:** Always - significantly speeds up builds
**Example:**
```yaml
# Source: Docker Build CI/CD Documentation
- name: Build and push
  uses: docker/build-push-action@v6
  with:
    context: .
    file: ./Dockerfile.app
    push: true
    tags: ${{ steps.meta.outputs.tags }}
    labels: ${{ steps.meta.outputs.labels }}
    cache-from: type=gha
    cache-to: type=gha,mode=max
```

### Pattern 4: Lowercase Repository Name
**What:** Convert repository name to lowercase for GHCR compliance
**When to use:** When repository owner might have uppercase characters
**Example:**
```yaml
# Source: GitHub Community Discussion #27086
- name: Set lowercase repository name
  run: |
    echo "IMAGE_NAME=${GITHUB_REPOSITORY,,}" >> $GITHUB_ENV
```

### Anti-Patterns to Avoid
- **Hardcoded image names:** Use `${{ github.repository }}` for portability
- **Manual tag extraction:** Use metadata-action instead of shell scripting
- **PAT for authentication:** Use GITHUB_TOKEN for automatic token management
- **Skipping Buildx setup:** Required for GHA cache backend
- **Using cache API v1:** Deprecated as of April 2025 - use v2 (automatic with latest actions)

## Don't Hand-Roll

Problems that look simple but have existing solutions:

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Version extraction from git tag | Shell script parsing | docker/metadata-action | Handles edge cases, pre-releases, OCI labels |
| Docker registry login | echo/docker login | docker/login-action | Token masking, multi-registry support |
| Build caching | Manual layer management | type=gha cache | Integrated with GitHub Actions cache API v2 |
| Lowercase conversion | Complex sed/awk | `${VAR,,}` bash syntax | Native bash, no external tools |
| Image tagging | Manual docker tag | metadata-action outputs | Consistent, includes labels |

**Key insight:** The Docker official actions handle many edge cases (pre-release versions, case sensitivity, OCI compliance, attestations) that would be error-prone to implement manually.

## Common Pitfalls

### Pitfall 1: Missing packages: write Permission
**What goes wrong:** Push to GHCR fails with 403 Forbidden
**Why it happens:** GITHUB_TOKEN has read-only packages access by default
**How to avoid:** Explicitly set `permissions: packages: write` in job
**Warning signs:** "denied: permission denied" or "403 Forbidden" errors

### Pitfall 2: Uppercase Repository Name
**What goes wrong:** Docker push fails with "repository name must be lowercase"
**Why it happens:** GitHub preserves case in repository names, Docker requires lowercase
**How to avoid:** Use `${GITHUB_REPOSITORY,,}` or let metadata-action handle it
**Warning signs:** "invalid reference format" error during push

### Pitfall 3: GHA Cache API v1 Deprecation
**What goes wrong:** Cache operations fail with legacy service error
**Why it happens:** GitHub Cache API v1 was deprecated April 15, 2025
**How to avoid:** Use latest docker/build-push-action@v6 and docker/setup-buildx-action@v3
**Warning signs:** "This legacy service is shutting down" error message

### Pitfall 4: Git Context vs Path Context
**What goes wrong:** Local file changes before build step are ignored
**Why it happens:** build-push-action uses git context by default, ignoring working directory changes
**How to avoid:** Use `context: .` explicitly to use path context
**Warning signs:** Built image doesn't include expected file modifications

### Pitfall 5: Multi-Stage Build Cache Invalidation
**What goes wrong:** Build cache provides minimal benefit for multi-stage builds
**Why it happens:** Inline cache only includes layers in final image, not intermediate stages
**How to avoid:** Use `mode=max` with GHA cache to cache all stages
**Warning signs:** Builder stages rebuild every time despite cache

### Pitfall 6: Missing id-token Permission for Attestations
**What goes wrong:** Attestation generation fails with OIDC token error
**Why it happens:** Sigstore requires OIDC token for signing
**How to avoid:** Include `id-token: write` in permissions if using attestations
**Warning signs:** "Error: Unable to get ACTIONS_ID_TOKEN_REQUEST_URL" error

### Pitfall 7: Package Not Connected to Repository
**What goes wrong:** GITHUB_TOKEN cannot push to existing package
**Why it happens:** Package was previously pushed without repository link
**How to avoid:** First push establishes link automatically; or add `org.opencontainers.image.source` label
**Warning signs:** "permission denied" on push to existing package namespace

## Code Examples

Verified patterns from official sources:

### Complete Release Workflow
```yaml
# Source: GitHub Docs - Publishing Docker images, Docker Docs - GitHub Actions CI/CD
name: Release

on:
  push:
    tags:
      - 'v*.*.*'

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to Container registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract Docker metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=semver,pattern={{version}}
            type=semver,pattern={{major}}.{{minor}}
          flavor: |
            latest=auto

      - name: Build and push Docker image
        uses: docker/build-push-action@v6
        with:
          context: .
          file: ./Dockerfile.app
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### Tag Pattern Matching Examples
```yaml
# Source: GitHub Actions Workflow Syntax Documentation

# Match exact semver pattern
on:
  push:
    tags:
      - 'v*.*.*'      # Matches v1.0.0, v2.1.3, v10.20.30

# Match any v-prefixed tag
on:
  push:
    tags:
      - 'v*'          # Matches v1, v1.0, v1.0.0, v1.0.0-beta

# Match multiple patterns
on:
  push:
    tags:
      - 'v[0-9]+.[0-9]+.[0-9]+'    # Stricter semver
      - 'v[0-9]+.[0-9]+.[0-9]+-*'  # Include pre-releases
```

### Semver Tag Output Examples
```yaml
# Source: docker/metadata-action README
# Git tag: refs/tags/v1.2.3

# With this configuration:
tags: |
  type=semver,pattern={{version}}
  type=semver,pattern={{major}}.{{minor}}

# Generates:
# - ghcr.io/owner/repo:1.2.3
# - ghcr.io/owner/repo:1.2
# - ghcr.io/owner/repo:latest (if latest=auto and stable release)
```

### Flavor Configuration
```yaml
# Source: docker/metadata-action README
flavor: |
  latest=auto     # Add :latest for stable releases only
  prefix=         # No prefix (default)
  suffix=         # No suffix (default)

# latest=auto behavior:
# - v1.2.3 -> adds :latest
# - v1.2.3-beta.1 -> no :latest (pre-release)
# - v0.1.0 -> adds :latest (initial development still gets latest)
```

### Lowercase Repository Handling
```yaml
# Source: GitHub Community Discussion, docker/metadata-action handles this automatically
# Option 1: Let metadata-action handle it (recommended)
- name: Extract Docker metadata
  uses: docker/metadata-action@v5
  with:
    images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
    # metadata-action automatically lowercases

# Option 2: Manual handling if needed elsewhere
- name: Set lowercase image name
  run: echo "IMAGE_NAME_LC=${GITHUB_REPOSITORY,,}" >> $GITHUB_ENV
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| GitHub Cache API v1 | Cache API v2 | April 2025 | Must use latest action versions |
| docker/build-push-action@v5 | @v6 | 2025 | Better cache support, attestations |
| Manual tag extraction | docker/metadata-action@v5 | 2023+ | Automatic OCI labels, less code |
| PAT authentication | GITHUB_TOKEN | 2022+ | Better security, no secret management |
| Inline cache only | mode=max GHA cache | 2024+ | Full multi-stage caching |

**Deprecated/outdated:**
- GitHub Cache API v1: Shut down April 15, 2025
- docker/build-push-action@v2-v4: Missing cache API v2 support
- Manual docker login commands: docker/login-action preferred

## Open Questions

Things that couldn't be fully resolved:

1. **Multi-architecture builds (linux/amd64, linux/arm64)**
   - What we know: Supported via `platforms` parameter in build-push-action
   - What's unclear: Whether Alexandria's ONNX dependencies work on ARM64
   - Recommendation: Start with linux/amd64 only; ARM64 can be added later if needed

2. **Attestation for private repositories**
   - What we know: Artifact attestations require GitHub Enterprise Cloud for private repos
   - What's unclear: Whether the repository will be public or private
   - Recommendation: Include attestation step with conditional (`if: github.repository_visibility == 'public'`)

## Sources

### Primary (HIGH confidence)
- [GitHub Docs - Publishing Docker images](https://docs.github.com/actions/guides/publishing-docker-images) - Complete workflow examples
- [Docker Docs - GitHub Actions Cache](https://docs.docker.com/build/ci/github-actions/cache/) - Cache configuration
- [docker/metadata-action](https://github.com/docker/metadata-action) - Tag extraction patterns
- [docker/build-push-action](https://github.com/docker/build-push-action) - Build configuration
- [GitHub Docs - Workflow syntax](https://docs.github.com/actions/using-workflows/workflow-syntax-for-github-actions) - Tag trigger patterns

### Secondary (MEDIUM confidence)
- [GitHub Community Discussion #27086](https://github.com/orgs/community/discussions/27086) - Lowercase repository name handling
- [Docker Docs - Tags and labels](https://docs.docker.com/build/ci/github-actions/manage-tags-labels/) - Tag management patterns

### Tertiary (LOW confidence)
- Various DEV.to and Medium articles for pattern validation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - Official Docker actions are well-documented with extensive examples
- Architecture: HIGH - Patterns from official GitHub and Docker documentation
- Pitfalls: HIGH - Multiple sources confirm common issues, deprecation dates verified

**Research date:** 2026-01-22
**Valid until:** 2026-04-22 (3 months - GitHub Actions ecosystem is stable)

---

## Project-Specific Notes

Based on the Alexandria project context:

1. **Dockerfile location:** Use `Dockerfile.app` (not `Dockerfile` which is for PostgreSQL)
2. **Repository name:** `sebc-dev/alexandria` (from branch naming pattern)
3. **No mvnw:** Maven installed directly in Dockerfile builder stage (already configured)
4. **Multi-stage build:** Already uses layered JAR extraction for optimal caching
5. **ONNX model loading:** 120s start_period for health checks (not relevant for CI build, only runtime)

### Recommended Image Name Pattern
```
ghcr.io/sebc-dev/alexandria:1.1.0
ghcr.io/sebc-dev/alexandria:1.1
ghcr.io/sebc-dev/alexandria:latest
```
