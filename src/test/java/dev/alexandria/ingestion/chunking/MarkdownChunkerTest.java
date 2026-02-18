package dev.alexandria.ingestion.chunking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        assertEquals(3, chunks.size());

        assertEquals("prose", chunks.get(0).contentType());
        assertEquals("introduction", chunks.get(0).sectionPath());
        assertTrue(chunks.get(0).text().contains("Some intro text."));

        assertEquals("prose", chunks.get(1).contentType());
        assertEquals("introduction/getting-started", chunks.get(1).sectionPath());
        assertTrue(chunks.get(1).text().contains("Getting started text."));

        assertEquals("prose", chunks.get(2).contentType());
        assertEquals("introduction/getting-started/configuration", chunks.get(2).sectionPath());
        assertTrue(chunks.get(2).text().contains("Config details here."));
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
        long proseCount = chunks.stream().filter(c -> "prose".equals(c.contentType())).count();
        long codeCount = chunks.stream().filter(c -> "code".equals(c.contentType())).count();
        assertEquals(1, proseCount);
        assertEquals(1, codeCount);

        DocumentChunkData prose = chunks.stream()
                .filter(c -> "prose".equals(c.contentType())).findFirst().orElseThrow();
        assertTrue(prose.text().contains("Install the package."));
        assertTrue(prose.text().contains("Then configure it."));
        assertNull(prose.language());

        DocumentChunkData code = chunks.stream()
                .filter(c -> "code".equals(c.contentType())).findFirst().orElseThrow();
        assertTrue(code.text().contains("import com.example.Foo;"));
        assertEquals("java", code.language());
        assertEquals("setup", code.sectionPath());
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

        assertEquals(1, chunks.size());
        DocumentChunkData chunk = chunks.get(0);
        assertTrue(chunk.text().contains("Main API docs."));
        assertTrue(chunk.text().contains("Method details."));
        assertTrue(chunk.text().contains("Property details."));
        assertEquals("api-reference", chunk.sectionPath());
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

        long proseCount = chunks.stream().filter(c -> "prose".equals(c.contentType())).count();
        long codeCount = chunks.stream().filter(c -> "code".equals(c.contentType())).count();
        assertEquals(1, proseCount);
        assertEquals(1, codeCount);

        // The heading inside the code block must NOT create a separate chunk
        DocumentChunkData code = chunks.stream()
                .filter(c -> "code".equals(c.contentType())).findFirst().orElseThrow();
        assertTrue(code.text().contains("## This Is Not A Real Heading"));
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

        long proseCount = chunks.stream().filter(c -> "prose".equals(c.contentType())).count();
        long codeCount = chunks.stream().filter(c -> "code".equals(c.contentType())).count();
        assertEquals(1, proseCount);
        assertEquals(2, codeCount);

        List<DocumentChunkData> codeChunks = chunks.stream()
                .filter(c -> "code".equals(c.contentType())).toList();
        assertEquals("java", codeChunks.get(0).language());
        assertEquals("python", codeChunks.get(1).language());

        // All share the same section path
        assertTrue(chunks.stream().allMatch(c -> "examples".equals(c.sectionPath())));
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
                .filter(c -> "code".equals(c.contentType())).findFirst().orElseThrow();
        assertEquals("java", code.language());
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

        assertEquals(1, chunks.size());
        assertEquals("code", chunks.get(0).contentType());
        assertEquals("bash", chunks.get(0).language());
    }

    // --- Case 8: Empty document ---

    @Test
    void emptyDocumentReturnsEmptyList() {
        List<DocumentChunkData> chunks = chunker.chunk("", SOURCE_URL, LAST_UPDATED);
        assertTrue(chunks.isEmpty());
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

        assertEquals(2, chunks.size());

        DocumentChunkData preamble = chunks.get(0);
        assertEquals("", preamble.sectionPath());
        assertTrue(preamble.text().contains("This is a preamble before any heading."));

        DocumentChunkData section = chunks.get(1);
        assertEquals("first-section", section.sectionPath());
        assertTrue(section.text().contains("Content here."));
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
            assertEquals(SOURCE_URL, chunk.sourceUrl());
            assertNotNull(chunk.sectionPath());
            assertNotNull(chunk.contentType());
            assertEquals(LAST_UPDATED, chunk.lastUpdated());

            if ("code".equals(chunk.contentType())) {
                assertNotNull(chunk.language());
            } else {
                assertNull(chunk.language());
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

        assertEquals(1, chunks.size());
        assertEquals("prose", chunks.get(0).contentType());
        // Table content preserved (not split by pipe characters)
        assertTrue(chunks.get(0).text().contains("foo"));
        assertTrue(chunks.get(0).text().contains("bar"));
    }

    // --- Additional edge cases ---

    @Test
    void sectionPathSlugifiesSpecialCharacters() {
        String markdown = """
                ## Configuration & Routes
                Some content.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertEquals(1, chunks.size());
        assertEquals("configuration-routes", chunks.get(0).sectionPath());
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

        assertEquals(4, chunks.size());
        assertEquals("top", chunks.get(0).sectionPath());
        assertEquals("top/sub-a", chunks.get(1).sectionPath());
        assertEquals("top/sub-a/detail-a1", chunks.get(2).sectionPath());
        // Sub B should NOT carry Detail A1 -- deeper levels cleared
        assertEquals("top/sub-b", chunks.get(3).sectionPath());
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
                .filter(c -> "code".equals(c.contentType())).findFirst().orElseThrow();
        assertEquals("java", code.language());
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
                .filter(c -> "prose".equals(c.contentType())).findFirst().orElseThrow();
        assertFalse(prose.text().contains("print(\"hello\")"));
        assertTrue(prose.text().contains("Before code."));
        assertTrue(prose.text().contains("After code."));
    }
}
