# Phase 1: Infrastructure - Context

**Gathered:** 2026-01-19
**Status:** Ready for planning

<domain>
## Phase Boundary

L'environnement de développement est prêt avec PostgreSQL 17, pgvector et Apache AGE configurés. Le projet Maven compile avec toutes les dépendances LangChain4j. Un test de connexion valide que pgvector et AGE répondent correctement.

</domain>

<decisions>
## Implementation Decisions

### Docker setup
- Persistance via bind mount `./data` (données visibles dans le filesystem projet)
- Port PostgreSQL standard 5432
- Initialisation du schéma via Liquibase (migrations versionnées)
- Tests utilisent le même conteneur avec un schéma séparé (isolation par schéma)

### Schema design
- Convention de nommage snake_case pour tous les objets PostgreSQL
- Embeddings intégrés directement dans la table chunks (une colonne `embedding vector(384)`)
- Identifiants UUID pour les documents
- Timestamps `created_at` et `updated_at` sur toutes les tables principales

### Project structure
- Architecture en couches avec Dependency Inversion pour les composants externes:
  ```
  API (entrée) → MCP handlers, REST controllers
  CORE (orchestration) → Services applicatifs
  INFRA (implémentations) → Repositories, clients API (derrière interfaces)
  ```
- Le core définit les interfaces, l'infra implémente
- Package racine: `fr.kalifazzia.alexandria`
- Single module Maven (un seul pom.xml)
- Tests organisés par type: `unit/` et `integration/` séparés, profils Maven différents

### Dev workflow
- Lancement via `docker compose up` (commandes Docker standard)
- Réinitialisation via Liquibase rollback (migrations contrôlées)
- Configuration via `application.yml` (format YAML)
- Script `healthcheck.sh` pour vérifier PG, pgvector, AGE sont up et fonctionnels

### Claude's Discretion
- Structure exacte des tables Liquibase changelog
- Paramètres de configuration pgvector (m, ef_construction pour HNSW)
- Organisation interne des sous-packages
- Détails du script healthcheck

</decisions>

<specifics>
## Specific Ideas

- L'architecture layered avec DI n'est PAS hexagonal complet — pas d'interfaces pour les points d'entrée (API), seulement pour l'infra externe
- Le but est flexibilité sur l'infra (changer de provider embedding) sans complexité excessive
- Projet solo, logique métier simple → cette approche suffit

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-infrastructure*
*Context gathered: 2026-01-19*
