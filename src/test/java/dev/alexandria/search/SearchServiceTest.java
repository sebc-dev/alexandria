package dev.alexandria.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.alexandria.document.DocumentChunkRepository;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("NullAway.Init")
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

  @Mock EmbeddingStore<TextSegment> embeddingStore;

  @Mock EmbeddingModel embeddingModel;

  @Mock RerankerService rerankerService;

  @Mock DocumentChunkRepository documentChunkRepository;

  @Captor ArgumentCaptor<EmbeddingSearchRequest> searchRequestCaptor;

  SearchService searchService;

  private static final Embedding DUMMY_EMBEDDING = Embedding.from(new float[] {0.1f, 0.2f, 0.3f});

  @BeforeEach
  void setUp() {
    SearchProperties props = new SearchProperties();
    props.setAlpha(0.7);
    props.setRerankCandidates(30);
    searchService =
        new SearchService(
            embeddingStore, embeddingModel, rerankerService, documentChunkRepository, props);
  }

  private void stubEmbeddingModel(String query) {
    when(embeddingModel.embed(SearchService.BGE_QUERY_PREFIX + query))
        .thenReturn(Response.from(DUMMY_EMBEDDING));
  }

  private void stubRerankerReturnsEmpty() {
    when(rerankerService.rerank(any(), any(), anyInt(), any())).thenReturn(List.of());
  }

  private void stubFtsReturnsEmpty() {
    when(documentChunkRepository.fullTextSearch(any(), anyInt())).thenReturn(List.of());
  }

  private List<EmbeddingMatch<TextSegment>> stubStoreWithOneMatch() {
    TextSegment segment =
        TextSegment.from(
            "Spring Boot routing guide",
            Metadata.from("source_url", "https://docs.spring.io/routing")
                .put("section_path", "Web > Routing"));
    EmbeddingMatch<TextSegment> match =
        new EmbeddingMatch<>(0.85, "id-1", DUMMY_EMBEDDING, segment);
    List<EmbeddingMatch<TextSegment>> matches = List.of(match);
    when(embeddingStore.search(searchRequestCaptor.capture()))
        .thenReturn(new EmbeddingSearchResult<>(matches));
    return matches;
  }

  private void stubFtsWithOneMatch() {
    // FTS returns [embedding_id, text, source_url, section_path, chunk_type, parent_id,
    //              content_type, version, source_name, score]
    Object[] ftsRow =
        new Object[] {
          "id-fts-1",
          "Full-text search result",
          "https://docs.spring.io/fts",
          "FTS Section",
          null,
          null,
          "prose",
          "3.5",
          "Spring Docs",
          0.42f
        };
    when(documentChunkRepository.fullTextSearch(any(), anyInt()))
        .thenReturn(List.<Object[]>of(ftsRow));
  }

  // --- Dual-query pipeline ---

  @Test
  void searchExecutesBothQueriesAndFuses() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsWithOneMatch();
    stubRerankerReturnsEmpty();

    searchService.search(new SearchRequest("test query"));

    verify(embeddingStore).search(any(EmbeddingSearchRequest.class));
    verify(documentChunkRepository).fullTextSearch(eq("test query"), eq(30));
  }

  @Test
  void searchUsesRerankCandidatesFromProperties() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    stubRerankerReturnsEmpty();

    searchService.search(new SearchRequest("test query", 3));

    EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
    assertThat(captured.maxResults()).isEqualTo(30);
    verify(documentChunkRepository).fullTextSearch(any(), eq(30));
  }

  @Test
  void searchHandlesEmptyVectorResults() {
    stubEmbeddingModel("test query");
    when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
        .thenReturn(new EmbeddingSearchResult<>(List.of()));
    stubFtsWithOneMatch();
    stubRerankerReturnsEmpty();

    searchService.search(new SearchRequest("test query"));

    // Pipeline should not throw; FTS results flow through fusion
    verify(rerankerService).rerank(any(), any(), anyInt(), any());
  }

  @Test
  void searchHandlesEmptyFtsResults() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    stubRerankerReturnsEmpty();

    searchService.search(new SearchRequest("test query"));

    // Pipeline should not throw; vector results flow through fusion
    verify(rerankerService).rerank(any(), any(), anyInt(), any());
  }

  @Test
  void searchReturnsEmptyListWhenBothSourcesEmpty() {
    stubEmbeddingModel("test query");
    when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
        .thenReturn(new EmbeddingSearchResult<>(List.of()));
    stubFtsReturnsEmpty();
    when(rerankerService.rerank(any(), eq(List.of()), eq(10), eq(null))).thenReturn(List.of());

    List<SearchResult> results = searchService.search(new SearchRequest("test query"));

    assertThat(results).isEmpty();
  }

  // --- Filter tests (unchanged logic, verified via vector search request) ---

  @Test
  void searchWithNoFiltersPassesNullFilter() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    stubRerankerReturnsEmpty();

    searchService.search(new SearchRequest("test query"));

    EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
    assertThat(captured.filter()).isNull();
  }

  @Test
  void searchWithSourceFilterBuildsEqualityFilter() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    stubRerankerReturnsEmpty();

    SearchRequest request =
        new SearchRequest("test query", 10, "spring-boot", null, null, null, null);
    searchService.search(request);

    EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
    Filter filter = captured.filter();
    assertThat(filter).isInstanceOf(IsEqualTo.class);
    IsEqualTo isEqualTo = (IsEqualTo) filter;
    assertThat(isEqualTo.key()).isEqualTo("source_name");
    assertThat(isEqualTo.comparisonValue()).isEqualTo("spring-boot");
  }

  @Test
  void searchWithVersionFilterBuildsEqualityFilter() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    stubRerankerReturnsEmpty();

    SearchRequest request = new SearchRequest("test query", 10, null, null, "3.2.0", null, null);
    searchService.search(request);

    EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
    Filter filter = captured.filter();
    assertThat(filter).isInstanceOf(IsEqualTo.class);
    IsEqualTo isEqualTo = (IsEqualTo) filter;
    assertThat(isEqualTo.key()).isEqualTo("version");
    assertThat(isEqualTo.comparisonValue()).isEqualTo("3.2.0");
  }

  @Test
  void searchWithSectionPathFilterBuildsContainsStringFilter() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    stubRerankerReturnsEmpty();

    SearchRequest request =
        new SearchRequest("test query", 10, null, "Getting Started/Quick Start", null, null, null);
    searchService.search(request);

    EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
    Filter filter = captured.filter();
    assertThat(filter).isInstanceOf(ContainsString.class);
    ContainsString containsString = (ContainsString) filter;
    assertThat(containsString.key()).isEqualTo("section_path");
    assertThat(containsString.comparisonValue()).isEqualTo("getting-started-quick-start");
  }

  @Test
  void searchWithContentTypeFilterBuildsEqualityFilter() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    stubRerankerReturnsEmpty();

    SearchRequest request = new SearchRequest("test query", 10, null, null, null, "CODE", null);
    searchService.search(request);

    EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
    Filter filter = captured.filter();
    assertThat(filter).isInstanceOf(IsEqualTo.class);
    IsEqualTo isEqualTo = (IsEqualTo) filter;
    assertThat(isEqualTo.key()).isEqualTo("content_type");
    assertThat(isEqualTo.comparisonValue()).isEqualTo("code");
  }

  @Test
  void searchWithContentTypeMixedSkipsFilter() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    stubRerankerReturnsEmpty();

    SearchRequest request = new SearchRequest("test query", 10, null, null, null, "MIXED", null);
    searchService.search(request);

    EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
    assertThat(captured.filter()).isNull();
  }

  @Test
  void searchWithMultipleFiltersCombinesWithAnd() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    stubRerankerReturnsEmpty();

    SearchRequest request =
        new SearchRequest("test query", 10, "spring-boot", null, "3.2.0", null, null);
    searchService.search(request);

    EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
    Filter filter = captured.filter();
    assertThat(filter).isInstanceOf(And.class);
  }

  // --- Reranker delegation ---

  @Test
  void searchDelegatesToRerankerService() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    SearchResult expectedResult = new SearchResult("text", 0.85, "url", "path", 0.9);
    when(rerankerService.rerank(eq("test query"), any(), eq(10), eq(null)))
        .thenReturn(List.of(expectedResult));

    SearchRequest request = new SearchRequest("test query");
    List<SearchResult> results = searchService.search(request);

    verify(rerankerService).rerank(eq("test query"), any(), eq(10), eq(null));
    assertThat(results).containsExactly(expectedResult);
  }

  @Test
  void searchPassesMinScoreToReranker() {
    stubEmbeddingModel("test query");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    when(rerankerService.rerank(eq("test query"), any(), eq(5), eq(0.7))).thenReturn(List.of());

    SearchRequest request = new SearchRequest("test query", 5, null, null, null, null, 0.7);
    searchService.search(request);

    verify(rerankerService).rerank(eq("test query"), any(), eq(5), eq(0.7));
  }

  @Test
  void searchPrependsQueryPrefixBeforeEmbedding() {
    stubEmbeddingModel("my search");
    stubStoreWithOneMatch();
    stubFtsReturnsEmpty();
    stubRerankerReturnsEmpty();

    searchService.search(new SearchRequest("my search"));

    verify(embeddingModel).embed(SearchService.BGE_QUERY_PREFIX + "my search");
  }

  // --- Parent-child context resolution ---

  @Test
  void searchSubstitutesParentTextForChildMatch() {
    stubEmbeddingModel("spring config");
    TextSegment childSegment =
        TextSegment.from(
            "Use @Value to inject properties.",
            Metadata.from("source_url", "https://docs.spring.io/config")
                .put("section_path", "configuration/properties")
                .put("chunk_type", "child")
                .put("parent_id", "https://docs.spring.io/config#configuration/properties"));
    EmbeddingMatch<TextSegment> childMatch =
        new EmbeddingMatch<>(0.90, "id-child", DUMMY_EMBEDDING, childSegment);
    when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
        .thenReturn(new EmbeddingSearchResult<>(List.of(childMatch)));
    stubFtsReturnsEmpty();
    Object[] parentRow =
        new Object[] {
          "https://docs.spring.io/config#configuration/properties",
          "## Properties\nUse @Value to inject properties.\n```java\n@Value(\"${key}\")\n```"
        };
    List<Object[]> parentRows = List.<Object[]>of(parentRow);
    when(documentChunkRepository.findParentTextsByKeys(
            eq(new String[] {"https://docs.spring.io/config#configuration/properties"})))
        .thenReturn(parentRows);
    when(rerankerService.rerank(any(), any(), anyInt(), any()))
        .thenReturn(
            List.of(
                new SearchResult(
                    "Use @Value to inject properties.",
                    0.90,
                    "https://docs.spring.io/config",
                    "configuration/properties",
                    0.85)));

    List<SearchResult> results = searchService.search(new SearchRequest("spring config"));

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().text())
        .isEqualTo(
            "## Properties\nUse @Value to inject properties.\n```java\n@Value(\"${key}\")\n```");
    assertThat(results.getFirst().sourceUrl()).isEqualTo("https://docs.spring.io/config");
    assertThat(results.getFirst().sectionPath()).isEqualTo("configuration/properties");
  }

  @Test
  void searchDeduplicatesMultipleChildrenOfSameParent() {
    String parentId = "https://docs.example.com/guide#setup";
    EmbeddingMatch<TextSegment> child1 =
        new EmbeddingMatch<>(
            0.85,
            "id-c1",
            DUMMY_EMBEDDING,
            TextSegment.from(
                "Child text one",
                Metadata.from("source_url", "https://docs.example.com/guide")
                    .put("section_path", "setup")
                    .put("chunk_type", "child")
                    .put("parent_id", parentId)));
    EmbeddingMatch<TextSegment> child2 =
        new EmbeddingMatch<>(
            0.90,
            "id-c2",
            DUMMY_EMBEDDING,
            TextSegment.from(
                "Child text two",
                Metadata.from("source_url", "https://docs.example.com/guide")
                    .put("section_path", "setup")
                    .put("chunk_type", "child")
                    .put("parent_id", parentId)));
    EmbeddingMatch<TextSegment> child3 =
        new EmbeddingMatch<>(
            0.70,
            "id-c3",
            DUMMY_EMBEDDING,
            TextSegment.from(
                "Child text three",
                Metadata.from("source_url", "https://docs.example.com/guide")
                    .put("section_path", "setup")
                    .put("chunk_type", "child")
                    .put("parent_id", parentId)));

    List<EmbeddingMatch<TextSegment>> deduplicated =
        searchService.deduplicateByParent(List.of(child1, child2, child3));

    assertThat(deduplicated).hasSize(1);
    assertThat(deduplicated.getFirst().embedded().text()).isEqualTo("Child text two");
    assertThat(deduplicated.getFirst().score()).isEqualTo(0.90);
  }

  @Test
  void searchPassesThroughParentMatchDirectly() {
    TextSegment parentSegment =
        TextSegment.from(
            "## Setup\nFull section content with code and prose.",
            Metadata.from("source_url", "https://docs.example.com/guide")
                .put("section_path", "setup")
                .put("chunk_type", "parent"));
    EmbeddingMatch<TextSegment> parentMatch =
        new EmbeddingMatch<>(0.80, "id-parent", DUMMY_EMBEDDING, parentSegment);

    List<EmbeddingMatch<TextSegment>> deduplicated =
        searchService.deduplicateByParent(List.of(parentMatch));

    assertThat(deduplicated).hasSize(1);
    assertThat(deduplicated.getFirst().embedded().text())
        .isEqualTo("## Setup\nFull section content with code and prose.");
  }

  @Test
  void searchHandlesLegacyChunksWithoutChunkType() {
    TextSegment legacySegment =
        TextSegment.from(
            "Legacy chunk without parent-child metadata",
            Metadata.from("source_url", "https://old-docs.example.com/page")
                .put("section_path", "intro"));
    EmbeddingMatch<TextSegment> legacyMatch =
        new EmbeddingMatch<>(0.75, "id-legacy", DUMMY_EMBEDDING, legacySegment);

    List<EmbeddingMatch<TextSegment>> deduplicated =
        searchService.deduplicateByParent(List.of(legacyMatch));

    assertThat(deduplicated).hasSize(1);
    assertThat(deduplicated.getFirst().embedded().text())
        .isEqualTo("Legacy chunk without parent-child metadata");

    // Also verify that resolveParentTexts returns empty map for legacy chunks
    var parentTexts = searchService.resolveParentTexts(List.of(legacyMatch));
    assertThat(parentTexts).isEmpty();
  }

  @Test
  void searchKeepsHighestScoringChildPerParent() {
    String parentId = "https://docs.example.com/guide#setup";
    EmbeddingMatch<TextSegment> lowScoreChild =
        new EmbeddingMatch<>(
            0.60,
            "id-low",
            DUMMY_EMBEDDING,
            TextSegment.from(
                "Low score child",
                Metadata.from("source_url", "https://docs.example.com/guide")
                    .put("section_path", "setup")
                    .put("chunk_type", "child")
                    .put("parent_id", parentId)));
    EmbeddingMatch<TextSegment> highScoreChild =
        new EmbeddingMatch<>(
            0.95,
            "id-high",
            DUMMY_EMBEDDING,
            TextSegment.from(
                "High score child",
                Metadata.from("source_url", "https://docs.example.com/guide")
                    .put("section_path", "setup")
                    .put("chunk_type", "child")
                    .put("parent_id", parentId)));

    List<EmbeddingMatch<TextSegment>> deduplicated =
        searchService.deduplicateByParent(List.of(lowScoreChild, highScoreChild));

    assertThat(deduplicated).hasSize(1);
    assertThat(deduplicated.getFirst().score()).isEqualTo(0.95);
    assertThat(deduplicated.getFirst().embedded().text()).isEqualTo("High score child");
  }

  // --- FTS row parsing ---

  @Test
  void executeFullTextSearchParsesRowsIntoScoredCandidates() {
    Object[] row =
        new Object[] {
          "fts-id-1",
          "Some FTS text",
          "https://docs.example.com",
          "getting-started",
          "child",
          "https://docs.example.com#getting-started",
          "prose",
          "3.5",
          "Example Docs",
          0.65f
        };
    when(documentChunkRepository.fullTextSearch("query", 30)).thenReturn(List.<Object[]>of(row));

    List<ScoredCandidate> results = searchService.executeFullTextSearch("query", 30);

    assertThat(results).hasSize(1);
    ScoredCandidate candidate = results.getFirst();
    assertThat(candidate.embeddingId()).isEqualTo("fts-id-1");
    assertThat(candidate.segment().text()).isEqualTo("Some FTS text");
    assertThat(candidate.segment().metadata().getString("source_url"))
        .isEqualTo("https://docs.example.com");
    assertThat(candidate.segment().metadata().getString("chunk_type")).isEqualTo("child");
    assertThat(candidate.segment().metadata().getString("source_name")).isEqualTo("Example Docs");
    assertThat(candidate.embedding()).isNull();
    assertThat(candidate.score()).isEqualTo(0.65, org.assertj.core.api.Assertions.within(0.01));
  }

  @Test
  void executeFullTextSearchHandlesNullMetadataFields() {
    Object[] row =
        new Object[] {"fts-id-2", "Text only", null, null, null, null, null, null, null, 0.30f};
    when(documentChunkRepository.fullTextSearch("query", 30)).thenReturn(List.<Object[]>of(row));

    List<ScoredCandidate> results = searchService.executeFullTextSearch("query", 30);

    assertThat(results).hasSize(1);
    ScoredCandidate candidate = results.getFirst();
    assertThat(candidate.segment().text()).isEqualTo("Text only");
    assertThat(candidate.segment().metadata().getString("source_url")).isNull();
  }
}
