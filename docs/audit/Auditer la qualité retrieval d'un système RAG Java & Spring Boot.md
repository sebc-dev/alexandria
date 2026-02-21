# Auditer la qualité retrieval d'un système RAG Java/Spring Boot

**La première action à entreprendre est d'implémenter ~200 lignes de code Java pour calculer Recall@k, MRR, NDCG@k, puis de construire un golden set de 50-100 requêtes annotées, et enfin d'intégrer le tout dans JUnit 5 avec Testcontainers pgvector.** Aucun framework d'évaluation RAG n'offre de bibliothèque Java native — RAGAS, DeepEval, continuous-eval et TruLens sont tous Python-only. L'approche la plus efficace pour un développeur solo sur stack Java consiste à coder les métriques IR directement (formules simples de 10-20 lignes chacune), stocker le golden set en JSON versionné dans Git, et utiliser les tests paramétrés JUnit 5 comme harnais d'évaluation avec assertions-seuils comme quality gates CI/CD. Les trois leviers d'optimisation à tester en priorité sont : **ef_search** pgvector (paramètre runtime, zéro rebuild), le paramètre **k** du RRF vs Convex Combination, et le nombre de candidats reranking (30/50/75).

---

## 1. Résumé exécutif

### Top 3 actions prioritaires

**Action 1 — Métriques Java custom + golden set minimal (2 jours).** Créer une classe `RetrievalMetrics` implémentant Recall@k, Precision@k, MRR, NDCG@k, MAP et Hit Rate. Annoter manuellement 30 requêtes couvrant les 4 types (factuelle, conceptuelle, code lookup, troubleshooting). Écrire un test JUnit 5 paramétré qui charge le golden set JSON et asserte des seuils minimaux.

**Action 2 — Ablation study hybride (3 jours).** Exécuter vector-only, FTS-only, hybride RRF, hybride+reranking sur le golden set. Mesurer NDCG@10, Recall@10, MRR pour chaque configuration. Identifier les types de requêtes où chaque composant apporte ou dégrade la qualité.

**Action 3 — CI/CD quality gates + monitoring Micrometer (5 jours).** Intégrer la test suite dans Gradle avec `@Tag("retrieval-quality")`, archiver les métriques en CSV, exposer les métriques runtime (latence, empty rate, score distribution) via Micrometer/Prometheus.

### Stack d'audit recommandée

|Composant|Outil|Justification|
|---|---|---|
|Métriques IR|Java custom (~200 LOC)|Zéro dépendance, contrôle total|
|Golden set|JSON versionné + Jackson|Simple, diffable dans Git|
|Test harness|JUnit 5 `@ParameterizedTest` + Testcontainers pgvector|Déjà dans la stack|
|Validation externe|`trec_eval` CLI (optionnel)|Standard NIST, vérification croisée|
|Visualisation|Phoenix Arize self-hosted (optionnel)|REST API, Apache 2.0, Docker|
|Monitoring prod|Micrometer → Prometheus → Grafana|Standard Spring Boot|

---

## 2. Tableau comparatif des frameworks d'évaluation RAG

|Framework|Métriques IR LLM-free|Compat. Java|LLM requis ?|Effort intégration|Licence|Score /5|Confiance|
|---|---|---|---|---|---|---|---|
|**Java custom**|Recall, Precision, MRR, NDCG, MAP, Hit Rate, F1 — toutes|✅ Natif|Non|1-2 jours|N/A|⭐⭐⭐⭐⭐|Élevé|
|**continuous-eval** (Relari)|PrecisionRecallF1, MAP, MRR, NDCG — toutes|❌ Python-only, pas de REST API|Non pour IR metrics|Élevé (sidecar Python)|Apache 2.0|⭐⭐⭐⭐|Élevé|
|**ranx**|Toutes métriques IR (40+)|❌ Python-only|Non|Élevé (sidecar Python)|MIT|⭐⭐⭐⭐|Élevé|
|**trec_eval**|Toutes métriques IR standard|✅ CLI appelable via ProcessBuilder|Non|Faible|Public domain|⭐⭐⭐⭐|Élevé|
|**RAGAS**|NonLLMContextPrecision/Recall (set-based), ID-based P/R|❌ Python CLI|Oui pour la plupart|Moyen (CLI/sidecar)|Apache 2.0|⭐⭐⭐|Élevé|
|**Phoenix/Arize**|Aucune built-in, mais REST API pour stocker résultats|⚠️ Tracing Java SDK, REST API|Oui pour évaluations|Moyen (Docker + REST)|Apache 2.0|⭐⭐⭐|Élevé|
|**DeepEval**|Minimal (presque tout LLM-based)|⚠️ REST wrapper communautaire Docker|Oui quasi-systématiquement|Moyen|Apache 2.0|⭐⭐|Moyen|
|**TruLens**|BLEU/ROUGE ; P@k/NDCG [INCERTAIN si LLM-free]|❌ Python-only|Probablement oui|Élevé|MIT|⭐⭐|Faible|
|**LangSmith**|Aucune built-in|REST API (cloud)|Custom code|N/A|Propriétaire|⭐|Élevé|
|**LangChain4j**|**Aucune capacité d'évaluation**|✅ Java natif|N/A|N/A|Apache 2.0|⭐|Élevé|

