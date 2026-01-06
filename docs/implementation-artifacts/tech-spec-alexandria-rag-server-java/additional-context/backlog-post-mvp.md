# Backlog Post-MVP

**Source:** decisions-finales.md (revue tech-spec 2026-01-06)
**Statut:** Reporté - À implémenter après le MVP

---

## F6: Tests de Concurrence

**Sévérité originale:** HIGH
**Raison du report:** Usage single-user local rend les race conditions improbables

### Contexte
- F1 (@Transactional) couvre le risque principal de corruption de données
- Tests de concurrence = complexité significative pour un risque faible en mono-utilisateur

### Implémentation future
```java
@Test
void concurrentIngestionSameDocument_shouldNotCorruptData() {
    // Simuler 10 threads ingérant le même document simultanément
    // Vérifier: pas de chunks dupliqués, pas de corruption metadata
}

@Test
void concurrentSearchDuringIngestion_shouldReturnConsistentResults() {
    // Un thread ingère, un autre recherche
    // Vérifier: résultats cohérents (ancien OU nouveau, jamais mixte)
}
```

### Déclencheur
- Passage en multi-utilisateur
- Retours utilisateurs sur corruption de données

---

## F8: CLI Error Handling Amélioré

**Sévérité originale:** MEDIUM
**Raison du report:** Usage développeur local = environnement contrôlé

### Acceptance Criteria manquants
- [ ] Fichier avec encodage non-UTF8 → message clair avec suggestion `file --mime-encoding`
- [ ] Symlink cassé → message "Symbolic link target not found: {target}"
- [ ] Permission denied → message avec suggestion `chmod` ou `sudo`
- [ ] Disque plein → message "Insufficient disk space" avec espace requis estimé
- [ ] Path trop long (Windows) → message avec limite et suggestion

### Implémentation future
```java
public sealed interface IngestionError {
    record EncodingError(Path file, String detectedEncoding) implements IngestionError {}
    record BrokenSymlink(Path link, Path target) implements IngestionError {}
    record PermissionDenied(Path file, Set<PosixFilePermission> required) implements IngestionError {}
    record DiskFull(Path file, long requiredBytes, long availableBytes) implements IngestionError {}
}
```

### Déclencheur
- Feedback utilisateurs sur messages d'erreur confus
- Extension à des utilisateurs non-développeurs

---

## F14: Tests HTML Jsoup

**Sévérité originale:** MEDIUM
**Raison du report:** Jsoup est une librairie mature (15+ ans), HTML est format secondaire

### Tests proposés
```java
@ParameterizedTest
@ValueSource(strings = {
    "<html><body><p>Simple</p></body></html>",
    "<div><script>evil()</script><p>Content</p></div>",  // Script removal
    "<p>Nested <b>bold <i>italic</i></b></p>",           // Nested tags
    "<!DOCTYPE html><html>...</html>",                   // DOCTYPE handling
    "<meta charset='iso-8859-1'>Café"                    // Encoding
})
void parseHtml_variousFormats(String html) {
    // Vérifier extraction texte correcte
}
```

### Déclencheur
- Bugs rencontrés avec fichiers HTML réels
- Augmentation significative du volume HTML ingéré

---

## F15: RAG Latency Metrics (Micrometer)

**Sévérité originale:** MEDIUM
**Raison du report:** JFR + logs suffisent pour mono-utilisateur

### Métriques proposées
```java
@Timed(value = "alexandria.search",
       description = "Search latency",
       extraTags = {"phase", "embedding"})
public EmbeddingResponse embed(String query) { ... }

@Timed(value = "alexandria.search",
       extraTags = {"phase", "rerank"})
public RerankResponse rerank(List<String> docs) { ... }

@Timed(value = "alexandria.search",
       extraTags = {"phase", "total"})
public McpSearchResponse search(String query) { ... }
```

### Dashboards Grafana
- P50/P95/P99 latency par phase
- Throughput (searches/min)
- Error rate par type

### Déclencheur
- Passage en production multi-utilisateur
- Besoin de SLO/SLA monitoring
- Intégration avec infrastructure observabilité existante

---

## F17: .env.example

**Sévérité originale:** LOW
**Raison du report:** Projet personnel, docker-compose.yml suffit

### Contenu proposé
```bash
# .env.example - Copy to .env and customize

# PostgreSQL (pgvector)
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=alexandria
POSTGRES_USER=alexandria
POSTGRES_PASSWORD=changeme

# Infinity Embedding API (RunPod)
INFINITY_BASE_URL=http://localhost:7997
INFINITY_MODEL=BAAI/bge-m3

# Reranking
RERANK_BASE_URL=http://localhost:7997
RERANK_MODEL=BAAI/bge-reranker-v2-m3

# Application
ALEXANDRIA_PORT=8080
LOG_LEVEL=INFO
```

### Déclencheur
- Onboarding de contributeurs externes
- Open-source du projet

---

## F18: Tests Progress MCP

**Sévérité originale:** LOW
**Raison du report:** Test unitaire avec mock suffit pour MVP

### Tests proposés

**Test unitaire (MVP):**
```java
@Test
void ingestDocument_shouldReportProgress() {
    var mockContext = mock(McpSyncRequestContext.class);

    mcpTools.ingestDocument("/path/to/doc.md", mockContext);

    var inOrder = inOrder(mockContext);
    inOrder.verify(mockContext).progress(0.1, "Validating document");
    inOrder.verify(mockContext).progress(0.3, "Parsing content");
    inOrder.verify(mockContext).progress(0.5, "Splitting into chunks");
    inOrder.verify(mockContext).progress(0.7, "Generating embeddings");
    inOrder.verify(mockContext).progress(1.0, "Ingestion complete");
}
```

**Test E2E (POST-MVP):**
```java
@Test
void ingestViaHttpMcp_shouldStreamProgress() {
    // Client MCP HTTP
    // Capturer les notifications SSE de progress
    // Vérifier séquence 0.1 → 0.3 → 0.5 → 0.7 → 1.0
}
```

### Déclencheur
- Bugs sur progress reporting en production
- Intégration avec clients MCP autres que Claude Desktop

---

## Priorisation Post-MVP

| Priorité | Item | Effort estimé | Valeur |
|----------|------|---------------|--------|
| 1 | F15: Micrometer Metrics | Medium | High (si multi-user) |
| 2 | F6: Tests Concurrence | Medium | High (si multi-user) |
| 3 | F8: CLI Errors | Low | Medium |
| 4 | F18: Tests Progress E2E | Low | Low |
| 5 | F14: Tests HTML | Low | Low |
| 6 | F17: .env.example | Trivial | Low |

**Note:** F6 et F15 deviennent prioritaires si le projet évolue vers un usage multi-utilisateur.
