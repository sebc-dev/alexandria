package dev.alexandria.ingestion.chunking;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AST-based Markdown chunker that splits content at H1/H2/H3 heading boundaries
 * and extracts fenced code blocks as separate chunks.
 *
 * <p>H4+ headings remain inside their parent H3 chunk.
 * Every chunk carries five metadata fields: sourceUrl, sectionPath, contentType,
 * lastUpdated, and language.
 */
@Component
public class MarkdownChunker {

    private final Parser parser;
    private final TextContentRenderer textRenderer;

    public MarkdownChunker() {
        var extensions = List.of(TablesExtension.create());
        this.parser = Parser.builder()
                .extensions(extensions)
                .includeSourceSpans(IncludeSourceSpans.BLOCKS)
                .build();
        this.textRenderer = TextContentRenderer.builder()
                .extensions(extensions)
                .build();
    }

    /**
     * Chunks a Markdown document into prose and code segments.
     *
     * @param markdown    the raw Markdown text
     * @param sourceUrl   the URL of the page this content came from
     * @param lastUpdated ISO-8601 timestamp of the source page
     * @return ordered list of chunks with metadata
     */
    public List<DocumentChunkData> chunk(String markdown, String sourceUrl, String lastUpdated) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }

        Node document = parser.parse(markdown);
        String[] lines = markdown.split("\n", -1);
        List<DocumentChunkData> chunks = new ArrayList<>();

        // Track heading hierarchy: index 0=H1, 1=H2, 2=H3
        String[] headingPath = new String[3];
        Heading currentHeading = null;
        List<Node> currentContentNodes = new ArrayList<>();
        List<FencedCodeBlock> currentCodeBlocks = new ArrayList<>();

        Node child = document.getFirstChild();
        while (child != null) {
            Node next = child.getNext();

            if (child instanceof Heading heading && heading.getLevel() <= 3) {
                // Flush the previous section
                emitChunks(chunks, currentHeading, currentContentNodes, currentCodeBlocks,
                        headingPath, sourceUrl, lastUpdated, lines);
                currentHeading = null;
                currentContentNodes.clear();
                currentCodeBlocks.clear();

                // Update heading hierarchy
                int level = heading.getLevel();
                headingPath[level - 1] = extractHeadingText(heading);
                for (int i = level; i < 3; i++) {
                    headingPath[i] = null;
                }

                currentHeading = heading;
            } else if (child instanceof FencedCodeBlock codeBlock) {
                currentCodeBlocks.add(codeBlock);
            } else {
                currentContentNodes.add(child);
            }

            child = next;
        }

        // Flush the final section
        emitChunks(chunks, currentHeading, currentContentNodes, currentCodeBlocks,
                headingPath, sourceUrl, lastUpdated, lines);

        return chunks;
    }

    private void emitChunks(List<DocumentChunkData> chunks,
                            Heading sectionHeading,
                            List<Node> contentNodes,
                            List<FencedCodeBlock> codeBlocks,
                            String[] headingPath,
                            String sourceUrl,
                            String lastUpdated,
                            String[] lines) {
        if (sectionHeading == null && contentNodes.isEmpty() && codeBlocks.isEmpty()) {
            return;
        }

        String sectionPath = buildSectionPath(headingPath);

        // Build the prose text: heading + content (but only if there is content)
        String proseText = extractProseText(sectionHeading, contentNodes, lines);
        if (!proseText.isBlank()) {
            chunks.add(new DocumentChunkData(
                    proseText, sourceUrl, sectionPath, ContentType.PROSE, lastUpdated, null
            ));
        }

        // Emit code chunks
        for (FencedCodeBlock codeBlock : codeBlocks) {
            String code = codeBlock.getLiteral();
            if (code.endsWith("\n")) {
                code = code.substring(0, code.length() - 1);
            }
            String language = detectLanguage(codeBlock);
            chunks.add(new DocumentChunkData(
                    code, sourceUrl, sectionPath, ContentType.CODE, lastUpdated, language
            ));
        }
    }

    private String extractProseText(Heading sectionHeading, List<Node> contentNodes,
                                    String[] lines) {
        // If there are no content nodes (only code blocks in the section), do not emit
        // the heading as a standalone prose chunk
        if (contentNodes.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // Include the heading itself in the prose output
        if (sectionHeading != null) {
            appendNodeText(sectionHeading, lines, sb);
        }

        for (Node node : contentNodes) {
            appendNodeText(node, lines, sb);
        }

        return sb.toString().trim();
    }

    private void appendNodeText(Node node, String[] lines, StringBuilder sb) {
        var sourceSpans = node.getSourceSpans();
        if (sourceSpans != null && !sourceSpans.isEmpty()) {
            for (SourceSpan span : sourceSpans) {
                if (span == null) {
                    continue;
                }
                int lineIndex = span.getLineIndex();
                if (lineIndex >= 0 && lineIndex < lines.length) {
                    if (!sb.isEmpty()) {
                        sb.append("\n");
                    }
                    sb.append(lines[lineIndex]);
                }
            }
        } else {
            // Fallback to TextContentRenderer for nodes without source spans
            String rendered = textRenderer.render(node).trim();
            if (!rendered.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(rendered);
            }
        }
    }

    private String extractHeadingText(Heading heading) {
        return textRenderer.render(heading).trim();
    }

    private String buildSectionPath(String[] headingPath) {
        return Arrays.stream(headingPath)
                .filter(Objects::nonNull)
                .map(this::slugify)
                .collect(Collectors.joining("/"));
    }

    private String slugify(String text) {
        return text.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private String detectLanguage(FencedCodeBlock codeBlock) {
        String info = codeBlock.getInfo();
        if (info != null && !info.isBlank()) {
            return info.split("\\s+")[0].toLowerCase();
        }
        return LanguageDetector.detect(codeBlock.getLiteral());
    }
}
