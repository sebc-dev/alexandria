# Prompts de Consolidation - Tech-Spec Alexandria

Ces prompts sont conçus pour être utilisés dans un Projet Claude Desktop avec le prompt de recherche Alexandria comme instructions système.

---

## 1. MCP Tools & API Contract

### 1.1 Définition des outils MCP exposés

```
Recherche les best practices et patterns pour définir des outils MCP avec Spring AI MCP SDK 1.1.2.

Questions spécifiques:
1. Quels outils MCP sont typiquement exposés par un serveur RAG? (search, ingest, list, delete?)
2. Quelles sont les conventions de nommage et signatures recommandées pour @McpTool?
3. Quels paramètres sont pertinents pour un outil de recherche sémantique? (query, limit, filters, threshold?)
4. Comment structurer les réponses pour une consommation optimale par Claude Code?

Propose une définition complète de l'interface McpTools.java avec:
- Liste des outils à exposer
- Signature de chaque méthode (paramètres, types, annotations)
- Records/DTOs pour les réponses
- Description et documentation de chaque outil

Contexte: serveur RAG mono-utilisateur pour documentation technique, usage via Claude Code.
```

### 1.2 Format de réponse des outils MCP

```
Recherche le format optimal pour les réponses des outils MCP destinés à Claude Code.

Questions:
1. Quel format Claude Code consomme-t-il le mieux? (JSON structuré, Markdown, texte brut?)
2. Comment formater les résultats de recherche pour maximiser l'utilité du contexte?
3. Quelle quantité de métadonnées inclure? (source, score, section, date?)
4. Comment gérer la troncature si les résultats sont trop longs?

Propose un format de réponse type pour:
- Résultats de recherche (liste de chunks avec métadonnées)
- Confirmation d'ingestion
- Liste des sources disponibles
- Messages d'erreur

Inclus des exemples JSON/texte concrets.
```

### 1.3 Gestion des erreurs MCP

```
Recherche comment gérer et communiquer les erreurs dans un serveur MCP Spring AI.

Questions:
1. Quel est le format d'erreur standard MCP? (code, message, details?)
2. Comment Spring AI MCP SDK gère-t-il les exceptions?
3. Quelles erreurs spécifiques prévoir? (timeout Infinity, DB down, aucun résultat, query invalide?)
4. Comment Claude Code interprète-t-il les erreurs MCP?

Propose:
- Une stratégie de gestion d'erreurs (exceptions custom, @ExceptionHandler?)
- Le mapping exception Java → erreur MCP
- Des messages d'erreur clairs et actionnables pour l'utilisateur
```

---

## 2. Pipeline RAG - Paramètres

### 2.1 Paramètres Top-K, Top-N et seuils

```
Recherche les valeurs optimales pour les paramètres d'un pipeline RAG avec reranking.

Contexte Alexandria:
- Base: centaines de documents techniques (markdown)
- Chunks: 450-500 tokens
- Embeddings: BGE-M3 1024D
- Reranker: bge-reranker-v2-m3
- Usage: recherche documentation pour Claude Code

Questions:
1. Quel Top-K initial pour la recherche vectorielle? (candidats avant rerank)
2. Quel Top-N final après reranking?
3. Quel seuil de relevance_score minimum pour inclure un résultat?
4. Ces valeurs doivent-elles être configurables ou hardcodées?
5. Y a-t-il des benchmarks ou études sur ces paramètres avec BGE-M3 + bge-reranker?

Propose des valeurs concrètes avec justification, et indique si elles doivent être dans application.yml.
```

### 2.2 Stratégie si aucun résultat pertinent

```
Recherche les best practices quand une recherche RAG ne retourne aucun résultat pertinent.

Scénarios à couvrir:
1. Aucun document dans la base (base vide)
2. Aucun résultat vectoriel (query trop éloignée)
3. Tous les résultats sous le seuil de relevance après rerank
4. Query mal formée ou trop courte

Questions:
1. Quel message retourner dans chaque cas?
2. Faut-il retourner les meilleurs résultats même sous le seuil (avec avertissement)?
3. Faut-il suggérer des reformulations ou des termes alternatifs?
4. Comment différencier "pas de résultat" de "erreur système"?

Propose une stratégie de réponse pour chaque scénario.
```

