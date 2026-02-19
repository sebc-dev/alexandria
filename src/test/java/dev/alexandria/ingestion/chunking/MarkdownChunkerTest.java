package dev.alexandria.ingestion.chunking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static dev.alexandria.ingestion.chunking.ContentType.CODE;
import static dev.alexandria.ingestion.chunking.ContentType.PROSE;

class MarkdownChunkerTest {

    private MarkdownChunker chunker;

    private static final String SOURCE_URL = "https://docs.example.com/guide";
    private static final String LAST_UPDATED = "2026-02-18T10:00:00Z";

    @BeforeEach
    void setUp() {
        chunker = new MarkdownChunker();
    }

    // --- Case 1: Basic heading split ---

    @Test
    void splitsAtH1H2H3Boundaries() {
        String markdown = """
                # Introduction
                Some intro text.
                ## Getting Started
                Getting started text.
                ### Configuration
                Config details here.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertThat(chunks).hasSize(3);

        assertThat(chunks.get(0).contentType()).isEqualTo(PROSE);
        assertThat(chunks.get(0).sectionPath()).isEqualTo("introduction");
        assertThat(chunks.get(0).text()).contains("Some intro text.");

        assertThat(chunks.get(1).contentType()).isEqualTo(PROSE);
        assertThat(chunks.get(1).sectionPath()).isEqualTo("introduction/getting-started");
        assertThat(chunks.get(1).text()).contains("Getting started text.");

        assertThat(chunks.get(2).contentType()).isEqualTo(PROSE);
        assertThat(chunks.get(2).sectionPath()).isEqualTo("introduction/getting-started/configuration");
        assertThat(chunks.get(2).text()).contains("Config details here.");
    }

    // --- Case 2: Code block extraction ---

    @Test
    void extractsCodeBlockAsSeparateChunk() {
        String markdown = """
                ## Setup
                Install the package.
                ```java
                import com.example.Foo;
                ```
                Then configure it.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        // 1 prose + 1 code
        assertThat(chunks.stream().filter(c -> PROSE == c.contentType()).count()).isEqualTo(1);
        assertThat(chunks.stream().filter(c -> CODE == c.contentType()).count()).isEqualTo(1);

        DocumentChunkData prose = chunks.stream()
                .filter(c -> PROSE == c.contentType()).findFirst().orElseThrow();
        assertThat(prose.text()).contains("Install the package.");
        assertThat(prose.text()).contains("Then configure it.");
        assertThat(prose.language()).isNull();

        DocumentChunkData code = chunks.stream()
                .filter(c -> CODE == c.contentType()).findFirst().orElseThrow();
        assertThat(code.text()).contains("import com.example.Foo;");
        assertThat(code.language()).isEqualTo("java");
        assertThat(code.sectionPath()).isEqualTo("setup");
    }

    // --- Case 3: H4+ stays in parent H3 ---

    @Test
    void h4PlusStaysInParentH3Chunk() {
        String markdown = """
                ### API Reference
                Main API docs.
                #### Methods
                Method details.
                #### Properties
                Property details.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertThat(chunks).hasSize(1);
        DocumentChunkData chunk = chunks.get(0);
        assertThat(chunk.text()).contains("Main API docs.");
        assertThat(chunk.text()).contains("Method details.");
        assertThat(chunk.text()).contains("Property details.");
        assertThat(chunk.sectionPath()).isEqualTo("api-reference");
    }

    // --- Case 4: Heading inside code block NOT treated as split ---

    @Test
    void headingInsideCodeBlockDoesNotTriggerSplit() {
        String markdown = """
                ## Example
                Here is a markdown example:
                ```markdown
                ## This Is Not A Real Heading
                Some content.
                ```
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertThat(chunks.stream().filter(c -> PROSE == c.contentType()).count()).isEqualTo(1);
        assertThat(chunks.stream().filter(c -> CODE == c.contentType()).count()).isEqualTo(1);

        // The heading inside the code block must NOT create a separate chunk
        DocumentChunkData code = chunks.stream()
                .filter(c -> CODE == c.contentType()).findFirst().orElseThrow();
        assertThat(code.text()).contains("## This Is Not A Real Heading");
    }

