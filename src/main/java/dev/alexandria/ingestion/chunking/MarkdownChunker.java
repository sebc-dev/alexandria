package dev.alexandria.ingestion.chunking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * AST-based Markdown chunker that splits content at H1/H2/H3 heading boundaries and produces
 * parent-child chunk hierarchies for small-to-big retrieval.
 *
 * <p>For each section (H2/H3), a <em>parent chunk</em> is emitted containing the full section text
 * (heading + prose + code blocks as raw markdown), followed by <em>child chunks</em> for each
 * individual block (paragraph, code block, table, list). Children carry a {@code parentId} linking
 * them back to their parent.
 *
 * <p>H4+ headings remain inside their parent H3 chunk. Every chunk carries metadata fields:
 * sourceUrl, sectionPath, contentType, lastUpdated, language, chunkType, and parentId.
 */
@Component
public class MarkdownChunker {

  static final int DEFAULT_MAX_CHUNK_SIZE = 2000;

  private final Parser parser;
  private final TextContentRenderer textRenderer;
  private final int maxChunkSize;

  public MarkdownChunker() {
    this(DEFAULT_MAX_CHUNK_SIZE);
  }

  public MarkdownChunker(int maxChunkSize) {
    if (maxChunkSize < 100) {
      throw new IllegalArgumentException("maxChunkSize must be at least 100");
    }
    this.maxChunkSize = maxChunkSize;
    var extensions = List.of(TablesExtension.create());
    this.parser =
        Parser.builder()
            .extensions(extensions)
            .includeSourceSpans(IncludeSourceSpans.BLOCKS)
            .build();
    this.textRenderer = TextContentRenderer.builder().extensions(extensions).build();
  }

  /**
   * Chunks a Markdown document into parent and child prose/code segments.
   *
   * <p>For each section (H2/H3), emits:
   *
   * <ol>
   *   <li>A parent chunk with the full section content (heading + prose + raw code fences)
   *   <li>Child chunks for each individual block (prose paragraphs, code blocks)
   * </ol>
   *
   * @param markdown the raw Markdown text
   * @param sourceUrl the URL of the page this content came from
   * @param lastUpdated ISO-8601 timestamp of the source page
   * @return ordered list of chunks with metadata
   */
  public List<DocumentChunkData> chunk(
      @Nullable String markdown, String sourceUrl, String lastUpdated) {
    if (markdown == null || markdown.isBlank()) {
      return List.of();
    }

    Node document = parser.parse(markdown);
    String[] lines = markdown.split("\n", -1);
    List<DocumentChunkData> chunks = new ArrayList<>();

    // Track heading hierarchy: index 0=H1, 1=H2, 2=H3
    @Nullable String[] headingPath = new @Nullable String[3];
    @Nullable Heading currentHeading = null;
    List<Node> currentContentNodes = new ArrayList<>();
    List<FencedCodeBlock> currentCodeBlocks = new ArrayList<>();

    Node child = document.getFirstChild();
    while (child != null) {
      Node next = child.getNext();

      if (child instanceof Heading heading && heading.getLevel() <= 3) {
        emitSection(
            chunks,
            currentHeading,
            currentContentNodes,
            currentCodeBlocks,
            headingPath,
            sourceUrl,
            lastUpdated,
            lines);
        currentContentNodes.clear();
        currentCodeBlocks.clear();
        updateHeadingPath(headingPath, heading);
        currentHeading = heading;
      } else if (child instanceof FencedCodeBlock codeBlock) {
        currentCodeBlocks.add(codeBlock);
      } else {
        currentContentNodes.add(child);
      }

      child = next;
    }

    emitSection(
        chunks,
        currentHeading,
        currentContentNodes,
        currentCodeBlocks,
        headingPath,
        sourceUrl,
        lastUpdated,
        lines);

    return chunks;
  }

