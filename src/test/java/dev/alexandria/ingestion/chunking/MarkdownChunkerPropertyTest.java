package dev.alexandria.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests for {@link MarkdownChunker} structural invariants using jqwik.
 *
 * <p>Rather than testing specific Markdown inputs, these tests verify invariants that MUST hold for
 * ALL valid Markdown inputs: content conservation, size bounds, code block balance, table
 * completeness, parent-child structural integrity, chunk type consistency, and sectionPath
 * slugification.
 */
class MarkdownChunkerPropertyTest {

  private static final String SOURCE_URL = "https://docs.example.com/guide";
  private static final String LAST_UPDATED = "2026-02-18T10:00:00Z";

  private final MarkdownChunker chunker = new MarkdownChunker();
  private final MarkdownChunker smallChunker = new MarkdownChunker(200);

  // =========================================================================
  // Markdown Arbitrary Generator
  // =========================================================================

  @Provide
  Arbitrary<String> markdownDocuments() {
    return Combinators.combine(
            optionalPreamble(),
            Arbitraries.integers().between(0, 5).flatMap(this::generateSections))
        .as((preamble, sections) -> preamble + sections);
  }

  private Arbitrary<String> optionalPreamble() {
    return Arbitraries.oneOf(Arbitraries.just(""), proseParagraphs(1, 2).map(p -> p + "\n\n"));
  }