    // --- Case 5: Multiple code blocks in one section ---

    @Test
    void multipleCodeBlocksInOneSectionCreateMultipleCodeChunks() {
        String markdown = """
                ## Examples
                First example:
                ```java
                class Foo {}
                ```
                Second example:
                ```python
                class Foo: pass
                ```
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertThat(chunks.stream().filter(c -> PROSE == c.contentType()).count()).isEqualTo(1);
        assertThat(chunks.stream().filter(c -> CODE == c.contentType()).count()).isEqualTo(2);

        List<DocumentChunkData> codeChunks = chunks.stream()
                .filter(c -> CODE == c.contentType()).toList();
        assertThat(codeChunks.get(0).language()).isEqualTo("java");
        assertThat(codeChunks.get(1).language()).isEqualTo("python");

        // All share the same section path
        assertThat(chunks).allMatch(c -> "examples".equals(c.sectionPath()));
    }

    // --- Case 6: Code block without language tag (auto-detection) ---

    @Test
    void codeBlockWithoutLanguageTagUsesAutoDetection() {
        String markdown = """
                ## Config
                ```
                public class Main { public static void main(String[] args) {} }
                ```
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        DocumentChunkData code = chunks.stream()
                .filter(c -> CODE == c.contentType()).findFirst().orElseThrow();
        assertThat(code.language()).isEqualTo("java");
    }

    // --- Case 7: Section with only code blocks (no prose) ---

    @Test
    void sectionWithOnlyCodeBlocksProducesNoEmptyProseChunk() {
        String markdown = """
                ## Snippet
                ```bash
                echo "hello"
                ```
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).contentType()).isEqualTo(CODE);
        assertThat(chunks.get(0).language()).isEqualTo("bash");
    }

    // --- Case 8: Empty document ---

    @Test
    void emptyDocumentReturnsEmptyList() {
        List<DocumentChunkData> chunks = chunker.chunk("", SOURCE_URL, LAST_UPDATED);
        assertThat(chunks).isEmpty();
    }

    @Test
    void nullDocumentReturnsEmptyList() {
        List<DocumentChunkData> chunks = chunker.chunk(null, SOURCE_URL, LAST_UPDATED);
        assertThat(chunks).isEmpty();
    }

    // --- Case 9: Content before first heading ---

    @Test
    void contentBeforeFirstHeadingBecomesPreambleChunk() {
        String markdown = """
                This is a preamble before any heading.
                ## First Section
                Content here.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertThat(chunks).hasSize(2);

        DocumentChunkData preamble = chunks.get(0);
        assertThat(preamble.sectionPath()).isEmpty();
        assertThat(preamble.text()).contains("This is a preamble before any heading.");

        DocumentChunkData section = chunks.get(1);
        assertThat(section.sectionPath()).isEqualTo("first-section");
        assertThat(section.text()).contains("Content here.");
    }

    // --- Case 10: Metadata completeness ---

