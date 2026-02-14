# Stack RAG Java 21 self-hosted : guide complet pour alimenter Claude Code

**La stack optimale pour un développeur solo sous contrainte CPU-only et budget zéro est LangChain4j + pgvector + ONNX Runtime in-process + serveur MCP Java, le tout orchestré via Spring Boot 3.5 sur Java 21.** Cette combinaison offre le meilleur ratio performance/complexité opérationnelle : zéro service externe pour les embeddings, réutilisation de PostgreSQL existant, et exposition directe vers Claude Code via le SDK MCP officiel. L'ensemble tient confortablement dans **10-14 Go de RAM** sur les 24 Go disponibles, laissant de la marge pour Ollama en sidecar si nécessaire. L'écosystème Java RAG a atteint la maturité production en mai 2025 avec les sorties simultanées de LangChain4j 1.0 et Spring AI 1.0 GA, mais reste en retard de **12-18 mois** sur Python pour les techniques RAG avancées (GraphRAG, Self-RAG, évaluation automatisée).

---

## Matrice de décision par composant

| Composant | Recommandation principale | Alternative | Critère décisif | Confiance |
|-----------|--------------------------|-------------|-----------------|-----------|
| **Embedding model** | bge-small-en-v1.5-q (ONNX, 384d, ~65 Mo) | nomic-embed-text-v1.5 via Ollama (768d, 8192 tokens) | Rapport qualité/vitesse sur CPU | Élevée |
| **Runtime embedding** | LangChain4j in-process ONNX | Ollama sidecar (API HTTP) | Zero dépendance externe, 1 ligne de code | Élevée |
| **Base vectorielle** | pgvector 0.8.x (PostgreSQL) | Qdrant (Docker, client Java officiel) | Zéro infra supplémentaire, SQL natif | Élevée |
| **Orchestration RAG** | LangChain4j 1.0+ | Spring AI 1.0 GA | Plus de features RAG, embeddings in-process | Élevée |
| **Framework web** | Spring Boot 3.5 | Quarkus 3.x | Écosystème, starters MCP, familiarité | Élevée |
| **Chunking** | LangChain4j recursive + flexmark-java AST | Spring AI TokenTextSplitter | Chunking markdown-aware critique pour la doc technique | Moyenne |
| **Crawling** | JSoup + virtual threads custom | Firecrawl self-hosted (sites JS) | Contrôle total, zero dépendance pour sites statiques | Élevée |
| **Parsing Markdown** | flexmark-java 0.64.8 | commonmark-java 0.24.0 | AST source-level pour chunking intelligent | Moyenne |
| **Détection changements** | JGit 6.10+ | Hash SHA-256 pour sites crawlés | La plupart des docs sont sur GitHub | Élevée |
| **Serveur MCP** | SDK MCP Java officiel 0.17.x + Spring Boot | SDK Kotlin MCP (JetBrains) | Transport stdio pour Claude Code local | Élevée |

---

## Modèles d'embedding sur CPU : le compromis taille-qualité

Pour une machine 4 cores / 24 Go sans GPU, les modèles utilisables se limitent à ceux de **moins de 600M paramètres**. Le sweet spot se situe dans la gamme des modèles "small" et "base" de la famille BERT.

**bge-small-en-v1.5 quantifié** est le choix recommandé pour démarrer : **~65 Mo** en ONNX quantifié INT8, **384 dimensions**, score MTEB d'environ 59, et surtout il est **prépackagé dans LangChain4j** en tant que dépendance Maven unique. L'intégration se fait en une ligne :

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-embeddings-bge-small-en-v15-q</artifactId>
    <version>1.0.0</version>