**Verdict : L'implémentation Java custom est la seule option réaliste** pour un développeur solo sans budget, sans Python, sans GPU. Les formules IR sont triviales mathématiquement. Pour validation croisée, `trec_eval` (binaire C) s'appelle depuis Java en une ligne de ProcessBuilder.

---

## 3. Guide des métriques retrieval

### Quelle métrique prioriser pour la documentation technique

Pour un système RAG de documentation technique, **Recall@k est la métrique primaire** car manquer un document pertinent est irréversible — le LLM ne peut pas inventer l'information absente, mais il peut généralement ignorer le bruit. **NDCG@10 est la métrique secondaire** car elle capture la qualité du ranking avec relevance gradée, et c'est la métrique canonique du benchmark BEIR (Thakur et al., NeurIPS 2021). **MRR** est critique pour les requêtes factuelles/code lookup où l'utilisateur cherche une réponse unique.

### Tableau des métriques

|Métrique|Quand l'utiliser|Formule|Seuil recommandé|Note seuil|
|---|---|---|---|---|
|**Recall@10**|Métrique primaire — complétude du contexte RAG|`\|relevant ∩ top-k\| / \|relevant\|`|≥ 0.80|[CONVENTION] Variable selon difficulté corpus|
|**NDCG@10**|Qualité du ranking avec relevance gradée, métrique BEIR|`DCG@k / IDCG@k` où `DCG = Σ (2^rel - 1) / log₂(i+1)`|≥ 0.55|[CONVENTION] BEIR : BM25 moyen ~0.43, top modèles ~0.50-0.55|
|**MRR**|Requêtes factuelles, code lookup — position du 1er résultat pertinent|`(1/\|Q\|) Σ 1/rank_i`|≥ 0.70|[CONVENTION] sbert.net montre MRR@10 ~0.48-0.60 pour cross-encoders|
|**Precision@5**|Contrôle du bruit quand fenêtre contexte LLM limitée|`\|relevant ∩ top-k\| / k`|≥ 0.60|[CONVENTION]|
|**Hit Rate@5**|Sanity check développement — "au moins un résultat utile"|`\|{q : relevant(q) ∩ top-k ≠ ∅}\| / \|Q\|`|≥ 0.90|[CONVENTION]|
|**MAP**|Score synthétique unique, utilisé dans TREC|`mean(AP)` où `AP = (1/\|Rel\|) Σ P@k × rel(k)`|≥ 0.40|[CONVENTION] TREC evaluations typiques|
|**F1@k**|Équilibre précision/rappel, utile pour choisir k optimal|`2 × P@k × R@k / (P@k + R@k)`|≥ 0.50|[CONVENTION]|

**Caveat important sur les seuils** : tous les seuils ci-dessus sont des conventions praticien, pas des standards empiriques universels. Le paper BEIR montre que les performances varient dramatiquement par dataset (NDCG@10 de 0.15 sur ArguAna à 0.87 sur Quora pour le même modèle). Les seuils doivent être calibrés sur votre corpus spécifique.

### Métriques spécifiques recherche hybride

**Contribution relative vector vs FTS** : la méthode est l'**ablation study**. Exécuter trois évaluations parallèles (vector-only, FTS-only, hybride) sur le même golden set. Pour chaque requête, tracker quels documents pertinents sont trouvés _uniquement_ par vector, _uniquement_ par FTS, ou par les deux. Ce "unique recall" par méthode identifie la contribution marginale de chaque composant. La documentation technique présente un pattern caractéristique : le FTS excelle sur les codes d'erreur, noms de fonctions et identifiants exacts, tandis que le vector search excelle sur les requêtes conceptuelles.

**Apport du reranking** : comparer NDCG@k, MRR@10, MAP **avant et après** reranking sur le même ensemble de candidats. La bibliothèque sentence-transformers fournit un `CrossEncoderRerankingEvaluator` qui reporte exactement ces deltas. Sur NanoBEIR, ms-marco-MiniLM-L6-v2 montre : MAP 48.96→60.35, MRR@10 47.75→59.63, NDCG@10 54.04→66.86 sur NanoMSMARCO.

