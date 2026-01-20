package fr.kalifazzia.alexandria.core.ingestion;

import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

import fr.kalifazzia.alexandria.core.port.MarkdownParserPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Parses markdown content with YAML frontmatter extraction.
 * Uses CommonMark parser with YAML front matter extension.
 */
@Component
public class MarkdownParser implements MarkdownParserPort {

    private final Parser parser;

    public MarkdownParser() {
        this.parser = Parser.builder()
                .extensions(List.of(YamlFrontMatterExtension.create()))
                .build();
    }

    @Override
    public ParsedDocument parse(String content) {
        if (content == null || content.isBlank()) {
            return ParsedDocument.withoutMetadata("");
        }

        Node document = parser.parse(content);

        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        Map<String, List<String>> frontmatter = visitor.getData();

        DocumentMetadata metadata = extractMetadata(frontmatter);
        String cleanContent = extractContentAfterFrontmatter(content);

        return new ParsedDocument(metadata, cleanContent);
    }

    /**
     * Extracts structured metadata from raw frontmatter map.
     */
    private DocumentMetadata extractMetadata(Map<String, List<String>> frontmatter) {
        if (frontmatter == null || frontmatter.isEmpty()) {
            return DocumentMetadata.empty();
        }

        String title = getFirst(frontmatter, "title");
        String category = getFirst(frontmatter, "category");
        List<String> tags = frontmatter.getOrDefault("tags", List.of());

        return new DocumentMetadata(title, category, tags, frontmatter);
    }

    /**
     * Gets the first value from a frontmatter field, or null if not present.
     */
    private String getFirst(Map<String, List<String>> frontmatter, String key) {
        List<String> values = frontmatter.get(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }

    /**
     * Extracts content after YAML frontmatter block.
     * Frontmatter is delimited by --- at the start and --- after YAML content.
     */
    private String extractContentAfterFrontmatter(String content) {
        if (content == null || !content.startsWith("---")) {
            return content != null ? content.trim() : "";
        }

        // Find the closing --- delimiter
        int endIndex = content.indexOf("---", 3);
        if (endIndex == -1) {
            // Malformed frontmatter - treat as no frontmatter
            return content.trim();
        }

        // Skip past the closing --- and any trailing newlines
        int contentStart = endIndex + 3;
        while (contentStart < content.length() &&
               (content.charAt(contentStart) == '\n' || content.charAt(contentStart) == '\r')) {
            contentStart++;
        }

        return content.substring(contentStart).trim();
    }
}