### 2.3 Métadonnées des résultats

```
Recherche quelles métadonnées accompagner avec chaque chunk retourné par le RAG.

Contexte:
- Documents markdown avec front matter YAML possible
- Chunking avec breadcrumbs (H1 > H2 > H3)
- Usage par Claude Code pour augmenter le contexte

Questions:
1. Quelles métadonnées sont essentielles? (source, titre, section, score?)
2. Quelles métadonnées optionnelles sont utiles? (date, tags, auteur?)
3. Comment structurer les breadcrumbs dans les métadonnées?
4. Faut-il inclure le chemin du fichier source?
5. Le score de relevance doit-il être exposé à l'utilisateur?

Propose un schéma de métadonnées (record Java) avec justification de chaque champ.
```

---

## 3. Ingestion de Documents

### 3.1 Mécanisme de déclenchement de l'ingestion

```
Recherche comment déclencher l'ingestion de documents dans un serveur RAG MCP.

Options à évaluer:
1. Outil MCP `ingest` appelable par Claude Code
2. Endpoint REST séparé (POST /api/ingest)
3. Commande CLI (java -jar alexandria.jar ingest /path)
4. Watcher de répertoire (auto-détection de nouveaux fichiers)
5. Combinaison de plusieurs approches

Questions:
1. Quelle approche est la plus pratique pour un développeur solo?
2. L'ingestion doit-elle être synchrone ou asynchrone?
3. Comment gérer l'ingestion de gros volumes (batch)?
4. Faut-il un feedback de progression?

Contexte: usage mono-utilisateur, centaines de documents, mises à jour occasionnelles.

Propose l'approche recommandée avec justification et implémentation suggérée.
```

### 3.2 Gestion des mises à jour et doublons

```
Recherche les stratégies de gestion des mises à jour de documents dans un système RAG avec pgvector.

Scénarios:
1. Document modifié (même chemin, contenu différent)
2. Document renommé (chemin différent, contenu identique)
3. Document supprimé de la source
4. Ré-ingestion accidentelle du même fichier

Questions:
1. Comment identifier un document de manière unique? (chemin? hash? UUID?)
2. Stratégie de mise à jour: delete+insert ou upsert?
3. Faut-il détecter les changements (hash du contenu) avant ré-ingestion?
4. Faut-il garder un historique des versions?
5. Comment Langchain4j PgVectorEmbeddingStore gère-t-il les updates?

Propose une stratégie d'identification et de mise à jour avec le schéma de métadonnées associé.
```

### 3.3 Formats de documents supportés

```
Recherche les formats de documents à supporter pour l'ingestion dans Alexandria.

Formats candidats:
1. Markdown (.md) - prioritaire
2. Texte brut (.txt)
3. llms.txt / llms-full.txt
4. reStructuredText (.rst)
5. AsciiDoc (.adoc)
6. Code source avec docstrings (.py, .java, .ts)
7. HTML
8. PDF

Questions:
1. Quels formats sont essentiels pour la documentation technique?
2. Existe-t-il des parsers Java matures pour chaque format?
3. Quel effort d'implémentation pour chaque format?
4. Faut-il un système de plugins pour les formats?

Propose une liste priorisée (MVP vs futur) avec les dépendances Java nécessaires.
```

---

## 4. Parser llms.txt

### 4.1 Comportement du parser llms.txt

```
Recherche le standard llms.txt (llmstxt.org) et son implémentation pour Alexandria.

Questions:
1. Quelle est la structure exacte du format llms.txt?
2. Différence entre llms.txt et llms-full.txt?
3. Le parser doit-il fetcher les URLs référencées ou juste les stocker?
4. Quelles sections du standard sont obligatoires vs optionnelles?
5. Comment gérer les URLs invalides ou inaccessibles?

Propose:
- Spécification du comportement du LlmsTxtParser.java
- Gestion des URLs (fetch synchrone, asynchrone, ou référence seule)
- Structure de données pour représenter un llms.txt parsé
- Exemples de fichiers llms.txt valides

Référence: https://llmstxt.org/
```

