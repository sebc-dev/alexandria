# Stack Validation (2026-01-04)

| Composant | Version | Statut | Notes |
|-----------|---------|--------|-------|
| Java | 25 LTS (25.0.1) | ✅ GA | Support jusqu'en 2030, JEP 491 inclus. **25.0.2 prévu 20 jan 2026** |
| Spring Boot | 3.5.9 | ✅ GA | Dernière 3.x, support OSS jusqu'en juin 2026 |
| Spring Framework | 6.2.15 | ✅ GA | Inclus dans Boot 3.5.9, compatible Langchain4j |
| Spring AI MCP SDK | 1.1.2 | ✅ GA | HTTP Streamable transport, @McpTool annotations |
| MCP Java SDK | 0.17.0 | ✅ GA | Inclus via Spring AI 1.1.2, client McpSyncClient |
| Resilience4j | 2.3.0 | ✅ GA | @Retry, compatible Virtual Threads, metrics Micrometer |
| Langchain4j core | 1.10.0 | ✅ GA | BOM recommandé |
| langchain4j-pgvector | 1.10.0-beta18 | ⚠️ Beta | API stable depuis 0.31.0, crée IVFFlat par défaut |
| langchain4j-open-ai | 1.10.0 | ✅ GA | baseUrl custom supporté |
| langchain4j-spring-boot-starter | 1.10.0-beta18 | ⚠️ Beta | Compatible Boot 3.x uniquement |
| PostgreSQL | 18.1 | ✅ GA | Depuis 25 sept. 2025 |
| PostgreSQL JDBC | 42.7.4 | ✅ GA | Dernière version stable |
| pgvector | 0.8.1 | ✅ GA | Compatible PG13-18, hnsw.iterative_scan nouveau |
| Testcontainers | 2.0.3 | ⚠️ Non certifié Java 25 | Devrait fonctionner, préfixes modules changés |
| WireMock | 3.13.2 | ⚠️ Non certifié Java 25 | Devrait fonctionner, minimum Java 11 |

**Risques identifiés:**
1. langchain4j-pgvector en beta - API stable mais possible breaking changes
2. langchain4j-spring-boot-starter en beta - nécessite version explicite
3. **Java 25.0.2 patch sécurité** - Prévu 20 janvier 2026, mise à jour recommandée
4. Testcontainers/WireMock non certifiés Java 25 - Fonctionnent mais pas officiellement testés

**Pourquoi Spring Boot 3.5.x (pas 4.x):**
- Langchain4j 1.10.0 **incompatible** avec Boot 4.0 (Jackson 3, Jakarta EE 11)
- Spring AI 1.1.2 GA stable pour Boot 3.x (2.0.0-M1 pour Boot 4.x est milestone)
- Migration vers Boot 4.x bloquée tant que Langchain4j ne supporte pas Jackson 3

**Migration future vers Boot 4.x:**
- Attendre Langchain4j compatible Jackson 3 + Jakarta EE 11
- Suivre github.com/langchain4j/langchain4j-spring pour annonces
- Resilience4j 2.x compatible avec Spring Framework 7 (migration transparente)