**Détection de dégradation RRF** : pour chaque requête, calculer NDCG@10 pour vector-only, FTS-only, et hybride RRF. Les requêtes où `NDCG_hybrid < max(NDCG_vector, NDCG_fts)` sont des cas de dégradation RRF. Cela arrive typiquement quand un retriever est significativement plus fort que l'autre pour un type de requête donné.

### Métriques contextuelles sans LLM externe

|Approche|Ce qu'elle mesure|LLM requis ?|Qualité|
|---|---|---|---|
|IR metrics classiques (R@k, NDCG, MRR) + golden set|Qualité retrieval directe|Non|Élevée (gold standard)|
|Cosine similarity embedding query↔chunks|Relevance contexte approchée|Non (utilise le modèle embedding existant)|Moyenne|
|ROUGE-L precision (answer vs context)|Faithfulness approchée|Non|Basse — ne détecte pas les erreurs factuelles|
|Modèle NLI (Natural Language Inference)|Entailment answer↔context|Non (modèle NLI local)|Moyenne-haute|
|Token overlap precision|Grounding basique|Non|Basse|

**Faithfulness sans LLM** : strictement parlant, évaluer si une réponse est fidèle au contexte récupéré nécessite une compréhension sémantique profonde. Un modèle NLI local (ex: DeBERTa-v3-base-mnli-fever-anli, ~180M params, CPU-compatible) peut classifier si les claims de la réponse sont "entailed" par le contexte. C'est l'approche la plus fiable sans LLM génératif. Le token overlap (ROUGE-L) est un proxy grossier qui ne détecte pas les fabrications subtiles.

---

## 4. Méthodologie golden set

### Taille minimale

La littérature IR académique converge : **50 requêtes minimum** pour des comparaisons statistiquement significatives avec des métriques stables (MAP, NDCG). Voorhees (2009, SIGIR, "Topic Set Size Redux") démontre que 50 topics sont insuffisants pour des métriques instables comme P@10. Sakai (2016, Information Retrieval Journal) propose des méthodes formelles de dimensionnement. Pour un développeur solo, **commencer à 50 requêtes, viser 100+ pour détecter des améliorations fines** (ex: tuning reranking). Au-delà de 200 requêtes, les rendements sont décroissants sauf pour des domaines à très haute variance.

### Template structure golden set

```json
{
  "version": "1.0",
  "created": "2026-02-20",
  "metadata": {
    "corpus_version": "docs-v2.3",
    "total_queries": 80,
    "relevance_scale": "binary"
  },
  "queries": [
    {
      "id": "q001",
      "text": "How to configure HNSW index parameters in pgvector?",
      "type": "HOW_TO",
      "content_type": "PROSE",
      "expected_section_prefix": "configuration/pgvector",
      "relevant_chunks": [
        {"chunk_id": "chunk-042", "relevance": 1},
        {"chunk_id": "chunk-088", "relevance": 1}
      ],
      "irrelevant_distractors": ["chunk-201", "chunk-305"]
    },
    {
      "id": "q002",
      "text": "EmbeddingStore.search maxResults example",
      "type": "CODE_LOOKUP",
      "content_type": "CODE",
      "relevant_chunks": [
        {"chunk_id": "chunk-117", "relevance": 1}
      ]
    }
  ]
}
```

### Stratégie de sampling par type de requête

|Type de requête|Proportion cible|Caractéristiques|Retriever dominant attendu|
|---|---|---|---|
|**Factuelle**|25%|Réponse unique, termes précis. Ex: "What is the default ef_search value?"|FTS (termes exacts)|
|**Conceptuelle**|25%|Explication, compréhension. Ex: "How does HNSW approximate nearest neighbor search?"|Vector (sémantique)|
|**Code lookup**|25%|Recherche d'exemple de code, API. Ex: "PgVectorEmbeddingStore search example"|Hybride (noms de classe + contexte)|
|**Troubleshooting**|15%|Messages d'erreur, debugging. Ex: "hnsw graph no longer fits into maintenance_work_mem"|FTS (message exact)|
|**How-to / Procédural**|10%|Instructions étape par étape. Ex: "How to enable pgvector extension?"|Hybride|

### Workflow de création (solo developer)

**Semaine 1 — Bootstrap semi-automatique.** Générer 50 requêtes synthétiques depuis les chunks existants avec un LLM local (Ollama + Llama/Mistral). Prompt : donner un chunk de documentation, demander au LLM de générer 1-2 questions auxquelles ce chunk répond. Filtrer les questions triviales. Pré-annoter la relevance automatiquement (le chunk source = relevant).