</dependency>
```

Pour davantage de qualité, **bge-base-en-v1.5** (109M paramètres, 768 dimensions, ~430 Mo, MTEB ~63) offre un gain significatif de précision au retrieval. Pour les documents techniques longs, **nomic-embed-text-v1.5** se distingue avec un contexte de **8192 tokens** (vs 512 pour les BGE/E5), une architecture Matryoshka permettant d'ajuster les dimensions (64 à 768), et un poids de ~530 Mo.

| Modèle | Paramètres | Dimensions | Taille ONNX | MTEB ~avg | Contexte max | Licence |
|--------|-----------|------------|-------------|-----------|-------------|---------|
| all-MiniLM-L6-v2 | 22M | 384 | ~80 Mo | ~56 | 256 | Apache 2.0 |
| **bge-small-en-v1.5** | 33M | 384 | ~130 Mo | ~59 | 512 | MIT |
| bge-base-en-v1.5 | 109M | 768 | ~430 Mo | ~63 | 512 | MIT |
| **nomic-embed-text-v1.5** | 137M | 768 | ~530 Mo | ~62 | 8192 | Apache 2.0 |
| bge-large-en-v1.5 | 335M | 1024 | ~1,3 Go | ~64 | 512 | MIT |

*Scores MTEB approximatifs, variant selon la version du leaderboard (v1 vs v2). [INCERTAIN] Les scores de retrieval spécifiques diffèrent des moyennes globales.*

Les runtimes Java pour exécuter ces modèles se répartissent en deux catégories. **L'approche in-process via ONNX Runtime** (com.microsoft.onnxruntime:onnxruntime:1.23.2) offre la latence la plus basse — sous la milliseconde d'overhead — et c'est exactement ce que LangChain4j encapsule dans ses modules `langchain4j-embeddings-*`. L'approche **Ollama en sidecar** ajoute 1-5 ms de latence HTTP par requête, ce qui devient significatif en batch sur des milliers de chunks, mais offre en contrepartie une gestion simplifiée des modèles et un accès à `nomic-embed-text` sans conversion ONNX manuelle.

**Point critique sur les virtual threads** : l'inférence ONNX est CPU-bound, donc les virtual threads n'accélèrent **pas** le calcul d'embeddings lui-même. En revanche, elles sont essentielles pour le pipeline global — crawling parallèle, appels HTTP vers Ollama, écriture en base — où elles permettent des milliers de tâches I/O concurrentes avec un overhead mémoire négligeable (~quelques Ko par thread virtuel vs ~1 Mo par thread plateforme).

---

## pgvector domine pour un développeur solo sur PostgreSQL

Le choix de la base vectorielle est le plus tranché de cette analyse. **pgvector 0.8.x sur PostgreSQL est supérieur** à toute base vectorielle dédiée pour ce profil d'utilisation, et ce pour une raison simple : zéro infrastructure supplémentaire.

La version 0.8.0 (octobre 2024) a résolu la plus grande faiblesse historique de pgvector : le problème de sur-filtrage. Les **iterative index scans** permettent désormais de combiner efficacement une recherche HNSW avec des filtres SQL classiques — exactement ce dont un système RAG de documentation a besoin pour filtrer par framework, version ou type de contenu. Un simple `WHERE framework = 'Spring Boot' AND version >= '3.0'` couplé à une recherche vectorielle fonctionne naturellement, sans API propriétaire à apprendre.

**Estimation mémoire pour 1M de chunks** : à 384 dimensions avec halfvec (16-bit), comptez environ **3-4 Go** pour les vecteurs + index HNSW. À 768 dimensions en halfvec, environ **5-8 Go**. Dans les deux cas, le budget de 24 Go est largement respecté.

La recherche hybride (vecteur + mots-clés) se réalise via le **full-text search natif de PostgreSQL** (`tsvector/tsquery` + index GIN) combiné avec la recherche vectorielle, fusionné par Reciprocal Rank Fusion (RRF) en SQL pur. Pour une implémentation BM25 plus fidèle, l'extension **ParadeDB pg_search** (basée sur Tantivy) est disponible, mais PostgreSQL FTS suffit pour commencer.

```sql
-- Schema recommandé
CREATE TABLE document_chunks (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    embedding halfvec(384),  -- halfvec pour 57% d'économie de stockage
    framework VARCHAR(50),
    framework_version VARCHAR(20),
    content_type VARCHAR(30),  -- 'api-reference', 'tutorial', 'changelog', 'conceptual'
    section_path TEXT,  -- 'Getting Started > Installation > Maven'
    source_url TEXT,
    metadata JSONB,
    tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED
);

