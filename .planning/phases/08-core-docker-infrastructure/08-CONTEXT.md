# Phase 8: Core Docker Infrastructure - Context

**Gathered:** 2026-01-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Package Alexandria as a Docker container with HTTP/SSE transport for MCP communication. Users can run `docker compose up` and connect Claude Desktop (or other MCP clients) without installing Java locally.

</domain>

<decisions>
## Implementation Decisions

### MCP transport mode
- HTTP/SSE only — no stdio support in container
- Port 8080 for MCP endpoint
- Optional API key authentication via env var (e.g., `ALEXANDRIA_API_KEY`)
  - If set, requests require `Authorization: Bearer <key>` header
  - If unset, no auth required (local-only use case)
- Create a Claude Code skill with connection instructions (not just README docs)

### Container startup behavior
- Block until ready — health check fails during ONNX model loading
- Log verbosity configurable via `LOG_LEVEL` env var (default: INFO)
- Simple text progress messages during startup:
  - "Loading embedding model (1/2)..."
  - "Connecting to database (2/2)..."
  - "Ready on port 8080"
- Health endpoint at `/health` — returns 200 when ready

### Volume mounting strategy
- Documentation mounted at `/docs` (read-only)
- PostgreSQL data persisted via bind mount to `./data`
- ONNX model downloaded at startup (not embedded in image)
  - Cached after first download
  - Requires network on first run

### Resource constraints
- Memory limit with configurable default (e.g., `MEMORY_LIMIT=2g`)
- CPU limit configurable via env var (e.g., `CPU_LIMIT=2`)
- No CPU limit by default, memory limit enforced
- On OOM: stay down with error log (no automatic restart)
  - Prevents restart loop on persistent memory issues
  - `restart: on-failure` but OOM counts as failure threshold
- Defaults optimized for both developer laptops and small servers
- Documentation explains tuning for constrained environments

### Claude's Discretion
- Exact Dockerfile multi-stage build structure
- Spring profile naming conventions
- Health check interval and timeout values
- Specific memory/CPU default values based on ONNX requirements

</decisions>

<specifics>
## Specific Ideas

- Claude Code skill for connection setup — user runs skill and gets everything configured
- Progress messages should be human-readable, not Spring Boot noise
- Bind mount for `./data` keeps database visible and easy to backup

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 08-core-docker-infrastructure*
*Context gathered: 2026-01-22*
