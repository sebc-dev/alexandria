# Prompts de Recherche - Projet Alexandria (Java)

## Prompt de Projet (Contexte Global)

Copier ce prompt dans les "System Instructions" du projet Claude Desktop :

```
# Prompt de Recherche - Projet Alexandria RAG Server

## Instructions Système

Tu es un assistant de recherche technique spécialisé pour le projet Alexandria. Tes recherches doivent être précises, sourcées, et directement applicables à l'implémentation.

## Description du Projet

Alexandria est un serveur RAG (Retrieval-Augmented Generation) exposé via MCP (Model Context Protocol) pour Claude Code. Il permet de stocker et rechercher de la documentation technique via recherche sémantique, avec ingestion de documents markdown et reranking.

## Architecture Cible

- **Type**: Serveur MCP SSE + outils exposés à Claude Code
- **Pattern**: Architecture flat simplifiée (core/ + adapters/ + config/) - pas d'hexagonal, pas de DDD
- **Usage**: Mono-utilisateur, développeur utilisant Claude Code quotidiennement
- **Concurrence**: Virtual Threads (Project Loom) gérés par Spring Boot

## Stack Technique

| Composant | Choix | Justification |
|-----------|-------|---------------|
| Runtime | Java 25 | Virtual Threads matures, pattern matching, typage fort |
| Framework | Spring Boot 3.4+ | Écosystème mature, Spring AI MCP SDK officiel |
| MCP Transport | Spring AI MCP SDK (WebMVC SSE) | Transport SSE uniquement, simplicité |
| RAG Pipeline | Langchain4j 1.0.1 | Pipeline RAG mature (chunking, retrieval, reranking) |
| Database | PostgreSQL 18 + pgvector 0.8.1 | Robuste, halfvec support natif |
| Vector Type | halfvec (1024D) | 50% économie mémoire vs vector |
| Embeddings | BGE-M3 via Infinity/RunPod | 1024D, multilingue, 8K context |
| Reranker | bge-reranker-v2-m3 via Infinity | État de l'art, même endpoint |

## Structure du Projet

src/main/java/dev/alexandria/
├── core/
│   ├── Document.java                # POJO simple
│   ├── DocumentChunk.java           # Chunk avec metadata
│   └── RetrievalService.java        # Logique métier RAG
├── adapters/
│   ├── InfinityEmbeddingClient.java # Client Infinity (embeddings + rerank)
│   ├── PgVectorRepository.java      # Langchain4j EmbeddingStore wrapper
│   └── McpTools.java                # @Tool annotations Spring AI
├── config/
│   ├── LangchainConfig.java         # Beans Langchain4j
│   └── McpConfig.java               # Config MCP transport
└── AlexandriaApplication.java

## Points Clés Techniques

- **MCP Transport**: Spring AI MCP SDK - SSE via WebMVC, pas de stdio
- **Embeddings**: BGE-M3 (1024D) sur RunPod/Infinity, API OpenAI-compatible
- **Reranking**: bge-reranker-v2-m3 sur même endpoint Infinity
- **Chunking**: 450-500 tokens, 50-75 overlap, markdown-aware avec préservation code blocks
- **HNSW Index**: m=24, ef_construction=100, cosine similarity
- **Retrieval Pipeline**: Top-K candidats → rerank → Top-N résultats

## Contraintes Hardware (Self-hosted)

- CPU: Intel Core i5-4570 (4c/4t @ 3.2-3.6 GHz, Haswell 2013)
- RAM: 24 GB DDR3-1600
- Pas de GPU local (embeddings/reranking déportés sur RunPod L4)
- PostgreSQL config optimisée pour 24GB RAM mono-utilisateur

## Sources de Données

- Fichiers Markdown (documentation technique)
- Fichiers texte
- Format llms.txt / llms-full.txt (standard llmstxt.org)
- Volume: centaines de documents

## Dépendances Maven Principales

<!-- Spring AI MCP SDK -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-mcp-server-webmvc-spring-boot-starter</artifactId>
</dependency>

<!-- Langchain4j -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.0.1</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>1.0.1</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.0.1</version>
</dependency>

## Format de Réponse Attendu

Pour chaque recherche, structure ta réponse ainsi:

### [Sujet]

**Status**: Validé | À valider | À modifier | Bloquant

**Résumé**:
- Réponse concise à la question

**Détails**:
- Informations complètes trouvées
- Versions exactes avec dates de release
- Configurations recommandées
- Exemples de code si pertinent

**Impact sur Alexandria**:
- Ce que ça change pour le projet
- Décisions à prendre
- Modifications de la spec si nécessaire

**Sources**:
- [Nom](URL) - Description

## Règles de Recherche

1. Privilégie les sources officielles (docs Spring, Langchain4j, GitHub releases)
2. Vérifie les dates de release - nous sommes en janvier 2026
3. Indique clairement quand une information est incertaine ou non documentée
4. Si un choix technique est obsolète ou problématique, propose une alternative concrète
5. Donne des exemples de code/config Java quand pertinent
6. Sois précis sur les numéros de version et leur compatibilité
7. Pour les APIs, donne les signatures exactes (méthodes, paramètres, types)

## Date de Référence

Janvier 2026 - Vérifie que les versions mentionnées sont GA (General Availability) à cette date.
```

