# Code Review — phase-2/core-search

## Actions traitées

### 1. Fusionner V2 dans V1 et supprimer V2 — DONE
- Expression GIN corrigée dans V1, V2 supprimée, commentaire EmbeddingConfig mis à jour

### 2. Seuil RRF dans HybridSearchIT — DONE
- `0.3` → `0.05` dans `search_with_no_matching_content`

### 3. Tests unitaires manquants — DONE
- Ajout `search_passes_maxResults_to_embedding_store` (captor)
- Ajout `search_returns_empty_list_when_no_matches`

### 4. BaseIntegrationTest visibilité — DONE
- Package-private → `public` pour accès depuis sous-packages

## Issues GitHub créées

- [#6 — Harden search API before public exposure](https://github.com/sebc-dev/alexandria/issues/6)
  - maxResults borne haute, minScore configurable, nullabilité documentée
- [#7 — Support multilingual full-text search](https://github.com/sebc-dev/alexandria/issues/7)
  - Config `english` hardcodée → à revisiter si support multilingue

## Points positifs

- `SearchService` comme anti-corruption layer propre entre LangChain4j et le domaine
- Commentaire `CRITICAL` sur `.query()` nécessaire au mode hybride
- Injection par constructeur dans le service (testable, idiomatique)
- `ArgumentCaptor` dans SearchServiceTest pour prouver le contrat hybride
- Test explicite de la nullabilité des métadonnées
- HybridSearchIT : seed data bien choisie, isolation par `removeAll()`
- Assertions `.as("...")` pour des messages d'échec explicites en CI
