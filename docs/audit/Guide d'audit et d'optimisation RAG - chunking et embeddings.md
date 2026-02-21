# Guide d'audit et d'optimisation RAG : chunking et embeddings

**bge-small-en-v1.5-q est un choix défendable mais sous-optimal pour votre cas d'usage.** Le modèle offre un bon rapport performance/ressources pour du texte en anglais (MTEB Retrieval ~51.7 nDCG@10), mais ses limitations sont significatives : **512 tokens maximum** (tronque les sections longues), **performance faible sur le code** (APPS=5.6, CosQA=32.1 sur CoIR), pas de support Matryoshka, et une quantization qui coûte 1-3% de qualité. L'extraction séparée du code et de la prose est le problème le plus critique de votre pipeline — elle brise le lien sémantique entre explication et exemple, dégradant la réponse aux requêtes "comment faire X". Les trois optimisations chunking à plus fort impact sont : (1) réunir code+prose dans des chunks parents avec retrieval hiérarchique, (2) ajouter un breadcrumb contextuel hiérarchique à chaque chunk, (3) activer la recherche hybride BM25+vecteur pour les identifiants techniques. Pour les embeddings : (1) ajouter immédiatement le préfixe query BGE, (2) passer l'index pgvector en halfvec pour 50% de stockage gratuit, (3) benchmarker nomic-embed-text-v1.5 comme candidat de remplacement (Matryoshka, 8192 tokens, meilleur retrieval).

---

## 1. Résumé exécutif

**Verdict bge-small-en-v1.5-q** : Acceptable en baseline CPU, mais trois faiblesses majeures justifient une migration à moyen terme — contexte limité à 512 tokens (vos sections Markdown dépassent souvent cette limite), performance médiocre sur le code (5.6 nDCG@10 sur APPS, vs 11.5 pour E5-base-v2), et absence de Matryoshka empêchant toute optimisation dimensionnelle. La quantization INT8 coûte **~1-2% en nDCG@10** — acceptable.

**Top 3 optimisations chunking** :

1. **Réunir code+prose** en chunks parents et implémenter un parent-child retrieval (~2 jours, impact élevé)
2. **Ajouter des breadcrumbs contextuels** type "Spring Boot > Database Configuration > Connection Pooling" à chaque chunk (1 jour, impact moyen-élevé)
3. **Activer la recherche hybride** BM25+vecteur dans `PgVectorEmbeddingStore` pour les noms de classes, annotations, identifiants (0.5 jour, impact élevé sur les requêtes techniques)

**Top 3 optimisations embedding** :

1. **Ajouter le préfixe query BGE** `"Represent this sentence for searching relevant passages: "` — **non appliqué par LangChain4j automatiquement**, gain estimé 1-5% (30 min)
2. **Passer l'index HNSW en halfvec** — 50% de stockage en moins, recall identique, accélération build (1h)
3. **Benchmarker nomic-embed-text-v1.5** (768d, Matryoshka, 8192 tokens, Apache 2.0) comme remplacement — meilleur retrieval, contexte 16x plus long (1 semaine avec réindexation)

---

## 2. Tableau comparatif des modèles d'embedding

|Modèle|Dims|Taille ONNX (fp32/int8)|RAM estimée|Latence CPU estimée (batch 256)|MTEB Retrieval nDCG@10|Support code|Dispo ONNX|Dispo LangChain4j|Score /5|Confiance|
|---|---|---|---|---|---|---|---|---|---|---|
|**bge-small-en-v1.5**|384|~127 / ~32 MB|~200 MB|~50ms|~51.7|Faible (CoIR 45.8)|✅ Officiel + quantized|✅ Pré-packagé|⭐⭐⭐|Haute|
|**bge-base-en-v1.5**|768|~438 / ~110 MB|~500 MB|~150ms [ESTIMÉ]|~53.3|Faible (CoIR 46.1)|✅ Intel int8 dispo|⚠️ Via OnnxEmbeddingModel|⭐⭐⭐½|Haute|
|**bge-large-en-v1.5**|1024|~1.3 GB / ~335 MB|~1.5 GB|~400ms [ESTIMÉ]|~54.3|Modéré|✅ Intel int8 dispo|⚠️ Via OnnxEmbeddingModel|⭐⭐⭐½|Haute|
|**bge-m3**|1024|~2.2 GB / [À VÉRIFIER]|~2.5 GB|~800ms [ESTIMÉ]|~53-54|Modéré + ColBERT|✅ yuniko-software/bge-m3-onnx|⚠️ Custom Java|⭐⭐⭐⭐|Moyenne|
|**nomic-embed-text-v1.5**|768 (Matryoshka: 64-768)|~548 MB / [À VÉRIFIER]|~700 MB|~200ms [ESTIMÉ]|~53.0|Modéré|✅ Dans le repo modèle|⚠️ Via OnnxEmbeddingModel|⭐⭐⭐⭐|Moyenne|
|**E5-small-v2**|384|~127 / ~32 MB|~200 MB|~50ms|~49.0|Modéré (CoIR 47.1)|✅ nixiesearch ONNX|✅ Pré-packagé|⭐⭐½|Haute|
|**E5-base-v2**|768|~438 / ~110 MB|~500 MB|~150ms [ESTIMÉ]|~50.3|Bon (CoIR 50.3)|✅ Via Optimum|⚠️ Via OnnxEmbeddingModel|⭐⭐⭐|Haute|
|**gte-small**|384|~127 / ~32 MB|~200 MB|~50ms|~49.5|Faible|✅ Via Optimum|⚠️ Via OnnxEmbeddingModel|⭐⭐½|Haute|
|**gte-base**|768|~438 / ~110 MB|~500 MB|~150ms [ESTIMÉ]|~51.1|Faible|✅ Via Optimum|⚠️ Via OnnxEmbeddingModel|⭐⭐⭐|Haute|
|**jina-embeddings-v2-base-code**|768|~640 MB / [À VÉRIFIER]|~800 MB|~250ms [ESTIMÉ]|[À VÉRIFIER]|**Excellent** (30+ langages)|✅ Variantes ONNX|⚠️ Via OnnxEmbeddingModel|⭐⭐⭐⭐|Moyenne|
|**all-MiniLM-L6-v2**|384|~91 / ~23 MB|~150 MB|~35ms|~41.9|Faible|✅ Très bien supporté|✅ Pré-packagé|⭐⭐|Haute|

