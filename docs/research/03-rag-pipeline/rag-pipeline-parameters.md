# Paramètres optimaux pour un pipeline RAG avec BGE-M3 et reranking

Les recherches convergent vers des valeurs bien définies pour votre configuration. La combinaison BGE-M3 + bge-reranker-v2-m3 est parmi les plus performantes du marché avec un **nDCG@10 de 70.0** sur MIRACL et **Recall@100 de 75.5%** sur MKQA, validant ce choix technique pour la documentation technique multilingue.

## Top-K initial : récupérer 50 à 100 candidats avant reranking

Les benchmarks BAAI officiels utilisent systématiquement **100 candidats** comme valeur standard pour le reranking. Une étude Elastic Labs (2024) sur BEIR montre que **90% du gain maximal** est atteint à environ 100 documents, avec un point d'équilibre coût-efficacité à 30 documents.

Pour votre cas d'usage spécifique (documentation technique avec chunks de 450-500 tokens), la recommandation est :

| Scénario | Top-K recommandé | Justification |
|----------|-----------------|---------------|
| Configuration par défaut | **50** | Optimal NDCG@10 selon ZeroEntropy 2025 |
| Recherche exhaustive | **100** | Standard BAAI, capture tous les cas edge |
| Latence prioritaire | **30** | ~40% du gain nDCG, 3x plus rapide |

Le compromis recall/latence suit une courbe de Pareto : reranker 50 documents prend environ **1.5 secondes** avec un cross-encoder moderne. Au-delà de 100 candidats, les améliorations plafonnent tandis que la latence croît linéairement.

## Top-N final : retourner 3 à 5 résultats après reranking

La documentation BAAI recommande explicitement de "re-ranker top-100 → final top-3 results". Les frameworks RAG convergent vers des défauts similaires : **LlamaIndex utilise top_n=2-3**, Langchain4j maxResults=3, Cohere API top_n=3.

Pour alimenter Claude Code avec du contexte technique, la recommandation est **3 à 5 documents**. Cette limitation n'est pas arbitraire : les recherches Pinecone sur le problème "Lost in the Middle" démontrent que les LLM peinent à exploiter l'information située au milieu du contexte. Maximiser le recall initial puis **minimiser** les documents envoyés au LLM optimise la qualité des réponses.

Le budget contexte idéal représente **5 à 20% de la fenêtre disponible**. Avec des chunks de 450-500 tokens et 5 documents, vous consommez environ 2250-2500 tokens de contexte, laissant amplement d'espace pour les prompts système et l'historique de conversation.

| Type de query | Top-N recommandé |
|---------------|-----------------|
| Question factuelle | **3** |
| Question technique complexe | **5** |
| Analyse multi-facettes | **7-10** |

## Seuil de score et interprétation du bge-reranker-v2-m3

Le bge-reranker-v2-m3 produit **par défaut des logits bruts** (valeurs non bornées, typiquement -10 à +10). Pour obtenir des scores normalisés entre 0 et 1, il faut explicitement passer `normalize=True` qui applique une fonction sigmoïde.

```python
from FlagEmbedding import FlagReranker
reranker = FlagReranker('BAAI/bge-reranker-v2-m3', use_fp16=True)

# Scores bruts (logits)
score = reranker.compute_score(['query', 'passage'])  # Ex: -5.65

# Scores normalisés 0-1 (recommandé)
score = reranker.compute_score(['query', 'passage'], normalize=True)  # Ex: 0.003
```

**Interprétation des scores normalisés** d'après les exemples officiels :
- Passage hautement pertinent : **~0.99** ("what is panda?" → description détaillée)
- Passage non pertinent : **~0.0003** ("what is panda?" → "hi")

La documentation BAAI est explicite : **aucun seuil universel n'est recommandé**. Leur guidance officielle : "sélectionnez un seuil approprié basé sur la distribution de similarité de vos données (comme 0.8, 0.85, voire 0.9)". Pour les rerankers, l'**ordre relatif des scores** importe plus que les valeurs absolues.

### Stratégie de seuil recommandée pour production

Plutôt qu'un seuil absolu fixe, privilégiez une approche **adaptative** :

1. **Seuil relatif** : conserver les documents scorant > 50-70% du score du meilleur résultat
2. **Détection de "knee"** (approche Vectara) : identifier la rupture naturelle dans la distribution des scores
3. **Garantie minimale** : toujours retourner au moins 2-3 documents même sous le seuil

Valeur de départ pour tests : **0.3 à 0.5** en scores normalisés, puis ajuster selon vos données. Le paper InfoGain-RAG utilise un seuil de **0.2** avec garantie de minimum 2 documents.

## Configuration Spring Boot : ce qui doit être paramétrable

La distinction entre paramètres stables et tuneables est claire selon les best practices :

### À configurer dans application.yml (tuning fréquent)