**Semaine 2 — Validation humaine.** Exécuter la pipeline retrieval sur les 50 requêtes. Examiner manuellement les top-10 résultats de chaque requête. Corriger les annotations erronées. Ajouter des documents pertinents manqués dans les jugements. Ceci transforme le "silver set" en "gold set".

**Semaine 3+ — Enrichissement continu.** Ajouter 20-30 requêtes manuelles basées sur les cas d'utilisation réels et les échecs connus. Ajouter 5-10 requêtes par semaine depuis les logs production. Versionner le golden set avec Git (`golden-set-v1.0.json`, `v1.1`, etc.).

### Formats standard et outils

Le format **BEIR** (corpus.jsonl + queries.jsonl + qrels.tsv) est le standard de facto pour l'évaluation retrieval. Le format **TREC qrels** (query_id, iteration, doc_id, relevance) est le standard NIST utilisé par trec_eval. Pour un système Java, un JSON custom (comme ci-dessus) est plus ergonomique et se charge directement avec Jackson. Convertir vers TREC qrels pour validation avec trec_eval est trivial.

**Outils d'annotation** : pour un développeur solo, un simple spreadsheet (CSV/Google Sheets) avec colonnes query, doc_id, snippet, relevance est le plus rapide. **Argilla** (open-source, déployable sur Docker) offre une interface web dédiée avec support de rating/ranking. **Label Studio** offre des templates spécifiques pour l'annotation de pertinence retrieval.

---

## 5. Guide de paramétrage et benchmarking

### Paramètres HNSW pgvector

|Paramètre|Valeur actuelle|Plage recommandée|Impact|Méthodologie test|
|---|---|---|---|---|
|**ef_search**|40 (défaut pgvector)|**100-200**|Recall↑, latence↑ — levier #1, runtime, zéro rebuild|`SET hnsw.ef_search = X;` puis mesurer recall vs exact (brute-force) pour X ∈ {40, 80, 100, 150, 200, 400}|
|**m**|16|**16-24**|Recall↑, taille index↑ ~50%, build time↑|Rebuild index avec m=16 vs m=24, mesurer recall@50 vs brute-force. Pour 384d/500K vecs, m=16 est approprié|
|**ef_construction**|64|**128-200**|Qualité graphe↑, build time↑ — doit être ≥ 2×m|Rebuild avec ef_construction=128, 200. One-time cost. Valeurs ANN-Benchmarks : 100, 200|

**Méthodologie recall HNSW vs brute-force** : (1) Désactiver l'index HNSW avec `SET enable_indexscan = off; SET enable_indexonlyscan = off;` pour forcer le scan séquentiel exact. (2) Pour N requêtes de test, récupérer top-50 en exact et top-50 en HNSW. (3) Calculer `recall_hnsw@50 = |exact_top50 ∩ hnsw_top50| / 50` par requête, puis moyenner. (4) Cible : recall HNSW ≥ 0.95 pour ne pas introduire d'erreur d'approximation significative dans le pipeline.

**Considérations mémoire** : pour 500K vecteurs de 384 dimensions en float32, les données vectorielles seules ≈ 730 MB. L'index HNSW avec m=16 ajoute environ 500 MB - 1 GB. `maintenance_work_mem` doit être ≥ 1-2 GB pendant la construction de l'index, sinon pgvector bascule en construction on-disk significativement plus lente (message "hnsw graph no longer fits into maintenance_work_mem"). Avec 24 GB RAM total et ~14 GB pour la stack applicative, cela laisse suffisamment de marge.

### Paramètre RRF k et alternatives de fusion

|Paramètre|Valeur actuelle|Plage recommandée|Méthodologie test|
|---|---|---|---|
|**RRF k**|60|**20-80**|Sweep k ∈ {20, 40, 60, 80} sur golden set, mesurer NDCG@10|
|**Convex Combination α**|Non implémenté|**0.3-0.7**|Normaliser scores vector et FTS (min-max), sweep α ∈ {0.0, 0.1, ..., 1.0}|

**Fait établi** : Bruch et al. (2022, ACM TOIS, "An Analysis of Fusion Functions for Hybrid Retrieval") démontrent que **Convex Combination (CC) surpasse systématiquement RRF** sur tous les corpus testés, en NDCG in-domain et out-of-domain. CC est plus sample-efficient (une poignée de requêtes annotées suffit pour tuner α) et plus interprétable. **RRF discard l'information de distribution des scores** — il n'utilise que les rangs, ce qui est une limitation fondamentale. Considérer le remplacement de RRF par CC est potentiellement le changement ayant le plus fort impact sur la qualité retrieval.

