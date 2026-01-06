# Notes

- Format llms.txt: Standard défini sur https://llmstxt.org/
- Usage prévu: mono-utilisateur, développeur utilisant Claude Code quotidiennement
- Centaines de documents (taille typique de documentation technique)
- Recherches existantes dans `docs/research/` réutilisables (organisées par thème)
- Spring AI MCP reference: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
- Langchain4j pgvector: https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/

---

## Risques Acceptés

### Testcontainers 2.x + Java 25 *(F7 Remediation)*

**Risque:** Testcontainers 2.0.3 (décembre 2025) n'a pas de garantie officielle de compatibilité avec Java 25.

**Analyse:**
- Aucune issue connue à ce jour
- Le projet n'utilise pas de features exclusives Java 25 nécessitant support spécial
- La solution alternative proposée (H2) est invalide car H2 ne supporte pas pgvector

**Décision:** Risque accepté.

**Actions:**
- Surveiller les releases Testcontainers pour mises à jour de compatibilité
- Si problèmes rencontrés: downgrade temporaire vers Java 21 LTS pour les tests

**Mitigation:** Les tests d'intégration peuvent être désactivés en cas de blocage, les tests unitaires restent fonctionnels.
