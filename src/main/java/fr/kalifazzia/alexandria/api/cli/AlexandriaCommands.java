package fr.kalifazzia.alexandria.api.cli;

import fr.kalifazzia.alexandria.core.ingestion.IngestionService;
import fr.kalifazzia.alexandria.core.port.ChunkRepository;
import fr.kalifazzia.alexandria.core.port.DocumentRepository;
import fr.kalifazzia.alexandria.core.port.GraphRepository;
import fr.kalifazzia.alexandria.core.search.SearchResult;
import fr.kalifazzia.alexandria.core.search.SearchService;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * CLI commands for Alexandria RAG system.
 * Provides command-line interface for indexation and search operations.
 */
@Command(group = "Alexandria")
@Component
public class AlexandriaCommands {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final IngestionService ingestionService;
    private final SearchService searchService;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final GraphRepository graphRepository;

    public AlexandriaCommands(
            IngestionService ingestionService,
            SearchService searchService,
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
            GraphRepository graphRepository) {
        this.ingestionService = ingestionService;
        this.searchService = searchService;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.graphRepository = graphRepository;
    }

    @Command(command = "index", description = "Index markdown files from a directory")
    public String index(
            @Option(longNames = "path", shortNames = 'p', required = true,
                    description = "Path to directory containing markdown files")
            String pathStr) {

        Path directory = Path.of(pathStr).toAbsolutePath();

        if (!Files.exists(directory)) {
            throw new IllegalArgumentException("Directory does not exist: " + directory);
        }
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Path is not a directory: " + directory);
        }

        long startTime = System.currentTimeMillis();
        ingestionService.ingestDirectory(directory);
        long duration = System.currentTimeMillis() - startTime;

        long docCount = documentRepository.count();
        long chunkCount = chunkRepository.count();

        return String.format("""
                Indexing completed in %.1f seconds.
                Documents indexed: %d
                Total chunks: %d
                """, duration / 1000.0, docCount, chunkCount);
    }

    @Command(command = "search", description = "Search indexed documentation")
    public String search(
            @Option(longNames = "query", shortNames = 'q', required = true,
                    description = "Search query text")
            String query,
            @Option(longNames = "limit", shortNames = 'n', defaultValue = "5",
                    description = "Maximum results to return (1-20)")
            int limit) {

        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("Limit must be between 1 and 20");
        }

        List<SearchResult> results = searchService.hybridSearch(query, limit);

        if (results.isEmpty()) {
            return "No results found for: " + query;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d results for '%s':\n\n", results.size(), query));

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append(String.format("%d. %s\n", i + 1, r.documentTitle()));
            sb.append(String.format("   Path: %s\n", r.documentPath()));
            sb.append(String.format("   Score: %.3f\n", r.similarity()));
            sb.append(String.format("   Excerpt: %s\n\n", truncate(r.childContent(), 150)));
        }

        return sb.toString();
    }

    @Command(command = "status", description = "Show database status")
    public String status() {
        long docCount = documentRepository.count();
        long chunkCount = chunkRepository.count();
        Optional<Instant> lastIndexed = documentRepository.findLastUpdated();

        StringBuilder sb = new StringBuilder();
        sb.append("Alexandria Status\n");
        sb.append("=================\n");
        sb.append(String.format("Documents: %d\n", docCount));
        sb.append(String.format("Chunks: %d\n", chunkCount));
        sb.append(String.format("Last indexed: %s\n",
                lastIndexed.map(DATE_FORMAT::format).orElse("Never")));

        return sb.toString();
    }

    @Command(command = "clear", description = "Clear all indexed data")
    public String clear(
            @Option(longNames = "force", shortNames = 'f', defaultValue = "false",
                    description = "Skip confirmation (required to actually clear)")
            boolean force) {

        if (!force) {
            return """
                    WARNING: This will delete all indexed data.

                    Use --force to confirm: clear --force
                    """;
        }

        // Clear graph data first (references PostgreSQL data)
        graphRepository.clearAll();

        // Then clear PostgreSQL tables (chunks reference documents)
        chunkRepository.deleteAll();
        documentRepository.deleteAll();

        return "All indexed data has been cleared.";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        // Normalize whitespace (newlines, multiple spaces)
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength - 3) + "...";
    }
}