CREATE INDEX ON document_chunks USING hnsw (embedding halfvec_cosine_ops);
CREATE INDEX ON document_chunks (framework);
CREATE INDEX ON document_chunks USING gin(tsv);
```

**Qdrant** (client Java officiel `io.qdrant:client:1.16.2`) reste la meilleure alternative si les limites de pgvector sont atteintes. Écrit en Rust, il offre une excellente efficacité mémoire (2-4 Go pour 1M vecteurs avec quantification) et un filtrage de payload intégré à la recherche vectorielle. Le surcoût est un conteneur Docker supplémentaire et un backup séparé.

**Milvus est à éviter** pour ce cas d'usage : ses dépendances (etcd + MinIO + Pulsar) consomment **12-20 Go de RAM** en standalone, ne laissant aucune marge dans le budget de 24 Go. **Weaviate** offre la meilleure recherche hybride native, mais son client Java v6 est explicitement labellisé **"BETA — not recommended for production"**. **ChromaDB et LanceDB** n'ont pas de clients Java de qualité production.

---

## LangChain4j l'emporte sur Spring AI pour le RAG spécifiquement

Les deux frameworks ont atteint la version 1.0 GA en mai 2025 et sont soutenus par des acteurs majeurs (Red Hat/Microsoft pour LangChain4j, Broadcom/VMware pour Spring AI). Pour un pipeline RAG de documentation technique avec contraintes CPU-only, **LangChain4j présente quatre avantages décisifs** :

**1. Embeddings in-process sans service externe.** LangChain4j est le seul framework Java offrant des modèles d'embedding prépackagés en ONNX via Maven. Spring AI nécessite soit un service Ollama externe, soit une configuration manuelle de `TransformersEmbeddingModel` qui souffre de problèmes de compatibilité documentés avec certains modèles.

**2. Fonctionnalités RAG avancées plus complètes.** LangChain4j propose nativement le `ReRankingContentAggregator`, le `DefaultContentAggregator` avec **Reciprocal Rank Fusion**, le `LanguageModelQueryRouter` et l'`ExpandingQueryTransformer`. Spring AI offre des équivalents pour la transformation de requêtes mais manque de RRF natif et de re-ranking intégré.

**3. Adoption explicite des virtual threads.** Le PR #3541 de Mario Fusco (Red Hat) a fait des virtual threads l'exécuteur par défaut de LangChain4j pour les appels d'outils et les invocations de modèles — un avantage concret pour le throughput I/O.

**4. Indépendance du framework web.** LangChain4j fonctionne avec Spring Boot, Quarkus, Helidon, Micronaut ou en standalone. Spring AI est intrinsèquement lié à l'écosystème Spring.

Spring AI conserve des avantages propres : **l'observabilité** (Micrometer/Actuator), l'**auto-configuration** Spring Boot, et une **abstraction ETL structurée**. Pour un projet où l'observabilité est critique ou où l'équipe est purement Spring, Spring AI reste excellent.

| Critère | LangChain4j | Spring AI |
|---------|-------------|-----------|
| GitHub stars | ~10 700 | ~7 200 |
| Embeddings in-process (ONNX) | ✅ Prépackagés Maven | ⚠️ Manuel, problèmes connus |
| Embedding stores | 30+ | 20+ |
| RRF natif | ✅ | ❌ |
| Re-ranking intégré | ✅ | ⚠️ Manuel |
| Query routing | ✅ LLM-based | ❌ |
| Support Claude/Anthropic | ✅ Complet | ✅ Complet |
| Virtual threads | ✅ Exécuteur par défaut | ✅ Via config Spring Boot |
| Observabilité | ⚠️ Expérimental | ✅ Micrometer/Actuator |
| Auto-configuration | ⚠️ Starters Spring Boot en cours | ✅ Native |

**La stratégie recommandée** : utiliser LangChain4j pour le cœur du pipeline RAG (embeddings, retrieval, chunking) dans une application Spring Boot qui bénéficie de l'auto-configuration, des actuators et du starter MCP de Spring AI. Les deux frameworks cohabitent sans conflit.

---

## Pipeline d'ingestion : du crawling au vecteur en Java 21

Le pipeline d'ingestion comporte six étapes, chacune exploitant les capacités de Java 21.

**Étape 1 — Crawling.** Pour les sites de documentation statiques (Docusaurus, Starlight, ReadTheDocs), un crawler custom avec **JSoup 1.18.1 + virtual threads** est la solution la plus légère et contrôlable. La plupart des documentations de frameworks cibles sont du HTML pré-rendu où JSoup excelle. Pour les sites nécessitant un rendu JavaScript, **Firecrawl self-hosted** (AGPL-3.0, Docker + Redis) offre un SDK Java (`com.github.firecrawl:firecrawl-java-sdk:2.0` via JitPack) et produit directement du Markdown nettoyé. **Crawler4j est à éviter** — effectivement abandonné depuis plus de 5 ans.

**Étape 2 — Extraction.** La chaîne d'extraction s'adapte au format source : **JSoup** pour le HTML (sélecteurs CSS ciblant `<article>`, `<main>`, `.markdown`), **flexmark-java 0.64.8** pour le Markdown/MDX (AST source-level avec tracking des offsets), **Apache Tika 3.2.0** pour les PDF et formats exotiques. Pour les repos GitHub de documentation, **JGit 6.10+** permet le clonage et la lecture directe des fichiers `.md`.

**Étape 3 — Chunking markdown-aware.** C'est l'étape la plus critique pour la qualité du RAG. La documentation technique a une structure hiérarchique naturelle (titres, sous-titres, blocs de code) qu'un chunking naïf détruit. L'approche recommandée combine le parsing AST de flexmark-java avec `DocumentSplitters.recursive()` de LangChain4j :

- Découper aux frontières de titres (`#`, `##`, `###`)
- **Ne jamais couper un bloc de code** (``` ... ```)
- Conserver les tableaux comme unités atomiques
- Taille cible : **400-512 tokens** avec **50-100 tokens de chevauchement**
- Attacher le chemin hiérarchique comme métadonnée (`"Getting Started > Installation > Maven"`)

