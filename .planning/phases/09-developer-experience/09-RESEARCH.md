# Phase 9: Developer Experience - Research

**Researched:** 2026-01-22
**Domain:** CLI wrapper scripts, Docker configuration, project documentation
**Confidence:** HIGH

## Summary

Phase 9 focuses on improving developer experience for the containerized Alexandria application. The research covers five main areas:

1. **CLI Wrapper Script (DEVX-01)**: A shell script that wraps `docker compose exec` to provide a clean `./alexandria` command interface. Users can run `./alexandria index /docs` instead of `docker compose exec app java org.springframework.boot.loader.launch.JarLauncher index --path /docs`.

2. **.dockerignore Optimization (DEVX-02)**: Essential for Spring Boot Maven projects to exclude build artifacts, IDE files, and version control from Docker build context. This improves build speed and reduces context size.

3. **.env Files (CONF-03, CONF-04)**: Docker Compose automatically loads `.env` files from the project root. A well-documented `.env.example` with all configuration options helps users understand available settings without exposing actual credentials.

4. **README Update (DEVX-03)**: Complete installation instructions from git clone through running search queries. Needs separate sections for Docker-based usage vs traditional Java installation.

The primary challenge is providing seamless CLI access to the containerized application while maintaining profile compatibility (the wrapper must activate the `cli,docker` profiles instead of `http,docker`).

**Primary recommendation:** Create a POSIX-compatible shell script wrapper that detects whether the container is running and executes Spring Shell commands via `docker compose exec` with proper argument passing.

## Standard Stack

### Core

| Tool | Version | Purpose | Why Standard |
|------|---------|---------|--------------|
| POSIX shell (`/bin/sh`) | - | Wrapper script interpreter | Maximum portability, no bash-specific features needed |
| docker compose | V2+ | Container orchestration | Already used in Phase 8, exec command for CLI access |
| .env file format | - | Configuration | Docker Compose native support, no additional tooling |

### Supporting

| Tool | Version | Purpose | When to Use |
|------|---------|---------|-------------|
| tput | - | Terminal colors | Optional: colorize error messages in wrapper |
| realpath | - | Path resolution | Resolve DOCS_PATH to absolute path |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Shell script wrapper | Docker alias/function | Aliases not portable, script can be committed |
| .env file | --env-file flag | .env is automatic, --env-file requires explicit flag |
| Single .env.example | Multiple env files (.env.dev, .env.prod) | Project is personal use, single file sufficient |

**No installation required** - all tools are part of Docker and standard Unix systems.

## Architecture Patterns

### Recommended Project Structure

```
/
├── alexandria               # CLI wrapper script (executable)
├── .env                     # User configuration (gitignored)
├── .env.example             # Documented configuration template
├── .dockerignore            # Docker build exclusions
├── README.md                # Updated installation docs
├── docker-compose.yml       # Existing (from Phase 8)
├── Dockerfile.app           # Existing (from Phase 8)
└── Dockerfile               # Existing (PostgreSQL image)
```

### Pattern 1: CLI Wrapper Script

**What:** Shell script that wraps docker compose exec with proper argument handling
**When to use:** Any command the user wants to run in the container
**Example:**

```sh
#!/bin/sh
# alexandria - CLI wrapper for Alexandria Docker container
#
# Usage:
#   ./alexandria index /docs
#   ./alexandria search --query "my query"
#   ./alexandria status
#   ./alexandria clear --force

set -e

# Script directory (for running from anywhere)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Check if container is running
check_container() {
    if ! docker compose -f "$SCRIPT_DIR/docker-compose.yml" ps --status running 2>/dev/null | grep -q alexandria-app; then
        echo "Error: Alexandria container is not running." >&2
        echo "Start it with: docker compose up -d" >&2
        exit 1
    fi
}

# Main execution
check_container

# Execute command in container with cli,docker profiles
# Note: JarLauncher is the exploded class entrypoint from Phase 8
docker compose -f "$SCRIPT_DIR/docker-compose.yml" exec \
    -T \
    -e SPRING_PROFILES_ACTIVE=cli,docker \
    app \
    java org.springframework.boot.loader.launch.JarLauncher "$@"
```

### Pattern 2: .env.example Documentation Pattern

