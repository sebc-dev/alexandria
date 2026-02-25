# Concepts RAG dans Alexandria

Guide de référence de tous les concepts RAG (Retrieval-Augmented Generation) mis en œuvre dans le projet Alexandria, organisés par étape du pipeline.

---

## Table des matières

1. [Crawling & Acquisition de contenu](#1-crawling--acquisition-de-contenu)
2. [Chunking (Découpage de documents)](#2-chunking-découpage-de-documents)
3. [Embeddings (Vectorisation)](#3-embeddings-vectorisation)
4. [Vector Store (Stockage vectoriel)](#4-vector-store-stockage-vectoriel)
5. [Recherche Hybride (Retrieval)](#5-recherche-hybride-retrieval)
6. [Reranking (Ré-ordonnancement)](#6-reranking-ré-ordonnancement)
7. [Post-traitement & Exposition](#7-post-traitement--exposition)
8. [Évaluation de la qualité du retrieval](#8-évaluation-de-la-qualité-du-retrieval)
9. [Infrastructure & Patterns d'architecture](#9-infrastructure--patterns-darchitecture)

---

## 1. Crawling & Acquisition de contenu

L'étape d'acquisition va chercher le contenu brut des sites de documentation technique pour le transformer en texte exploitable.

### 1.1 Web Crawling

- **Définition** : Parcourir automatiquement des pages web pour en extraire le contenu textuel (HTML → Markdown).
- **Pourquoi c'est nécessaire** : Un système RAG a besoin de contenu indexé. Le crawling automatise l'acquisition plutôt que d'importer manuellement chaque page.
- **Dans Alexandria** :
  - Sidecar Python [Crawl4AI](https://github.com/unclecode/crawl4ai) sur le port 11235
  - Rendu JavaScript via navigateur Chromium headless (nécessaire pour les sites SPA comme Docusaurus)
  - Suppression automatique du boilerplate (menus, footers, etc.)
  - Communication REST entre l'app Java et le sidecar
- **Fichiers clés** : `crawl/Crawl4AiClient.java`, `crawl/CrawlService.java`

### 1.2 Découverte d'URLs (Page Discovery)

- **Définition** : Identifier toutes les pages à crawler avant de lancer le crawl proprement dit.
- **Stratégies disponibles** :
  - **Sitemap.xml** : Fichier standard listant toutes les URLs d'un site. C'est la source la plus fiable.
  - **llms.txt / llms-full.txt** : Standard émergent spécifique aux LLMs. Certains sites exposent un fichier `llms.txt` listant les pages pertinentes pour l'IA. `llms-full.txt` peut contenir directement le contenu.
  - **BFS link crawling** : Parcours en largeur (Breadth-First Search) des liens HTML en fallback quand aucun fichier de découverte n'existe.
- **Dans Alexandria** : `PageDiscoveryService` essaie chaque stratégie en cascade : sitemap → llms.txt → BFS.
- **Fichiers clés** : `crawl/PageDiscoveryService.java`, `crawl/SitemapParser.java`, `crawl/LlmsTxtParser.java`

### 1.3 Scope Filtering (Filtrage de périmètre)

- **Définition** : Restreindre le crawl à un sous-ensemble pertinent d'un site pour éviter d'indexer du contenu hors-sujet (blog, changelog, pages marketing).
- **Mécanismes** :
  - **Allow/block patterns** : Globs sur les chemins d'URL (`/docs/**` autorisé, `/blog/**` bloqué)
  - **Profondeur maximale** : Limiter la profondeur de parcours BFS
  - **Nombre max de pages** : Safety cap pour éviter les crawls infinis
- **Dans Alexandria** : Configurable par source via `CrawlScope`.
- **Fichiers clés** : `crawl/CrawlScope.java`, `crawl/UrlScopeFilter.java`

### 1.4 URL Normalization

- **Définition** : Transformer des URLs variantes en une forme canonique pour éviter de crawler la même page deux fois.
- **Exemples** :
  - `https://example.com/page/` et `https://example.com/page` → même page
  - Suppression des fragments (`#section`), tri des query params
- **Fichiers clés** : `crawl/UrlNormalizer.java`

### 1.5 Incremental Crawling (Crawl incrémental)

- **Définition** : Lors d'un recrawl, ne ré-indexer que les pages dont le contenu a réellement changé. Évite de recalculer les embeddings de pages inchangées.
- **Comment ça marche** :
  1. À l'ingestion, on calcule un hash SHA-256 du contenu Markdown de chaque page
  2. Au recrawl, on compare le nouveau hash avec le hash stocké
  3. Si identique → skip. Si différent → ré-ingestion.
  4. Les pages disparues sont détectées et leurs chunks orphelins supprimés.
- **Dans Alexandria** : Table `ingestion_state` stockant `(source_id, page_url, content_hash)`.
- **Fichiers clés** : `ingestion/IngestionState.java`, `ingestion/ContentHasher.java`

### 1.6 Progress Tracking (Suivi de progression)

- **Définition** : Suivre en temps réel l'avancement d'un crawl (pages traitées, erreurs, pages skippées).
- **Dans Alexandria** : `CrawlProgressTracker` avec dispatch asynchrone via virtual threads. Support de l'annulation.
- **Fichiers clés** : `crawl/CrawlProgressTracker.java`, `crawl/CrawlProgress.java`

---

## 2. Chunking (Découpage de documents)

Le chunking transforme des pages entières en morceaux de taille optimale pour l'indexation vectorielle. C'est une étape critique : un mauvais chunking dégrade toute la chaîne.

### 2.1 AST-based Markdown Chunking

- **Définition** : Découper le texte en utilisant l'arbre syntaxique (Abstract Syntax Tree) du Markdown plutôt qu'un simple split par nombre de caractères.
- **Pourquoi c'est important** :
  - Un split naïf par taille peut couper un bloc de code en deux, rendant le chunk inutile
  - L'AST respecte la structure logique du document (sections, paragraphes, code fences, tables)
- **Règles de découpe dans Alexandria** :
  1. Découpe aux frontières de headings (H1/H2/H3) — jamais au milieu
  2. Si une section est trop grande : découpe aux frontières de paragraphes
  3. En dernier recours : découpe aux frontières de phrases
  4. Un code block ou une table n'est **jamais** coupé
- **Librairie** : CommonMark/flexmark-java avec support GFM (GitHub Flavored Markdown)
- **Taille cible** : ~400-512 tokens par chunk (max configurable : 2000 caractères)
- **Fichiers clés** : `ingestion/chunking/MarkdownChunker.java`

### 2.2 Parent-Child Chunk Hierarchy

- **Définition** : Système à deux niveaux où chaque section produit un chunk "parent" (section complète) et des chunks "enfants" (blocs individuels).
- **Comment ça marche** :
  - **Parent** : Heading + tout le contenu de la section (prose + code + tables), jusqu'à ~800-1500 tokens
  - **Enfant** : Un bloc individuel (paragraphe, code block), ~200-500 tokens
  - Chaque enfant porte un `parentId` pointant vers son parent
- **Pourquoi c'est important** :
  - Le retrieval cherche sur les **enfants** (plus précis, meilleur matching)
  - Mais on retourne le **parent** au LLM (contexte complet avec le code ET l'explication)
  - Résout le problème classique "code-prose separation" : quand code et explication sont dans des chunks séparés, le LLM perd le lien entre les deux
- **Fichiers clés** : `ingestion/chunking/MarkdownChunker.java`, `ingestion/DocumentChunkData.java`

### 2.3 Content Type Tagging

- **Définition** : Classifier chaque chunk comme PROSE (texte narratif) ou CODE (exemple de code).
- **Utilité** : Permet de filtrer les résultats par type à la recherche. Un développeur cherchant un exemple de code peut filtrer `contentType=CODE`.
- **Détection** : Fenced code blocks (```` ```java ````) détectés par l'AST. Détection de langage en fallback si pas de tag explicite.
- **Fichiers clés** : `ingestion/chunking/ContentType.java`, `ingestion/chunking/LanguageDetector.java`

### 2.4 Section Path (Breadcrumb hiérarchique)

- **Définition** : Un chemin slugifié représentant la position du chunk dans la hiérarchie du document.
- **Exemple** : Un chunk sous `# Guide > ## Configuration > ### Routes` aura le section path `guide/configuration/routes`.
- **Utilité** :
  - Filtrage par section à la recherche (ex: "cherche uniquement dans la section configuration")
  - Contexte pour le LLM (sait d'où vient l'information)
- **Fichiers clés** : Métadonnée calculée dans `MarkdownChunker`

### 2.5 Chunk Metadata (Métadonnées enrichies)

- **Définition** : Chaque chunk est enrichi de métadonnées stockées en JSONB dans PostgreSQL.
- **Métadonnées stockées** :
  - `source_url` : URL d'origine de la page
  - `source_name` : Nom humain de la source (ex: "Spring Boot")
  - `section_path` : Breadcrumb hiérarchique
  - `content_type` : PROSE ou CODE
  - `language` : Langage de programmation (pour les chunks CODE)
  - `version` : Version de la documentation
  - `chunk_type` : "parent" ou "child"
  - `parent_id` : Référence vers le chunk parent (pour les enfants)
- **Stockage** : Colonne JSONB dans la table `document_chunks`

### 2.6 Pre-chunked Import

- **Définition** : Bypass du chunking automatique pour importer des données déjà découpées par un système externe.
- **Cas d'usage** : Quand une source fournit du contenu pré-structuré (API, export d'un autre système).
- **Fichiers clés** : `ingestion/prechunked/PreChunkedImporter.java`

---

## 3. Embeddings (Vectorisation)

Les embeddings transforment du texte en vecteurs numériques. Deux textes sémantiquement proches auront des vecteurs proches dans l'espace vectoriel.

### 3.1 Embedding Model

- **Définition** : Un modèle de machine learning qui encode du texte en un vecteur de nombres (ici 384 dimensions).
- **Modèle utilisé** : `bge-small-en-v1.5` quantized (INT8)
  - **Taille** : ~65 MB
  - **Dimensions** : 384
  - **Contexte max** : 512 tokens
  - **Performance** : MTEB Retrieval nDCG@10 ~51.7
  - **Faiblesse connue** : Performance modeste sur le code pur (CoIR: 45.8)
- **Principe** : Le modèle a été pré-entraîné sur des millions de paires (query, passage) pour apprendre que des textes sémantiquement similaires doivent avoir des vecteurs proches.
- **Fichiers clés** : `config/EmbeddingConfig.java`

### 3.2 ONNX Runtime (Exécution in-process)

- **Définition** : ONNX (Open Neural Network Exchange) est un format standard pour exécuter des modèles ML sans GPU ni API externe.
- **Avantage dans Alexandria** :
  - Zéro dépendance réseau pour les embeddings
  - Latence sub-milliseconde
  - Pas de coût API (OpenAI, Cohere, etc.)
  - Le modèle est embarqué dans le JAR
- **Compromis** : Limité aux petits modèles exécutables sur CPU. Les modèles plus puissants (1B+ params) nécessiteraient un GPU ou une API.

### 3.3 Query Prefix (BGE-specific)

- **Définition** : Certains modèles d'embedding (comme BGE) utilisent un préfixe spécial pour les requêtes afin d'améliorer la qualité du retrieval.
- **Le préfixe** : `"Represent this sentence for searching relevant passages: "`
- **Pourquoi** : Le modèle a été entraîné avec ce préfixe. L'ajouter aux requêtes (mais PAS aux documents) améliore le recall de +1-5%.
- **Piège courant** : LangChain4j n'applique PAS ce préfixe automatiquement. Il faut le faire manuellement dans le code.
- **Fichiers clés** : `search/SearchService.java`

### 3.4 Batch Embedding

- **Définition** : Vectoriser les chunks par lots plutôt qu'un par un pour des raisons de performance.
- **Dans Alexandria** : Lots de 256 chunks via `EmbeddingModel.embedAll()`. Le modèle ONNX optimise les calculs par batch.
- **Fichiers clés** : `ingestion/IngestionService.java`

### 3.5 Vector Quantization (halfvec)

- **Définition** : Stocker les vecteurs en demi-précision (float16) au lieu de pleine précision (float32) pour réduire l'espace disque et mémoire.
- **Impact** :
  - **Stockage** : -50% (768 bytes → 384 bytes par vecteur en 384d)
  - **Recall** : Perte négligeable (~0-0.2%)
  - **Performance** : Légèrement plus rapide (moins de données à lire)
- **Dans Alexandria** : `halfvec(384)` dans pgvector — fonctionnalité de pgvector 0.7+
- **Fichiers clés** : `config/EmbeddingConfig.java`, migrations Flyway

---

## 4. Vector Store (Stockage vectoriel)

Le vector store persiste les embeddings et fournit une recherche de similarité efficace.

### 4.1 pgvector

- **Définition** : Extension PostgreSQL qui ajoute un type de données `vector` et des opérateurs de similarité.
- **Pourquoi pgvector plutôt qu'un DB vectorielle dédiée (Pinecone, Qdrant, Weaviate)** :
  - Zéro infrastructure additionnelle — tout dans PostgreSQL
  - SQL natif pour le filtrage par métadonnées
  - Transactions ACID, backups standard
  - Performant jusqu'à ~5M vecteurs
- **Version requise** : pgvector 0.8+ (nécessaire pour le filtrage efficace avec HNSW)
- **Fichiers clés** : `config/EmbeddingConfig.java`, `document/DocumentChunk.java`

### 4.2 HNSW Index (Hierarchical Navigable Small World)

- **Définition** : Algorithme d'indexation pour la recherche de plus proches voisins approximative (ANN — Approximate Nearest Neighbor).
- **Comment ça marche** (simplifié) :
  1. Construit un graphe multi-couches de voisins
  2. La recherche "navigue" le graphe du niveau le plus haut (peu de nœuds, grandes enjambées) vers le plus bas (beaucoup de nœuds, ajustements fins)
  3. Ne parcourt pas tous les vecteurs → beaucoup plus rapide qu'une recherche exhaustive
- **Paramètres dans Alexandria** :
  - `m=16` : Nombre de connexions par nœud (plus haut = meilleur recall, plus de mémoire)
  - `ef_construction=64` : Qualité de construction de l'index (plus haut = meilleur index, construction plus lente)
  - `ef_search` : Qualité de recherche au runtime (tunable, plus haut = meilleur recall, plus lent)
- **Compromis** : C'est une recherche **approximative** — elle peut manquer quelques résultats pertinents au profit de la vitesse. Les paramètres contrôlent ce compromis.

### 4.3 Cosine Similarity (Similarité cosinus)

- **Définition** : Mesure l'angle entre deux vecteurs, indépendamment de leur magnitude. Valeur entre -1 (opposés) et 1 (identiques).
- **Formule** : `cos(θ) = (A·B) / (||A|| × ||B||)`
- **Pourquoi cosinus plutôt qu'Euclidienne** :
  - Insensible à la longueur du texte (un paragraphe court et un long sur le même sujet auront une similarité élevée)
  - Standard pour les modèles d'embedding textuels
- **Dans Alexandria** : Opérateur `vector_cosine_ops` dans l'index HNSW de pgvector.

### 4.4 Schéma de la table document_chunks

- **Colonnes** :
  - `embedding_id` (UUID, PK) — identifiant unique du chunk
  - `embedding` (vector(384)) — le vecteur
  - `text` (TEXT) — le contenu textuel complet du chunk
  - `metadata` (JSONB) — métadonnées enrichies
  - `source_id` (FK → sources, ON DELETE CASCADE) — lien vers la source
  - `created_at` (TIMESTAMP)
- **Indexs** : HNSW sur `embedding`, GIN sur `to_tsvector(text)`, B-tree sur `source_id`
- **Fichiers clés** : `document/DocumentChunk.java`, `db/migration/V1__initial_schema.sql`

---

## 5. Recherche Hybride (Retrieval)

La recherche hybride combine deux approches complémentaires pour couvrir à la fois les requêtes sémantiques et les requêtes par mots-clés exacts.

### 5.1 Vector Search (Recherche sémantique)

- **Définition** : Trouver les chunks dont le vecteur est le plus proche du vecteur de la requête.
- **Forces** : Comprend le sens — "comment configurer une base de données" trouvera un chunk sur "database setup" même sans mot commun.
- **Faiblesses** : Peut manquer des termes techniques exacts (`@SpringBootApplication`, numéros d'erreur).
- **Pipeline** :
  1. Embedding de la requête avec le même modèle + query prefix BGE
  2. Recherche HNSW dans pgvector
  3. Retour des top-K candidats avec score de similarité cosinus

### 5.2 Full-Text Search (FTS / Recherche textuelle)

- **Définition** : Recherche par mots-clés utilisant le moteur FTS natif de PostgreSQL (tsvector/tsquery).
- **Comment ça marche** :
  - `tsvector` : Représentation indexée du texte (mots normalisés, stems, positions)
  - `tsquery` : Requête booléenne sur ces termes
  - `ts_rank_cd()` : Score de pertinence (similaire à BM25)
  - Index GIN pour des recherches rapides
- **Forces** : Excellent pour les identifiants exacts (noms de classes, annotations, messages d'erreur).
- **Faiblesses** : Ne comprend pas la synonymie ni le sens.
- **Fichiers clés** : `document/DocumentChunkRepository.java` (native query)

### 5.3 Exécution parallèle

- **Définition** : Vector search et FTS sont lancés en parallèle via `CompletableFuture` pour ne pas additionner leurs latences.
- **Résultat** : La latence totale = max(vector, FTS) au lieu de vector + FTS.

### 5.4 Convex Combination (Fusion des scores)

- **Définition** : Algorithme de fusion qui combine les scores des deux sources de recherche en une note finale pondérée.
- **Formule** : `score_final = α × norm(score_vector) + (1 - α) × norm(score_FTS)`
- **Étapes** :
  1. **Normalisation min-max** de chaque source indépendamment (ramène les scores entre 0 et 1)
  2. **Pondération** par alpha : `α = 0.7` signifie 70% poids sémantique, 30% poids mots-clés
  3. **Déduplication** par embedding ID (un chunk trouvé par les deux sources n'apparaît qu'une fois)
- **Pourquoi pas RRF (Reciprocal Rank Fusion)** :
  - RRF n'utilise que le **rang** (position), pas le **score**. Un résultat #2 avec un score de 0.99 est traité pareil qu'un #2 avec 0.51.
  - Convex Combination exploite la magnitude des scores → meilleur classement
  - Alexandria a migré de RRF vers CC dans la Phase 15 (v0.2)
- **Fichiers clés** : `search/ConvexCombinationFusion.java`

### 5.5 Score Normalization (Normalisation des scores)

- **Définition** : Ramener les scores de chaque source sur une échelle commune [0, 1] avant de les combiner.
- **Méthode** : Min-max normalization : `norm(s) = (s - min) / (max - min)`
- **Pourquoi c'est nécessaire** : Les scores vector (0.0-1.0 cosine) et FTS (0.0-∞ ts_rank) sont sur des échelles incomparables. Sans normalisation, une source dominerait toujours l'autre.

### 5.6 Metadata Filtering

- **Définition** : Restreindre la recherche à un sous-ensemble de chunks via leurs métadonnées.
- **Filtres disponibles** :
  - `sourceName` : Nom de la source (ex: "Spring Boot")
  - `version` : Version de la doc (ex: "3.5")
  - `sectionPath` : Préfixe de section (ex: "guide/configuration")
  - `contentType` : PROSE, CODE, ou MIXED (tous)
- **Implémentation** : LangChain4j Filter API avec composition AND
- **Fichiers clés** : `search/SearchService.java`, `search/SearchRequest.java`

---

## 6. Reranking (Ré-ordonnancement)

Le reranking utilise un second modèle plus puissant pour affiner le classement des candidats retournés par la recherche hybride.

### 6.1 Bi-encoder vs Cross-encoder

- **Bi-encoder** (embedding model) :
  - Encode query et document **séparément**
  - Très rapide (un seul passage par document, pré-calculable)
  - Moins précis (pas d'interaction entre query et document)
  - Utilisé pour le retrieval initial (top-K candidats)
- **Cross-encoder** (reranking model) :
  - Encode query ET document **ensemble** (attention conjointe)
  - Plus lent (doit recalculer pour chaque paire)
  - Plus précis (voit les interactions entre mots de la query et du document)
  - Utilisé pour le reranking (affiner le top-K)
- **Analogie** : Le bi-encoder est un filtre grossier rapide, le cross-encoder est un juge expert lent.

### 6.2 Modèle de reranking

- **Modèle** : `ms-marco-MiniLM-L-6-v2` (ONNX, in-process)
- **Entraînement** : Entraîné sur MS MARCO, un dataset de 8.8M passages avec des jugements de pertinence humains
- **Taille** : ~80 MB
- **Output** : Score de pertinence pour chaque paire (query, passage)
- **Fichiers clés** : `search/RerankerService.java`, `config/EmbeddingConfig.java`

### 6.3 Pipeline de reranking

1. Recevoir les top-K candidats de la fusion (défaut: 30)
2. Scorer chaque paire (query, candidat) avec le cross-encoder
3. Trier par score de reranking décroissant
4. Appliquer le seuil de score minimum (`minScore`)
5. Retourner les `maxResults` meilleurs
- **Fichiers clés** : `search/RerankerService.java`

### 6.4 Scores multiples dans le pipeline

Chaque chunk accumule des scores à chaque étape :

| Étape | Score | Signification |
|---|---|---|
| Vector search | Cosine similarity (0-1) | Proximité sémantique |
| FTS | ts_rank (0-∞) | Correspondance mots-clés |
| Fusion | Convex combination (0-1) | Score combiné normalisé |
| Reranking | Cross-encoder output (0-1) | Pertinence finale (le plus fiable) |

Le **rerank score** est le score final utilisé pour le classement.

---

## 7. Post-traitement & Exposition

Après la recherche et le reranking, les résultats sont transformés et exposés au LLM via MCP.

### 7.1 Deduplication by Parent

- **Définition** : Quand plusieurs chunks enfants du même parent sont dans les résultats, ne garder que le mieux classé.
- **Pourquoi** : Évite de retourner 3 paragraphes de la même section, qui seraient redondants une fois le parent substitué.
- **Logique** : Grouper par `parent_id` → garder le score max → les chunks sans parent passent directement.
- **Fichiers clés** : `search/SearchService.java`

### 7.2 Parent Text Substitution

- **Définition** : Remplacer le texte d'un chunk enfant par le texte complet de son chunk parent.
- **Pourquoi** : Le LLM a besoin du contexte complet (explication + code ensemble), pas juste du paragraphe isolé qui a matché.
- **Pipeline** :
  1. Collecter tous les `parentId` des résultats
  2. Une seule requête DB pour récupérer les textes parents
  3. Substituer le texte enfant par le texte parent
  4. Conserver les métadonnées du chunk enfant (score, source URL, etc.)
- **Fichiers clés** : `search/SearchService.java`, `document/DocumentChunkRepository.java`

### 7.3 Token Budget Truncation

- **Définition** : Limiter la taille totale des résultats retournés pour ne pas dépasser la fenêtre de contexte du LLM.
- **Comment ça marche** :
  - Estimation : 1 token ≈ 4 caractères
  - Accumulation des résultats jusqu'à épuisement du budget (défaut: 5000 tokens)
  - Au moins un résultat toujours retourné (tronqué si nécessaire)
- **Fichiers clés** : `mcp/TokenBudgetTruncator.java`

### 7.4 MCP (Model Context Protocol)

- **Définition** : Protocole d'Anthropic permettant à des outils externes d'exposer des capacités à Claude. Alexandria expose ses fonctionnalités comme des "tools" MCP.
- **7 tools exposés** :
  1. `search_docs` — Recherche sémantique + mots-clés avec filtres
  2. `list_sources` — Lister les sources indexées
  3. `add_source` — Ajouter et crawler une nouvelle source
  4. `remove_source` — Supprimer une source (cascade delete des chunks)
  5. `crawl_status` — Progression d'un crawl en cours
  6. `recrawl_source` — Re-crawler une source (incrémental ou complet)
  7. `index_statistics` — Statistiques globales de l'index
- **Fichiers clés** : `mcp/McpToolService.java`, `mcp/McpToolConfig.java`

### 7.5 STDIO vs SSE Transport

- **STDIO** : Communication via stdin/stdout. Utilisé en production avec Claude Code (pas de serveur web).
- **SSE (Server-Sent Events)** : Communication HTTP. Utilisé en développement avec le profil `web` (port 8080).
- **Dans Alexandria** : Un seul JAR, deux profils Spring Boot (`stdio`, `web`).

---

## 8. Évaluation de la qualité du retrieval

Mesurer objectivement la qualité du système de recherche pour guider les optimisations.

### 8.1 Golden Set (Jeu de test de référence)

- **Définition** : Ensemble de requêtes avec les documents pertinents annotés manuellement. C'est la "vérité terrain" du système.
- **Structure** : `{query, relevantChunkIds[], queryType}` en JSON
- **Taille recommandée** : 50-200 requêtes pour être statistiquement significatif
- **Types de requêtes** :
  - Factual (40%) : "Quel est le port par défaut de Spring Boot ?"
  - Conceptual (25%) : "Comment fonctionne l'auto-configuration ?"
  - Code lookup (25%) : "Comment configurer un DataSource ?"
  - Edge case (10%) : Erreurs, cas limites
- **Fichiers clés** : `search/RetrievalEvaluationService.java`

### 8.2 Recall@K

- **Définition** : Proportion des documents pertinents qui apparaissent dans les K premiers résultats.
- **Formule** : `Recall@K = |pertinents ∩ top-K| / |pertinents|`
- **Exemple** : Si 3 documents sont pertinents et 2 sont dans le top-10, Recall@10 = 2/3 = 0.67
- **Cible Alexandria** : Recall@10 ≥ 0.70
- **Interprétation** : Mesure la **complétude** — "est-ce qu'on retrouve tout ce qui est pertinent ?"

### 8.3 MRR (Mean Reciprocal Rank)

- **Définition** : Moyenne de l'inverse du rang du premier résultat pertinent.
- **Formule** : `MRR = (1/N) × Σ (1/rang_premier_pertinent)`
- **Exemple** : Si le premier résultat pertinent est en position 3, le reciprocal rank = 1/3 ≈ 0.33
- **Cible Alexandria** : MRR ≥ 0.60
- **Interprétation** : Mesure la **rapidité** — "est-ce que le bon résultat est en haut ?"

### 8.4 NDCG@K (Normalized Discounted Cumulative Gain)

- **Définition** : Mesure la qualité du classement en tenant compte de la pertinence graduée ET de la position.
- **Principe** : Un document très pertinent en position 1 vaut plus que le même document en position 10 (gain "discounted" par la position).
- **Cible Alexandria** : NDCG@K ≥ 0.55
- **Interprétation** : Le metric le plus complet — combine complétude, ordre, et gradation de pertinence.

### 8.5 Precision@K

- **Définition** : Proportion des résultats dans le top-K qui sont effectivement pertinents.
- **Formule** : `Precision@K = |pertinents ∩ top-K| / K`
- **Interprétation** : Mesure le **bruit** — "est-ce que les résultats retournés sont utiles, ou y a-t-il beaucoup de déchets ?"

### 8.6 Ablation Study

- **Définition** : Désactiver des composants un par un pour mesurer leur contribution individuelle.
- **Configurations testées** :
  - Vector search seul
  - FTS seul
  - Hybride sans reranking
  - Hybride avec reranking (pipeline complet)
- **Utilité** : Identifie quel composant apporte de la valeur et lequel est superflu.

---

## 9. Infrastructure & Patterns d'architecture

### 9.1 Sidecar Pattern

- **Définition** : Déployer un service auxiliaire à côté de l'application principale, communiquant via réseau local.
- **Dans Alexandria** : Crawl4AI (Python) tourne en container Docker séparé, communique en REST avec l'app Java.
- **Pourquoi** : Le crawling JS nécessite un navigateur Chromium, difficile à intégrer en Java. Le sidecar encapsule cette complexité.

### 9.2 Virtual Threads (Java 21)

- **Définition** : Threads ultra-légers gérés par la JVM (pas par l'OS), idéaux pour les opérations I/O-bound.
- **Dans Alexandria** :
  - Crawl asynchrone via `Thread.startVirtualThread()`
  - Recherche parallèle vector + FTS
  - Requêtes DB concurrentes
- **Avantage** : Des milliers de threads virtuels pour le coût mémoire d'une poignée de threads OS.

### 9.3 Flyway Migrations

- **Définition** : Outil de gestion versionnée du schéma de base de données.
- **Migrations dans Alexandria** :
  - `V1` : Schéma initial (pgvector extension, tables, index HNSW + GIN)
  - `V2` : Colonnes de scope sur les sources (allow/block patterns)
  - `V3` : Colonne version sur les sources
  - `V4` : Nettoyage des chunks orphelins

### 9.4 LangChain4j

- **Définition** : Framework Java pour construire des applications LLM (équivalent Java de LangChain Python).
- **Composants utilisés dans Alexandria** :
  - `EmbeddingModel` : Interface pour les modèles d'embedding (implémentation ONNX BGE)
  - `EmbeddingStore` : Interface pour le vector store (implémentation pgvector)
  - `ScoringModel` : Interface pour le reranking (implémentation ONNX cross-encoder)
  - `Filter API` : Construction de filtres composables pour la recherche
  - `TextSegment` / `Embedding` : Types de données pour chunks et vecteurs

### 9.5 Spring Boot Dual-Mode Deployment

- **Profil `web`** : REST API (port 8080) + MCP SSE. Pour le développement et l'administration.
- **Profil `stdio`** : MCP stdio uniquement, pas de serveur web. Pour l'intégration avec Claude Code en production.
- **Un seul JAR** : Le même artefact supporte les deux modes, activés par profil Spring.

---

## Glossaire rapide

| Terme | Définition courte |
|---|---|
| **ANN** | Approximate Nearest Neighbor — recherche de voisins approximative (rapide) vs exacte (lente) |
| **BM25** | Algorithme de scoring par mots-clés (ce que simule `ts_rank_cd` de PostgreSQL) |
| **Cosine similarity** | Mesure d'angle entre vecteurs (0 = orthogonaux, 1 = identiques) |
| **Cross-encoder** | Modèle qui score une paire (query, passage) conjointement |
| **Embedding** | Représentation vectorielle d'un texte |
| **FTS** | Full-Text Search — recherche par mots-clés avec stemming |
| **GIN** | Generalized Inverted Index — type d'index PostgreSQL pour FTS |
| **HNSW** | Hierarchical Navigable Small World — algorithme d'index vectoriel |
| **MCP** | Model Context Protocol — protocole d'exposition d'outils pour Claude |
| **ONNX** | Open Neural Network Exchange — format portable pour modèles ML |
| **pgvector** | Extension PostgreSQL pour vecteurs |
| **RAG** | Retrieval-Augmented Generation — enrichir un LLM avec du contexte récupéré |
| **Reranking** | Ré-ordonnancement des résultats par un modèle plus précis |
| **RRF** | Reciprocal Rank Fusion — algorithme de fusion par rangs (remplacé par CC) |
| **tsvector** | Représentation indexée du texte en PostgreSQL FTS |
