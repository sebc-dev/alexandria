package dev.alexandria.search;

import static org.assertj.core.api.Assertions.assertThat;

import dev.alexandria.BaseIntegrationTest;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class HybridSearchIT extends BaseIntegrationTest {

  @Autowired SearchService searchService;

  @Autowired EmbeddingStore<TextSegment> embeddingStore;

  @Autowired EmbeddingModel embeddingModel;

  // --- Test data chunks ---

  static final String CHUNK_A_TEXT =
      "To configure routing in Spring Boot, use @RequestMapping annotation "
          + "on controller methods. Routes map HTTP methods and URL patterns to handler methods.";
  static final String CHUNK_A_URL = "https://docs.spring.io/routing";
  static final String CHUNK_A_SECTION = "Web > Routing > Basics";

  static final String CHUNK_B_TEXT =
      "The RouterModule in Angular provides directives and services for "
          + "in-app navigation. Import RouterModule.forRoot(routes) in your AppModule.";
  static final String CHUNK_B_URL = "https://angular.dev/guide/routing";
  static final String CHUNK_B_SECTION = "Guide > Routing > Setup";

  static final String CHUNK_C_TEXT =
      "PostgreSQL supports JSONB columns for storing semi-structured data. "
          + "Use the -> operator to access JSON fields.";
  static final String CHUNK_C_URL = "https://postgresql.org/docs/jsonb";
  static final String CHUNK_C_SECTION = "Data Types > JSONB";

  static final String CHUNK_D_TEXT =
      """
            ```java
            @RestController
            public class UserController {
                @GetMapping("/users")
                public List<User> getUsers() { return userService.findAll(); }
            }
            ```""";
  static final String CHUNK_D_URL = "https://docs.spring.io/rest";
  static final String CHUNK_D_SECTION = "Web > REST > Controllers";

  @BeforeEach
  void seedTestData() {
    embeddingStore.removeAll();

    seed(CHUNK_A_TEXT, CHUNK_A_URL, CHUNK_A_SECTION);
    seed(CHUNK_B_TEXT, CHUNK_B_URL, CHUNK_B_SECTION);
    seed(CHUNK_C_TEXT, CHUNK_C_URL, CHUNK_C_SECTION);
    seed(CHUNK_D_TEXT, CHUNK_D_URL, CHUNK_D_SECTION);
  }

  private void seed(String text, String sourceUrl, String sectionPath) {
    TextSegment segment =
        TextSegment.from(
            text, Metadata.from("source_url", sourceUrl).put("section_path", sectionPath));
    Embedding embedding = embeddingModel.embed(segment).content();
    embeddingStore.add(embedding, segment);
  }

  @Test
  void semanticSearchFindsRelevantChunksByMeaning() {
    // Query shares meaning with chunks A and B (routing) but uses different words
    List<SearchResult> results =
        searchService.search(new SearchRequest("how to set up URL routing"));

    assertThat(results).isNotEmpty();

    // Top results should be routing-related (chunks A or B), not PostgreSQL (chunk C)
    List<String> topTexts = results.stream().limit(2).map(SearchResult::text).toList();
    assertThat(topTexts).doesNotContain(CHUNK_C_TEXT);

    // At least one routing chunk should appear in top results
    boolean hasRoutingChunk =
        results.stream()
            .limit(3)
            .anyMatch(
                r ->
                    r.text().contains("routing")
                        || r.text().contains("Routing")
                        || r.text().contains("RouterModule")
                        || r.text().contains("RequestMapping"));
    assertThat(hasRoutingChunk).as("Top 3 results should include routing-related content").isTrue();
  }

  @Test
  void keywordSearchFindsChunksWithExactTerms() {
    // "RouterModule" is an exact term only in chunk B
    List<SearchResult> results = searchService.search(new SearchRequest("RouterModule"));

    assertThat(results).isNotEmpty();

    boolean containsChunkB = results.stream().anyMatch(r -> r.text().contains("RouterModule"));
    assertThat(containsChunkB)
        .as("Results should include chunk B with exact term 'RouterModule'")
        .isTrue();

    // Chunk B should have a non-zero score
    SearchResult chunkBResult =
        results.stream().filter(r -> r.text().contains("RouterModule")).findFirst().orElseThrow();
    assertThat(chunkBResult.score()).isGreaterThan(0);
  }

  @Test
  void hybridSearchCombinesVectorAndKeywordResults() {
    // Has both semantic meaning (routing configuration) and exact keyword (RouterModule)
    List<SearchResult> results =
        searchService.search(new SearchRequest("RouterModule routing configuration"));

    assertThat(results).isNotEmpty();
    assertThat(results.size()).isGreaterThanOrEqualTo(2);

    // Chunk B (Angular RouterModule) should rank highly because it matches BOTH semantically AND by
    // keyword
    boolean chunkBInTop3 =
        results.stream().limit(3).anyMatch(r -> r.text().contains("RouterModule"));
    assertThat(chunkBInTop3)
        .as("Chunk B should rank in top 3 due to both semantic and keyword match")
        .isTrue();
  }

  @Test
  void searchResultsIncludeCitationMetadata() {
    // Should match chunk C (PostgreSQL JSONB)
    List<SearchResult> results = searchService.search(new SearchRequest("PostgreSQL JSONB"));

    assertThat(results).isNotEmpty();
    SearchResult topResult = results.getFirst();
    assertThat(topResult.sourceUrl()).isEqualTo(CHUNK_C_URL);
    assertThat(topResult.sectionPath()).isEqualTo(CHUNK_C_SECTION);
    assertThat(topResult.text()).isNotBlank();
    assertThat(topResult.score()).isGreaterThan(0);
  }

  @Test
  void searchRespectsMaxResultsParameter() {
    // With maxResults=2, should return at most 2 results
    List<SearchResult> limitedResults =
        searchService.search(new SearchRequest("programming documentation guide", 2));
    assertThat(limitedResults.size()).isLessThanOrEqualTo(2);

    // With default maxResults (10), should return all seeded chunks (we have 4)
    List<SearchResult> defaultResults =
        searchService.search(new SearchRequest("programming documentation guide"));
    assertThat(defaultResults.size()).isLessThanOrEqualTo(10);
    assertThat(defaultResults.size()).isGreaterThanOrEqualTo(4);
  }

  @Test
  void searchWithNoMatchingContentReturnsEmptyOrLowScoreResults() {
    // Completely unrelated to all seeded data
    List<SearchResult> results =
        searchService.search(new SearchRequest("quantum entanglement particle physics"));

    // Either empty or all results have very low relevance scores
    if (!results.isEmpty()) {
      boolean allLowScores = results.stream().allMatch(r -> r.score() < 0.05);
      assertThat(allLowScores)
          .as(
              "All results for unrelated query should have low scores (< 0.05), but got: %s",
              results.stream().map(r -> String.format("%.3f", r.score())).toList())
          .isTrue();
    }
  }
}
