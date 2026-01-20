package fr.kalifazzia.alexandria.core.search;

import fr.kalifazzia.alexandria.core.port.EmbeddingGenerator;
import fr.kalifazzia.alexandria.core.port.SearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private EmbeddingGenerator embeddingGenerator;

    @Mock
    private SearchRepository searchRepository;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(embeddingGenerator, searchRepository);
    }

    @Test
    void search_shouldGenerateEmbeddingAndCallRepository() {
        // Given
        String query = "how to configure spring";
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        SearchFilters filters = SearchFilters.simple(10);
        SearchResult expectedResult = createSearchResult(0.85);

        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.searchSimilar(embedding, filters)).thenReturn(List.of(expectedResult));

        // When
        List<SearchResult> results = searchService.search(query, filters);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).similarity()).isEqualTo(0.85);
        verify(embeddingGenerator).embed(query);
        verify(searchRepository).searchSimilar(embedding, filters);
    }

    @Test
    void search_shouldThrowOnNullQuery() {
        assertThatThrownBy(() -> searchService.search(null, SearchFilters.simple(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void search_shouldThrowOnBlankQuery() {
        assertThatThrownBy(() -> searchService.search("   ", SearchFilters.simple(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void searchSimple_shouldUseSimpleFilters() {
        // Given
        String query = "test query";
        float[] embedding = new float[]{0.1f};
        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.searchSimilar(eq(embedding), any())).thenReturn(List.of());

        // When
        searchService.search(query, 5);

        // Then
        verify(searchRepository).searchSimilar(eq(embedding), argThat(f ->
                f.maxResults() == 5 && f.category() == null && f.tags() == null
        ));
    }

    @Test
    void search_shouldReturnEmptyListWhenNoMatches() {
        // Given
        when(embeddingGenerator.embed(anyString())).thenReturn(new float[]{0.1f});
        when(searchRepository.searchSimilar(any(), any())).thenReturn(List.of());

        // When
        List<SearchResult> results = searchService.search("unknown topic", 10);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    void hybridSearch_shouldGenerateEmbeddingAndCallRepository() {
        // Given
        String query = "spring boot configuration";
        float[] embedding = new float[]{0.1f, 0.2f, 0.3f};
        HybridSearchFilters filters = HybridSearchFilters.defaults(10);
        SearchResult expectedResult = createSearchResult(1.5);  // RRF score can exceed 1.0

        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.hybridSearch(embedding, query, filters)).thenReturn(List.of(expectedResult));

        // When
        List<SearchResult> results = searchService.hybridSearch(query, filters);

        // Then
        assertThat(results).hasSize(1);
        verify(embeddingGenerator).embed(query);
        verify(searchRepository).hybridSearch(embedding, query, filters);
    }

    @Test
    void hybridSearch_shouldThrowOnNullQuery() {
        assertThatThrownBy(() -> searchService.hybridSearch(null, HybridSearchFilters.defaults(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void hybridSearch_shouldThrowOnBlankQuery() {
        assertThatThrownBy(() -> searchService.hybridSearch("   ", HybridSearchFilters.defaults(10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null or blank");
    }

    @Test
    void hybridSearchSimple_shouldUseDefaultFilters() {
        // Given
        String query = "test query";
        float[] embedding = new float[]{0.1f};
        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.hybridSearch(eq(embedding), eq(query), any())).thenReturn(List.of());

        // When
        searchService.hybridSearch(query, 5);

        // Then
        verify(searchRepository).hybridSearch(eq(embedding), eq(query), argThat(f ->
                f.maxResults() == 5 && f.vectorWeight() == 1.0 && f.textWeight() == 1.0 && f.rrfK() == 60
        ));
    }

    private SearchResult createSearchResult(double similarity) {
        return new SearchResult(
                UUID.randomUUID(),
                "child content",
                0,
                UUID.randomUUID(),
                "parent context with more detail",
                UUID.randomUUID(),
                "Document Title",
                "/docs/file.md",
                "java",
                List.of("spring", "configuration"),
                similarity
        );
    }
}
