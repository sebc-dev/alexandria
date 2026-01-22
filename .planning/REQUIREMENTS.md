# Requirements: Alexandria v1.1 Full Docker

**Defined:** 2026-01-22
**Core Value:** Claude Code peut acceder a ma documentation technique personnelle pendant l'implementation pour respecter mes conventions et bonnes pratiques.

## v1.1 Requirements

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
- [ ] **CONF-03**: Support fichier .env pour configuration overrides
- [ ] **CONF-04**: Fichier .env.example avec documentation des options

### Developer Experience

- [ ] **DEVX-01**: Script wrapper CLI (`alexandria` command) pour docker exec
- [ ] **DEVX-02**: .dockerignore pour optimisation build (exclure target/, .git/, etc.)
- [ ] **DEVX-03**: README mis a jour avec instructions installation et usage Docker

### CI/CD

- [ ] **CICD-01**: GitHub Actions workflow pour build image Docker
- [ ] **CICD-02**: Publication automatique sur GitHub Container Registry (ghcr.io)
- [ ] **CICD-03**: Tagging semantique depuis git tags (v1.1.0 -> ghcr.io/user/alexandria:1.1.0)

## v1.2+ Requirements

Deferred to future releases.

### Search Enhancements

- Scoring pondere configurable (vector vs keyword weights)
- Reranking des resultats avec cross-encoder
- Tool `similar_docs` pour documents similaires

### Format Support

- Support fichiers RST (reStructuredText)
- Support fichiers AsciiDoc

## Out of Scope

Explicitly excluded from v1.1.

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
| CONF-03 | Phase 9 | Pending |
| CONF-04 | Phase 9 | Pending |
| DEVX-01 | Phase 9 | Pending |
| DEVX-02 | Phase 9 | Pending |
| DEVX-03 | Phase 9 | Pending |
| CICD-01 | Phase 10 | Pending |
| CICD-02 | Phase 10 | Pending |
| CICD-03 | Phase 10 | Pending |

**Coverage:**
- v1.1 requirements: 20 total
- Mapped to phases: 20
- Unmapped: 0

---
*Requirements defined: 2026-01-22*
*Last updated: 2026-01-22 after Phase 8 completion*
