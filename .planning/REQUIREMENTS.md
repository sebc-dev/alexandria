# Requirements: Alexandria v0.2 Full Docker

**Defined:** 2026-01-22
**Core Value:** Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.

## v0.2 Requirements

Requirements for Docker packaging milestone. Each maps to roadmap phases.

### Docker Infrastructure

- [ ] **DOCK-01**: Multi-stage Dockerfile avec JDK build et JRE runtime
- [ ] **DOCK-02**: Spring Boot layered JAR extraction pour cache efficiency
- [ ] **DOCK-03**: docker-compose.yml avec service app + postgres
- [ ] **DOCK-04**: Health check avec depends_on service_healthy condition
- [ ] **DOCK-05**: Container execute en tant que non-root user
- [ ] **DOCK-06**: Graceful shutdown (exec form ENTRYPOINT + Spring lifecycle)
- [ ] **DOCK-07**: Extended start_period (120s) pour accommoder ONNX loading

### MCP Transport

- [ ] **MCP-01**: HTTP/SSE transport via spring-ai-starter-mcp-server-webmvc
- [ ] **MCP-02**: Port 8080 expose pour clients MCP externes
- [ ] **MCP-03**: STDIO transport conserve et configurable via Spring profile

### Configuration

- [ ] **CONF-01**: Variables d'environnement pour connexion database (DB_HOST, etc.)
- [ ] **CONF-02**: Volume mount pour repertoire documents configurable
- [x] **CONF-03**: Support fichier .env pour configuration overrides
- [x] **CONF-04**: Fichier .env.example avec documentation des options

### Developer Experience

- [x] **DEVX-01**: Script wrapper CLI (`alexandria` command) pour docker exec
- [x] **DEVX-02**: .dockerignore pour optimisation build (exclure target/, .git/, etc.)
- [x] **DEVX-03**: README mis a jour avec instructions installation et usage Docker

### CI/CD

- [x] **CICD-01**: GitHub Actions workflow pour build image Docker
- [x] **CICD-02**: Publication automatique sur GitHub Container Registry (ghcr.io)
- [x] **CICD-03**: Tagging semantique depuis git tags (v0.2.0 -> ghcr.io/user/alexandria:0.2.0)

## v0.3+ Requirements

Deferred to future releases.

### Search Enhancements

- Scoring pondere configurable (vector vs keyword weights)
- Reranking des resultats avec cross-encoder
- Tool `similar_docs` pour documents similaires

### Format Support

- Support fichiers RST (reStructuredText)
- Support fichiers AsciiDoc

## Out of Scope

Explicitly excluded from v0.2.

| Feature | Reason |
|---------|--------|
| Multi-arch images (ARM) | Complexite excessive pour usage personnel |
| Kubernetes manifests | Docker Compose suffit |
| GraalVM native image | Problemes compatibilite ONNX, pas worth it |
| Auto-scaling | Usage personnel, pas besoin |
| Vault secrets | Overkill, env vars suffisent |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DOCK-01 | Phase 8 | Complete |
| DOCK-02 | Phase 8 | Complete |
| DOCK-03 | Phase 8 | Complete |
| DOCK-04 | Phase 8 | Complete |
| DOCK-05 | Phase 8 | Complete |
| DOCK-06 | Phase 8 | Complete |
| DOCK-07 | Phase 8 | Complete |
| MCP-01 | Phase 8 | Complete |
| MCP-02 | Phase 8 | Complete |
| MCP-03 | Phase 8 | Complete |
| CONF-01 | Phase 8 | Complete |
| CONF-02 | Phase 8 | Complete |
| CONF-03 | Phase 9 | Complete |
| CONF-04 | Phase 9 | Complete |
| DEVX-01 | Phase 9 | Complete |
| DEVX-02 | Phase 9 | Complete |
| DEVX-03 | Phase 9 | Complete |
| CICD-01 | Phase 10 | Complete |
| CICD-02 | Phase 10 | Complete |
| CICD-03 | Phase 10 | Complete |

**Coverage:**
- v0.2 requirements: 20 total
- Mapped to phases: 20
- Unmapped: 0

---
*Requirements defined: 2026-01-22*
*Last updated: 2026-01-22 after Phase 10 completion*