Pour les API references (beaucoup de petites entrées), réduire à 200-300 tokens par chunk. Pour les tutoriels narratifs, élargir à 500-800 tokens.

**Étape 4 — Enrichissement des métadonnées.** Chaque chunk doit porter un record Java 21 riche :

```java
record ChunkMetadata(
    String frameworkName,
    String frameworkVersion,
    String sectionPath,
    String contentType,    // "api-reference", "tutorial", "changelog", "conceptual"
    String sourceUrl,
    String language,       // pour les blocs de code
    String contentHash     // SHA-256 pour les mises à jour incrémentales
) {}
```

**Étape 5 — Embedding batch.** Avec LangChain4j in-process ONNX, l'embedding est synchrone et parallélisé automatiquement sur les cœurs CPU. Pour 1M de chunks de 400 tokens avec bge-small-en-v1.5 quantifié sur 4 cœurs, l'estimation est de **50-200 embeddings/seconde**, soit **1,5 à 5,5 heures** pour l'indexation initiale complète. [INCERTAIN — estimation basée sur des extrapolations de benchmarks GPU, les benchmarks CPU purs sont rares.]

**Étape 6 — Mise à jour incrémentale.** JGit surveille les commits des repos de documentation. À chaque pull, un `DiffFormatter` identifie les fichiers modifiés, les anciens chunks sont supprimés du vector store, et seuls les fichiers changés traversent le pipeline. Pour les sites crawlés, un hash SHA-256 du contenu détecte les modifications.

---

## Serveur MCP Java : le pont vers Claude Code

L'intégration avec Claude Code se fait via un serveur MCP exposant le pipeline RAG comme outils appelables. Le **SDK MCP Java officiel** (`io.modelcontextprotocol.sdk:mcp:0.17.2`, licence MIT) est développé en collaboration entre Anthropic et l'équipe Spring AI. Il évolue rapidement — 18 releases en 10 mois — mais fournit une base solide.

**Le transport stdio est recommandé** pour une utilisation locale avec Claude Code. Le serveur est un simple JAR que Claude Code lance via `java -jar rag-mcp-server.jar`. Configuration dans `.mcp.json` à la racine du projet :