**Implémentation CC en Java** : normaliser les scores cosine similarity (vector) et les scores FTS (ts_rank) via min-max sur le batch de résultats, puis `score_hybrid = α × score_vector_norm + (1-α) × score_fts_norm`. Tester α = 0.5 comme point de départ, puis optimiser sur le golden set.

**Cas où RRF dégrade** : quand un retriever est significativement plus fort que l'autre pour un type de requête, RRF dilue son signal en injectant des résultats de moindre qualité du retriever plus faible. Quand l'espacement des scores est informatif (un résultat avec score 0.95 vs un autre à 0.30), RRF perd cette information en ne regardant que les rangs.

### Cross-encoder reranking

|Paramètre|Valeur actuelle|Plage recommandée|Méthodologie test|
|---|---|---|---|
|**Nombre de candidats**|50|**30-75**|Mesurer NDCG@10 et latence pour n_candidates ∈ {30, 50, 75, 100}|
|**Modèle**|ms-marco-MiniLM-L-6-v2|Voir alternatives ci-dessous|A/B sur golden set|
|**minScore seuil**|Optionnel|À calibrer empiriquement|Histogramme des scores reranker, identifier le gap naturel|

**Performance CPU du ms-marco-MiniLM-L6-v2** (benchmarks Metarank, ONNX sur CPU) : **~12 ms pour 1 paire, ~59 ms pour 10 paires, ~740 ms pour 100 paires**. Pour 50 candidats, estimer **~350-400 ms** [INCERTAIN — interpolé]. Avec quantization int8 ONNX, sentence-transformers rapporte un speedup **~3× sans perte de qualité significative**.

**Qualité** (sbert.net) : sur TREC DL 2019, NDCG@10 = **74.30**, MRR@10 MS Marco Dev = **39.01**. Le modèle L12-v2 (74.31 NDCG@10) est quasi-identique en qualité mais 2× plus lent. Le L6-v2 offre le meilleur ratio qualité/performance sur CPU.

**Attention au "Drowning in Documents"** (arxiv 2411.11767, SIGIR ReNeuIR 2025) : augmenter naïvement le nombre de candidats peut **dégrader le recall** à cause de "phantom hits" — les rerankers attribuent parfois des scores élevés à des documents complètement non pertinents. Ne pas dépasser 75-100 candidats sans validation empirique.

**Alternatives CPU-friendly** :

|Modèle|Params|Qualité relative|Latence relative CPU|Recommandation|
|---|---|---|---|---|
|ms-marco-MiniLM-L6-v2|22.7M|Baseline|1×|✅ Défaut recommandé|
|ms-marco-TinyBERT-L2-v2|~15M|-6% NDCG@10|~0.3× (5× plus rapide)|Si latence critique|
|bge-reranker-base (BAAI)|278M|+qualité [INCERTAIN]|~3-5× plus lent|Si qualité insuffisante|
|jina-reranker-v2-base|278M|Comparable bge-reranker|~3-5× plus lent|Alternative à bge|

**Le ms-marco-MiniLM-L6-v2 est-il optimal pour la doc technique ?** Il est entraîné sur MS Marco (requêtes web Bing), donc général et non spécialisé technique. Pour la documentation technique avec du code, les noms de fonctions, classes et API sont importants. Le modèle gère correctement le texte technique en anglais mais n'a pas de compréhension spécifique du code. Un fine-tuning sur des paires (query, passage) issues de votre documentation améliorerait la qualité, mais c'est un investissement significatif pour un dev solo. **Recommandation : garder L6-v2 comme baseline, mesurer la qualité, ne fine-tuner que si les métriques sont insuffisantes.**

---

## 6. Plan d'implémentation

### Phase 1 — Quick wins (< 2 jours)

**Jour 1 matin : Classe RetrievalMetrics (~200 LOC)**