---

## 5. AlexandriaMarkdownSplitter

### 5.1 Spécification du splitter markdown

```
Recherche les edge cases et comportements attendus pour un splitter markdown RAG.

Contexte Alexandria:
- Taille cible: 450-500 tokens
- Overlap: 50-75 tokens (10-15%)
- Préservation des frontières de code blocks
- Breadcrumbs headers (H1 > H2 > H3)

Edge cases à spécifier:
1. Code block > 500 tokens: split ou garder entier?
2. Table markdown > 500 tokens: préserver ou split?
3. Liste à puces longue: split entre items ou au milieu?
4. Front matter YAML: métadonnées ou premier chunk?
5. Liens et images: préservés inline ou extraits?
6. Headers imbriqués profonds (H4, H5, H6): inclus dans breadcrumb?

Propose une spécification détaillée du comportement pour chaque cas, avec exemples avant/après split.
```

---

## 6. Configuration & Environnement

### 6.1 Structure application.yml

```
Recherche et propose une structure application.yml complète pour Alexandria.

Sections à couvrir:
1. Server (port, context-path)
2. Spring AI MCP (transport SSE, endpoints)
3. Datasource PostgreSQL
4. Langchain4j (embedding model, pgvector store)
5. Infinity (base URL, API key, timeouts)
6. RAG pipeline (top-k, top-n, thresholds)
7. Retry (attempts, backoff)
8. Logging levels

Questions:
1. Quelles propriétés doivent être externalisées (env vars)?
2. Faut-il des profils Spring (dev, test, prod)?
3. Quels défauts sensés pour le développement local?
4. Comment sécuriser les secrets (API keys)?

Propose un fichier application.yml complet et commenté, avec un .env.example pour les variables sensibles.
```

### 6.2 Variables d'environnement

```
Recherche les best practices pour la configuration par variables d'environnement dans Spring Boot.

Variables à définir pour Alexandria:
1. INFINITY_BASE_URL - URL du serveur Infinity/RunPod
2. INFINITY_API_KEY - Clé API RunPod
3. DATABASE_URL ou SPRING_DATASOURCE_URL
4. DATABASE_USERNAME / DATABASE_PASSWORD

Questions:
1. Naming convention: SCREAMING_SNAKE_CASE ou spring.style.dots?
2. Comment Spring Boot mappe les env vars aux propriétés?
3. Faut-il un fichier .env pour le dev local? Quel loader?
4. Comment gérer les secrets en production? (Vault, K8s secrets?)

Propose la liste complète des variables avec leur mapping Spring et un .env.example.
```

---

## 7. Sécurité & Authentification

### 7.1 Authentification du serveur MCP

```
Recherche si/comment sécuriser un serveur MCP Spring AI.

Questions:
1. Le protocole MCP supporte-t-il l'authentification? (Bearer token, API key?)
2. Spring AI MCP SDK 1.1.2 a-t-il des options de sécurité intégrées?
3. Faut-il sécuriser les endpoints SSE (/sse, /mcp/message)?
4. Pour un usage local mono-utilisateur, la sécurité est-elle nécessaire?
5. Si le serveur est exposé sur le réseau, quelles protections minimales?

Propose une recommandation (sécurisé vs non sécurisé) avec justification pour le contexte Alexandria.
```

---

## 8. Observabilité & Monitoring

### 8.1 Logging et health checks

```
Recherche les patterns de logging et health checks pour un serveur RAG Spring Boot.

Questions logging:
1. Quels événements logger? (ingestion, recherche, erreurs, latences?)
2. Quel format de log? (JSON structuré pour parsing, ou human-readable?)
3. Quels niveaux par package? (INFO pour app, WARN pour libs?)
4. Faut-il un correlation ID pour tracer les requêtes?

Questions health checks:
1. Quels composants vérifier? (DB, Infinity, espace disque?)
2. Spring Actuator suffit-il?
3. Faut-il un endpoint custom pour l'état du RAG? (nombre de docs, dernier ingest?)

Propose une configuration logging (logback-spring.xml ou application.yml) et les health checks nécessaires.
```

