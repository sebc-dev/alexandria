package fr.kalifazzia.alexandria.core.search;

import fr.kalifazzia.alexandria.core.model.Document;
import fr.kalifazzia.alexandria.core.port.DocumentRepository;
import fr.kalifazzia.alexandria.core.port.EmbeddingGenerator;
import fr.kalifazzia.alexandria.core.port.GraphRepository;
import fr.kalifazzia.alexandria.core.port.SearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Mock
    private GraphRepository graphRepository;

    @Mock
    private DocumentRepository documentRepository;

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(embeddingGenerator, searchRepository, graphRepository, documentRepository);
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
        SearchResult expectedResult = createSearchResult(0.9);
        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.searchSimilar(eq(embedding), any())).thenReturn(List.of(expectedResult));

        // When
        List<SearchResult> results = searchService.search(query, 5);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isEqualTo(expectedResult);
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
        SearchResult expectedResult = createSearchResult(1.5);
        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.hybridSearch(eq(embedding), eq(query), any())).thenReturn(List.of(expectedResult));

        // When
        List<SearchResult> results = searchService.hybridSearch(query, 5);

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0)).isEqualTo(expectedResult);
        verify(searchRepository).hybridSearch(eq(embedding), eq(query), argThat(f ->
                f.maxResults() == 5 && f.vectorWeight() == 1.0 && f.textWeight() == 1.0 && f.rrfK() == 60
        ));
    }

    @Test
    void hybridSearchWithGraph_shouldReturnSearchResultsAndRelatedDocuments() {
        // Given
        String query = "spring configuration";
        float[] embedding = new float[]{0.1f};
        HybridSearchFilters filters = HybridSearchFilters.defaults(10);

        UUID docId1 = UUID.randomUUID();
        UUID relatedDocId = UUID.randomUUID();

        SearchResult searchResult = createSearchResultWithDocId(docId1, 1.5);
        Document relatedDoc = createDocument(relatedDocId, "Related Doc", "/related.md", "java");

        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.hybridSearch(embedding, query, filters)).thenReturn(List.of(searchResult));
        when(graphRepository.findRelatedDocuments(docId1, 2)).thenReturn(List.of(relatedDocId));
        when(documentRepository.findByIds(Set.of(relatedDocId))).thenReturn(List.of(relatedDoc));

        // When
        HybridSearchResult result = searchService.hybridSearchWithGraph(query, filters);

        // Then
        assertThat(result.searchResults()).hasSize(1);
        assertThat(result.relatedDocuments()).hasSize(1);
        assertThat(result.relatedDocuments().get(0).documentId()).isEqualTo(relatedDocId);
    }

    @Test
    void hybridSearchWithGraph_shouldExcludeSearchResultDocsFromRelated() {
        // Given
        String query = "test";
        float[] embedding = new float[]{0.1f};
        HybridSearchFilters filters = HybridSearchFilters.defaults(10);

        UUID docId = UUID.randomUUID();
        SearchResult searchResult = createSearchResultWithDocId(docId, 1.5);

        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.hybridSearch(embedding, query, filters)).thenReturn(List.of(searchResult));
        // Graph returns the same doc that's already in results
        when(graphRepository.findRelatedDocuments(docId, 2)).thenReturn(List.of(docId));

        // When
        HybridSearchResult result = searchService.hybridSearchWithGraph(query, filters);

        // Then
        assertThat(result.relatedDocuments()).isEmpty();
        verify(documentRepository, never()).findByIds(any());
    }

    @Test
    void hybridSearchWithGraph_shouldHandleEmptySearchResults() {
        // Given
        String query = "unknown";
        float[] embedding = new float[]{0.1f};
        HybridSearchFilters filters = HybridSearchFilters.defaults(10);

        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.hybridSearch(embedding, query, filters)).thenReturn(List.of());

        // When
        HybridSearchResult result = searchService.hybridSearchWithGraph(query, filters);

        // Then
        assertThat(result.searchResults()).isEmpty();
        assertThat(result.relatedDocuments()).isEmpty();
        verify(graphRepository, never()).findRelatedDocuments(any(), anyInt());
    }

    @Test
    void hybridSearchWithGraph_shouldValidateMaxHops_rejectZero() {
        assertThatThrownBy(() -> searchService.hybridSearchWithGraph("test", HybridSearchFilters.defaults(10), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxHops");
    }

    @Test
    void hybridSearchWithGraph_shouldValidateMaxHops_rejectEleven() {
        assertThatThrownBy(() -> searchService.hybridSearchWithGraph("test", HybridSearchFilters.defaults(10), 11))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxHops");
    }

    @Test
    void hybridSearchWithGraph_shouldAcceptMaxHops_minBoundary() {
        // Given
        String query = "test";
        float[] embedding = new float[]{0.1f};
        HybridSearchFilters filters = HybridSearchFilters.defaults(10);
        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.hybridSearch(embedding, query, filters)).thenReturn(List.of());

        // When - maxHops = 1 should be accepted
        HybridSearchResult result = searchService.hybridSearchWithGraph(query, filters, 1);

        // Then - no exception, returns empty result
        assertThat(result.searchResults()).isEmpty();
    }

    @Test
    void hybridSearchWithGraph_shouldAcceptMaxHops_maxBoundary() {
        // Given
        String query = "test";
        float[] embedding = new float[]{0.1f};
        HybridSearchFilters filters = HybridSearchFilters.defaults(10);
        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.hybridSearch(embedding, query, filters)).thenReturn(List.of());

        // When - maxHops = 10 should be accepted
        HybridSearchResult result = searchService.hybridSearchWithGraph(query, filters, 10);

        // Then - no exception, returns empty result
        assertThat(result.searchResults()).isEmpty();
    }

    @Test
    void hybridSearchWithGraph_shouldContinueOnGraphTraversalFailure() {
        // Given
        String query = "test";
        float[] embedding = new float[]{0.1f};
        HybridSearchFilters filters = HybridSearchFilters.defaults(10);

        UUID docId = UUID.randomUUID();
        SearchResult searchResult = createSearchResultWithDocId(docId, 1.5);

        when(embeddingGenerator.embed(query)).thenReturn(embedding);
        when(searchRepository.hybridSearch(embedding, query, filters)).thenReturn(List.of(searchResult));
        when(graphRepository.findRelatedDocuments(docId, 2)).thenThrow(new RuntimeException("Graph error"));

        // When
        HybridSearchResult result = searchService.hybridSearchWithGraph(query, filters);

        // Then - should return results without related documents, not fail
        assertThat(result.searchResults()).hasSize(1);
        assertThat(result.relatedDocuments()).isEmpty();
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

    private SearchResult createSearchResultWithDocId(UUID docId, double similarity) {
        return new SearchResult(
                UUID.randomUUID(),
                "child content",
                0,
                UUID.randomUUID(),
                "parent context",
                docId,
                "Document Title",
                "/docs/file.md",
                "java",
                List.of("tag"),
                similarity
        );
    }

    private Document createDocument(UUID id, String title, String path, String category) {
        return new Document(
                id, path, title, category, List.of(),
                "hash", Map.of(), Instant.now(), Instant.now()
        );
    }
}