  /**
   * Updates the heading hierarchy when a new H1/H2/H3 heading is encountered. Clears all sub-levels
   * beneath the current heading level.
   */
  private void updateHeadingPath(@Nullable String[] headingPath, Heading heading) {
    int level = heading.getLevel();
    headingPath[level - 1] = extractHeadingText(heading);
    for (int i = level; i < 3; i++) {
      headingPath[i] = null;
    }
  }

  /**
   * Emits parent and child chunks for a completed section. The parent chunk contains the full raw
   * section text (heading + prose + code fences). Child chunks are emitted for each individual
   * block.
   */
  private void emitSection(
      List<DocumentChunkData> chunks,
      @Nullable Heading sectionHeading,
      List<Node> contentNodes,
      List<FencedCodeBlock> codeBlocks,
      @Nullable String[] headingPath,
      String sourceUrl,
      String lastUpdated,
      String[] lines) {
    if (sectionHeading == null && contentNodes.isEmpty() && codeBlocks.isEmpty()) {
      return;
    }

    String sectionPath = buildSectionPath(headingPath);
    String parentId = sourceUrl + "#" + sectionPath;

    // Build parent chunk text: heading + all content + code blocks as raw markdown
    String parentText = buildParentText(sectionHeading, contentNodes, codeBlocks, lines);

    // Emit parent chunk first (if there is content)
    if (!parentText.isBlank()) {
      chunks.add(
          new DocumentChunkData(
              parentText,
              sourceUrl,
              sectionPath,
              ContentType.PROSE,
              lastUpdated,
              null,
              null,
              null,
              "parent",
              null));
    }

    // Emit child chunks
    emitChildProseChunks(
        chunks, sectionHeading, contentNodes, sectionPath, sourceUrl, lastUpdated, parentId, lines);
    emitChildCodeChunks(chunks, codeBlocks, sectionPath, sourceUrl, lastUpdated, parentId);
  }

  /**
   * Builds the full parent chunk text from the section heading, content nodes, and code blocks,
   * preserving the original markdown including code fences.
   */
  private String buildParentText(
      @Nullable Heading sectionHeading,
      List<Node> contentNodes,
      List<FencedCodeBlock> codeBlocks,
      String[] lines) {
    if (contentNodes.isEmpty() && codeBlocks.isEmpty()) {
      return "";
    }

    // Collect all nodes in source-order to reconstruct raw section text
    List<Node> allNodes = new ArrayList<>();
    if (sectionHeading != null) {
      allNodes.add(sectionHeading);
    }

    // Merge content nodes and code blocks by source position
    int contentIdx = 0;
    int codeIdx = 0;
    while (contentIdx < contentNodes.size() || codeIdx < codeBlocks.size()) {
      int contentLine =
          contentIdx < contentNodes.size()
              ? getFirstSourceLine(contentNodes.get(contentIdx))
              : Integer.MAX_VALUE;
      int codeLine =
          codeIdx < codeBlocks.size()
              ? getFirstSourceLine(codeBlocks.get(codeIdx))
              : Integer.MAX_VALUE;
      if (contentLine <= codeLine) {
        allNodes.add(contentNodes.get(contentIdx++));
      } else {
        allNodes.add(codeBlocks.get(codeIdx++));
      }
    }

    StringBuilder sb = new StringBuilder();
    for (Node node : allNodes) {
      appendNodeText(node, lines, sb);
    }
    return sb.toString().trim();
  }

  /** Returns the first source line index for a node, or Integer.MAX_VALUE if unavailable. */
  private int getFirstSourceLine(Node node) {
    var sourceSpans = node.getSourceSpans();
    if (sourceSpans != null && !sourceSpans.isEmpty()) {
      SourceSpan first = sourceSpans.getFirst();
      if (first != null) {
        return first.getLineIndex();
      }
    }
    return Integer.MAX_VALUE;
  }