**What:** Template file with all variables, defaults, and documentation
**When to use:** Always for projects with environment configuration
**Example:**

```sh
# Alexandria Configuration
# Copy this file to .env and modify as needed
# All variables have sensible defaults for local development

# =============================================================================
# Documentation Path
# =============================================================================
# Path to your documentation directory on the host machine
# This directory will be mounted read-only at /docs inside the container
DOCS_PATH=./docs

# =============================================================================
# Database Configuration
# =============================================================================
# Only change if using external PostgreSQL instead of docker-compose postgres
DB_HOST=postgres
DB_PORT=5432
DB_NAME=alexandria
DB_USER=alexandria
DB_PASSWORD=alexandria

# =============================================================================
# Application Settings
# =============================================================================
# Logging level: DEBUG, INFO, WARN, ERROR
LOG_LEVEL=INFO

# Memory limit for the application container
# ONNX model needs ~500MB, app needs ~500MB, GC buffer recommended
# Format: 1g, 2g, 512m
MEM_LIMIT=2g
```

### Pattern 3: .dockerignore for Spring Boot Maven

**What:** Exclusion rules to minimize Docker build context
**When to use:** All Maven/Gradle projects
**Example:**

```
# Build artifacts - Maven rebuilds inside container
target/

# Version control
.git/
.gitignore

# IDE files
.idea/
*.iml
.classpath
.project
.settings/
.vscode/

# Documentation files (not needed in image)
*.md
LICENSE
docs/

# Local configuration
.env
.env.local
*.log

# Planning and development files
.planning/
.claude/

# Docker-related (already in image context)
Dockerfile
Dockerfile.app
.dockerignore
docker-compose.yml

# Data directory (PostgreSQL volume)
data/
```

### Anti-Patterns to Avoid

- **Bash-specific syntax in wrapper:** Use `#!/bin/sh`, not `#!/bin/bash` for portability
- **Relative paths in wrapper:** Always resolve to absolute paths for reliability
- **Hardcoded container name:** Use `docker compose exec SERVICE` not `docker exec CONTAINER`
- **TTY allocation in scripts:** Use `-T` flag to disable TTY when running in scripts/CI
- **Missing error handling:** Always `set -e` and check container is running

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Configuration management | Custom config parser | .env + docker compose interpolation | Native support, standard format |
| Container detection | Custom docker ps parsing | `docker compose ps --status running` | Compose-aware, handles service names |
| Path resolution | String manipulation | `realpath` or shell `$(cd ... && pwd)` | Handles symlinks, edge cases |
| README structure | Freeform documentation | Standard sections (Prerequisites, Quick Start, etc.) | Users expect consistent structure |

**Key insight:** Docker Compose provides all the configuration and orchestration features needed. The wrapper script is just argument forwarding with basic validation.

## Common Pitfalls

### Pitfall 1: Profile Mismatch in CLI Wrapper

**What goes wrong:** CLI commands fail or start HTTP server unexpectedly
**Why it happens:** Container runs with `http,docker` profiles, CLI needs `cli,docker`
**How to avoid:** Pass `SPRING_PROFILES_ACTIVE=cli,docker` via `-e` flag in exec command
**Warning signs:** "Web server started" message when running CLI, or Shell not finding commands

### Pitfall 2: TTY Allocation in Non-Interactive Context

**What goes wrong:** `the input device is not a TTY` error when running in scripts/CI
**Why it happens:** `docker compose exec` allocates TTY by default
**How to avoid:** Use `-T` flag to disable TTY allocation
**Warning signs:** Works interactively but fails in scripts or CI

### Pitfall 3: .env File Not Loading

**What goes wrong:** Environment variables not applied, defaults used instead
**Why it happens:** .env file not in same directory as docker-compose.yml, or wrong file permissions
**How to avoid:** Ensure .env is next to docker-compose.yml, has read permissions
**Warning signs:** `docker compose config` shows default values instead of .env values

### Pitfall 4: Arguments Not Passed Correctly

**What goes wrong:** Quotes and spaces in arguments get mangled
**Why it happens:** Shell expansion issues when forwarding arguments
**How to avoid:** Use `"$@"` to pass all arguments, properly quoted
**Warning signs:** Commands with spaces in paths fail, `--query "multiple words"` broken