  private Arbitrary<String> generateSections(int count) {
    if (count == 0) {
      return Arbitraries.just("");
    }
    List<Arbitrary<String>> sections = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      sections.add(generateH2Section());
    }
    return Combinators.combine(sections).as(parts -> String.join("\n", parts));
  }

  private Arbitrary<String> generateH2Section() {
    return Combinators.combine(
            headingText(),
            Arbitraries.integers().between(0, 3).flatMap(this::generateH3Subsections),
            sectionContent())
        .as(
            (heading, subsections, content) ->
                "## " + heading + "\n" + content + "\n" + subsections);
  }

  private Arbitrary<String> generateH3Subsections(int count) {
    if (count == 0) {
      return Arbitraries.just("");
    }
    List<Arbitrary<String>> subs = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      subs.add(generateH3Section());
    }
    return Combinators.combine(subs).as(parts -> String.join("\n", parts));
  }

  private Arbitrary<String> generateH3Section() {
    return Combinators.combine(headingText(), sectionContent(), optionalH4())
        .as((heading, content, h4) -> "### " + heading + "\n" + content + "\n" + h4);
  }

  private Arbitrary<String> optionalH4() {
    return Arbitraries.oneOf(
        Arbitraries.just(""),
        Combinators.combine(headingText(), proseParagraphs(1, 2))
            .as((heading, content) -> "#### " + heading + "\n" + content + "\n"));
  }

  private Arbitrary<String> sectionContent() {
    return Combinators.combine(proseParagraphs(0, 4), codeBlocks(0, 2), optionalTable())
        .as((prose, code, table) -> prose + "\n" + code + "\n" + table);
  }

  private Arbitrary<String> proseParagraphs(int min, int max) {
    return Arbitraries.integers()
        .between(min, max)
        .flatMap(
            count -> {
              if (count == 0) {
                return Arbitraries.just("");
              }
              List<Arbitrary<String>> paragraphs = new ArrayList<>();
              for (int i = 0; i < count; i++) {
                paragraphs.add(proseParagraph());
              }
              return Combinators.combine(paragraphs).as(parts -> String.join("\n\n", parts));
            });
  }

  private Arbitrary<String> proseParagraph() {
    return Arbitraries.integers()
        .between(1, 3)
        .flatMap(
            sentenceCount -> {
              List<Arbitrary<String>> sentences = new ArrayList<>();
              for (int i = 0; i < sentenceCount; i++) {
                sentences.add(sentence());
              }
              return Combinators.combine(sentences).as(parts -> String.join(" ", parts));
            });
  }

  private Arbitrary<String> sentence() {
    return Arbitraries.of(
        "The framework provides robust error handling.",
        "Configuration is managed through YAML files.",
        "Authentication uses JWT tokens by default.",
        "The API supports both REST and GraphQL.",
        "Database migrations run automatically at startup.",
        "Logging is configured via logback-spring.xml.",
        "Rate limiting prevents API abuse.",
        "The service registers with the discovery server.",
        "Tests run in parallel using virtual threads.",
        "Dependency injection follows constructor patterns.",
        "Caching improves response times significantly.",
        "The scheduler runs tasks every five minutes.");
  }

  private Arbitrary<String> codeBlocks(int min, int max) {
    return Arbitraries.integers()
        .between(min, max)
        .flatMap(
            count -> {
              if (count == 0) {
                return Arbitraries.just("");
              }
              List<Arbitrary<String>> blocks = new ArrayList<>();
              for (int i = 0; i < count; i++) {
                blocks.add(codeBlock());
              }
              return Combinators.combine(blocks).as(parts -> String.join("\n", parts));
            });
  }

  private Arbitrary<String> codeBlock() {
    return Combinators.combine(
            Arbitraries.of("java", "python", "javascript", "bash", ""), codeContent())
        .as(
            (lang, code) -> {
              String fence = "```" + lang + "\n";
              return fence + code + "\n```\n";
            });
  }

  private Arbitrary<String> codeContent() {
    return Arbitraries.of(
        "public class Main {\n  public static void main(String[] args) {}\n}",
        "def hello():\n    print(\"hello\")",
        "function greet(name) {\n  return `Hello, ${name}`;\n}",
        "echo \"hello world\"",
        "SELECT * FROM users WHERE active = true;",
        "import java.util.List;\nimport java.util.Map;",
        "const config = {\n  port: 8080,\n  host: 'localhost'\n};",
        "@Component\npublic class MyService {\n  private final Repository repo;\n}");
  }

  private Arbitrary<String> optionalTable() {
    return Arbitraries.oneOf(Arbitraries.just(""), generateTable());
  }

  private Arbitrary<String> generateTable() {
    return Combinators.combine(
            Arbitraries.integers().between(2, 4), Arbitraries.integers().between(1, 3))
        .flatAs(
            (cols, rows) -> {
              List<Arbitrary<String>> headerCells = new ArrayList<>();
              for (int i = 0; i < cols; i++) {
                headerCells.add(Arbitraries.of("Name", "Value", "Type", "Description"));
              }
              return Combinators.combine(headerCells)
                  .as(
                      headers -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append("| ").append(String.join(" | ", headers)).append(" |\n");
                        sb.append("|");
                        for (int i = 0; i < cols; i++) {
                          sb.append("------|");
                        }
                        sb.append("\n");
                        for (int r = 0; r < rows; r++) {
                          sb.append("| ");
                          for (int c = 0; c < cols; c++) {
                            sb.append("cell").append(r).append(c);
                            if (c < cols - 1) sb.append(" | ");
                          }
                          sb.append(" |\n");
                        }
                        return sb.toString();
                      });
            });
  }

  private Arbitrary<String> headingText() {
    return Arbitraries.of(
        "Getting Started",
        "Configuration",
        "API Reference",
        "Installation",
        "Quick Start",
        "Advanced Usage",
        "Troubleshooting",
        "Authentication & Security",
        "Database Setup",
        "Testing Guide",
        "Deployment",
        "Error Handling");
  }

  // =========================================================================
  // Property Tests
  // =========================================================================

  /**
   * Content conservation: every prose paragraph and code block literal from the input must appear
   * in at least one child chunk. No data is lost during chunking.
   */
  @Property(tries = 200)
  void contentConservation(@ForAll("markdownDocuments") String markdown) {
    List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

    if (markdown.isBlank()) {
      assertThat(chunks).isEmpty();
      return;
    }

    String allChildText =
        chunks.stream()
            .filter(c -> "child".equals(c.chunkType()))
            .map(DocumentChunkData::text)
            .reduce("", (a, b) -> a + "\n" + b);

    // Every fenced code block literal must appear in a child chunk
    Pattern codePattern = Pattern.compile("```\\w*\\n(.*?)\\n```", Pattern.DOTALL);
    Matcher codeMatcher = codePattern.matcher(markdown);
    while (codeMatcher.find()) {
      String codeLiteral = codeMatcher.group(1).trim();
      if (!codeLiteral.isEmpty()) {
        assertThat(allChildText)
            .as("Code block content must be in a child chunk: %s", truncate(codeLiteral))
            .contains(codeLiteral);
      }
    }

    // Every non-whitespace prose line (not a heading, not inside code block, not table separator)
    // should appear in some child chunk
    String[] lines = markdown.split("\n");
    boolean inCodeBlock = false;
    for (String line : lines) {
      if (line.trim().startsWith("```")) {
        inCodeBlock = !inCodeBlock;
        continue;
      }
      if (inCodeBlock) continue;
      String trimmed = line.trim();
      if (trimmed.isEmpty()) continue;
      if (trimmed.startsWith("#")) continue; // headings are included in prose children but optional
      if (trimmed.matches("\\|[-|\\s]+\\|")) continue; // table separator
      if (trimmed.startsWith("|")) {
        // Table cell lines -- check that cell values appear somewhere
        String[] cells = trimmed.split("\\|");
        for (String cell : cells) {
          String cellTrimmed = cell.trim();
          if (!cellTrimmed.isEmpty() && !cellTrimmed.matches("[-]+")) {
            assertThat(allChildText)
                .as("Table cell must be preserved: %s", cellTrimmed)
                .contains(cellTrimmed);
          }
        }
        continue;
      }
      assertThat(allChildText)
          .as("Prose line must be in a child chunk: %s", truncate(trimmed))
          .contains(trimmed);
    }
  }

  /**
   * Child chunks of type PROSE respect maxChunkSize. Exception: single sentences that exceed
   * maxChunkSize are emitted as-is. CODE child chunks are exempt.
   */
  @Property(tries = 200)
  void childChunksRespectMaxSize(@ForAll("markdownDocuments") String markdown) {
    int maxSize = 200;
    List<DocumentChunkData> chunks = smallChunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

    List<DocumentChunkData> proseChildren =
        chunks.stream()
            .filter(c -> "child".equals(c.chunkType()))
            .filter(c -> ContentType.PROSE == c.contentType())
            .toList();

    for (DocumentChunkData child : proseChildren) {
      // Either the chunk respects maxSize, or it is a single unsplittable sentence
      if (child.text().length() > maxSize) {
        // Must be a single sentence (no sentence-break pattern)
        boolean hasSentenceBreak = child.text().matches(".*[.!?]\\s+.*");
        assertThat(hasSentenceBreak)
            .as(
                "Oversized prose child (%d chars) must be a single unsplittable sentence",
                child.text().length())
            .isFalse();
      }
    }
  }

  /**
   * The number of fenced code blocks in the input equals the number of CODE-type child chunks in
   * the output. Every code block appears as exactly one child chunk.
   */
  @Property(tries = 200)
  void codeBlockBalance(@ForAll("markdownDocuments") String markdown) {
    List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

    // Count fenced code blocks in the input
    int inputCodeBlocks = countFencedCodeBlocks(markdown);

    // Count CODE-type child chunks in the output
    long outputCodeChildren =
        chunks.stream()
            .filter(c -> "child".equals(c.chunkType()))
            .filter(c -> ContentType.CODE == c.contentType())
            .count();

    assertThat(outputCodeChildren)
        .as("Input code blocks (%d) must equal output CODE children", inputCodeBlocks)
        .isEqualTo(inputCodeBlocks);
  }

  /**
   * If the input contains a GFM table, the table cell values must appear intact in a child chunk.
   * No cell data is lost.
   */
  @Property(tries = 200)
  void tableCompleteness(@ForAll("markdownDocuments") String markdown) {
    List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

    String allChildText =
        chunks.stream()
            .filter(c -> "child".equals(c.chunkType()))
            .map(DocumentChunkData::text)
            .reduce("", (a, b) -> a + "\n" + b);

    // Extract table cell values from input
    String[] lines = markdown.split("\n");
    boolean inCodeBlock = false;
    for (String line : lines) {
      if (line.trim().startsWith("```")) {
        inCodeBlock = !inCodeBlock;
        continue;
      }
      if (inCodeBlock) continue;
      String trimmed = line.trim();
      if (trimmed.startsWith("|") && !trimmed.matches("\\|[-|\\s]+\\|")) {
        // Table data row -- extract cell values
        String[] cells = trimmed.split("\\|");
        for (String cell : cells) {
          String cellTrimmed = cell.trim();
          if (!cellTrimmed.isEmpty()) {
            assertThat(allChildText)
                .as("Table cell value '%s' must be preserved in child chunks", cellTrimmed)
                .contains(cellTrimmed);
          }
        }
      }
    }
  }

  /**
   * Every child chunk has a non-null parentId. Every parentId corresponds to exactly one parent
   * chunk with matching sourceUrl and sectionPath. No orphan children.
   */
  @Property(tries = 200)
  void parentChildStructuralIntegrity(@ForAll("markdownDocuments") String markdown) {
    List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

    List<DocumentChunkData> parents =
        chunks.stream().filter(c -> "parent".equals(c.chunkType())).toList();
    List<DocumentChunkData> children =
        chunks.stream().filter(c -> "child".equals(c.chunkType())).toList();

    // Every child must have a non-null parentId
    for (DocumentChunkData child : children) {
      assertThat(child.parentId())
          .as("Child chunk must have non-null parentId: sectionPath=%s", child.sectionPath())
          .isNotNull();
    }

    // Every parentId must correspond to a parent chunk
    List<String> parentIds =
        parents.stream().map(p -> p.sourceUrl() + "#" + p.sectionPath()).toList();

    for (DocumentChunkData child : children) {
      assertThat(parentIds)
          .as("Child parentId '%s' must match a parent chunk", child.parentId())
          .contains(child.parentId());
    }

    // Every parent that has content should have at least one child
    // (edge case: sections with only a heading and no content produce no chunks at all)
    for (DocumentChunkData parent : parents) {
      String expectedParentId = parent.sourceUrl() + "#" + parent.sectionPath();
      long childCount =
          children.stream().filter(c -> expectedParentId.equals(c.parentId())).count();
      assertThat(childCount)
          .as("Parent '%s' must have at least one child (has %d)", parent.sectionPath(), childCount)
          .isGreaterThanOrEqualTo(1);
    }
  }

  /**
   * Every chunk with chunkType="parent" has null parentId. Every chunk with chunkType="child" has
   * non-null parentId.
   */
  @Property(tries = 200)
  void parentChunkTypeConsistency(@ForAll("markdownDocuments") String markdown) {
    List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

    for (DocumentChunkData chunk : chunks) {
      assertThat(chunk.chunkType()).as("Every chunk must have a chunkType").isIn("parent", "child");

      if ("parent".equals(chunk.chunkType())) {
        assertThat(chunk.parentId())
            .as("Parent chunk must have null parentId (sectionPath=%s)", chunk.sectionPath())
            .isNull();
      }
      if ("child".equals(chunk.chunkType())) {
        assertThat(chunk.parentId())
            .as("Child chunk must have non-null parentId (sectionPath=%s)", chunk.sectionPath())
            .isNotNull();
      }
    }
  }

  /**
   * sectionPath values are always slugified: lowercase, hyphens, no special characters. This
   * verifies the slugification invariant holds for arbitrary heading text.
   */
  @Property(tries = 200)
  void sectionPathNeverContainsRawHeadingText(@ForAll("markdownDocuments") String markdown) {
    List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

    for (DocumentChunkData chunk : chunks) {
      String sectionPath = chunk.sectionPath();
      if (sectionPath.isEmpty()) {
        continue; // Preamble chunks have empty sectionPath, which is valid
      }

      // Each segment of the sectionPath must be properly slugified
      String[] segments = sectionPath.split("/");
      for (String segment : segments) {
        assertThat(segment)
            .as("Section path segment must be slugified: '%s' in '%s'", segment, sectionPath)
            .matches("[a-z0-9]+(-[a-z0-9]+)*");
      }
    }
  }

  // =========================================================================
  // Helpers
  // =========================================================================

  /** Counts fenced code blocks in Markdown (```) pairs. */
  private int countFencedCodeBlocks(String markdown) {
    int count = 0;
    boolean inBlock = false;
    for (String line : markdown.split("\n")) {
      if (line.trim().startsWith("```")) {
        if (inBlock) {
          count++;
        }
        inBlock = !inBlock;
      }
    }
    return count;
  }

  /** Truncates text for assertion messages. */
  private String truncate(String text) {
    return text.length() > 80 ? text.substring(0, 80) + "..." : text;
  }
}
