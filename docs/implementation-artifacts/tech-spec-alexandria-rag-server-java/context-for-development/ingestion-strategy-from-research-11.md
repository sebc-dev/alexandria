# Ingestion Strategy (from research #11)

**Architecture hybride:** CLI pour bulk ingestion + MCP tool pour usage léger.

| Mode | Usage | Limite | Timeout |
|------|-------|--------|---------|
| **CLI Picocli** | Bulk ingestion (dossiers) | Illimité | N/A |
| **MCP tool** | 1-5 documents à la fois | 5 docs max | 60s |

**Pas de watcher répertoire** - Complexité évitée (race conditions, locks, battery drain).

```java
package dev.alexandria.cli;

import com.google.common.collect.Lists;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI Picocli pour ingestion bulk.
 * Usage: java -jar alexandria.jar ingest /path/to/docs --recursive
 */
@Component
@Command(name = "ingest", mixinStandardHelpOptions = true,
         description = "Ingest documents into the knowledge base")
public class IngestCommand implements Runnable {

    @Parameters(index = "0", description = "Path to file or directory")
    private Path path;

    @Option(names = {"-r", "--recursive"}, description = "Process subdirectories")
    private boolean recursive = false;

    @Option(names = {"-b", "--batch-size"}, description = "Documents per batch")
    private int batchSize = 25;  // Optimal pour hardware limité (4 cores)

    @Option(names = {"--dry-run"}, description = "Show what would be ingested")
    private boolean dryRun = false;

    private final IngestionService ingestionService;

    @Override
    public void run() {
        List<Path> files = collectFiles(path, recursive);

        if (dryRun) {
            files.forEach(f -> System.out.println("Would ingest: " + f));
            return;
        }

        // Batch processing avec progress
        Lists.partition(files, batchSize).forEach(batch -> {
            System.out.printf("Processing batch: %d files...%n", batch.size());
            ingestionService.ingestBatch(batch);
        });

        System.out.printf("Ingested %d documents successfully.%n", files.size());
    }

    private List<Path> collectFiles(Path path, boolean recursive) {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }

        try (var stream = recursive
                ? Files.walk(path)
                : Files.list(path)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(this::isSupportedFormat)
                .toList();
        } catch (IOException e) {
            throw new IngestionException("Failed to scan directory", e);
        }
    }

    private boolean isSupportedFormat(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".txt")
            || name.equals("llms.txt") || name.equals("llms-full.txt")
            || name.endsWith(".html");
    }
}
```

**MCP Tool limité:**

```java
import org.springframework.ai.mcp.server.McpSyncRequestContext;
import org.springframework.ai.mcp.server.annotation.McpTool;
import org.springframework.ai.mcp.server.annotation.McpToolParam;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

@McpTool(name = "ingest_document",
         description = "Ingest a single document into the knowledge base")
public CallToolResult ingestDocument(
        McpSyncRequestContext context,  // Auto-injecté, non exposé comme paramètre
        @McpToolParam(description = "File path") String filePath) {

    Path path = Path.of(filePath);

    // Validation: max 5 documents via MCP
    if (Files.isDirectory(path)) {
        try (var files = Files.list(path)) {
            if (files.count() > 5) {
                return McpResponseFormatter.errorResult(
                    "Directory contains more than 5 files. Use CLI for bulk ingestion.",
                    ErrorCategory.VALIDATION);
            }
        }
    }

    try {
        context.progress(p -> p.progress(0.1).total(1.0).message("Validating document..."));
        ingestionService.validateDocument(path);

        context.progress(p -> p.progress(0.3).total(1.0).message("Parsing document..."));
        var document = parseDocument(path);

        context.progress(p -> p.progress(0.5).total(1.0).message("Chunking..."));
        var chunks = splitter.split(document);

        context.progress(p -> p.progress(0.7).total(1.0).message("Generating embeddings..."));
        ingestionService.embedAndStore(chunks);

        context.progress(p -> p.progress(1.0).total(1.0).message("Done!"));

        return CallToolResult.builder()
            .addTextContent(String.format(
                "Successfully ingested: %s (%d chunks)",
                path.getFileName(), chunks.size()))
            .build();

    } catch (AlexandriaException e) {
        return McpResponseFormatter.errorResult(e.getMessage(), e.getCategory());
    }
}
```