---

## 9. Gestion des Erreurs

### 9.1 Comportement en cas d'indisponibilité

```
Recherche les stratégies de résilience quand les dépendances externes sont indisponibles.

Scénarios Alexandria:
1. Infinity (RunPod) down ou timeout après 4 retries
2. PostgreSQL indisponible
3. Infinity répond mais avec erreurs (rate limit, quota)
4. Latence excessive (> 10s pour une recherche)

Questions:
1. Après échec des retries, quel message retourner à Claude Code?
2. Faut-il un mode dégradé? (recherche sans rerank si reranker down?)
3. Comment logger ces événements pour diagnostic?
4. Faut-il un circuit breaker (Resilience4j) ou spring-retry suffit?

Propose le comportement attendu pour chaque scénario et le message d'erreur associé.
```

### 9.2 Timeouts globaux

```
Recherche les timeouts appropriés pour un pipeline RAG avec services externes.

Étapes du pipeline Alexandria:
1. Embedding de la query (appel Infinity)
2. Recherche vectorielle pgvector
3. Reranking (appel Infinity)
4. Assemblage de la réponse

Questions:
1. Quel timeout pour chaque étape individuellement?
2. Quel timeout global pour une recherche complète?
3. Comment configurer les timeouts dans Spring Boot / RestClient?
4. Le client MCP a-t-il ses propres timeouts?

Propose des valeurs de timeout avec justification et la configuration Spring correspondante.
```

---

## 10. Déploiement & Opérations

### 10.1 Packaging et déploiement

```
Recherche les options de packaging pour Alexandria.

Options:
1. JAR exécutable (java -jar)
2. Image Docker
3. Les deux avec multi-stage build

Questions:
1. Quelle base image Docker pour Java 25? (eclipse-temurin:25-jre?)
2. Faut-il un Dockerfile multi-stage?
3. Comment gérer la config (env vars, volumes)?
4. Taille cible de l'image?
5. docker-compose pour le stack complet (app + postgres)?

Propose un Dockerfile et docker-compose.yml pour le développement local.
```

### 10.2 Initialisation de la base de données

```
Recherche comment initialiser le schéma PostgreSQL + pgvector pour Alexandria.

Questions:
1. Langchain4j PgVectorEmbeddingStore avec createTable=true crée-t-il tout automatiquement?
2. L'extension pgvector est-elle créée automatiquement? (CREATE EXTENSION vector)
3. L'index HNSW est-il créé par Langchain4j ou faut-il un script?
4. Faut-il Flyway/Liquibase pour la gestion de schéma?
5. Comment gérer les migrations futures?

Propose:
- La stratégie d'initialisation (auto vs scripts)
- Les scripts SQL si nécessaire
- La procédure de premier démarrage
```

---

## 11. Tests

### 11.1 Stratégie de test et fixtures

```
Recherche les best practices de test pour un serveur RAG Spring Boot.

Questions:
1. Quels types de tests? (unit, integration, E2E?)
2. Comment mocker Infinity avec WireMock? Quels stubs préparer?
3. Quel jeu de données de test? (documents markdown fixtures)
4. Comment tester le splitter markdown avec des edge cases?
5. Comment tester le flow MCP complet?

Propose:
- Structure des tests (packages, naming)
- Fixtures de documents markdown de test
- Stubs WireMock pour Infinity (embeddings, rerank)
- Test d'intégration type avec Testcontainers
```

### 11.2 Test E2E du flow MCP

```
Recherche comment tester end-to-end un serveur MCP Spring AI.

Questions:
1. Existe-t-il un client MCP de test dans Spring AI?
2. Comment simuler les appels que ferait Claude Code?
3. Faut-il un test avec le vrai Claude Code CLI?
4. Comment valider que les réponses sont bien formées?

Propose une stratégie de test E2E avec exemple de code de test.
```

---

## 12. Questions Transverses

### 12.1 Décisions d'architecture à valider

