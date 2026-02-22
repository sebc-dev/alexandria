package dev.alexandria.search.eval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.alexandria.BaseIntegrationTest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test that runs the golden set against a live search index and validates retrieval
 * quality thresholds.
 *
 * <p>Tagged with {@code @Tag("eval")} so it is excluded from normal CI builds. Run explicitly with:
 *
 * <pre>./gradlew integrationTest -PincludeEvalTag</pre>
 *
 * <p>This test requires a populated index with Spring Boot documentation. It will fail against an
 * empty Testcontainers database (expected behavior). The real value is running it against a
 * deployment with indexed Spring Boot docs.
 */
@Tag("eval")
@SuppressWarnings("NullAway.Init")
class RetrievalEvaluationIT extends BaseIntegrationTest {

  private static final Logger log = LoggerFactory.getLogger(RetrievalEvaluationIT.class);

  @Autowired RetrievalEvaluationService evaluationService;

  @Test
  void goldenSetMeetsMinimumRetrievalQuality() throws Exception {
    // This test requires a populated index with Spring Boot documentation.
    // It will fail if the index is empty (expected -- the test is meant to run
    // against a real deployment with indexed Spring Boot docs).

    EvaluationSummary summary = evaluationService.evaluate("baseline");

    // Skip if the index is empty (Testcontainers without data)
    Assumptions.assumeTrue(
        summary.globalHitRateAt10() > 0.0, "Skipping: index appears empty (hit rate = 0)");

    assertThat(summary.passed()).as("Evaluation should pass configured thresholds").isTrue();

    // Log per-type breakdown for visibility
    summary
        .byType()
        .forEach(
            (type, metrics) ->
                log.info(
                    "Type {}: recall@10={}, mrr={}, ndcg@10={} (n={})",
                    type,
                    metrics.recallAt10(),
                    metrics.mrr(),
                    metrics.ndcgAt10(),
                    metrics.count()));

    // Log failed queries for diagnosis
    if (!summary.failedQueries().isEmpty()) {
      log.warn("Failed queries ({}):", summary.failedQueries().size());
      summary.failedQueries().forEach(q -> log.warn("  - {}", q));
    }
  }
}
