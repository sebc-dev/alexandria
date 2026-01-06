# Annexes

## Dependances Maven a Ajouter

```xml
<!-- Pour F2: Rate Limiting -->
<!-- Deja inclus via resilience4j-spring-boot3 -->

<!-- Pour F3: Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Pour F15: Metrics Prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

## Checklist Pre-Implementation

- [ ] Lire toutes les solutions proposees
- [ ] Identifier les interdependances (ex: F1 requiert F4 pour la verification)
- [ ] Planifier l'ordre d'implementation: CRITICAL > HIGH > MEDIUM > LOW
- [ ] Preparer les environnements de test (Testcontainers, WireMock)

## Ordre d'Implementation Recommande

1. **F3** (Auth) - Securite d'abord
2. **F1** (Transaction) - Integrite des donnees
3. **F2** (Rate Limiting) - Protection service
4. **F4** (getStoredDocumentInfo) - Prerequis pour F1 complet
5. **F5** (Logging) - Observabilite
6. **F15** (Metrics) - Observabilite
7. **F11** (Graceful Shutdown) - Fiabilite
8. **F12** (HNSW params) - Performance
9. **F6** (Concurrent tests) - Validation F1
10. Reste par ordre de severite