  /**
   * Builds prose text from the section heading and content nodes, then adds child chunks. Oversized
   * prose is split at paragraph or sentence boundaries.
   */
  private void emitChildProseChunks(
      List<DocumentChunkData> chunks,
      @Nullable Heading sectionHeading,
      List<Node> contentNodes,
      String sectionPath,
      String sourceUrl,
      String lastUpdated,
      String parentId,
      String[] lines) {
    String proseText = extractProseText(sectionHeading, contentNodes, lines);
    if (proseText.isBlank()) {
      return;
    }
    if (proseText.length() <= maxChunkSize) {
      chunks.add(
          new DocumentChunkData(
              proseText,
              sourceUrl,
              sectionPath,
              ContentType.PROSE,
              lastUpdated,
              null,
              null,
              null,
              "child",
              parentId));
    } else {
      splitOversizedText(proseText, maxChunkSize)
          .forEach(
              part ->
                  chunks.add(
                      new DocumentChunkData(
                          part,
                          sourceUrl,
                          sectionPath,
                          ContentType.PROSE,
                          lastUpdated,
                          null,
                          null,
                          null,
                          "child",
                          parentId)));
    }
  }

  /** Emits each fenced code block as a separate child chunk with its detected language. */
  private void emitChildCodeChunks(
      List<DocumentChunkData> chunks,
      List<FencedCodeBlock> codeBlocks,
      String sectionPath,
      String sourceUrl,
      String lastUpdated,
      String parentId) {
    for (FencedCodeBlock codeBlock : codeBlocks) {
      String code = codeBlock.getLiteral();
      if (code.endsWith("\n")) {
        code = code.substring(0, code.length() - 1);
      }
      String language = detectLanguage(codeBlock);
      chunks.add(
          new DocumentChunkData(
              code,
              sourceUrl,
              sectionPath,
              ContentType.CODE,
              lastUpdated,
              language,
              null,
              null,
              "child",
              parentId));
    }
  }

  private String extractProseText(
      @Nullable Heading sectionHeading, List<Node> contentNodes, String[] lines) {
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

  private String buildSectionPath(@Nullable String[] headingPath) {
    return Arrays.stream(headingPath)
        .filter(Objects::nonNull)
        .map(this::slugify)
        .collect(Collectors.joining("/"));
  }

  private String slugify(String text) {
    return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }

  private String detectLanguage(FencedCodeBlock codeBlock) {
    String info = codeBlock.getInfo();
    if (info != null && !info.isBlank()) {
      return info.split("\\s+")[0].toLowerCase();
    }
    return LanguageDetector.detect(codeBlock.getLiteral());
  }

  /**
   * Splits oversized text into parts that each fit within maxSize. Splits at paragraph boundaries
   * (blank lines) first, then at sentence boundaries.
   */
  static List<String> splitOversizedText(String text, int maxSize) {
    // Split at paragraph boundaries (blank lines)
    String[] paragraphs = text.split("\n\n");
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();

    for (String para : paragraphs) {
      String trimmed = para.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      if (current.isEmpty()) {
        if (trimmed.length() <= maxSize) {
          current.append(trimmed);
        } else {
          // Single paragraph too large — split by sentences
          result.addAll(splitBySentences(trimmed, maxSize));
        }
      } else if (current.length() + 2 + trimmed.length() <= maxSize) {
        current.append("\n\n").append(trimmed);
      } else {
        result.add(current.toString());
        current.setLength(0);
        if (trimmed.length() <= maxSize) {
          current.append(trimmed);
        } else {
          result.addAll(splitBySentences(trimmed, maxSize));
        }
      }
    }

    if (!current.isEmpty()) {
      result.add(current.toString());
    }

    return result;
  }

  private static List<String> splitBySentences(String text, int maxSize) {
    // Split on sentence-ending punctuation followed by space
    String[] sentences = text.split("(?<=[.!?])\\s+");
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();

    for (String sentence : sentences) {
      if (current.isEmpty()) {
        current.append(sentence);
      } else if (current.length() + 1 + sentence.length() <= maxSize) {
        current.append(" ").append(sentence);
      } else {
        result.add(current.toString());
        current.setLength(0);
        current.append(sentence);
      }
    }

    if (!current.isEmpty()) {
      // If a single sentence exceeds maxSize, we still emit it as-is
      result.add(current.toString());
    }

    return result;
  }
}