**Notes sur les scores MTEB** : Tous les scores sont MTEB v1 (BEIR benchmark, nDCG@10 sur 15 datasets retrieval). Sources : model cards HuggingFace (BAAI/bge-small-en-v1.5, etc.), paper Granite Embeddings (arxiv.org/html/2502.20204v1), rapport technique Nomic (arxiv.org/pdf/2402.01613). Les scores marqués ~ sont des approximations compilées depuis plusieurs sources concordantes. Les latences marquées [ESTIMÉ] sont extrapolées linéairement depuis les 50ms mesurées pour bge-small-en-v1.5-q sur votre infra, proportionnellement au nombre de paramètres.

**Modèles spécialisés code** : Voyage Code 3 et Codestral Embed sont API-only — **incompatibles self-hosted**. CodeSage-small-v2 (130M, Apache 2.0, Matryoshka) n'a pas d'export ONNX officiel — conversion manuelle nécessaire et non triviale. **jina-embeddings-v2-base-code** (161M, 768d, 8192 tokens, Apache 2.0, ONNX disponible) est le meilleur candidat code auto-hébergeable.

**Recommandation modèle** : Pour votre cas (documentation technique code+prose, CPU-only), le meilleur rapport qualité/ressources est **nomic-embed-text-v1.5** — Matryoshka permet d'opérer à 256d pour la même empreinte mémoire que bge-small à 384d, avec un meilleur retrieval et un contexte de 8192 tokens. Pour un budget mémoire identique, utiliser nomic à 256d via Matryoshka. ⚠️ Tout changement de modèle **nécessite une réindexation complète**.

---

## 3. Tableau comparatif des stratégies de chunking

