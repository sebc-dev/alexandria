# Roadmap: Alexandria

## Milestones

- [x] **v1.0 MVP** - Phases 1-7 (shipped 2026-01-20)
- [ ] **v1.1 Full Docker** - Phases 8-10 (in progress)

## Phases

<details>
<summary>v1.0 MVP (Phases 1-7) - SHIPPED 2026-01-20</summary>

### Phase 1: Infrastructure
**Goal**: PostgreSQL with pgvector + Apache AGE running in Docker
**Plans**: 2 plans (complete)

### Phase 2: Ingestion Core
**Goal**: Markdown files can be indexed with hierarchical chunking
**Plans**: 3 plans (complete)

### Phase 3: Graph Relations
**Goal**: Document relationships stored in Apache AGE graph
**Plans**: 2 plans (complete)

### Phase 4: Recherche Base
**Goal**: Basic semantic search over indexed documents
**Plans**: 2 plans (complete)

### Phase 5: Recherche Avancee
**Goal**: Hybrid search with vector + full-text + graph traversal
**Plans**: 2 plans (complete)

### Phase 6: MCP Server
**Goal**: Claude Code can search documentation via MCP tools
**Plans**: 2 plans (complete)

### Phase 7: CLI
**Goal**: Manual indexing and maintenance via command line
**Plans**: 2 plans (complete)

</details>

### v1.1 Full Docker (In Progress)

**Milestone Goal:** Rendre l'application installable et utilisable via docker compose sans installer de dependances.

#### Phase 8: Core Docker Infrastructure
**Goal**: Application runs in Docker with HTTP/SSE transport for MCP communication
**Depends on**: Phase 7 (v1.0 complete)
**Requirements**: DOCK-01, DOCK-02, DOCK-03, DOCK-04, DOCK-05, DOCK-06, DOCK-07, MCP-01, MCP-02, MCP-03, CONF-01, CONF-02
**Success Criteria** (what must be TRUE):
  1. User can run `docker compose up` and have both app and postgres services start successfully
  2. MCP client can connect to HTTP/SSE endpoint on port 8080 and execute search_docs tool
  3. Container restarts gracefully on SIGTERM without data corruption
  4. Health check reports healthy after ONNX model loads (within 120s start period)
  5. Container runs as non-root user (verified via `docker exec ... id`)
**Plans**: TBD

Plans:
- [ ] 08-01: HTTP/SSE transport and Spring profile configuration
- [ ] 08-02: Multi-stage Dockerfile with layered JAR extraction
- [ ] 08-03: Docker Compose service definition with health checks

#### Phase 9: Developer Experience
**Goal**: Users can easily configure and interact with containerized Alexandria
**Depends on**: Phase 8
**Requirements**: CONF-03, CONF-04, DEVX-01, DEVX-02, DEVX-03
**Success Criteria** (what must be TRUE):
  1. User can run `./alexandria index /path/to/docs` without knowing docker exec syntax
  2. User can copy .env.example to .env and customize configuration
  3. README contains complete installation instructions that work from git clone to running search
  4. Docker build completes faster on code-only changes (layered caching works)
**Plans**: TBD

Plans:
- [ ] 09-01: CLI wrapper script and .dockerignore
- [ ] 09-02: .env support and documentation update

#### Phase 10: CI/CD Pipeline
**Goal**: Docker images automatically published to GitHub Container Registry on release
**Depends on**: Phase 9
**Requirements**: CICD-01, CICD-02, CICD-03
**Success Criteria** (what must be TRUE):
  1. Pushing a git tag (v1.1.0) triggers GitHub Actions workflow
  2. Image is published to ghcr.io/[owner]/alexandria with correct tag (1.1.0)
  3. User can `docker pull ghcr.io/[owner]/alexandria:1.1.0` without git clone
**Plans**: TBD

Plans:
- [ ] 10-01: GitHub Actions workflow for Docker build and publish

## Progress

**Execution Order:** 8 -> 9 -> 10

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Infrastructure | v1.0 | 2/2 | Complete | 2026-01-19 |
| 2. Ingestion Core | v1.0 | 3/3 | Complete | 2026-01-20 |
| 3. Graph Relations | v1.0 | 2/2 | Complete | 2026-01-20 |
| 4. Recherche Base | v1.0 | 2/2 | Complete | 2026-01-20 |
| 5. Recherche Avancee | v1.0 | 2/2 | Complete | 2026-01-20 |
| 6. MCP Server | v1.0 | 2/2 | Complete | 2026-01-20 |
| 7. CLI | v1.0 | 2/2 | Complete | 2026-01-20 |
| 8. Core Docker Infrastructure | v1.1 | 0/3 | Not started | - |
| 9. Developer Experience | v1.1 | 0/2 | Not started | - |
| 10. CI/CD Pipeline | v1.1 | 0/1 | Not started | - |
