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
| DOCK-01 | - | Pending |
| DOCK-02 | - | Pending |
| DOCK-03 | - | Pending |
| DOCK-04 | - | Pending |
| DOCK-05 | - | Pending |
| DOCK-06 | - | Pending |
| DOCK-07 | - | Pending |
| MCP-01 | - | Pending |
| MCP-02 | - | Pending |
| MCP-03 | - | Pending |
| CONF-01 | - | Pending |
| CONF-02 | - | Pending |
| CONF-03 | - | Pending |
| CONF-04 | - | Pending |
| DEVX-01 | - | Pending |
| DEVX-02 | - | Pending |
| DEVX-03 | - | Pending |
| CICD-01 | - | Pending |
| CICD-02 | - | Pending |
| CICD-03 | - | Pending |

**Coverage:**
- v1.1 requirements: 20 total
- Mapped to phases: 0
- Unmapped: 20

---
*Requirements defined: 2026-01-22*
*Last updated: 2026-01-22 after initial definition*