---

## Prompts de Recherche Individuels

### 1. Spring AI MCP SDK - @Tool Annotations

```
## Recherche: Spring AI MCP SDK - Annotations @Tool pour exposer des outils MCP

### Contexte
Je développe un serveur MCP en Java avec Spring AI MCP SDK. Je dois exposer des outils (tools) à Claude Code via transport SSE.

### Questions Précises
1. Quelle est la syntaxe exacte des annotations @Tool dans Spring AI MCP SDK ?
2. Comment définir les paramètres d'un tool (types, descriptions, required/optional) ?
3. Comment le SDK gère-t-il la sérialisation des réponses ?
4. Y a-t-il des annotations complémentaires (@ToolParam, @Description, etc.) ?
5. Comment enregistrer les tools dans le contexte MCP ?

### Exemple de Code Attendu
Montre-moi un exemple complet d'une classe avec 2-3 tools MCP annotés, incluant:
- Un tool avec paramètres simples (String, int)
- Un tool avec objet complexe en paramètre
- Un tool retournant une liste d'objets

### Sources à Consulter
- Documentation Spring AI MCP: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
- GitHub Spring AI: https://github.com/spring-projects/spring-ai
- Exemples officiels Spring AI MCP

### Format de Réponse
Donne-moi les signatures exactes des annotations avec tous leurs attributs, puis un exemple de code complet et fonctionnel.
```

---

### 2. Langchain4j pgvector - Support halfvec

```
## Recherche: Langchain4j pgvector - Support du type halfvec

### Contexte
J'utilise Langchain4j 1.0.1 avec PostgreSQL 18 et pgvector 0.8.1. Je veux utiliser le type halfvec (16-bit float) au lieu de vector (32-bit) pour économiser 50% de mémoire sur mes embeddings 1024D.

### Questions Précises
1. langchain4j-pgvector 1.0.1 supporte-t-il nativement le type halfvec ?
2. Si non, quel est le workaround ? (custom SQL, extension de PgVectorEmbeddingStore, etc.)
3. Comment configurer le PgVectorEmbeddingStore pour utiliser halfvec ?
4. Y a-t-il des limitations connues avec halfvec + Langchain4j ?
5. Quel driver JDBC utilise langchain4j-pgvector ? (pgjdbc, pgjdbc-ng, r2dbc ?)

### Code de Référence Actuel
// Ce que je veux faire
EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
    .host("localhost")
    .port(5432)
    .database("alexandria")
    .table("chunks")
    .dimension(1024)
    // .vectorType("halfvec") ← existe-t-il ?
    .build();

### Sources à Consulter
- Langchain4j pgvector docs: https://docs.langchain4j.dev/integrations/embedding-stores/pgvector/
- GitHub langchain4j-pgvector: https://github.com/langchain4j/langchain4j/tree/main/langchain4j-pgvector
- pgvector halfvec: https://github.com/pgvector/pgvector#halfvec

### Format de Réponse
Confirme le support ou non, avec code de configuration exact, et alternative si non supporté.
```

---

### 3. Langchain4j DocumentSplitter Markdown

```
## Recherche: Langchain4j DocumentSplitter - Support Markdown-aware

### Contexte
Je dois chunker des documents markdown techniques (documentation API, guides, specs) en préservant:
- Les frontières de code blocks (```)
- La hiérarchie des headers (H1 > H2 > H3)
- Les métadonnées breadcrumb dans chaque chunk