### Pitfall 5: Container Not Ready Before Command

**What goes wrong:** CLI command fails with connection refused or similar
**Why it happens:** User runs CLI before container is healthy
**How to avoid:** Check container is running in wrapper script
**Warning signs:** Intermittent failures on first command after `docker compose up`

### Pitfall 6: Wrapper Script Not Executable

**What goes wrong:** `permission denied` when running `./alexandria`
**Why it happens:** Git does not preserve execute bit by default on some systems
**How to avoid:** Document `chmod +x alexandria` in README, or use `sh alexandria`
**Warning signs:** Works on dev machine, fails after git clone

## Code Examples

### CLI Wrapper Complete Implementation

```sh
#!/bin/sh
# Source: Best practices from Docker documentation
# alexandria - CLI wrapper for Alexandria Docker container
#
# Usage:
#   ./alexandria index /docs
#   ./alexandria search --query "my query"
#   ./alexandria status
#   ./alexandria clear --force
#   ./alexandria help
#
# Environment:
#   Configuration loaded from .env file next to this script

set -e

# Resolve script directory (works even when called via symlink)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output (optional, degrades gracefully)
if [ -t 1 ] && command -v tput >/dev/null 2>&1; then
    RED=$(tput setaf 1)
    GREEN=$(tput setaf 2)
    RESET=$(tput sgr0)
else
    RED=""
    GREEN=""
    RESET=""
fi

error() {
    echo "${RED}Error: $1${RESET}" >&2
    exit 1
}

info() {
    echo "${GREEN}$1${RESET}"
}

# Show help if no arguments
if [ $# -eq 0 ]; then
    cat <<EOF
Alexandria - RAG system for personal documentation

Usage: ./alexandria <command> [options]

Commands:
  index --path <dir>    Index markdown files from directory
  search --query <text> Search indexed documentation
  status                Show database status
  clear --force         Clear all indexed data

Examples:
  ./alexandria index --path /docs
  ./alexandria search --query "how to configure logging"
  ./alexandria status

Note: Container must be running (docker compose up -d)
EOF
    exit 0
fi

# Check Docker is available
command -v docker >/dev/null 2>&1 || error "Docker is not installed"

# Check container is running
if ! docker compose ps --status running 2>/dev/null | grep -q alexandria-app; then
    error "Alexandria container is not running.
Start it with: docker compose up -d"
fi

# Execute command in container
# -T: Disable TTY (works in scripts)
# SPRING_PROFILES_ACTIVE: Override to cli,docker for shell commands
exec docker compose exec \
    -T \
    -e SPRING_PROFILES_ACTIVE=cli,docker \
    app \
    java org.springframework.boot.loader.launch.JarLauncher "$@"
```

### .env.example Complete File

```sh
# ============================================================================
# Alexandria Configuration
# ============================================================================
# Copy to .env and customize. All values below are defaults.
#
# Quick start:
#   cp .env.example .env
#   # Edit DOCS_PATH to point to your documentation
#   docker compose up -d
#   ./alexandria index --path /docs

# ============================================================================
# REQUIRED: Documentation Path
# ============================================================================
# Path to your documentation directory on the host machine.
# This directory is mounted read-only at /docs inside the container.
#
# Examples:
#   DOCS_PATH=./docs                    # Relative to project root
#   DOCS_PATH=/home/user/my-docs        # Absolute path
#   DOCS_PATH=~/Documentation           # Home directory path
#
DOCS_PATH=./docs

# ============================================================================
# Database Connection (usually no changes needed)
# ============================================================================
# These are for the bundled PostgreSQL container.
# Only change if connecting to an external database.

DB_HOST=postgres
DB_PORT=5432
DB_NAME=alexandria
DB_USER=alexandria
DB_PASSWORD=alexandria

# ============================================================================
# Logging
# ============================================================================
# Log level for application output.
# Options: DEBUG, INFO, WARN, ERROR
#
LOG_LEVEL=INFO

# ============================================================================
# Resource Limits
# ============================================================================
# Memory limit for the Alexandria container.
# The ONNX embedding model requires ~500MB, application needs ~500MB.
# 2g is recommended minimum; reduce at your own risk.
#
# Format: 512m, 1g, 2g, 4g
#
MEM_LIMIT=2g
```

