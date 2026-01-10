package dev.alexandria.test;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.List;
import java.util.Locale;

/**
 * Stubs WireMock pour l'API Infinity (embeddings et rerank).
 *
 * <p>Infinity utilise:
 *
 * <ul>
 *   <li>Format OpenAI pour les embeddings (/embeddings)
 *   <li>Format Cohere pour le rerank (/rerank)
 * </ul>
 */
public final class InfinityStubs {

  /** Modèle d'embedding par défaut pour les stubs. Configurable pour matcher l'environnement. */
  public static final String DEFAULT_EMBEDDING_MODEL = "michaelfeil/bge-base-en-v1.5";

  private InfinityStubs() {}

  /** Reset tous les stubs WireMock. À appeler entre les tests pour éviter la pollution. */
  public static void reset() {
    WireMock.reset();
  }

  /**
   * Configure un stub pour l'endpoint /embeddings.
   *
   * <p>Retourne un embedding au format OpenAI.
   *
   * @param embedding vecteur d'embedding à retourner
   */
  public static void stubEmbeddings(float[] embedding) {
    stubFor(
        post(urlPathEqualTo("/embeddings"))
            .willReturn(
                okJson(formatEmbeddingResponse(embedding))
                    .withHeader("Content-Type", "application/json")));
  }

  /**
   * Configure un stub pour l'endpoint /embeddings avec plusieurs embeddings.
   *
   * @param embeddings liste de vecteurs d'embedding à retourner
   */
  public static void stubEmbeddings(List<float[]> embeddings) {
    stubFor(
        post(urlPathEqualTo("/embeddings"))
            .willReturn(
                okJson(formatEmbeddingResponse(embeddings))
                    .withHeader("Content-Type", "application/json")));
  }

  /**
   * Configure un stub simulant un cold start avec délai log-normal.
   *
   * <p>Le délai suit une distribution log-normale: médiane ~3s, max ~10s. Simule le comportement
   * réel d'Infinity au premier appel après inactivité.
   *
   * @param embedding vecteur d'embedding à retourner après le délai
   */
  public static void stubColdStart(float[] embedding) {
    stubFor(
        post(urlPathEqualTo("/embeddings"))
            .willReturn(
                okJson(formatEmbeddingResponse(embedding))
                    .withHeader("Content-Type", "application/json")
                    .withLogNormalRandomDelay(3000, 0.5))); // médiane 3s, sigma 0.5
  }

  /**
   * Configure un stub pour l'endpoint /rerank.
   *
   * <p>Retourne les résultats au format Cohere.
   *
   * @param scores liste de scores de pertinence (index = position dans la liste de documents)
   */
  public static void stubRerank(List<Double> scores) {
    stubFor(
        post(urlPathEqualTo("/rerank"))
            .willReturn(
                okJson(formatRerankResponse(scores))
                    .withHeader("Content-Type", "application/json")));
  }

  /**
   * Configure un stub de retry scenario: échec 503 puis succès.
   *
   * <p>Utilise les scenarios WireMock pour simuler une erreur transitoire suivie d'une
   * récupération.
   *
   * @param embedding vecteur d'embedding à retourner au second appel
   */
  public static void stubRetryScenario(float[] embedding) {
    String scenarioName = "Retry Scenario";

    // Premier appel: 503 Service Unavailable
    stubFor(
        post(urlPathEqualTo("/embeddings"))
            .inScenario(scenarioName)
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(
                serviceUnavailable()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"error\": \"Service temporarily unavailable\"}"))
            .willSetStateTo("First Failure"));

    // Second appel: succès
    stubFor(
        post(urlPathEqualTo("/embeddings"))
            .inScenario(scenarioName)
            .whenScenarioStateIs("First Failure")
            .willReturn(
                okJson(formatEmbeddingResponse(embedding))
                    .withHeader("Content-Type", "application/json")));
  }

  /**
   * Configure un stub de retry scenario avec plusieurs échecs avant succès.
   *
   * @param embedding vecteur d'embedding à retourner
   * @param failuresBeforeSuccess nombre d'échecs 503 avant le succès
   */
  public static void stubRetryScenario(float[] embedding, int failuresBeforeSuccess) {
    String scenarioName = "Multi-Retry Scenario";
    String currentState = Scenario.STARTED;

    for (int i = 0; i < failuresBeforeSuccess; i++) {
      String nextState = "Failure-" + (i + 1);
      stubFor(
          post(urlPathEqualTo("/embeddings"))
              .inScenario(scenarioName)
              .whenScenarioStateIs(currentState)
              .willReturn(
                  serviceUnavailable()
                      .withHeader("Content-Type", "application/json")
                      .withBody("{\"error\": \"Service temporarily unavailable\"}"))
              .willSetStateTo(nextState));
      currentState = nextState;
    }

    // Dernier appel: succès
    stubFor(
        post(urlPathEqualTo("/embeddings"))
            .inScenario(scenarioName)
            .whenScenarioStateIs(currentState)
            .willReturn(
                okJson(formatEmbeddingResponse(embedding))
                    .withHeader("Content-Type", "application/json")));
  }

  /**
   * Configure un stub qui retourne une erreur 4xx (client error).
   *
   * @param statusCode code HTTP (400-499)
   * @param errorMessage message d'erreur
   * @throws IllegalArgumentException si statusCode n'est pas dans [400, 499]
   */
  public static void stubClientError(int statusCode, String errorMessage) {
    if (statusCode < 400 || statusCode > 499) {
      throw new IllegalArgumentException("statusCode must be in [400, 499], got: " + statusCode);
    }
    stubFor(
        post(urlPathEqualTo("/embeddings"))
            .willReturn(
                WireMock.status(statusCode)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("{\"error\": \"%s\"}", errorMessage))));
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Helpers de formatage
  // ═══════════════════════════════════════════════════════════════════════════

  private static String formatEmbeddingResponse(float[] embedding) {
    return formatEmbeddingResponse(List.of(embedding));
  }

  private static String formatEmbeddingResponse(List<float[]> embeddings) {
    StringBuilder dataArray = new StringBuilder();
    for (int i = 0; i < embeddings.size(); i++) {
      if (i > 0) {
        dataArray.append(",");
      }
      dataArray.append(
          String.format(
              """
              {"object":"embedding","index":%d,"embedding":%s}""",
              i, formatFloatArray(embeddings.get(i))));
    }

    return String.format(
        """
        {"object":"list","data":[%s],"model":"%s",\
        "usage":{"prompt_tokens":10,"total_tokens":10}}""",
        dataArray, DEFAULT_EMBEDDING_MODEL);
  }

  private static String formatRerankResponse(List<Double> scores) {
    StringBuilder resultsArray = new StringBuilder();
    for (int i = 0; i < scores.size(); i++) {
      if (i > 0) {
        resultsArray.append(",");
      }
      resultsArray.append(
          String.format(Locale.US, "{\"index\":%d,\"relevance_score\":%.6f}", i, scores.get(i)));
    }

    return String.format("{\"results\":[%s]}", resultsArray);
  }

  private static String formatFloatArray(float[] array) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < array.length; i++) {
      if (i > 0) {
        sb.append(",");
      }
      sb.append(String.format(Locale.US, "%.8f", array[i]));
    }
    sb.append("]");
    return sb.toString();
  }
}