|Stratégie|Implémentation LangChain4j|Qualité prose|Qualité code|Complexité|Effort migration|Score /5|Confiance|
|---|---|---|---|---|---|---|---|
|**Header-based AST (actuel)**|❌ Custom (votre implémentation)|⭐⭐⭐⭐|⭐⭐⭐|Faible|—|⭐⭐⭐½|Haute|
|**Recursive character**|✅ `DocumentSplitters.recursive()`|⭐⭐⭐|⭐⭐ (casse le code)|Très faible|Trivial|⭐⭐½|Haute|
|**Header-based + parent-child**|❌ Custom (metadata linking)|⭐⭐⭐⭐⭐|⭐⭐⭐⭐⭐|Moyenne|2-3 jours|⭐⭐⭐⭐⭐|Haute|
|**Semantic chunking**|❌ Non disponible (issue #1081)|⭐⭐⭐⭐|⭐⭐ (confus par code)|Moyenne|3-5 jours|⭐⭐⭐|Moyenne|
|**Late chunking (Jina)**|❌ Custom + modèle Jina requis|⭐⭐⭐⭐|⭐⭐⭐|Élevée|1-2 semaines|⭐⭐⭐½|Moyenne|
|**Contextual retrieval (Anthropic)**|❌ Custom (LLM par chunk)|⭐⭐⭐⭐⭐|⭐⭐⭐⭐|Élevée|1 semaine|⭐⭐⭐⭐|Haute (benchmarks publiés)|
|**Agentic chunking**|❌ Custom (LLM par document)|⭐⭐⭐⭐⭐|⭐⭐⭐⭐|Très élevée|2+ semaines|⭐⭐⭐⭐|Faible (peu de benchmarks)|
|**Heuristic contextual (breadcrumbs)**|❌ Trivial custom|⭐⭐⭐⭐|⭐⭐⭐⭐|Très faible|0.5 jour|⭐⭐⭐⭐|Haute|

**Recommandation stratégie** : Conserver votre approche header-based AST (bien adaptée aux docs Markdown structurés) et l'enrichir avec parent-child retrieval + breadcrumbs contextuels. Le semantic chunking n'apporte que peu de valeur ajoutée sur des documents déjà bien structurés par headers — il est surtout utile pour de la prose non-structurée. Le late chunking nécessite un modèle long-contexte avec mean pooling (Jina ou nomic) et n'est pas applicable avec bge-small (512 tokens max).

---

## 4. Guide d'audit chunking

### Checklist métriques de qualité des chunks

**Distribution de taille** — La cible pour la documentation technique est **400-512 tokens par chunk** (consensus NVIDIA 2024, Pinecone, Weaviate). Les chunks < 64 tokens sont du bruit, les chunks > 1024 tokens diluent le signal d'embedding. L'étude NVIDIA (2024) montre que les chunks de 128 tokens sont les moins performants, avec un gain significatif entre 256 et 512 tokens. L'étude Chroma (2024) confirme qu'un `RecursiveCharacterTextSplitter` à 400 tokens atteint **91.3% de recall**.

```sql
-- Distribution de taille des chunks (adapter le nom de table/colonne)
SELECT 
  COUNT(*) as total_chunks,
  MIN(token_count) as min_tokens,
  PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY token_count)::int as p25,
  PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY token_count)::int as median,
  PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY token_count)::int as p75,
  MAX(token_count) as max_tokens,
  AVG(token_count)::int as mean,
  STDDEV(token_count)::int as stddev
FROM embedding_store;

-- Histogramme par buckets
SELECT 
  CASE 
    WHEN token_count < 64 THEN '⚠️ <64 (bruit)'
    WHEN token_count < 128 THEN '128'
    WHEN token_count < 256 THEN '256'
    WHEN token_count < 512 THEN '✅ 256-512 (optimal)'
    WHEN token_count < 1024 THEN '512-1024'
    ELSE '⚠️ >1024 (dilution)'
  END as bucket,
  COUNT(*) as count,
  ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 1) as pct
FROM embedding_store GROUP BY 1 ORDER BY MIN(token_count);

-- Red flag: chunks très courts (< 50 tokens) souvent des headers orphelins
SELECT id, token_count, LEFT(text_content, 150) as preview,
       metadata->>'contentType' as type, metadata->>'sectionPath' as section
FROM embedding_store WHERE token_count < 50 ORDER BY token_count;
```

**Détection de chunks orphelins code/prose** — C'est le red flag principal de votre pipeline actuel. Un chunk code sans prose associée dans la même section est un orphelin qui perdra au retrieval pour les requêtes en langage naturel.

```sql
-- Chunks code sans prose dans la même section
SELECT c.id, c.token_count, LEFT(c.text_content, 100) as code_preview,
       c.metadata->>'sectionPath' as section
FROM embedding_store c
WHERE c.metadata->>'contentType' = 'CODE'
  AND NOT EXISTS (
    SELECT 1 FROM embedding_store p 
    WHERE p.metadata->>'contentType' = 'PROSE'
      AND p.metadata->>'sectionPath' = c.metadata->>'sectionPath'
  );

-- Chunks prose référençant du code ("the following", "example", "snippet") 
-- sans chunk code dans la même section
SELECT id, LEFT(text_content, 200) as preview, metadata->>'sectionPath'
FROM embedding_store
WHERE metadata->>'contentType' = 'PROSE'
  AND (text_content ILIKE '%following example%' 
    OR text_content ILIKE '%code below%'
    OR text_content ILIKE '%snippet%'
    OR text_content ILIKE '%as shown%')
  AND NOT EXISTS (
    SELECT 1 FROM embedding_store c
    WHERE c.metadata->>'contentType' = 'CODE'
      AND c.metadata->>'sectionPath' = embedding_store.metadata->>'sectionPath'
  );

-- Near-duplicates (overlap excessif ou chunks redondants)
-- ATTENTION: requête coûteuse, exécuter sur un échantillon
SELECT a.id as id1, b.id as id2, 
       1 - (a.embedding <=> b.embedding) as cosine_sim,
       LEFT(a.text_content, 80), LEFT(b.text_content, 80)
FROM embedding_store a 
JOIN embedding_store b ON a.id < b.id
WHERE (a.embedding <=> b.embedding) < 0.05
ORDER BY a.embedding <=> b.embedding LIMIT 50;
```

**Cohérence sémantique intra-chunk** — Calculer la similarité cosine moyenne entre les phrases au sein de chaque chunk. Une moyenne < 0.5 indique un chunk incohérent mélangeant des sujets distincts. Implémentation : pour chaque chunk, splitter en phrases, calculer l'embedding de chaque phrase, mesurer la similarité cosine pairwise moyenne. Les chunks avec la plus faible cohérence sont des candidats au re-splitting.

### Red flags à surveiller

- **> 15% des chunks en dessous de 64 tokens** : le splitting est trop agressif, les headers isolés polluent l'index
- **> 10% des chunks au-dessus de 1024 tokens** : les sections sans H2/H3 produisent des méga-chunks dont l'embedding est dilué
- **Écart-type > 60% de la moyenne** : la distribution est trop hétérogène, le retrieval sera inconsistant
- **Chunks code sans prose dans la section** : requête de détection ci-dessus ; chaque orphelin dégrade le retrieval pour les requêtes en langage naturel
- **Chunks commençant par des pronoms** ("It", "This", "The above") : signe de contexte perdu au split — le breadcrumb contextuel corrige partiellement ce problème

---

## 5. Guide d'audit embeddings

### Protocole step-by-step

**Étape 1 — Vérifier le préfixe query BGE (30 min).** Le modèle bge-small-en-v1.5 attend le préfixe `"Represent this sentence for searching relevant passages: "` sur les requêtes (pas sur les documents). LangChain4j **ne l'applique pas automatiquement**. Vérifier dans votre code si le préfixe est ajouté. Si non, c'est votre quick win le plus immédiat — BAAI indique un gain de **1-5% en retrieval** pour les requêtes courtes.

**Étape 2 — Vérifier la normalisation L2 (15 min).** Les embeddings BGE doivent être L2-normalisés (norme = 1.0) avant stockage pour que la distance cosine fonctionne correctement. LangChain4j normalise par défaut, mais vérifier :

```sql
-- Vérifier que les normes sont ≈ 1.0
SELECT AVG(sqrt(embedding <-> array_fill(0, ARRAY[384])::vector)) as avg_norm,
       MIN(sqrt(embedding <-> array_fill(0, ARRAY[384])::vector)) as min_norm,
       MAX(sqrt(embedding <-> array_fill(0, ARRAY[384])::vector)) as max_norm
FROM embedding_store;
-- Si avg_norm ≠ 1.0 ± 0.01, il y a un problème de normalisation
```

**Étape 3 — Construire un golden set d'évaluation (4-8h).** Créer **100-300 paires query→document(s) pertinent(s)** couvrant votre documentation. Inclure : requêtes factuelles simples ("What is the default port for Spring Boot?"), requêtes conceptuelles ("How does auto-configuration work?"), requêtes code ("How to configure a DataSource?"), requêtes avec jargon technique. **Approche recommandée** : utiliser un LLM pour générer 3-5 questions par chunk de documentation, puis validation manuelle pour filtrer à ~200 paires de qualité.

**Étape 4 — Mesurer les métriques de retrieval baseline (2h).** Implémenter un pipeline d'évaluation minimal en Java (~200-300 lignes) :

```java
// Pseudo-code: pipeline d'évaluation minimal
record EvalSample(String query, Set<String> relevantChunkIds) {}
record EvalResult(double hitRate, double recall5, double mrr, double ndcg10) {}

EvalResult evaluate(List<EvalSample> goldenSet, EmbeddingStore store, EmbeddingModel model) {
    double totalHit = 0, totalRecall = 0, totalRR = 0, totalNDCG = 0;
    for (var sample : goldenSet) {
        var results = store.search(EmbeddingSearchRequest.builder()
            .queryEmbedding(model.embed(PREFIX + sample.query()).content())
            .maxResults(10).build());
        var retrievedIds = results.matches().stream()
            .map(m -> m.embeddingId()).toList();
        // Hit@10
        totalHit += retrievedIds.stream().anyMatch(sample.relevantChunkIds()::contains) ? 1 : 0;
        // Recall@5
        long hits5 = retrievedIds.subList(0, Math.min(5, retrievedIds.size())).stream()
            .filter(sample.relevantChunkIds()::contains).count();
        totalRecall += (double) hits5 / sample.relevantChunkIds().size();
        // MRR
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (sample.relevantChunkIds().contains(retrievedIds.get(i))) {
                totalRR += 1.0 / (i + 1); break;
            }
        }
        // NDCG@10: implémenter DCG/IDCG
    }
    int n = goldenSet.size();
    return new EvalResult(totalHit/n, totalRecall/n, totalRR/n, totalNDCG/n);
}
```

**Cibles** : Hit@10 ≥ 0.90, Recall@5 ≥ 0.70, MRR ≥ 0.60, NDCG@10 ≥ 0.50 sont des seuils raisonnables pour de la documentation technique.

**Étape 5 — Visualiser l'espace d'embedding (2-4h).** Utiliser **Smile v4.x** (compatible Java 21) pour UMAP :

```xml
<dependency>
  <groupId>com.github.haifengl</groupId>
  <artifactId>smile-core</artifactId>
  <version>4.2.0</version> <!-- vérifier dernière version 4.x -->
</dependency>
```

```java
import smile.manifold.UMAP;
// Exporter les embeddings depuis pgvector
double[][] embeddings = loadEmbeddingsFromPgVector(); // float[] → double[]
String[] labels = loadContentTypes(); // "PROSE", "CODE", etc.
double[][] projected = UMAP.of(embeddings, 15, 2); // 15 neighbors, 2D
// Exporter en CSV pour visualisation ou utiliser smile-plot
```

**Ce qu'il faut chercher** : les chunks PROSE et CODE doivent former des clusters thématiques (par framework, par section), PAS des clusters structurels (tous les codes ensemble, toute la prose ensemble). Si les chunks code forment un cluster isolé de la prose correspondante, c'est un signe que l'extraction séparée nuit à la qualité sémantique.

**Étape 6 — Mesurer le silhouette score par content_type et par source (1h).** Un score > 0.5 indique une bonne séparation des clusters thématiques. Un score < 0.25 indique que l'embedding ne capture pas bien les distinctions sémantiques.

### Checklist qualité embeddings

- ☐ Préfixe query BGE appliqué sur les requêtes
- ☐ Documents embedés SANS préfixe
- ☐ Normalisation L2 vérifiée (norme ≈ 1.0)
- ☐ Golden set ≥ 100 paires créé et versionné
- ☐ Recall@5 baseline mesuré
- ☐ MRR baseline mesuré
- ☐ UMAP visualisé et analysé pour clusters thématiques
- ☐ Silhouette score calculé
- ☐ Approximate vs exact recall vérifié (pgvector HNSW fidelity)
- ☐ Latence embedding mesurée avec JMH (single + batch)

```sql
-- Vérifier la fidélité de l'index HNSW (approximate vs exact recall)
-- Pour 20 requêtes de test, comparer les résultats avec et sans index
BEGIN;
SET LOCAL enable_indexscan = off;
-- Exact KNN (seq scan)
SELECT id FROM embedding_store ORDER BY embedding <=> '[query_vector]'::vector LIMIT 10;
COMMIT;
-- Puis comparer avec la requête indexée
SELECT id FROM embedding_store ORDER BY embedding <=> '[query_vector]'::vector LIMIT 10;
-- Les résultats doivent être identiques à ≥90%. Sinon, augmenter ef_search.
```

---

## 6. Optimisations priorisées en 3 phases

### Phase 1 — Quick wins (< 2 jours)

**1.1 Ajouter le préfixe query BGE (30 min, impact : +1-5% retrieval)**

```java
// Dans votre code de retrieval, avant l'embedding de la requête
private static final String BGE_QUERY_PREFIX = 
    "Represent this sentence for searching relevant passages: ";

Embedding queryEmb = embeddingModel.embed(BGE_QUERY_PREFIX + userQuery).content();
```

Vérifier que ce préfixe n'est PAS ajouté lors de l'embedding des documents à l'indexation. Ce quick win est celui avec le meilleur ratio effort/impact.

**1.2 Activer la recherche hybride pgvector (2h, impact : élevé sur requêtes techniques)**

LangChain4j pgvector supporte le mode hybride depuis les versions récentes :

```java
PgVectorEmbeddingStore store = PgVectorEmbeddingStore.datasourceBuilder()
    .dataSource(dataSource)
    .table("embedding_store")
    .dimension(384)
    .searchMode(SearchMode.HYBRID)  // BM25 + vector
    .rrfK(60)                        // Reciprocal Rank Fusion constant
    .textSearchConfig("english")     // Configuration full-text PostgreSQL
    .build();
```

La recherche hybride est particulièrement critique pour la documentation technique — BM25 excelle pour les identifiants exacts (noms de classes, annotations `@SpringBootApplication`, codes d'erreur) que les embeddings sémantiques manquent. Anthropic rapporte que la combinaison contextual embeddings + contextual BM25 réduit le taux d'échec de retrieval de **49%**.

**1.3 Passer l'index HNSW en halfvec (1h, impact : -50% stockage, vitesse identique)**

```sql
-- Créer un nouvel index halfvec (50% plus petit, recall identique)
DROP INDEX IF EXISTS embedding_store_hnsw_idx;
CREATE INDEX embedding_store_hnsw_halfvec_idx 
  ON embedding_store 
  USING hnsw ((embedding::halfvec(384)) halfvec_cosine_ops) 
  WITH (m = 16, ef_construction = 128);

-- Augmenter ef_search pour meilleur recall
SET hnsw.ef_search = 100; -- default est 40, 100 offre un bon compromis
```

Les benchmarks de Jonathan Katz (contributeur pgvector) montrent **0.0-0.2% de perte de recall** avec halfvec sur des datasets allant jusqu'à 1536 dimensions. C'est essentiellement gratuit.

**1.4 Ajouter des breadcrumbs contextuels aux chunks (4h, impact : moyen-élevé)**

Implémenter une version heuristique (sans LLM) du Contextual Retrieval d'Anthropic : préfixer chaque chunk avec le chemin de section complet.

```java
// Avant embedding, préfixer le chunk avec son contexte hiérarchique
String contextualizedText = String.format(
    "From %s documentation, section '%s': %s",
    metadata.get("source"),       // ex: "Spring Boot 3.5"
    metadata.get("sectionPath"),  // ex: "Data Access > JPA > Configuration"
    chunk.text()
);
// Embedder ce texte enrichi au lieu du chunk brut
```

Cette heuristique capture une partie significative du gain du Contextual Retrieval (qui réduit le taux d'échec de 35% avec un LLM) sans aucun coût LLM. ⚠️ Nécessite une **réindexation complète**.

**1.5 Enrichir les metadata pour le filtrage (2h)**

```java
// Ajouter des metadata exploitables pour le filtrage au retrieval
Metadata metadata = new Metadata()
    .put("source", "spring-boot")
    .put("version", "3.5.7")
    .put("sectionPath", "data-access/jpa/configuration")
    .put("contentType", "PROSE")       // ou CODE
    .put("language", "java")           // pour les chunks code
    .put("tokenCount", 342)
    .put("hasCode", true)              // la section contient du code
    .put("parentSectionId", "data-access-jpa"); // pour retrieval hiérarchique
```

Configurer le stockage metadata en `COMBINED_JSONB` pour des performances de filtrage optimales sur gros volumes :

```java
.metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
```

### Phase 2 — Benchmarking et restructuration (< 1 semaine)

**2.1 Construire le golden set d'évaluation (1-2 jours)**

Objectif : **200 paires query→document(s) pertinent(s)**, réparties en catégories : requêtes factuelles (40%), requêtes conceptuelles (25%), requêtes code "comment faire" (25%), requêtes edge-case (10%). Utiliser un LLM pour générer des questions candidates depuis vos chunks, puis valider manuellement. Stocker en JSON versionné dans le repo.

**2.2 Implémenter le pipeline d'évaluation Java (1 jour)**

Pipeline minimal : charger golden set → embedder les queries → rechercher dans pgvector → calculer Recall@k, MRR, NDCG@10 → comparer avec/sans préfixe, avec/sans hybride. Utiliser JMH (v1.37) pour les benchmarks de latence :

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class EmbeddingBenchmark {
    @Benchmark
    public Embedding singleEmbed(BenchmarkState state) {
        return state.model.embed("How to configure datasource in Spring Boot?").content();
    }
    
    @Benchmark
    public List<Embedding> batchEmbed256(BenchmarkState state) {
        return state.model.embedAll(state.batch256).content();
    }
}
```

**2.3 Restructurer le chunking code+prose (2-3 jours)**

C'est l'optimisation structurelle la plus impactante. Implémenter un **parent-child retrieval** :

- **Parent chunks** (800-1500 tokens) : la section complète H2/H3 incluant prose + code + tables. Stockés avec un ID unique.
- **Child chunks** (200-500 tokens) : paragraphes individuels et blocs de code. Chacun porte un `parentChunkId` en metadata.
- **Retrieval** : rechercher sur les child chunks (plus précis), mais retourner le parent chunk au LLM (plus de contexte).

```java
// Architecture parent-child
record ParentChunk(String id, String fullSectionText, Metadata metadata) {}
record ChildChunk(String id, String text, String parentId, Metadata metadata) {}

// Au retrieval: rechercher les children, puis charger les parents
var childResults = embeddingStore.search(request);
Set<String> parentIds = childResults.matches().stream()
    .map(m -> m.embedded().metadata().getString("parentChunkId"))
    .collect(Collectors.toSet());
// Charger les parents complets depuis la DB
List<TextSegment> parentSegments = loadParentChunks(parentIds);
```

LangChain4j n'a pas de `ParentDocumentRetriever` natif — l'implémentation est custom mais directe. Le lien code↔prose est restauré naturellement puisque le parent contient les deux.

**2.4 Benchmarker nomic-embed-text-v1.5 vs bge-small (2 jours)**

Préparer un comparatif rigoureux : même golden set, même chunking, deux index pgvector parallèles. Nomic-embed-text-v1.5 offre trois avantages théoriques majeurs : Matryoshka (tester à 768d, 512d, 256d), contexte 8192 tokens (vs 512), et retrieval légèrement supérieur (~53.0 vs ~51.7 nDCG@10 MTEB).

```java
// Charger nomic-embed-text-v1.5 via ONNX
EmbeddingModel nomicModel = new OnnxEmbeddingModel(
    "/path/to/nomic-embed-text-v1.5/model.onnx",
    "/path/to/nomic-embed-text-v1.5/tokenizer.json",
    PoolingMode.MEAN
);
```

⚠️ nomic-embed-text-v1.5 utilise un préfixe différent : `"search_query: "` pour les requêtes et `"search_document: "` pour les documents. Vérifier la compatibilité avec `OnnxEmbeddingModel` — le modèle utilise une architecture `nomic-bert` qui peut nécessiter `trust_remote_code` côté Python pour la conversion ONNX. [À VÉRIFIER : compatibilité complète avec le runtime ONNX Java de LangChain4j]

### Phase 3 — Migrations et optimisations avancées (< 1 mois)

**3.1 Migration vers nomic-embed-text-v1.5 si benchmarks positifs (3-5 jours)**

Si les benchmarks Phase 2 confirment un gain, migrer : exporter les chunks, réembedder en batch, recréer l'index HNSW, valider les métriques sur le golden set. Considérer l'utilisation de Matryoshka à 256d pour maintenir l'empreinte mémoire actuelle (256d halfvec ≈ 384d halfvec / 1.5).

**Estimation mémoire pour 500K vecteurs** :

|Configuration|Taille table|Taille index HNSW (m=16)|Total|
|---|---|---|---|
|bge-small 384d float32|~770 MB|~1.2-1.5 GB|~2-2.3 GB|
|bge-small 384d halfvec|~385 MB|~600-750 MB|~1-1.1 GB|
|nomic 768d halfvec|~770 MB|~1.2-1.5 GB|~2-2.3 GB|
|nomic 256d Matryoshka halfvec|~256 MB|~400-500 MB|~660-760 MB|

**3.2 Upgrade du cross-encoder reranker (2-3 jours)**

Remplacer ms-marco-MiniLM-L-6-v2 (22.7M params, 2021) par **bge-reranker-base** (110M params, significativement meilleur en ranking). ⚠️ LangChain4j n'a pas de module `langchain4j-onnx-scoring` officiel pour le reranking in-process ONNX (issue #1553 ouverte). Implémentation custom nécessaire en utilisant directement le runtime ONNX Java.

Alternative moins coûteuse : passer à **ms-marco-MiniLM-L-12-v2** (33M params, 12 couches au lieu de 6) — même architecture, ~2x plus lent, mais nettement meilleur en ranking. Drop-in replacement.

**Topologie de reranking recommandée** : retrieve top-30 avec embedding search → rerank → return top-5 au LLM. Avec MiniLM-L-6-v2 sur CPU, scorer 30 paires prend ~50-200ms — acceptable.

**3.3 Contextual Retrieval avec LLM local (1-2 semaines)**

Si le budget temps le permet, implémenter le Contextual Retrieval complet d'Anthropic en utilisant un LLM local. **Faisabilité CPU** : TinyLlama 1.1B (~2-4 GB RAM, 5-15s par génération sur 4 cores) ou Qwen2-1.5B. C'est un processus batch offline (lors de l'indexation), pas en temps réel, donc la latence est acceptable.

Benchmarks publiés par Anthropic : contextual embeddings seuls réduisent le taux d'échec de retrieval de **35%** (5.7% → 3.7%), et combiné avec BM25 contextuel + reranking, réduction de **67%** (5.7% → 1.9%).

**3.4 Considérer bge-m3 pour ColBERT reranking (2-3 semaines)**

Le package Java [yuniko-software/bge-m3-onnx](https://github.com/yuniko-software/bge-m3-onnx) fournit une implémentation ONNX Java de bge-m3 produisant simultanément des embeddings denses, sparse, et ColBERT. Utiliser ColBERT uniquement comme reranker (MaxSim sur les top-30 candidats) — PAS comme index primaire (stockage prohibitif : ~128KB par passage × 500K = ~64 GB).

**3.5 Fine-tuning embeddings sur votre domaine (1-2 semaines)**

Fine-tuner bge-small ou bge-base avec `sentence-transformers` en Python sur des paires synthétiques générées depuis votre documentation (200-10K paires). Workflow : générer données → fine-tune Python (30min-2h CPU pour bge-small) → exporter ONNX → déployer dans LangChain4j → réindexer. Le fine-tuning est la dernière optimisation à considérer — les gains sont généralement de 5-15% mais nécessitent un pipeline Python externe et une réindexation.

---

## 7. Pièges et limitations

**L'extraction séparée code/prose est votre plus gros risque.** Quand un utilisateur demande "comment configurer un DataSource Spring Boot", le chunk de code `@Bean DataSource dataSource()` et le chunk de prose expliquant les propriétés sont dans des vecteurs différents. L'embedding du code seul est faiblement corrélé avec la requête en langage naturel (bge-small score seulement 5.6 nDCG@10 sur les tâches de code pur). Le parent-child retrieval résout ce problème en réunissant code et prose au moment du retrieval.

**La quantization des embeddings (à ne pas confondre avec la quantization du modèle) dégrade significativement le recall à 384 dimensions.** La binary quantization pgvector (32x compression) cause 7-11% de perte de performance à 384d. Seule la scalar quantization halfvec (50% compression, ~0% perte) est recommandée à cette dimensionnalité. Pour utiliser la binary quantization efficacement, il faut un modèle à ≥ 768d et une stratégie de rescoring avec les vecteurs complets.

**Le changement de modèle d'embedding nécessite une réindexation complète.** Les vecteurs produits par bge-small (384d) et nomic-embed-text (768d) ne sont pas comparables — il est impossible de migrer progressivement. Planifier une fenêtre de maintenance pour la réindexation. Pour 500K chunks avec nomic sur CPU, estimer ~7-8 heures d'embedding (200ms × 500K / 256 batch × overhead).

**LangChain4j 1.11.0 n'expose pas le HNSW pour pgvector via son builder.** L'option `useIndex(true)` crée un index IVFFlat, pas HNSW. Vous devez créer l'index HNSW manuellement via SQL directement sur PostgreSQL, ce que vous faites déjà.

**Le module de scoring ONNX in-process n'existe pas dans LangChain4j (issue #1553).** Votre cross-encoder ms-marco-MiniLM-L-6-v2 ONNX fonctionne probablement via une implémentation custom. Les `ScoringModel` officiels LangChain4j (Cohere, Jina, Vertex AI) sont des API cloud. Vérifier que votre implémentation custom reste compatible avec les futures versions de LangChain4j.

**nomic-embed-text-v1.5 utilise une architecture custom (nomic-bert) qui peut poser des problèmes ONNX.** La conversion nécessite `trust_remote_code=True` côté Python. Vérifier que le tokenizer exporté est compatible avec le runtime ONNX Java de LangChain4j avant de lancer un benchmark complet. [À VÉRIFIER]

**Le semantic chunking est contre-productif pour le code.** Les algorithmes de semantic chunking basés sur la similarité cosine entre phrases consécutives se "confondent" face aux blocs de code dont les embeddings diffèrent radicalement de la prose environnante. Ils tendent à créer un split avant et après chaque bloc de code, isolant le code de son contexte — exactement le problème que vous cherchez à résoudre. L'approche "semantic double-pass merging" (BitPeak) atténue ce problème, mais ajoute de la complexité.

**HyDE est impraticable sur votre infrastructure CPU.** La génération d'un document hypothétique par query nécessite un LLM. Même TinyLlama (1.1B) ajouterait 5-15 secondes de latence par requête sur 4 cores CPU. HyDE est utile en batch offline pour l'évaluation, pas en production interactive.

**Attention à l'overlap avec le parent-child retrieval.** Si vous implémentez le parent-child chunking, l'overlap sur les child chunks devient largement redondant puisque le parent fournit déjà le contexte complet. Réduire ou supprimer l'overlap sur les children pour éviter la redondance (l'étude Chroma 2024 confirme que "reducing overlap improves IoU scores").

**La recherche hybride nécessite des noms de colonnes corrects.** Le mode `HYBRID` de `PgVectorEmbeddingStore` crée un index GIN tsvector sur la colonne de texte. Vérifier que votre schéma est compatible et que la configuration `textSearchConfig("english")` est appropriée pour de la documentation technique en anglais.

---

## Conclusion

Votre stack actuelle est fonctionnelle mais laisse un potentiel significatif sur la table. Le problème le plus critique n'est ni le modèle d'embedding ni les paramètres HNSW — **c'est l'extraction séparée du code et de la prose** qui brise le lien sémantique entre explication et exemple. La correction de ce problème via un parent-child retrieval, combinée aux quick wins (préfixe BGE, halfvec, recherche hybride, breadcrumbs), devrait produire un gain cumulé de **20-40% sur le recall** sans changer de modèle d'embedding.

La migration vers nomic-embed-text-v1.5 est le meilleur candidat si un changement de modèle est envisagé — non pas tant pour son léger avantage retrieval (+1.3 points nDCG@10), mais pour son contexte **16x plus long** (8192 vs 512 tokens) et son support Matryoshka qui offre une flexibilité stockage/performance que bge-small ne peut pas offrir. Le gain le plus sous-estimé dans les systèmes RAG documentation technique reste la **recherche hybride BM25+vecteur** — les identifiants techniques (`@Autowired`, `spring.datasource.url`, `RuntimeException`) sont des correspondances exactes que les embeddings sémantiques approximent mal. L'activation du mode hybride LangChain4j/pgvector corrige ce problème en une ligne de configuration.