### Questions Précises
1. Langchain4j 1.0.1 a-t-il un DocumentSplitter markdown-aware natif ?
2. Sinon, quel splitter se rapproche le plus ? (RecursiveCharacterSplitter, TokenTextSplitter, etc.)
3. Comment préserver les code blocks intacts lors du split ?
4. Comment injecter des métadonnées (headers breadcrumb) dans chaque chunk ?
5. Quelle est l'API pour définir overlap et chunk size en tokens (pas en caractères) ?

### Comportement Attendu
# Guide API        ← H1
## Authentication  ← H2
### OAuth2         ← H3

```java
public class Auth {
    // Ne pas couper ce bloc
}
```

Chunk résultant devrait avoir metadata: {"breadcrumb": "Guide API > Authentication > OAuth2", "source": "api-guide.md"}

### Sources à Consulter
- Langchain4j docs splitters: https://docs.langchain4j.dev/
- GitHub DocumentSplitter implementations
- Exemples RAG markdown

### Format de Réponse
Liste des splitters disponibles, recommandation pour markdown, et exemple de code avec metadata injection.
```

---

### 4. Testcontainers pgvector 0.8.1

```
## Recherche: Testcontainers - Image Docker PostgreSQL + pgvector 0.8.1

### Contexte
J'ai besoin de tester mon EmbeddingStore avec PostgreSQL 18 + pgvector 0.8.1 en tests d'intégration via Testcontainers.

### Questions Précises
1. Existe-t-il une image Docker officielle pgvector avec version 0.8.1 ?
2. Quelle est l'image recommandée ? (pgvector/pgvector, ankane/pgvector, postgres + extension ?)
3. Comment configurer Testcontainers pour utiliser cette image ?
4. Faut-il exécuter CREATE EXTENSION vector manuellement ou c'est pré-installé ?
5. Support de halfvec dans l'image ?

### Configuration Testcontainers Attendue
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("???:???")
    .withDatabaseName("alexandria_test")
    // Config pour pgvector ?

### Sources à Consulter
- Docker Hub pgvector images
- Testcontainers PostgreSQL module: https://java.testcontainers.org/modules/databases/postgres/
- GitHub pgvector releases

### Format de Réponse
Image exacte recommandée avec tag, configuration Testcontainers complète, et script init si nécessaire.
```

---

### 5. Java 25 - Status GA Janvier 2026

```
## Recherche: Java 25 - Disponibilité et Status en Janvier 2026

### Contexte
Le projet cible Java 25 pour bénéficier des Virtual Threads matures et du pattern matching. Je dois confirmer que c'est réaliste pour janvier 2026.

### Questions Précises
1. Java 25 est-il GA (General Availability) en janvier 2026 ?
2. Si non, quelle est la date de release prévue ?
3. Quelle version LTS est disponible en janvier 2026 ? (Java 21 ? Java 25 si LTS ?)
4. Virtual Threads sont-ils stables dans la dernière LTS disponible ?
5. Spring Boot 3.4+ supporte-t-il Java 25 ?

### Calendrier de Référence
- Java 21 LTS: Septembre 2023
- Java 22: Mars 2024
- Java 23: Septembre 2024
- Java 24: Mars 2025
- Java 25: ???

### Impact
Si Java 25 n'est pas GA, dois-je cibler Java 21 LTS ou Java 24 ?

### Sources à Consulter
- Oracle Java roadmap
- OpenJDK release schedule
- Spring Boot compatibility matrix

### Format de Réponse
Confirmation du status Java 25, recommandation de version, et impact sur les dépendances Spring.
```

---

### 6. Infinity API - Reranking Endpoint Détaillé

```
## Recherche: Infinity Embedding Server - API Reranking Complète

### Contexte
J'utilise Infinity 0.0.77+ sur RunPod pour servir BGE-M3 (embeddings) et bge-reranker-v2-m3 (reranking). J'ai besoin des détails exacts de l'API rerank.

### Questions Précises
1. Quel est le format exact de la requête POST /rerank ?
2. Quels sont tous les paramètres disponibles ? (top_n, return_documents, etc.)
3. Format de la réponse ? (scores, indices, documents ?)
4. Limites ? (max documents par requête, max query length)
5. Le score est-il normalisé (0-1) ou raw logits ?

### Exemple de Requête
curl -X POST http://<runpod>/rerank \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "BAAI/bge-reranker-v2-m3",
    "query": "How to configure authentication?",
    "documents": ["doc1 content", "doc2 content", ...],
    "top_n": 5,
    "return_documents": false
  }'

### Sources à Consulter
- Infinity GitHub: https://github.com/michaelfeil/infinity
- Infinity API docs
- bge-reranker-v2-m3 model card

