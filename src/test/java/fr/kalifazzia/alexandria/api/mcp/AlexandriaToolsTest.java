package fr.kalifazzia.alexandria.api.mcp;

import fr.kalifazzia.alexandria.api.mcp.dto.DocumentDto;
import fr.kalifazzia.alexandria.api.mcp.dto.IndexResultDto;
import fr.kalifazzia.alexandria.api.mcp.dto.SearchResultDto;
import fr.kalifazzia.alexandria.core.model.Document;
import fr.kalifazzia.alexandria.core.port.DocumentRepository;
import fr.kalifazzia.alexandria.core.port.EmbeddingGenerator;
import fr.kalifazzia.alexandria.core.port.GraphRepository;
import fr.kalifazzia.alexandria.core.port.SearchRepository;
import fr.kalifazzia.alexandria.core.search.HybridSearchFilters;
import fr.kalifazzia.alexandria.core.search.SearchResult;
import fr.kalifazzia.alexandria.core.search.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AlexandriaTools MCP endpoints.
 * Tests validation, path security, limit clamping, and DTO mapping.
 *
 * Uses port interfaces for mocking (Mockito limitation on Java 25 with concrete classes).
 */
class AlexandriaToolsTest {

    private DocumentRepository documentRepository;
    private EmbeddingGenerator embeddingGenerator;
    private SearchRepository searchRepository;
    private GraphRepository graphRepository;
    private SearchService searchService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Only mock port interfaces which Mockito can handle on Java 25
        documentRepository = mock(DocumentRepository.class);
        embeddingGenerator = mock(EmbeddingGenerator.class);
        searchRepository = mock(SearchRepository.class);
        graphRepository = mock(GraphRepository.class);

