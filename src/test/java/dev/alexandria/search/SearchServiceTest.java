package dev.alexandria.search;

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
import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    EmbeddingModel embeddingModel;

    @Mock
    RerankerService rerankerService;

    @InjectMocks
    SearchService searchService;

    @Captor
    ArgumentCaptor<EmbeddingSearchRequest> searchRequestCaptor;

    private static final Embedding DUMMY_EMBEDDING = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});

    private void stubEmbeddingModel(String query) {
        when(embeddingModel.embed(query)).thenReturn(Response.from(DUMMY_EMBEDDING));
    }

    private void stubEmptyStoreResult() {
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));
    }

    private void stubRerankerReturnsEmpty() {
        when(rerankerService.rerank(any(), any(), anyInt(), any()))
                .thenReturn(List.of());
    }

    private List<EmbeddingMatch<TextSegment>> stubStoreWithOneMatch() {
        TextSegment segment = TextSegment.from(
                "Spring Boot routing guide",
                Metadata.from("source_url", "https://docs.spring.io/routing")
                        .put("section_path", "Web > Routing")
        );
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.85, "id-1", DUMMY_EMBEDDING, segment);
        List<EmbeddingMatch<TextSegment>> matches = List.of(match);
        when(embeddingStore.search(searchRequestCaptor.capture()))
                .thenReturn(new EmbeddingSearchResult<>(matches));
        return matches;
    }

    @Test
    void searchWithNoFiltersPassesNullFilter() {
        stubEmbeddingModel("test query");
        stubStoreWithOneMatch();
        stubRerankerReturnsEmpty();

        searchService.search(new SearchRequest("test query"));

        EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
        assertThat(captured.filter()).isNull();
    }

    @Test
    void searchOverFetches50CandidatesForReranking() {
        stubEmbeddingModel("test query");
        stubStoreWithOneMatch();
        stubRerankerReturnsEmpty();

        searchService.search(new SearchRequest("test query", 3));

        EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
        assertThat(captured.maxResults()).isEqualTo(50);
    }

    @Test
    void searchWithSourceFilterBuildsEqualityFilter() {
        stubEmbeddingModel("test query");
        stubStoreWithOneMatch();
        stubRerankerReturnsEmpty();

        SearchRequest request = new SearchRequest(
                "test query", 10, "spring-boot", null, null, null, null, null
        );
        searchService.search(request);

        EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
        Filter filter = captured.filter();
        assertThat(filter).isInstanceOf(IsEqualTo.class);
        assertThat(filter).hasToString("metadataKey 'source_name' is equal to 'spring-boot'");
    }

    @Test
    void searchWithVersionFilterBuildsEqualityFilter() {
        stubEmbeddingModel("test query");
        stubStoreWithOneMatch();
        stubRerankerReturnsEmpty();

        SearchRequest request = new SearchRequest(
                "test query", 10, null, null, "3.2.0", null, null, null
        );
        searchService.search(request);

        EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
        Filter filter = captured.filter();
        assertThat(filter).isInstanceOf(IsEqualTo.class);
        assertThat(filter).hasToString("metadataKey 'version' is equal to '3.2.0'");
    }

    @Test
    void searchWithSectionPathFilterBuildsContainsStringFilter() {
        stubEmbeddingModel("test query");
        stubStoreWithOneMatch();
        stubRerankerReturnsEmpty();

        SearchRequest request = new SearchRequest(
                "test query", 10, null, "Getting Started/Quick Start", null, null, null, null
        );
        searchService.search(request);

        EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
        Filter filter = captured.filter();
        assertThat(filter).isInstanceOf(ContainsString.class);
        // Section path is slugified before filtering
        assertThat(filter).hasToString("metadataKey 'section_path' contains 'getting-started-quick-start'");
    }

    @Test
    void searchWithContentTypeFilterBuildsEqualityFilter() {
        stubEmbeddingModel("test query");
        stubStoreWithOneMatch();
        stubRerankerReturnsEmpty();

        SearchRequest request = new SearchRequest(
                "test query", 10, null, null, null, "CODE", null, null
        );
        searchService.search(request);

        EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
        Filter filter = captured.filter();
        assertThat(filter).isInstanceOf(IsEqualTo.class);
        assertThat(filter).hasToString("metadataKey 'content_type' is equal to 'code'");
    }

    @Test
    void searchWithContentTypeMixedSkipsFilter() {
        stubEmbeddingModel("test query");
        stubStoreWithOneMatch();
        stubRerankerReturnsEmpty();

        SearchRequest request = new SearchRequest(
                "test query", 10, null, null, null, "MIXED", null, null
        );
        searchService.search(request);

        EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
        assertThat(captured.filter()).isNull();
    }

    @Test
    void searchWithMultipleFiltersCombinesWithAnd() {
        stubEmbeddingModel("test query");
        stubStoreWithOneMatch();
        stubRerankerReturnsEmpty();

        SearchRequest request = new SearchRequest(
                "test query", 10, "spring-boot", null, "3.2.0", null, null, null
        );
        searchService.search(request);

        EmbeddingSearchRequest captured = searchRequestCaptor.getValue();
        Filter filter = captured.filter();
        assertThat(filter).isInstanceOf(And.class);
    }

    @Test
    void searchDelegatesToRerankerService() {
        stubEmbeddingModel("test query");
        List<EmbeddingMatch<TextSegment>> matches = stubStoreWithOneMatch();
        SearchResult expectedResult = new SearchResult("text", 0.85, "url", "path", 0.9);
        when(rerankerService.rerank(eq("test query"), eq(matches), eq(10), eq(null)))
                .thenReturn(List.of(expectedResult));

        SearchRequest request = new SearchRequest("test query");
        List<SearchResult> results = searchService.search(request);

        verify(rerankerService).rerank("test query", matches, 10, null);
        assertThat(results).containsExactly(expectedResult);
    }

    @Test
    void searchPassesMinScoreToReranker() {
        stubEmbeddingModel("test query");
        List<EmbeddingMatch<TextSegment>> matches = stubStoreWithOneMatch();
        when(rerankerService.rerank(eq("test query"), eq(matches), eq(5), eq(0.7)))
                .thenReturn(List.of());

        SearchRequest request = new SearchRequest(
                "test query", 5, null, null, null, null, 0.7, null
        );
        searchService.search(request);

        verify(rerankerService).rerank("test query", matches, 5, 0.7);
    }

    @Test
    void searchReturnsEmptyListWhenNoMatchesFromStore() {
        stubEmbeddingModel("test query");
        when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of()));
        when(rerankerService.rerank(eq("test query"), eq(List.of()), eq(10), eq(null)))
                .thenReturn(List.of());

        List<SearchResult> results = searchService.search(new SearchRequest("test query"));

        assertThat(results).isEmpty();
    }
}