```json
{
  "mcpServers": {
    "rag-docs": {
      "command": "java",
      "args": ["-jar", "tools/rag-mcp-server.jar"],
      "env": {
        "POSTGRES_URL": "jdbc:postgresql://localhost:5432/ragdb"
      }
    }
  }
}
```

**Trois outils MCP suffisent** pour couvrir les besoins principaux :

- `search_docs` — recherche sémantique avec filtres optionnels par framework/version
- `list_frameworks` — liste des frameworks indexés et leurs versions
- `get_code_examples` — recherche spécialisée dans les exemples de code

Le budget tokens par réponse MCP doit viser **1 500-3 000 tokens** (3-5 chunks pertinents avec métadonnées). Claude Code émet un avertissement à 10 000 tokens et tronque à 25 000 par défaut.

Pour l'implémentation, deux patterns coexistent. **Le pattern Spring Boot** utilise les starters `spring-ai-starter-mcp-server` avec l'annotation `@Tool` pour une exposition déclarative des outils RAG. **Le pattern standalone** utilise le SDK MCP brut avec `McpServer.sync()` et `StdioServerTransportProvider` — plus léger, démarrage plus rapide, idéal pour un processus stdio.

**L'alternative CLAUDE.md** reste complémentaire : y placer les conventions de projet stables, les commandes de build, et des pointeurs vers les fichiers générés statiquement par le pipeline RAG. CLAUDE.md pour le contexte permanent, MCP pour la recherche dynamique à la demande.

---

## Architecture recommandée avec flux de données

```
┌─────────────────────────────────────────────────────────────────┐
│                    SOURCES DE DOCUMENTATION                      │
│  GitHub repos (JGit)  │  Sites HTML (JSoup)  │  PDFs (Tika)    │
└──────────┬────────────┴──────────┬───────────┴────────┬────────┘
           │                       │                     │
           ▼                       ▼                     ▼
┌─────────────────────────────────────────────────────────────────┐
│              PIPELINE D'INGESTION (Java 21 + Virtual Threads)    │
│                                                                  │
│  Crawl ──▶ Extract ──▶ Clean ──▶ Chunk (flexmark AST) ──▶      │
│            (JSoup)     (regex)   (recursive 400-512 tok)         │
│                                                                  │
│  ──▶ Embed (LangChain4j ONNX in-process, bge-small-en-v1.5-q) │
│  ──▶ Index (pgvector HNSW + métadonnées SQL + FTS GIN)          │
│                                                                  │
│  Scheduler: JGit diff + hash-based ──▶ mise à jour incrémentale │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│              PostgreSQL 16 + pgvector 0.8.x                      │
│                                                                  │
│  document_chunks (halfvec(384), HNSW, GIN FTS, B-tree filters)  │
│  frameworks_registry (framework, version, last_updated, stats)   │
│  ingestion_state (source, last_commit, last_crawl, status)       │
└──────────────────────────┬──────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────┐
│         SERVEUR RAG (Spring Boot 3.5 + LangChain4j 1.0+)        │
│                                                                  │
│  ContentRetriever (vecteur + FTS + RRF)                          │
│  QueryTransformer (expansion, compression)                       │
│  MetadataFilter (framework, version, content_type)               │
│                                                                  │
│  Exposition :                                                    │
│  ├── MCP Server (stdio) ──▶ Claude Code                         │
│  ├── REST API ──▶ interface web locale (optionnel)               │
│  └── CLI ──▶ génération CLAUDE.md statique                       │
└─────────────────────────────────────────────────────────────────┘
```

**Estimation des ressources serveur :**

| Composant | RAM | CPU | Disque |
|-----------|-----|-----|--------|
| PostgreSQL + pgvector (1M chunks, 384d halfvec) | 4-6 Go | Partagé | 4-6 Go |
| JVM Application (Spring Boot + LangChain4j + ONNX) | 1-2 Go | Partagé | 0,5 Go |
| Modèle embedding ONNX (bge-small-en-v1.5-q) | ~0,2 Go | Partagé | 0,1 Go |
| Ollama (optionnel, chat 3B) | 2-3 Go | Partagé | 3-5 Go |
| OS + buffers | 2-3 Go | — | — |
| **Total** | **~10-14 Go** | **4 cœurs** | **~8-12 Go** |