```java
public final class RetrievalMetrics {

    public static double recallAtK(List<String> retrieved, Set<String> relevant, int k) {
        if (relevant.isEmpty()) return 1.0;
        long hits = retrieved.stream().limit(k).filter(relevant::contains).count();
        return (double) hits / relevant.size();
    }

    public static double precisionAtK(List<String> retrieved, Set<String> relevant, int k) {
        long hits = retrieved.stream().limit(k).filter(relevant::contains).count();
        return (double) hits / k;
    }

    public static double mrr(List<String> retrieved, Set<String> relevant) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (relevant.contains(retrieved.get(i))) return 1.0 / (i + 1);
        }
        return 0.0;
    }

    public static double ndcgAtK(List<String> retrieved, Map<String, Integer> relevanceGrades, int k) {
        double dcg = 0.0, idcg = 0.0;
        for (int i = 0; i < Math.min(k, retrieved.size()); i++) {
            int rel = relevanceGrades.getOrDefault(retrieved.get(i), 0);
            dcg += (Math.pow(2, rel) - 1) / log2(i + 2);
        }
        List<Integer> idealRels = relevanceGrades.values().stream()
            .sorted(Comparator.reverseOrder()).limit(k).toList();
        for (int i = 0; i < idealRels.size(); i++) {
            idcg += (Math.pow(2, idealRels.get(i)) - 1) / log2(i + 2);
        }
        return idcg == 0 ? 0.0 : dcg / idcg;
    }

    public static double hitRateAtK(List<String> retrieved, Set<String> relevant, int k) {
        return retrieved.stream().limit(k).anyMatch(relevant::contains) ? 1.0 : 0.0;
    }

    public static double map(List<String> retrieved, Set<String> relevant) {
        if (relevant.isEmpty()) return 1.0;
        double sumPrecision = 0.0;
        int relevantFound = 0;
        for (int i = 0; i < retrieved.size(); i++) {
            if (relevant.contains(retrieved.get(i))) {
                relevantFound++;
                sumPrecision += (double) relevantFound / (i + 1);
            }
        }
        return sumPrecision / relevant.size();
    }

    private static double log2(double x) { return Math.log(x) / Math.log(2); }
}
```

**Jour 1 après-midi : Golden set minimal (30 requêtes)**

Créer `src/test/resources/golden-set-v1.json` avec 30 requêtes réparties : 8 factuelles, 8 conceptuelles, 8 code lookup, 6 troubleshooting. Pour chaque requête, lister les chunk_ids pertinents en examinant manuellement les top-10 résultats du système actuel.

**Jour 2 : Test JUnit 5 paramétré**

```java
@SpringBootTest
@Testcontainers
@Tag("retrieval-quality")
class RetrievalQualityIT {

    @Container
    static PostgreSQLContainer<?> pg =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withInitScript("init-pgvector.sql");

    @ParameterizedTest(name = "[{0}] {1}")
    @MethodSource("goldenSetProvider")
    void retrievalMeetsQualityThresholds(String queryId, String queryText,
            Set<String> relevantIds, Map<String, Integer> grades) {
        List<String> retrieved = retrievalService.retrieve(queryText, 10)
            .stream().map(doc -> doc.metadata("chunk_id")).toList();

        double recall = RetrievalMetrics.recallAtK(retrieved, relevantIds, 10);
        double mrr = RetrievalMetrics.mrr(retrieved, relevantIds);
        double ndcg = RetrievalMetrics.ndcgAtK(retrieved, grades, 10);

        // Per-query assertions (soft — logged but don't fail individually)
        metricsCollector.record(queryId, recall, mrr, ndcg);
    }

    @AfterAll
    static void assertAggregateThresholds() {
        assertThat(metricsCollector.meanRecallAt10())
            .as("Mean Recall@10").isGreaterThanOrEqualTo(0.70);
        assertThat(metricsCollector.meanMRR())
            .as("Mean MRR").isGreaterThanOrEqualTo(0.60);
        // Export CSV for trend tracking
        metricsCollector.exportCsv(Path.of("build/retrieval-metrics/"));
    }
}
```

**Quick win immédiat : augmenter ef_search.** Exécuter `SET hnsw.ef_search = 200;` et re-lancer les tests. C'est un paramètre runtime — aucun rebuild d'index nécessaire. Mesurer le delta recall HNSW.

### Phase 2 — Golden set complet + ablation study (< 1 semaine)

**Jours 3-4 : Expansion golden set à 80-100 requêtes.** Utiliser un LLM local (Ollama) pour générer des requêtes synthétiques à partir des chunks, puis valider manuellement. Ajouter les cas d'échec connus et les requêtes edge-case (requêtes ambiguës, requêtes sans réponse dans le corpus).

**Jour 5 : Ablation study.** Implémenter un test paramétré `@CsvSource` qui teste 4 configurations :

```java
@ParameterizedTest(name = "mode={0}, rerank={1}")
@CsvSource({"VECTOR_ONLY,false", "FTS_ONLY,false", "HYBRID_RRF,false", "HYBRID_RRF,true"})
void ablationStudy(RetrievalMode mode, boolean withReranking) {
    Map<String, Double> metrics = evaluateFullGoldenSet(mode, withReranking);
    metricsReporter.recordAblation(mode, withReranking, metrics);
}
```