    @Test
    void everyChunkHasAllFiveMetadataFields() {
        String markdown = """
                ## Setup
                Some text.
                ```java
                class Foo {}
                ```
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        for (DocumentChunkData chunk : chunks) {
            assertThat(chunk.sourceUrl()).isEqualTo(SOURCE_URL);
            assertThat(chunk.sectionPath()).isNotNull();
            assertThat(chunk.contentType()).isNotNull();
            assertThat(chunk.lastUpdated()).isEqualTo(LAST_UPDATED);

            if (CODE == chunk.contentType()) {
                assertThat(chunk.language()).isNotNull();
            } else {
                assertThat(chunk.language()).isNull();
            }
        }
    }

    // --- Case 11: Table preservation ---

    @Test
    void tableContentPreservedInProseChunk() {
        String markdown = """
                ## Data
                | Name | Value |
                |------|-------|
                | foo  | bar   |
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).contentType()).isEqualTo(PROSE);
        // Table content preserved (not split by pipe characters)
        assertThat(chunks.get(0).text()).contains("foo");
        assertThat(chunks.get(0).text()).contains("bar");
    }

    // --- Additional edge cases ---

    @Test
    void sectionPathSlugifiesSpecialCharacters() {
        String markdown = """
                ## Configuration & Routes
                Some content.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).sectionPath()).isEqualTo("configuration-routes");
    }

    @Test
    void deeperHeadingClearsSublevels() {
        String markdown = """
                # Top
                Top content.
                ## Sub A
                Sub A content.
                ### Detail A1
                Detail content.
                ## Sub B
                Sub B content.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertThat(chunks).hasSize(4);
        assertThat(chunks.get(0).sectionPath()).isEqualTo("top");
        assertThat(chunks.get(1).sectionPath()).isEqualTo("top/sub-a");
        assertThat(chunks.get(2).sectionPath()).isEqualTo("top/sub-a/detail-a1");
        // Sub B should NOT carry Detail A1 -- deeper levels cleared
        assertThat(chunks.get(3).sectionPath()).isEqualTo("top/sub-b");
    }

    @Test
    void codeBlockLanguageInfoStringTrimmed() {
        // Info string may contain extra params after language
        String markdown = """
                ## Example
                ```java title="MyClass"
                class MyClass {}
                ```
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        DocumentChunkData code = chunks.stream()
                .filter(c -> CODE == c.contentType()).findFirst().orElseThrow();
        assertThat(code.language()).isEqualTo("java");
    }

    // --- Case: Oversized chunk splitting ---

    @Test
    void splitsOversizedProseChunkAtParagraphBoundaries() {
        // Use a small maxChunkSize to trigger splitting
        MarkdownChunker smallChunker = new MarkdownChunker(100);
        String markdown = """
                ## Large Section
                First paragraph with enough text to fill some space in the chunk.

                Second paragraph with additional content that pushes over the limit.

                Third paragraph with even more text to verify multi-split works.
                """;

        List<DocumentChunkData> chunks = smallChunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        // Should produce multiple prose chunks, all with same sectionPath
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allMatch(c -> "large-section".equals(c.sectionPath()));
        assertThat(chunks).allMatch(c -> PROSE == c.contentType());
    }

    @Test
    void doesNotSplitChunksUnderMaxSize() {
        String markdown = """
                ## Short
                Brief content.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).contentType()).isEqualTo(PROSE);
    }

    @Test
    void splitOversizedTextPreservesAllContent() {
        String para1 = "A".repeat(50);
        String para2 = "B".repeat(50);
        String para3 = "C".repeat(50);
        String text = para1 + "\n\n" + para2 + "\n\n" + para3;

        List<String> parts = MarkdownChunker.splitOversizedText(text, 60);

        // Each part should be at most 60 chars
        assertThat(parts).allMatch(p -> p.length() <= 60);
        // All original content preserved
        String joined = String.join(" ", parts);
        assertThat(joined).contains(para1);
        assertThat(joined).contains(para2);
        assertThat(joined).contains(para3);
    }

    @Test
    void proseChunkExcludesCodeBlockContent() {
        String markdown = """
                ## Guide
                Before code.
                ```python
                print("hello")
                ```
                After code.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        DocumentChunkData prose = chunks.stream()
                .filter(c -> PROSE == c.contentType()).findFirst().orElseThrow();
        assertThat(prose.text()).doesNotContain("print(\"hello\")");
        assertThat(prose.text()).contains("Before code.");
        assertThat(prose.text()).contains("After code.");
    }
}
