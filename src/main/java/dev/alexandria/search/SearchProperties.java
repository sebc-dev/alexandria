package dev.alexandria.search;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalised configuration for the search pipeline.
 *
 * <p>Properties are bound from {@code alexandria.search.*} in application.yml /
 * application.properties.
 *
 * <ul>
 *   <li>{@code alpha} - weight for vector scores in convex combination fusion (0.0 = FTS only, 1.0
 *       = vector only; default 0.7)
 *   <li>{@code rerank-candidates} - number of candidates to fetch from each source before fusion
 *       and reranking (default 30, bounded [10, 100])
 * </ul>
 *
 * <p>Validated at startup via {@link #validate()}; the application fails to start if values are out
 * of range.
 */
@Configuration
@ConfigurationProperties(prefix = "alexandria.search")
public class SearchProperties {

  private double alpha = 0.7;
  private int rerankCandidates = 30;

  /** Validates configuration at startup. Throws if values are out of allowed range. */
  @PostConstruct
  void validate() {
    if (alpha < 0.0 || alpha > 1.0) {
      throw new IllegalStateException(
          "alexandria.search.alpha must be in [0.0, 1.0], got: " + alpha);
    }
    if (rerankCandidates < 10 || rerankCandidates > 100) {
      throw new IllegalStateException(
          "alexandria.search.rerank-candidates must be in [10, 100], got: " + rerankCandidates);
    }
  }

  public double getAlpha() {
    return alpha;
  }

  public void setAlpha(double alpha) {
    this.alpha = alpha;
  }

  public int getRerankCandidates() {
    return rerankCandidates;
  }

  public void setRerankCandidates(int rerankCandidates) {
    this.rerankCandidates = rerankCandidates;
  }
}