```
Valide les choix d'architecture suivants pour Alexandria et propose des ajustements si nécessaire.

Choix à valider:
1. Architecture flat (core/ + adapters/ + config/) vs packages par feature
2. Pas d'interfaces abstraites - adapters directs
3. POJOs simples vs records Java
4. spring-retry vs Resilience4j pour la résilience
5. RestClient vs WebClient pour les appels HTTP
6. Pas de cache (prévu pour version ultérieure)

Questions:
1. Ces choix sont-ils cohérents avec l'écosystème Spring Boot 3.5 / Langchain4j?
2. Y a-t-il des anti-patterns à éviter?
3. Quels compromis accepte-t-on avec ces choix?

Confirme ou propose des ajustements avec justification.
```

### 12.2 Checklist pré-implémentation

```
Génère une checklist de validation avant de commencer l'implémentation d'Alexandria.

La checklist doit couvrir:
1. Prérequis environnement (Java 25, Docker, PostgreSQL)
2. Accès aux services externes (RunPod, Infinity)
3. Décisions techniques tranchées
4. Contrats d'API définis
5. Schéma de données finalisé
6. Dépendances Maven vérifiées
7. Structure de projet créée

Pour chaque item, indique:
- Ce qui doit être vérifié/fait
- Comment le valider
- Bloquant ou non pour démarrer

Propose cette checklist au format markdown.
```

---

## 13. Validation Tech-Spec (R-TS)

### 13.1 Testcontainers 2.x - Artifact ID PostgreSQL (R-TS.1)

```
Recherche l'artifact ID correct pour le module PostgreSQL dans Testcontainers 2.0.3.

Contexte:
- La tech-spec Alexandria utilise `testcontainers-postgresql` comme artifact ID
- Testcontainers 2.x a changé la structure des modules depuis la version 1.x
- Le document mentionne "préfixes modules changés depuis 1.x"

Questions:
1. Quel est l'artifact ID exact pour PostgreSQL dans Testcontainers 2.0.3?
2. Le groupId reste-t-il `org.testcontainers`?
3. Y a-t-il un BOM Testcontainers 2.x à utiliser?
4. Quels autres modules ont changé de nom entre 1.x et 2.x?

Propose la dépendance Maven correcte avec version explicite.

Référence: https://java.testcontainers.org/ ou Maven Central
```

### 13.2 Spring AI MCP SDK - Client HTTP Streamable (R-TS.2)

```
Recherche le nom exact de la classe pour créer un client MCP HTTP Streamable dans Spring AI MCP SDK 1.1.2.

Contexte:
- La tech-spec Alexandria utilise `StreamableHttpMcpTransport.builder()` dans McpTestSupport.java
- Ce nom semble hypothétique et non vérifié
- Le transport HTTP Streamable est recommandé depuis MCP spec 2025-03-26

Questions:
1. Quelle est la classe exacte pour créer un client MCP HTTP Streamable dans Spring AI 1.1.2?
2. Quel est le package complet? (`org.springframework.ai.mcp.client.*`?)
3. Comment instancier ce transport pour les tests?
4. Y a-t-il des exemples dans la documentation Spring AI ou le repo GitHub?

Propose un snippet de code correct pour McpTestSupport.java avec les imports exacts.

Référence: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
GitHub: https://github.com/spring-projects/spring-ai
```

---

## Usage

1. Ouvrir Claude Desktop avec le projet Alexandria
2. S'assurer que le prompt système contient les instructions de recherche mises à jour
3. Copier-coller le prompt souhaité
4. Analyser la réponse et mettre à jour le tech-spec-wip.md
5. Marquer le point comme résolu dans l'analyse

## Ordre Recommandé

1. **Critique** : 1.1, 2.1, 3.1, 6.1, 10.2
2. **Important** : 1.2, 1.3, 2.2, 2.3, 3.2, 5.1, 9.1, 9.2
3. **Complémentaire** : 4.1, 6.2, 7.1, 8.1, 10.1, 11.1, 11.2
4. **Validation** : 12.1, 12.2
5. **Validation Tech-Spec** : 13.1 (R-TS.1), 13.2 (R-TS.2)