**Jours 6-7 : Tuning RRF k + test Convex Combination.** Sweep k ∈ {20, 40, 60, 80} et α ∈ {0.3, 0.4, 0.5, 0.6, 0.7} sur le golden set. Implémenter CC comme alternative à RRF et comparer les résultats.

### Phase 3 — Monitoring + CI/CD (< 1 mois)

**Semaine 2 : CI/CD quality gates Gradle**

```kotlin
// build.gradle.kts
tasks.register<Test>("retrievalQualityTest") {
    description = "Retrieval quality regression tests"
    group = "verification"
    useJUnitPlatform { includeTags("retrieval-quality") }
    systemProperty("metrics.output.dir",
        layout.buildDirectory.dir("retrieval-metrics").get().asFile.path)
}
tasks.named("check") { dependsOn("retrievalQualityTest") }
```

Fréquence recommandée : les tests retrieval quality avec Testcontainers prennent typiquement 30-120 secondes (ingestion + 100 requêtes + calcul métriques). **Exécuter sur chaque PR/push** est viable. Si trop lent, exécuter nightly avec `@Tag("retrieval-quality-full")` et garder un subset de 20 requêtes critiques sur chaque push.

**Semaines 3-4 : Monitoring production Micrometer**

```java
@Service
public class RetrievalObservability {
    private final Timer retrievalTimer;
    private final Timer rerankTimer;
    private final Counter emptyResultCounter;
    private final DistributionSummary topScoreDistribution;

    public RetrievalObservability(MeterRegistry registry) {
        this.retrievalTimer = Timer.builder("rag.retrieval.duration")
            .publishPercentiles(0.5, 0.95, 0.99).register(registry);
        this.rerankTimer = Timer.builder("rag.rerank.duration")
            .publishPercentiles(0.5, 0.95, 0.99).register(registry);
        this.emptyResultCounter = Counter.builder("rag.retrieval.empty").register(registry);
        this.topScoreDistribution = DistributionSummary.builder("rag.retrieval.top_score")
            .publishPercentiles(0.25, 0.5, 0.75).register(registry);
    }
}
```

Métriques clés à surveiller :

- **`rag.retrieval.duration` P95** : dérive de latence (impact ef_search, nombre candidats reranking)
- **`rag.retrieval.empty` rate** : indicateur de recall systémique — si >5%, problème grave
- **`rag.retrieval.top_score` distribution** : dérive de qualité — un shift vers le bas indique dégradation
- **`rag.rerank.duration` P95** : monitoring spécifique du cross-encoder
- **Résultat count distribution** : vérifier que maxResults est atteint régulièrement

Exposer via `management.endpoints.web.exposure.include=prometheus,health,metrics` et scraper avec Prometheus. Dashboard Grafana avec alertes sur : empty rate > 5%, P95 latence > 2s, top_score médian en baisse de >20% sur 24h.

---

## 7. Pièges et limitations

### Incompatibilités Java critiques

**L'écosystème RAG evaluation est à 95% Python.** RAGAS, DeepEval, TruLens, continuous-eval, ranx — tous Python-only sans REST API officielle. LangChain4j **n'a aucun module d'évaluation**. La seule voie Java réaliste est l'implémentation custom des métriques IR (formules triviales) ou l'appel à `trec_eval` en CLI. Ne pas perdre de temps à chercher un framework Java d'évaluation RAG — il n'existe pas.

**Phoenix/Arize est l'exception partielle** : il offre un serveur self-hosted avec REST API (Docker) qui peut stocker et visualiser des résultats d'évaluation postés depuis Java. Mais les métriques doivent être calculées côté Java et envoyées via HTTP POST. C'est utile pour la visualisation, pas pour le calcul.

### Erreurs courantes d'évaluation RAG

**Ne pas évaluer uniquement en agrégat.** Un MRR moyen de 0.75 peut masquer un échec total sur les requêtes troubleshooting (MRR = 0.2) compensé par d'excellents résultats sur les requêtes factuelles (MRR = 0.95). **Toujours segmenter les métriques par type de requête.**

**Ne pas confondre recall HNSW et recall retrieval.** Le recall HNSW mesure l'approximation de l'index (HNSW vs brute-force exact). Le recall retrieval mesure la pertinence des résultats vs le golden set. Ce sont deux métriques distinctes à mesurer séparément. Un recall HNSW de 0.99 n'implique pas un bon recall retrieval si les embeddings sont mauvais.

**Ne changer qu'une variable à la fois** entre les runs de test. Changer simultanément ef_search et le paramètre k du RRF rend impossible l'attribution de l'impact.