### README Docker Installation Section

```markdown
## Quick Start with Docker

### Prerequisites

- Docker 20.10+ with Docker Compose V2
- 4GB available RAM (2GB for container + database)
- Git

### 1. Clone and Configure

\`\`\`bash
git clone https://github.com/your-username/alexandria.git
cd alexandria

# Create your configuration
cp .env.example .env

# Edit .env to set your documentation path
# DOCS_PATH=/path/to/your/docs
\`\`\`

### 2. Start Services

\`\`\`bash
docker compose up -d

# Wait for services to be healthy (takes ~2 minutes for ONNX model loading)
docker compose ps
\`\`\`

### 3. Index Your Documentation

\`\`\`bash
./alexandria index --path /docs
\`\`\`

### 4. Search

\`\`\`bash
./alexandria search --query "how to configure logging"
\`\`\`

### 5. Connect Claude Code (Optional)

For MCP integration, add to your Claude Code settings:

\`\`\`json
{
  "mcpServers": {
    "alexandria": {
      "type": "sse",
      "url": "http://localhost:8080/sse"
    }
  }
}
\`\`\`
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `docker exec container_name` | `docker compose exec service_name` | Docker Compose V2 | Service-aware, config inheritance |
| Manual --env-file flag | Automatic .env loading | Compose V1.25+ | Simpler configuration |
| Bash scripts only | POSIX sh for portability | Best practice | Works on Alpine, macOS, any shell |
| Inline command in README | Wrapper script | Best practice | Cleaner UX, reduces documentation |

**Current best practices:**
- Docker Compose V2 is now the default (`docker compose` not `docker-compose`)
- .env file location is strictly next to compose file
- Service names preferred over container names for compose operations

## Open Questions

1. **Windows Support for Wrapper Script**
   - What we know: PowerShell or batch file would be needed for native Windows support
   - What's unclear: WSL2 users may use the sh script directly
   - Recommendation: Document WSL2 as primary Windows path; defer native Windows script to v0.3+

2. **MEM_LIMIT Variable Usage**
   - What we know: docker-compose.yml already has `mem_limit: 2g` hardcoded
   - What's unclear: Whether to make it dynamic via .env variable
   - Recommendation: Add `mem_limit: ${MEM_LIMIT:-2g}` to docker-compose.yml for consistency with .env.example

3. **Script Name Collision**
   - What we know: `alexandria` script name matches project name
   - What's unclear: Conflicts if user has other `alexandria` in PATH
   - Recommendation: Use `./alexandria` prefix in all docs; script is not meant for PATH installation

## Sources

### Primary (HIGH confidence)
- [Docker Compose exec documentation](https://docs.docker.com/reference/cli/docker/compose/exec/) - Command syntax, TTY behavior
- [Docker Compose environment variables best practices](https://docs.docker.com/compose/how-tos/environment-variables/best-practices/) - .env file patterns
- [Docker Compose set environment variables](https://docs.docker.com/compose/how-tos/environment-variables/set-environment-variables/) - Variable interpolation
- Existing codebase analysis - Phase 8 implementation details

### Secondary (MEDIUM confidence)
- [Docker 9 Tips for Spring Boot](https://www.docker.com/blog/9-tips-for-containerizing-your-spring-boot-code/) - .dockerignore patterns
- [Java Development .dockerignore Guide](https://gist.github.com/AdapaJohn/d4157706f85e86a2318231c4e7c02c39) - Comprehensive exclusions
- [Docker Best Practices for Java](https://www.javaguides.net/2025/02/docker-best-practices-for-java.html) - Spring Boot specific patterns

### Tertiary (LOW confidence)
- Shell script portability patterns (common knowledge, no specific source)

## Metadata

**Confidence breakdown:**
- CLI wrapper: HIGH - Docker documentation verified, POSIX shell standard
- .dockerignore: HIGH - Standard Spring Boot Maven patterns
- .env patterns: HIGH - Docker Compose documentation verified
- README structure: MEDIUM - Standard conventions, project-specific needs

**Research date:** 2026-01-22
**Valid until:** 2026-04-22 (90 days - Docker Compose stable, shell scripts stable)