```yaml
rag:
  retrieval:
    top-k-initial: 50           # Candidats avant reranking
    top-n-final: 5              # Résultats après reranking
    min-score: 0.3              # Seuil minimum (normalisé)
    score-threshold-type: relative  # relative | absolute
    relative-threshold-ratio: 0.5   # 50% du meilleur score
    
  reranking:
    enabled: true
    model: BAAI/bge-reranker-v2-m3
    normalize-scores: true
    use-fp16: true
    
  hybrid-search:
    alpha: 0.7                  # Pondération dense vs sparse (0=sparse, 1=dense)
```

### À hardcoder (stable après validation)

```java
@Configuration
public class RagConstants {
    // Modèle d'embedding - changer nécessite réindexation complète
    public static final String EMBEDDING_MODEL = "BAAI/bge-m3";
    public static final int EMBEDDING_DIMENSION = 1024;
    
    // Chunk size - validé pour documentation technique
    public static final int CHUNK_SIZE = 500;
    public static final int CHUNK_OVERLAP = 50;  // 10% overlap
    
    // Paramètres pgvector HNSW (optimisés par défaut)
    public static final int HNSW_M = 16;
    public static final int HNSW_EF_CONSTRUCTION = 64;
}
```

### Configuration complète Spring Boot

```java
@ConfigurationProperties(prefix = "rag")
@Validated
public class RagProperties {
    
    @Valid
    private RetrievalConfig retrieval = new RetrievalConfig();
    
    @Valid  
    private RerankingConfig reranking = new RerankingConfig();
    
    @Data
    public static class RetrievalConfig {
        @Min(10) @Max(200)
        private int topKInitial = 50;
        
        @Min(1) @Max(20)
        private int topNFinal = 5;
        
        @DecimalMin("0.0") @DecimalMax("1.0")
        private double minScore = 0.3;
        
        private ThresholdType thresholdType = ThresholdType.RELATIVE;
        
        @DecimalMin("0.0") @DecimalMax("1.0")
        private double relativeThresholdRatio = 0.5;
    }
    
    @Data
    public static class RerankingConfig {
        private boolean enabled = true;
        private String model = "BAAI/bge-reranker-v2-m3";
        private boolean normalizeScores = true;
        private boolean useFp16 = true;
    }
    
    public enum ThresholdType { ABSOLUTE, RELATIVE }
}
```

## Validation empirique et sources académiques

Les recommandations ci-dessus s'appuient sur des sources de qualité variable qu'il convient de distinguer :

### Validé empiriquement par benchmarks

Les évaluations BAAI sur BEIR et MIRACL utilisent `--rerank_top_k 100` avec k_values [10, 100]. L'étude Elastic Labs (2024) a testé systématiquement la profondeur optimale de reranking sur BEIR : le modèle bge-reranker-v2-gemma atteint 90% du gain maximal à **115 documents** en moyenne. NVIDIA (2024) a démontré que les chunks de **512-1024 tokens** offrent le meilleur équilibre pour la documentation technique, avec une dégradation notable au-delà de 2048 tokens.

### Consensus de l'industrie (règles générales)

La recommandation Top-N = 3-5 après reranking est un consensus entre Pinecone, LlamaIndex, Langchain4j et Cohere. Le problème "Lost in the Middle" a été documenté par plusieurs équipes indépendamment. L'approche seuil relatif vs absolu est recommandée par Vectara et Zilliz.

### À valider sur vos données

Les seuils exacts (0.3, 0.5, etc.) sont **data-dependent** selon BAAI. La pondération hybrid search (alpha) varie selon le domaine : le paper BGE-M3 suggère des poids [0.4, 0.2, 0.4] pour [dense, sparse, colbert] mais précise que cela dépend du cas d'usage. Le chunk size de 450-500 tokens est approprié mais peut nécessiter ajustement selon la structure de vos documents markdown.

## Synthèse des valeurs recommandées

| Paramètre | Valeur | Configurable | Confidence |
|-----------|--------|--------------|------------|
| **Top-K initial** | 50 (défaut), 30-100 (plage) | ✅ application.yml | Haute |
| **Top-N final** | 5 (défaut), 3-7 (plage) | ✅ application.yml | Haute |
| **Score threshold** | 0.3-0.5 (absolu) ou 0.5 ratio (relatif) | ✅ application.yml | Moyenne |
| **normalize** | true | ❌ hardcodé | Haute |
| **Chunk size** | 450-500 tokens | ❌ hardcodé | Haute |
| **Modèle reranker** | bge-reranker-v2-m3 | ✅ configurable | Haute |

Pour démarrer en production, utilisez Top-K=50, Top-N=5, seuil relatif à 50% du meilleur score avec garantie de minimum 2 résultats. Ces valeurs représentent un point de départ solide basé sur les benchmarks BAAI et le consensus des frameworks RAG, à affiner ensuite via A/B testing sur vos données réelles avec des métriques comme NDCG@10 et la satisfaction utilisateur.