        // Create real SearchService with mocked dependencies
        searchService = new SearchService(embeddingGenerator, searchRepository, graphRepository, documentRepository);
    }

    private AlexandriaTools createTools(Path allowedPath) {
        return new AlexandriaTools(
                searchService,
                null,  // IngestionService - will test path validation which doesn't need it
                documentRepository,
                List.of(allowedPath.toString())
        );
    }

    private AlexandriaTools createToolsForSearch() {
        return new AlexandriaTools(
                searchService,
                null,
                documentRepository,
                List.of(tempDir.toString())
        );
    }

    // =====================
    // search_docs tests - limit clamping
    // =====================

    @Test
    void searchDocs_limitNull_usesDefault10() {
        AlexandriaTools tools = createToolsForSearch();
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of());

        tools.search_docs("query", null, null, null);

        ArgumentCaptor<HybridSearchFilters> captor = ArgumentCaptor.forClass(HybridSearchFilters.class);
        verify(searchRepository).hybridSearch(any(), eq("query"), captor.capture());
        assertThat(captor.getValue().maxResults()).isEqualTo(10);
    }

    @Test
    void searchDocs_limitLessThan1_clampsTo1() {
        AlexandriaTools tools = createToolsForSearch();
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of());

        tools.search_docs("query", 0, null, null);

        ArgumentCaptor<HybridSearchFilters> captor = ArgumentCaptor.forClass(HybridSearchFilters.class);
        verify(searchRepository).hybridSearch(any(), eq("query"), captor.capture());
        assertThat(captor.getValue().maxResults()).isEqualTo(1);
    }

    @Test
    void searchDocs_limitNegative_clampsTo1() {
        AlexandriaTools tools = createToolsForSearch();
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of());

        tools.search_docs("query", -5, null, null);

        ArgumentCaptor<HybridSearchFilters> captor = ArgumentCaptor.forClass(HybridSearchFilters.class);
        verify(searchRepository).hybridSearch(any(), eq("query"), captor.capture());
        assertThat(captor.getValue().maxResults()).isEqualTo(1);
    }

    @Test
    void searchDocs_limitGreaterThan100_clampsTo100() {
        AlexandriaTools tools = createToolsForSearch();
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of());

        tools.search_docs("query", 150, null, null);

        ArgumentCaptor<HybridSearchFilters> captor = ArgumentCaptor.forClass(HybridSearchFilters.class);
        verify(searchRepository).hybridSearch(any(), eq("query"), captor.capture());
        assertThat(captor.getValue().maxResults()).isEqualTo(100);
    }

    @Test
    void searchDocs_limitWithinRange_usesProvidedValue() {
        AlexandriaTools tools = createToolsForSearch();
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of());

        tools.search_docs("query", 25, null, null);

        ArgumentCaptor<HybridSearchFilters> captor = ArgumentCaptor.forClass(HybridSearchFilters.class);
        verify(searchRepository).hybridSearch(any(), eq("query"), captor.capture());
        assertThat(captor.getValue().maxResults()).isEqualTo(25);
    }

    // =====================
    // search_docs tests - tags parsing
    // =====================

    @Test
    void searchDocs_tagsNull_passesNullToFilters() {
        AlexandriaTools tools = createToolsForSearch();
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of());

        tools.search_docs("query", 10, null, null);

        ArgumentCaptor<HybridSearchFilters> captor = ArgumentCaptor.forClass(HybridSearchFilters.class);
        verify(searchRepository).hybridSearch(any(), eq("query"), captor.capture());
        assertThat(captor.getValue().tags()).isNull();
    }

    @Test
    void searchDocs_tagsBlank_passesNullToFilters() {
        AlexandriaTools tools = createToolsForSearch();
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of());

        tools.search_docs("query", 10, null, "   ");

        ArgumentCaptor<HybridSearchFilters> captor = ArgumentCaptor.forClass(HybridSearchFilters.class);
        verify(searchRepository).hybridSearch(any(), eq("query"), captor.capture());
        assertThat(captor.getValue().tags()).isNull();
    }

    @Test
    void searchDocs_tagsEmpty_passesNullToFilters() {
        AlexandriaTools tools = createToolsForSearch();
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of());

        tools.search_docs("query", 10, null, "");

        ArgumentCaptor<HybridSearchFilters> captor = ArgumentCaptor.forClass(HybridSearchFilters.class);
        verify(searchRepository).hybridSearch(any(), eq("query"), captor.capture());
        assertThat(captor.getValue().tags()).isNull();
    }

    @Test
    void searchDocs_tagsValid_parsesSplitAndTrims() {
        AlexandriaTools tools = createToolsForSearch();
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of());

        tools.search_docs("query", 10, null, "java, spring , docker");

        ArgumentCaptor<HybridSearchFilters> captor = ArgumentCaptor.forClass(HybridSearchFilters.class);
        verify(searchRepository).hybridSearch(any(), eq("query"), captor.capture());
        assertThat(captor.getValue().tags()).containsExactly("java", "spring", "docker");
    }

    @Test
    void searchDocs_tagsWithEmptyElements_filtersEmptyStrings() {
        AlexandriaTools tools = createToolsForSearch();
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of());

        tools.search_docs("query", 10, null, "java,,spring,  ,docker");

        ArgumentCaptor<HybridSearchFilters> captor = ArgumentCaptor.forClass(HybridSearchFilters.class);
        verify(searchRepository).hybridSearch(any(), eq("query"), captor.capture());
        assertThat(captor.getValue().tags()).containsExactly("java", "spring", "docker");
    }

    @Test
    void searchDocs_categoryProvided_passesToFilters() {
        AlexandriaTools tools = createToolsForSearch();
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of());

        tools.search_docs("query", 10, "tutorials", null);

        ArgumentCaptor<HybridSearchFilters> captor = ArgumentCaptor.forClass(HybridSearchFilters.class);
        verify(searchRepository).hybridSearch(any(), eq("query"), captor.capture());
        assertThat(captor.getValue().category()).isEqualTo("tutorials");
    }

    @Test
    void searchDocs_withResults_mapsToDto() {
        AlexandriaTools tools = createToolsForSearch();
        UUID docId = UUID.randomUUID();
        SearchResult searchResult = new SearchResult(
                UUID.randomUUID(),
                "Child content",
                0,
                UUID.randomUUID(),
                "Parent context",
                docId,
                "Test Title",
                "/path/to/doc.md",
                "tutorials",
                List.of("java", "spring"),
                0.95
        );
        when(embeddingGenerator.embed("query")).thenReturn(new float[]{0.1f});
        when(searchRepository.hybridSearch(any(), eq("query"), any())).thenReturn(List.of(searchResult));

        List<SearchResultDto> results = tools.search_docs("query", 10, null, null);

        assertThat(results).hasSize(1);
        SearchResultDto dto = results.get(0);
        assertThat(dto.documentId()).isEqualTo(docId.toString());
        assertThat(dto.documentTitle()).isEqualTo("Test Title");
        assertThat(dto.documentPath()).isEqualTo("/path/to/doc.md");
        assertThat(dto.category()).isEqualTo("tutorials");
        assertThat(dto.tags()).containsExactly("java", "spring");
        assertThat(dto.matchedContent()).isEqualTo("Child content");
        assertThat(dto.parentContext()).isEqualTo("Parent context");
        assertThat(dto.similarity()).isEqualTo(0.95);
    }

    // =====================
    // index_docs tests - path validation
    // =====================

    @Test
    void indexDocs_pathNotExists_returnsError() {
        AlexandriaTools tools = createTools(tempDir);

        IndexResultDto result = tools.index_docs("/non/existent/path");

        assertThat(result.status()).isEqualTo("error");
        assertThat(result.message()).contains("does not exist");
    }

    @Test
    void indexDocs_pathIsFile_returnsError() throws Exception {
        AlexandriaTools tools = createTools(tempDir);
        Path file = Files.createFile(tempDir.resolve("file.md"));

        IndexResultDto result = tools.index_docs(file.toString());

        assertThat(result.status()).isEqualTo("error");
        assertThat(result.message()).contains("not a directory");
    }

    @Test
    void indexDocs_pathNotAllowed_returnsError() throws Exception {
        // Create two separate temp directories - one allowed, one not
        Path allowedDir = Files.createDirectory(tempDir.resolve("allowed"));
        Path notAllowedDir = Files.createDirectory(tempDir.resolve("not-allowed"));

        // Create tools that only allows the "allowed" directory
        AlexandriaTools tools = new AlexandriaTools(
                searchService,
                null,
                documentRepository,
                List.of(allowedDir.toString())
        );

        // Try to index the "not-allowed" directory
        IndexResultDto result = tools.index_docs(notAllowedDir.toString());

        assertThat(result.status()).isEqualTo("error");
        assertThat(result.message()).contains("not allowed");
    }

    @Test
    void indexDocs_returnsDirectoryPathInResult() {
        AlexandriaTools tools = createTools(tempDir);

        IndexResultDto result = tools.index_docs("/non/existent/path");

        assertThat(result.directoryPath()).isEqualTo("/non/existent/path");
    }

    @Test
    void indexDocs_validAllowedPath_passesValidation() throws Exception {
        // Create tools with null IngestionService - if path passes validation,
        // it will try to call IngestionService and throw NPE (caught as error)
        AlexandriaTools tools = createTools(tempDir);
        Path subDir = Files.createDirectory(tempDir.resolve("valid-docs"));

        // Error with NPE message means path validation passed (isPathAllowed returned true)
        // and code reached ingestionService.ingestDirectory() which is null
        IndexResultDto result = tools.index_docs(subDir.toString());

        assertThat(result.status()).isEqualTo("error");
        assertThat(result.message()).contains("Indexing failed");
        // NPE from null IngestionService proves path passed validation
    }

    // =====================
    // list_categories tests
    // =====================

    @Test
    void listCategories_delegatesToRepository() {
        AlexandriaTools tools = createToolsForSearch();
        when(documentRepository.findDistinctCategories())
                .thenReturn(List.of("tutorials", "api-reference", "guides"));

        List<String> categories = tools.list_categories();

        assertThat(categories).containsExactly("tutorials", "api-reference", "guides");
        verify(documentRepository).findDistinctCategories();
    }

    @Test
    void listCategories_emptyResult_returnsEmptyList() {
        AlexandriaTools tools = createToolsForSearch();
        when(documentRepository.findDistinctCategories()).thenReturn(List.of());

        List<String> categories = tools.list_categories();

        assertThat(categories).isEmpty();
    }

    // =====================
    // get_doc tests
    // =====================

    @Test
    void getDoc_invalidUuidFormat_throwsException() {
        AlexandriaTools tools = createToolsForSearch();

        assertThatThrownBy(() -> tools.get_doc("not-a-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid document ID format");
    }

    @Test
    void getDoc_documentNotFound_throwsException() {
        AlexandriaTools tools = createToolsForSearch();
        UUID docId = UUID.randomUUID();
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tools.get_doc(docId.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Document not found");
    }

    @Test
    void getDoc_documentFound_returnsDto() {
        AlexandriaTools tools = createToolsForSearch();
        UUID docId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-20T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-21T15:30:00Z");
        Document doc = new Document(
                docId,
                "/path/to/doc.md",
                "Test Document",
                "tutorials",
                List.of("java", "spring"),
                "hash123",
                Map.of("author", "John"),
                createdAt,
                updatedAt
        );
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        DocumentDto result = tools.get_doc(docId.toString());

        assertThat(result.id()).isEqualTo(docId.toString());
        assertThat(result.path()).isEqualTo("/path/to/doc.md");
        assertThat(result.title()).isEqualTo("Test Document");
        assertThat(result.category()).isEqualTo("tutorials");
        assertThat(result.tags()).containsExactly("java", "spring");
        assertThat(result.frontmatter()).containsEntry("author", "John");
        assertThat(result.createdAt()).isEqualTo(createdAt.toString());
        assertThat(result.updatedAt()).isEqualTo(updatedAt.toString());
    }

    // =====================
    // toDocumentDto tests - timestamps null handling
    // =====================

    @Test
    void getDoc_documentWithNullCreatedAt_returnsNullInDto() {
        AlexandriaTools tools = createToolsForSearch();
        UUID docId = UUID.randomUUID();
        Document doc = new Document(
                docId,
                "/path/to/doc.md",
                "Test Document",
                "tutorials",
                List.of(),
                "hash123",
                Map.of(),
                null,  // null createdAt
                Instant.now()
        );
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        DocumentDto result = tools.get_doc(docId.toString());

        assertThat(result.createdAt()).isNull();
    }

    @Test
    void getDoc_documentWithNullUpdatedAt_returnsNullInDto() {
        AlexandriaTools tools = createToolsForSearch();
        UUID docId = UUID.randomUUID();
        Document doc = new Document(
                docId,
                "/path/to/doc.md",
                "Test Document",
                "tutorials",
                List.of(),
                "hash123",
                Map.of(),
                Instant.now(),
                null  // null updatedAt
        );
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        DocumentDto result = tools.get_doc(docId.toString());

        assertThat(result.updatedAt()).isNull();
    }

    @Test
    void getDoc_documentWithBothTimestampsNull_returnsNullsInDto() {
        AlexandriaTools tools = createToolsForSearch();
        UUID docId = UUID.randomUUID();
        Document doc = new Document(
                docId,
                "/path/to/doc.md",
                "Test Document",
                "tutorials",
                List.of(),
                "hash123",
                Map.of(),
                null,
                null
        );
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        DocumentDto result = tools.get_doc(docId.toString());

        assertThat(result.createdAt()).isNull();
        assertThat(result.updatedAt()).isNull();
    }

    @Test
    void getDoc_documentWithBothTimestampsPresent_returnsStringsInDto() {
        AlexandriaTools tools = createToolsForSearch();
        UUID docId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-20T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-01-21T15:30:00Z");
        Document doc = new Document(
                docId,
                "/path/to/doc.md",
                "Test Document",
                "tutorials",
                List.of(),
                "hash123",
                Map.of(),
                createdAt,
                updatedAt
        );
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        DocumentDto result = tools.get_doc(docId.toString());

        assertThat(result.createdAt()).isEqualTo("2026-01-20T10:00:00Z");
        assertThat(result.updatedAt()).isEqualTo("2026-01-21T15:30:00Z");
    }
}
