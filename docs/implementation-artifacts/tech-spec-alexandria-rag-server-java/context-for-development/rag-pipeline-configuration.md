# RAG Pipeline Configuration

```yaml
# application.yml
rag:
  retrieval:
    top-k-initial: 50              # Candidats avant reranking (30-100)
    top-n-final: 5                 # Résultats après reranking (3-7)
    min-score: 0.3                 # Seuil minimum absolu (scores normalisés 0-1)
    score-threshold-type: relative # relative | absolute
    relative-threshold-ratio: 0.5  # 50% du meilleur score
    min-results-guarantee: 2       # Toujours retourner au moins 2 résultats

  reranking:
    enabled: true
    model: BAAI/bge-reranker-v2-m3
    normalize-scores: true         # OBLIGATOIRE - applique sigmoïde pour scores 0-1
```

```java
package dev.alexandria.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "rag")
@Validated
public class RagProperties {

    @Valid
    private RetrievalConfig retrieval = new RetrievalConfig();

    @Valid
    private RerankingConfig reranking = new RerankingConfig();

    // Getters/Setters...

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

        @Min(1) @Max(10)
        private int minResultsGuarantee = 2;

        // Getters/Setters...
    }

    public static class RerankingConfig {
        private boolean enabled = true;
        private String model = "BAAI/bge-reranker-v2-m3";
        private boolean normalizeScores = true;  // Toujours true pour scores 0-1

        // Getters/Setters...
    }

    public enum ThresholdType { ABSOLUTE, RELATIVE }
}
```

**Paramètres validés par benchmarks:**
- **Top-K=50**: Optimal nDCG@10 selon ZeroEntropy 2025, 90% du gain à 100 docs (Elastic Labs)
- **Top-N=5**: Consensus LlamaIndex/Langchain4j/Cohere, évite "Lost in the Middle"
- **Seuil relatif 50%**: Approche Vectara, plus robuste qu'un seuil absolu fixe
- **Garantie min 2**: UX - toujours retourner quelque chose même si scores faibles
- **normalize=true**: Obligatoire pour scores comparables (sigmoïde sur logits bruts)