---

## Plan d'implémentation pour un développeur solo

**Phase 1 — MVP fonctionnel (1-2 semaines)**

1. Installer PostgreSQL 16 + pgvector 0.8.x, créer le schéma avec halfvec
2. Créer un projet Spring Boot 3.5 avec les dépendances : `langchain4j`, `langchain4j-embeddings-bge-small-en-v15-q`, `langchain4j-pgvector`
3. Implémenter un ingestion manuelle : charger 1-2 documentations (ex: LangChain4j + Spring Boot) depuis les repos GitHub via JGit
4. Chunking basique avec `DocumentSplitters.recursive(500, 50)`
5. Recherche vectorielle simple via `EmbeddingStoreContentRetriever`
6. Tester via un endpoint REST local

**Phase 2 — Serveur MCP + Claude Code (1 semaine)**

7. Ajouter `spring-ai-starter-mcp-server` ou le SDK MCP Java brut
8. Exposer `search_docs`, `list_frameworks`, `get_code_examples` comme outils MCP
9. Configurer `.mcp.json` dans le projet, tester avec Claude Code
10. Itérer sur la qualité des résultats retournés (taille, formatage, métadonnées)

**Phase 3 — Pipeline d'ingestion robuste (2-3 semaines)**

11. Crawler custom JSoup + virtual threads pour les sites de documentation HTML
12. Parser markdown-aware avec flexmark-java pour les repos GitHub
13. Enrichissement des métadonnées (framework, version, section path, content type)
14. Mise à jour incrémentale via JGit (diff par commit)
15. Scheduler périodique (cron ou Spring Scheduler)

**Phase 4 — Qualité de retrieval (1-2 semaines)**

16. Recherche hybride : combiner pgvector + PostgreSQL FTS via RRF en SQL
17. Filtrage par métadonnées dans les requêtes MCP
18. Évaluation manuelle : constituer un jeu de 50 questions test, mesurer la pertinence
19. Ajuster chunk size, overlap, et paramètres HNSW si nécessaire
20. Optionnel : upgrader vers bge-base-en-v1.5 ou nomic-embed-text-v1.5 via Ollama si la qualité 384d est insuffisante

**Phase 5 — Montée en échelle (continu)**

21. Ajouter progressivement les 10-30 frameworks cibles
22. Monitorer les performances (temps de requête, mémoire pgvector, taille des index)
23. Explorer le re-ranking si la pertinence plafonne
24. Considérer Qdrant en remplacement de pgvector uniquement si les performances deviennent un goulot

---

## Pièges et anti-patterns documentés par la communauté

**Le chunking naïf est le piège n°1.** Selon les retours de la communauté RAG, **80% des échecs proviennent des décisions de chunking**, pas du retrieval ni de la génération. Un `split("\n\n")` basique sur de la documentation technique coupe les blocs de code, orpheline les tableaux et détruit la hiérarchie des sections. Le chunking markdown-aware via AST n'est pas un luxe — c'est une nécessité.

**La sur-ingénierie précoce tue la productivité solo.** Implémenter GraphRAG, RAPTOR ou Agentic RAG avant d'avoir un pipeline basique fonctionnel et évalué est un anti-pattern classique. La recherche vectorielle simple avec de bons chunks et des métadonnées couvre **80-90%** des cas d'usage pour de la documentation technique.

**Les virtual threads et les blocs `synchronized` ne font pas bon ménage.** Si le code interagissant avec ONNX Runtime, le driver JDBC ou des bibliothèques tierces utilise `synchronized`, les threads virtuels seront "pinned" sur les carrier threads, annulant leurs bénéfices. Monitorer avec `-Djdk.tracePinnedThreads=true` et remplacer par `ReentrantLock`.

**La mémoire native ONNX échappe au heap Java.** Un modèle ONNX chargé in-process consomme de la mémoire native (off-heap). Les limites Docker et les métriques JVM standard ne la comptabilisent pas par défaut. Activer `-XX:NativeMemoryTracking=summary` et dimensionner les limites de conteneur en conséquence.