**Les métriques IR classiques ont des limites fondamentales pour le RAG** : un paper de 2025 ("Redefining Retrieval Evaluation in the Era of LLMs", arxiv 2510.21440) argue que NDCG/MAP/MRR supposent un utilisateur humain qui examine les résultats séquentiellement avec attention décroissante, alors qu'un LLM traite tous les documents récupérés comme un bloc. Des documents "liés mais non pertinents" peuvent activement dégrader la qualité de génération (effet de distraction) — un aspect non capturé par les métriques classiques. [TENDANCE OBSERVÉE — adoption de nouvelles métriques incertaine]

**BLEU/ROUGE sont inadéquats pour l'évaluation RAG** : ils mesurent le chevauchement de surface et ne détectent pas les erreurs factuelles ou les informations manquantes.

### LLM externe vs calculable localement — matrice de décision

|Besoin d'évaluation|Sans LLM|Avec LLM local (Ollama)|Avec LLM API|
|---|---|---|---|
|Qualité retrieval (R@k, NDCG, MRR)|✅ Golden set suffit|Non nécessaire|Non nécessaire|
|Génération de golden set queries|❌ Manuel uniquement|✅ Génération semi-auto|✅ Meilleure qualité|
|Context relevance|⚠️ Proxy : cosine similarity|✅ NLI ou LLM-as-judge local|✅ Meilleure qualité|
|Faithfulness|⚠️ Proxy : token overlap|⚠️ NLI local ou petit LLM|✅ Seule option fiable|
|Answer correctness|❌|⚠️ LLM-as-judge local (qualité variable)|✅ Meilleure qualité|

**Recommandation pragmatique** : pour un dev solo sans budget, se concentrer exclusivement sur les métriques retrieval calculables avec golden set (Recall@k, NDCG@k, MRR). C'est là que 80% de la valeur se trouve. L'évaluation de faithfulness et context relevance avec LLM-as-judge est un luxe qui peut attendre que la qualité retrieval soit stabilisée. Un LLM local via Ollama (Mistral 7B, Llama 3.1 8B) peut servir pour la génération de golden set queries et un LLM-as-judge basique, mais les résultats seront moins fiables qu'avec un modèle frontier.

### Pièges spécifiques au cross-encoder reranking

Le paper "Drowning in Documents" (SIGIR ReNeuIR 2025) identifie des **"phantom hits"** — les cross-encoders attribuent parfois des scores très élevés à des documents totalement non pertinents. Cela signifie qu'augmenter naïvement le nombre de candidats à reranker peut paradoxalement **diminuer** le recall. **Toujours valider empiriquement** le nombre optimal de candidats sur votre golden set. Le seuil minScore du reranker est un filet de sécurité important contre ce phénomène.

### Considération sur bge-small-en-v1.5-q

Ce modèle d'embedding (33M params, 384d, sorti en septembre 2023) est **toujours fonctionnel mais n'est plus compétitif au sommet** du MTEB leaderboard. Pour un système en production avec des contraintes CPU, il reste un choix défendable grâce à sa petite taille et sa vitesse. Cependant, si les métriques retrieval sont insuffisantes après optimisation des paramètres HNSW/RRF/reranking, le remplacement du modèle d'embedding par un modèle plus récent (BGE-M3, ou un modèle compact fine-tuné sur votre domaine) pourrait avoir l'impact le plus significatif sur la qualité. Le fine-tuning d'embeddings sur des paires (query, passage) spécifiques à votre documentation technique peut apporter **+10-30% de gain** selon la littérature, mais c'est un investissement conséquent.

---

## Conclusion

L'audit de qualité retrieval pour un système RAG Java se résume à trois piliers : **des métriques IR implémentées nativement en Java** (~200 lignes de code, zéro dépendance externe), **un golden set versionné de 50-100 requêtes annotées** couvrant les types de requêtes de votre domaine, et **un test harness JUnit 5** qui transforme chaque modification du pipeline en validation mesurable. L'insight le plus actionnable de cette recherche est que **Convex Combination surpasse systématiquement RRF** selon Bruch et al. (2022), et que son implémentation en Java est triviale — c'est potentiellement le changement à plus fort impact pour un coût d'implémentation minimal. Le second levier à forte valeur est l'augmentation d'**ef_search** (paramètre runtime, zéro rebuild) qui peut immédiatement améliorer le recall HNSW. L'écosystème d'évaluation RAG est massivement Python-centric, mais les métriques retrieval classiques qui constituent le cœur de l'évaluation sont des formules mathématiques simples qui n'ont besoin d'aucun framework — un développeur solo Java peut construire un système d'audit complet et rigoureux en moins d'une semaine.