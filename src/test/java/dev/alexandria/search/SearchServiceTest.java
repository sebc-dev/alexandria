package dev.alexandria.search;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    EmbeddingModel embeddingModel;

    @InjectMocks
    SearchService searchService;

    @Captor
    ArgumentCaptor<EmbeddingSearchRequest> searchRequestCaptor;

    @Test
    void searchReturnsResultWithCorrectFieldsAndPassesBothQueryAndEmbedding() {
        Embedding dummyEmbedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
        when(embeddingModel.embed("test query")).thenReturn(Response.from(dummyEmbedding));

        TextSegment segment = TextSegment.from(
                "Spring Boot routing guide",
                Metadata.from("source_url", "https://docs.spring.io/routing")
                        .put("section_path", "Web > Routing")
        );
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.85, "id-1", dummyEmbedding, segment);
        EmbeddingSearchResult<TextSegment> storeResult = new EmbeddingSearchResult<>(List.of(match));
        when(embeddingStore.search(searchRequestCaptor.capture())).thenReturn(storeResult);

        List<SearchResult> results = searchService.search(new SearchRequest("test query"));

        assertThat(results).hasSize(1);
        SearchResult result = results.getFirst();
        assertThat(result.text()).isEqualTo("Spring Boot routing guide");
        assertThat(result.score()).isEqualTo(0.85);
        assertThat(result.sourceUrl()).isEqualTo("https://docs.spring.io/routing");
        assertThat(result.sectionPath()).isEqualTo("Web > Routing");

        // Verify both queryEmbedding AND query text are passed for hybrid search
        EmbeddingSearchRequest capturedRequest = searchRequestCaptor.getValue();
        assertThat(capturedRequest.queryEmbedding()).isNotNull();
        assertThat(capturedRequest.query()).isEqualTo("test query");
    }

    @Test
    void searchPassesMaxResultsToEmbeddingStore() {
        Embedding dummyEmbedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
        when(embeddingModel.embed("test query")).thenReturn(Response.from(dummyEmbedding));

        EmbeddingSearchResult<TextSegment> storeResult = new EmbeddingSearchResult<>(List.of());
        when(embeddingStore.search(searchRequestCaptor.capture())).thenReturn(storeResult);

        searchService.search(new SearchRequest("test query", 3));

        EmbeddingSearchRequest capturedRequest = searchRequestCaptor.getValue();
        assertThat(capturedRequest.maxResults()).isEqualTo(3);
    }

    @Test
    void searchReturnsEmptyListWhenNoMatches() {
        Embedding dummyEmbedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
        when(embeddingModel.embed("test query")).thenReturn(Response.from(dummyEmbedding));

        EmbeddingSearchResult<TextSegment> storeResult = new EmbeddingSearchResult<>(List.of());
        when(embeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(storeResult);

        List<SearchResult> results = searchService.search(new SearchRequest("test query"));

        assertThat(results).isEmpty();
    }

    @Test
    void searchHandlesNullMetadataGracefully() {
        Embedding dummyEmbedding = Embedding.from(new float[]{0.1f, 0.2f, 0.3f});
        when(embeddingModel.embed("test query")).thenReturn(Response.from(dummyEmbedding));

        // TextSegment with no source_url or section_path metadata
        TextSegment segment = TextSegment.from("Some text without citation metadata");
        EmbeddingMatch<TextSegment> match = new EmbeddingMatch<>(0.5, "id-2", dummyEmbedding, segment);
        EmbeddingSearchResult<TextSegment> storeResult = new EmbeddingSearchResult<>(List.of(match));
        when(embeddingStore.search(any(EmbeddingSearchRequest.class))).thenReturn(storeResult);

        List<SearchResult> results = searchService.search(new SearchRequest("test query"));

        assertThat(results).hasSize(1);
        SearchResult result = results.getFirst();
        assertThat(result.text()).isEqualTo("Some text without citation metadata");
        assertThat(result.score()).isEqualTo(0.5);
        assertThat(result.sourceUrl()).isNull();
        assertThat(result.sectionPath()).isNull();
    }
}