**Le changement de modèle d'embedding impose une réindexation complète.** Passer de bge-small (384d) à nomic-embed (768d) signifie recréer la table, les index et re-calculer tous les vecteurs. Documenter le choix du modèle et planifier cette migration comme un événement, pas comme une mise à jour anodine.

---

## L'écosystème Java RAG est-il suffisamment mature ?

**Oui, pour les fondamentaux. Non, pour les techniques avancées.** L'écosystème Java RAG est production-ready depuis mai 2025 pour les cas d'usage standard : recherche vectorielle, transformation de requêtes, chunking, embedding local. Microsoft rapporte des **centaines de clients** exécutant LangChain4j en production. Spring AI bénéficie du support de Broadcom/VMware.

Les **gaps réels** par rapport à Python concernent principalement :

- **Évaluation automatisée** : Python a RAGAS et DeepEval ; Java a Dokimos (très récent) et des évaluations basiques dans LangChain4j
- **Techniques RAG avancées** : HyDE, Parent Document Retriever, Self-Query, RAPTOR, GraphRAG sont absents ou nécessitent une implémentation custom en Java
- **Variété des modèles locaux** : `sentence-transformers` de Python offre un accès direct à des centaines de modèles ; Java nécessite la conversion ONNX
- **Re-ranking local** : Python a les cross-encoders via sentence-transformers ; Java s'appuie sur des API (Cohere) ou l'implémentation manuelle

**Le workaround le plus pragmatique** pour combler ces gaps est le pattern sidecar : un script Python minimal exposant une API HTTP pour les fonctionnalités absentes en Java (évaluation RAGAS, re-ranking cross-encoder), appelé depuis le pipeline Java. Mais pour le cas d'usage décrit — documentation technique alimentant Claude Code — les fonctionnalités Java existantes sont **largement suffisantes**.

[RETOUR COMMUNAUTAIRE] La communauté Java AI connaît une accélération visible : Devoxx 2024/2025, Spring I/O 2025 et JDConf 2025 ont tous présenté plusieurs talks RAG en Java. Le gap se réduit activement, avec un rythme de release de LangChain4j de plusieurs versions par mois.

**Condition de changement de recommandation** : si l'évaluation automatisée de la qualité RAG devient critique (mesure systématique de recall/MRR/F1), Python reste supérieur à court terme. Si des modèles d'embedding >1B paramètres deviennent nécessaires pour la qualité, un GPU sera requis indépendamment du langage.

---

## Conclusion : pragmatisme Java pour un RAG qui fonctionne

Ce rapport confirme qu'un système RAG **100% Java 21, self-hosted, sans GPU ni API payante** est non seulement faisable mais constitue un choix ingénierie solide pour un développeur solo maîtrisant l'écosystème Spring/PostgreSQL. La stack **LangChain4j + pgvector + ONNX in-process + MCP server** se distingue par sa simplicité opérationnelle — un seul JAR, une seule base de données, zéro service externe pour les embeddings.

L'insight non-évident de cette recherche est que **le choix du chunking et des métadonnées a plus d'impact sur la qualité du RAG que le choix du modèle d'embedding** pour de la documentation technique structurée. Investir dans un parser AST markdown (flexmark-java) et un schéma de métadonnées riche (framework, version, section path, content type) rapporte davantage que de passer de bge-small à bge-large.

Le flux optimal pour Claude Code combine le meilleur des deux mondes : **MCP server pour la recherche dynamique** à la demande (quand Claude Code a besoin de contexte spécifique) et **CLAUDE.md pour le contexte permanent** du projet (conventions, commandes, pointeurs). La configuration du serveur MCP en tant que processus stdio via `.mcp.json` permet un partage naturel avec le projet via git, rendant le setup reproductible sur n'importe quelle machine de développement.

Le vrai risque n'est pas la maturité de la stack Java — elle est suffisante — mais la tentation de sur-ingéniérer. Un MVP fonctionnel avec chunking récursif basique et recherche vectorielle simple, livré en 2 semaines, apportera déjà une valeur concrète dans les sessions Claude Code. L'optimisation du chunking, l'hybridation BM25+vecteur et le re-ranking viendront ensuite, guidés par l'évaluation des résultats réels.