**Document Formats supportés (from research #13):**

| Format | Parser | Extensions |
|--------|--------|------------|
| Markdown | TextDocumentParser + AlexandriaMarkdownSplitter | `.md` |
| Text | TextDocumentParser (Langchain4j) | `.txt` |
| llms.txt | LlmsTxtParser (custom) | `llms.txt`, `llms-full.txt` |
| HTML | JsoupDocumentParser (Langchain4j) | `.html`, `.htm` |

**Note:** CommonMark-java 0.27.0 + GFM est utilisé par `AlexandriaMarkdownSplitter` pour le parsing structurel (headers, code blocks, tables), pas comme DocumentParser Langchain4j.

```java
/**
 * Détection format par extension (simple switch, pas SPI).
 * Note: Langchain4j n'a pas de MarkdownDocumentParser - on utilise TextDocumentParser
 * puis AlexandriaMarkdownSplitter pour le chunking markdown-aware.
 *
 * ATTENTION: llms.txt utilise LlmsTxtParser (classe standalone, pas DocumentParser).
 * Traitement séparé dans IngestionService.ingestLlmsTxt().
 */
public DocumentParser getParser(Path file) {
    String name = file.getFileName().toString().toLowerCase();

    if (name.endsWith(".md")) {
        return new TextDocumentParser();  // Parsing brut, chunking via AlexandriaMarkdownSplitter
    } else if (name.endsWith(".html") || name.endsWith(".htm")) {
        return new JsoupDocumentParser(); // Langchain4j natif
    } else if (name.endsWith(".txt") && !name.startsWith("llms")) {
        return new TextDocumentParser();  // Langchain4j natif
    }

    // llms.txt / llms-full.txt → traitement séparé (pas DocumentParser)
    if (name.equals("llms.txt") || name.equals("llms-full.txt")) {
        return null;  // Signal pour IngestionService d'utiliser LlmsTxtParser
    }

    throw new IngestionException("Unsupported format: " + name, null);
}
```

**LlmsTxtParser (from research #14):**

```java
package dev.alexandria.core;

import java.util.regex.*;

/**
 * Parser pour le format llms.txt (spec llmstxt.org).
 * NOTE: Classe standalone, pas un DocumentParser Langchain4j.
 * Retourne LlmsTxtDocument (structure riche) pour traitement custom dans IngestionService.
 */
public class LlmsTxtParser {

    private static final Pattern TITLE_PATTERN =
        Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern BLOCKQUOTE_PATTERN =
        Pattern.compile("^>\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern SECTION_PATTERN =
        Pattern.compile("^##\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern LINK_PATTERN =
        Pattern.compile("^-\\s+\\[([^\\]]+)\\]\\(([^)]+)\\)(?::\\s*(.*))?$", Pattern.MULTILINE);

    public LlmsTxtDocument parse(String content) {
        // Extraire titre (premier H1)
        Matcher titleMatcher = TITLE_PATTERN.matcher(content);
        String title = titleMatcher.find() ? titleMatcher.group(1) : "Untitled";

        // Extraire description (premier blockquote)
        Matcher descMatcher = BLOCKQUOTE_PATTERN.matcher(content);
        String description = descMatcher.find() ? descMatcher.group(1) : "";

        // Parser les sections H2
        List<LlmsTxtSection> sections = new ArrayList<>();
        Matcher sectionMatcher = SECTION_PATTERN.matcher(content);

        while (sectionMatcher.find()) {
            String sectionName = sectionMatcher.group(1);
            boolean isOptional = sectionName.toLowerCase().contains("optional");

            // Trouver les liens dans cette section
            int sectionStart = sectionMatcher.end();
            int sectionEnd = findNextSectionOrEnd(content, sectionStart);
            String sectionContent = content.substring(sectionStart, sectionEnd);

            List<LlmsTxtLink> links = parseLinks(sectionContent);
            sections.add(new LlmsTxtSection(sectionName, isOptional, links));
        }

        return new LlmsTxtDocument(title, description, sections);
    }

    private List<LlmsTxtLink> parseLinks(String content) {
        List<LlmsTxtLink> links = new ArrayList<>();
        Matcher linkMatcher = LINK_PATTERN.matcher(content);

        while (linkMatcher.find()) {
            links.add(new LlmsTxtLink(
                linkMatcher.group(1),  // title
                linkMatcher.group(2),  // url
                linkMatcher.group(3)   // description (nullable)
            ));
        }
        return links;
    }

    public record LlmsTxtDocument(
        String title,
        String description,
        List<LlmsTxtSection> sections
    ) {}

    public record LlmsTxtSection(
        String name,
        boolean optional,
        List<LlmsTxtLink> links
    ) {}

    public record LlmsTxtLink(
        String title,
        String url,
        String description
    ) {}
}
```

**Reporté à v2:**
- Détection doublons par content_hash
- Rate limiter Resilience4j pour API externe