### Format de Réponse
Schema OpenAPI complet de l'endpoint /rerank avec tous les paramètres et exemple de réponse.
```

---

### 7. Schéma DB RAG - Best Practices pgvector

```
## Recherche: Schéma PostgreSQL optimal pour RAG avec pgvector

### Contexte
Je conçois le schéma de base de données pour stocker des chunks de documents avec leurs embeddings (halfvec 1024D) et métadonnées.

### Questions Précises
1. Quelle structure de tables recommandée ? (une table flat, ou sources + chunks séparées ?)
2. Quelles colonnes metadata sont essentielles pour un RAG efficace ?
3. Comment stocker les breadcrumb headers efficacement ?
4. Index sur metadata pour filtrage ? (GIN sur jsonb, B-tree sur colonnes ?)
5. Gestion des updates (upsert vs delete+insert) ?

### Contraintes
- halfvec(1024) pour embeddings
- Filtrage par source_file, par tags
- Reconstruction du contexte (chunks adjacents)
- Volume: ~10K-50K chunks

### Schéma de Départ
CREATE TABLE chunks (
    id bigserial PRIMARY KEY,
    content text NOT NULL,
    embedding halfvec(1024) NOT NULL,
    -- Quoi d'autre ?
);

### Sources à Consulter
- pgvector best practices
- Langchain4j PgVectorEmbeddingStore schema
- Articles RAG database design

### Format de Réponse
Schéma SQL complet recommandé avec justification de chaque colonne et index.
```

---

### 8. MCP Config Client - Claude Code

```
## Recherche: Configuration MCP côté Claude Code pour serveur SSE

### Contexte
Mon serveur MCP Java expose des tools via SSE sur http://localhost:8080. Je dois configurer Claude Code pour s'y connecter.

### Questions Précises
1. Où se trouve le fichier de config MCP pour Claude Code ? (~/.config/claude/ ?)
2. Quel est le format de config pour un serveur SSE (vs stdio) ?
3. Comment déclarer les tools disponibles côté client ?
4. Authentification supportée ? (headers, bearer token ?)
5. Y a-t-il un health check ou handshake initial ?

### Exemple de Config Attendu
{
  "mcpServers": {
    "alexandria": {
      "transport": "sse",
      "url": "http://localhost:8080/mcp",
      "tools": ["search", "ingest", "list_sources"]
    }
  }
}

### Sources à Consulter
- Claude Code MCP documentation
- Anthropic MCP specification: https://modelcontextprotocol.io/
- Exemples de serveurs MCP existants

### Format de Réponse
Fichier de config complet et fonctionnel, avec chemin exact et tous les paramètres disponibles.
```

---

### 9. Retry/Resilience Pattern - Client HTTP Java

```
## Recherche: Pattern Retry pour client HTTP en Java moderne (sans Spring Cloud)

### Contexte
Mon InfinityEmbeddingClient appelle une API externe (RunPod) qui peut avoir des erreurs transitoires. Je veux implémenter un retry simple sans dépendance lourde (pas de Resilience4j, pas de Spring Cloud).

### Questions Précises
1. Quel est le pattern retry recommandé en Java 21+ vanilla ?
2. Comment implémenter exponential backoff simplement ?
3. Quels HTTP status codes retry-er ? (429, 502, 503, 504 ?)
4. Timeout recommandé par requête pour embeddings batch ?
5. Spring Boot a-t-il un retry intégré léger ?

### Contraintes
- Pas de circuit breaker (over-engineering pour mono-utilisateur)
- Max 3 retries
- Backoff: 1s, 2s, 4s
- Timeout: configurable

### Code de Référence
// Ce que je veux éviter
@Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
public List<float[]> embed(List<String> texts) { ... }

// Ce que je cherche: solution plus légère

### Sources à Consulter
- Java HttpClient retry patterns
- Spring RestClient retry options
- Baeldung retry patterns

### Format de Réponse
Implémentation Java concise avec retry, backoff, et gestion des status codes, sans dépendance externe.
```

---

### 10. Format llms.txt - Parsing et Structure

```
## Recherche: Standard llms.txt - Format et Parsing

### Contexte
Alexandria doit ingérer des fichiers au format llms.txt (standard défini sur llmstxt.org). Je dois comprendre le format pour implémenter le parser.

