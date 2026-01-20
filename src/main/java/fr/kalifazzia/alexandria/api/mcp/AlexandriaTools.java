package fr.kalifazzia.alexandria.api.mcp;

import fr.kalifazzia.alexandria.api.mcp.dto.DocumentDto;
import fr.kalifazzia.alexandria.api.mcp.dto.IndexResultDto;
import fr.kalifazzia.alexandria.api.mcp.dto.SearchResultDto;
import fr.kalifazzia.alexandria.core.ingestion.IngestionService;
import fr.kalifazzia.alexandria.core.model.Document;
import fr.kalifazzia.alexandria.core.port.DocumentRepository;
import fr.kalifazzia.alexandria.core.search.HybridSearchFilters;
import fr.kalifazzia.alexandria.core.search.SearchResult;
import fr.kalifazzia.alexandria.core.search.SearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * MCP tools for Alexandria documentation RAG.
 * Exposes search and indexation capabilities to Claude Code.
 */
@Component
public class AlexandriaTools {

    private final SearchService searchService;
    private final IngestionService ingestionService;
    private final DocumentRepository documentRepository;

    public AlexandriaTools(
            SearchService searchService,
            IngestionService ingestionService,
            DocumentRepository documentRepository) {
        this.searchService = searchService;
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
    }

    @Tool(description = "Search Alexandria documentation by semantic similarity. Returns matching chunks with parent context for better understanding. Use this to find relevant documentation about coding conventions, patterns, or technical topics.")
    public List<SearchResultDto> search_docs(
            @ToolParam(description = "Natural language search query") String query,
            @ToolParam(description = "Maximum number of results (1-100, default 10)", required = false) Integer maxResults,
            @ToolParam(description = "Filter by category name", required = false) String category,
            @ToolParam(description = "Filter by tags (comma-separated)", required = false) String tags) {

        int limit = maxResults != null ? Math.min(Math.max(maxResults, 1), 100) : 10;
        List<String> tagList = tags != null && !tags.isBlank()
                ? Arrays.asList(tags.split(","))
                : null;

        // Use factory method which provides default weights (1.0, 1.0) and RRF k (60)
        HybridSearchFilters filters = HybridSearchFilters.withFilters(limit, category, tagList);

        return searchService.hybridSearch(query, filters).stream()
                .map(this::toSearchResultDto)
                .toList();
    }

    @Tool(description = "Index markdown documentation from a directory. Recursively processes all .md files, extracting metadata from YAML frontmatter and generating semantic embeddings.")
    public IndexResultDto index_docs(
            @ToolParam(description = "Absolute path to directory containing .md files") String directoryPath) {

        Path path = Path.of(directoryPath);

        if (!Files.exists(path)) {
            return new IndexResultDto(directoryPath, "error", "Directory does not exist: " + directoryPath);
        }

        if (!Files.isDirectory(path)) {
            return new IndexResultDto(directoryPath, "error", "Path is not a directory: " + directoryPath);
        }

        try {
            ingestionService.ingestDirectory(path);
            return new IndexResultDto(directoryPath, "success", "Indexing completed for directory: " + directoryPath);
        } catch (Exception e) {
            return new IndexResultDto(directoryPath, "error", "Indexing failed: " + e.getMessage());
        }
    }

    @Tool(description = "List all available documentation categories. Use this to discover what types of documentation are indexed.")
    public List<String> list_categories() {
        return documentRepository.findDistinctCategories();
    }

    @Tool(description = "Get full document details by ID. Returns complete metadata including frontmatter. Use this after search to get more context about a specific document.")
    public DocumentDto get_doc(
            @ToolParam(description = "Document UUID from search results") String documentId) {

        UUID id;
        try {
            id = UUID.fromString(documentId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid document ID format: " + documentId);
        }

        return documentRepository.findById(id)
                .map(this::toDocumentDto)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
    }

    private SearchResultDto toSearchResultDto(SearchResult result) {
        return new SearchResultDto(
                result.documentId().toString(),
                result.documentTitle(),
                result.documentPath(),
                result.category(),
                result.tags(),
                result.childContent(),
                result.parentContext(),
                result.similarity()
        );
    }

    private DocumentDto toDocumentDto(Document doc) {
        return new DocumentDto(
                doc.id().toString(),
                doc.path(),
                doc.title(),
                doc.category(),
                doc.tags(),
                doc.frontmatter(),
                doc.createdAt() != null ? doc.createdAt().toString() : null,
                doc.updatedAt() != null ? doc.updatedAt().toString() : null
        );
    }
}