### Questions Précises
1. Quelle est la structure exacte d'un fichier llms.txt ?
2. Différence entre llms.txt et llms-full.txt ?
3. Y a-t-il un schema formel ou juste des conventions ?
4. Quelles métadonnées extraire lors du parsing ?
5. Existe-t-il des parsers Java/JVM existants ?

### Exemple de Fichier
Montre-moi un exemple complet de llms.txt avec toutes les sections possibles.

### Sources à Consulter
- https://llmstxt.org/
- Exemples de llms.txt publics (projets open source)
- GitHub implémentations

### Format de Réponse
Spécification complète du format avec exemples, et recommandation pour le parsing en Java.
```

---

### 11. Spring AI MCP - Exposition SSE WebMVC

```
## Recherche: Spring AI MCP Server - Configuration SSE avec WebMVC

### Contexte
J'utilise spring-ai-mcp-server-webmvc-spring-boot-starter pour exposer mon serveur MCP via SSE. Je dois comprendre la configuration exacte.

### Questions Précises
1. Quel endpoint est exposé par défaut ? (/mcp, /sse, configurable ?)
2. Comment configurer le path de l'endpoint SSE ?
3. Comment les tools sont-ils découverts ? (scanning, registration manuelle ?)
4. Configuration CORS nécessaire pour Claude Code local ?
5. Logging/debugging des messages MCP ?

### Configuration Actuelle
# application.yaml
spring:
  ai:
    mcp:
      server:
        # Quelles options ?

### Sources à Consulter
- Spring AI MCP reference: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html
- GitHub spring-ai-mcp-server examples
- Spring AI autoconfiguration classes

### Format de Réponse
Configuration application.yaml complète, avec toutes les options disponibles documentées.
```

---

### 12. Langchain4j OpenAI Client - Compatibilité Infinity

```
## Recherche: Langchain4j OpenAI Client pour API compatible (Infinity)

### Contexte
Infinity expose une API OpenAI-compatible pour les embeddings (POST /v1/embeddings). Je veux utiliser le client Langchain4j OpenAI pour s'y connecter au lieu de créer un client custom.

### Questions Précises
1. langchain4j-open-ai peut-il pointer vers une URL custom (pas api.openai.com) ?
2. Comment configurer le base URL pour Infinity ?
3. Le client supporte-t-il les embeddings batch ?
4. Gestion de l'authentification (Bearer token) ?
5. Mapping du model name (BAAI/bge-m3 vs text-embedding-3-small) ?

### Code Attendu
EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
    .baseUrl("http://runpod-infinity:8080")  // possible ?
    .apiKey("runpod-api-key")
    .modelName("BAAI/bge-m3")
    .build();

### Sources à Consulter
- Langchain4j OpenAI integration docs
- GitHub langchain4j-open-ai source code
- Infinity OpenAI compatibility docs

### Format de Réponse
Configuration exacte pour utiliser le client OpenAI de Langchain4j avec Infinity, ou alternative si non compatible.
```

---

## Utilisation

1. **Créer un projet Claude Desktop** avec le prompt de projet comme System Instructions
2. **Lancer chaque recherche** en copiant le prompt individuel
3. **Consolider les résultats** dans la tech-spec
4. **Mettre à jour le status** de chaque point (Validé / À modifier / Bloquant)

## Tableau de Suivi

| # | Sujet | Priorité | Status |
|---|-------|----------|--------|
| 1 | Spring AI MCP - @Tool annotations | 🔴 Critique | ⏳ À faire |
| 2 | Langchain4j pgvector - halfvec | 🔴 Critique | ⏳ À faire |
| 3 | Langchain4j DocumentSplitter Markdown | 🔴 Critique | ⏳ À faire |
| 4 | Testcontainers pgvector 0.8.1 | 🟡 Moyenne | ⏳ À faire |
| 5 | Java 25 - Status GA | 🟡 Moyenne | ⏳ À faire |
| 6 | Infinity API - Reranking | 🟡 Moyenne | ⏳ À faire |
| 7 | Schéma DB RAG | 🔴 Critique | ⏳ À faire |
| 8 | MCP Config Client Claude Code | 🟡 Moyenne | ⏳ À faire |
| 9 | Retry Pattern HTTP Java | 🟢 Basse | ⏳ À faire |
| 10 | Format llms.txt | 🟢 Basse | ⏳ À faire |
| 11 | Spring AI MCP - Config SSE | 🔴 Critique | ⏳ À faire |
| 12 | Langchain4j OpenAI + Infinity | 🟡 Moyenne | ⏳ À faire |
