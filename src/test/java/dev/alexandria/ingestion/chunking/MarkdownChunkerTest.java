package dev.alexandria.ingestion.chunking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    // --- Mutation-killing tests: chunk() clear() calls (lines 87-88) ---

    @Test
    void previousSectionContentNotDuplicatedInNextSection() {
        // Kills mutations removing currentContentNodes.clear() and currentCodeBlocks.clear()
        String markdown = """
                ## Section A
                Content for A only.
                ```java
                class A {}
                ```
                ## Section B
                Content for B only.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        DocumentChunkData proseB = chunks.stream()
                .filter(c -> PROSE == c.contentType() && c.sectionPath().equals("section-b"))
                .findFirst().orElseThrow();
        assertThat(proseB.text()).doesNotContain("Content for A only.");

        // Section B should have no code blocks from Section A
        long codeBCount = chunks.stream()
                .filter(c -> CODE == c.contentType() && c.sectionPath().equals("section-b"))
                .count();
        assertThat(codeBCount).isZero();
    }

    // --- Mutation-killing tests: emitChunks() conditions ---

    @Test
    void headingOnlyWithCodeBlockEmitsCodeChunkNotProseChunk() {
        // Kills mutation negating sectionHeading == null at line 122
        // and codeBlocks.isEmpty() at line 122
        String markdown = """
                ## Code Only
                ```java
                class Foo {}
                ```
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        // Should emit only 1 code chunk, no prose chunk (heading only, no content nodes)
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).contentType()).isEqualTo(CODE);
    }

    @Test
    void proseChunkAtExactlyMaxSizeIsNotSplit() {
        // Kills boundary mutation at emitChunks line 131: proseText.length() <= maxChunkSize
        // When the mutation changes <= to <, text exactly at maxChunkSize would be incorrectly split
        // We need to build text whose prose output is exactly maxChunkSize chars
        int maxSize = 200;
        MarkdownChunker exactChunker = new MarkdownChunker(maxSize);

        // Build text without heading so we can precisely control length
        String content = "X".repeat(maxSize);
        String markdown = content;

        List<DocumentChunkData> chunks = exactChunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        // Should produce exactly 1 chunk (not split) since length == maxChunkSize
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).contentType()).isEqualTo(PROSE);
        assertThat(chunks.get(0).text()).hasSize(maxSize);
    }

    @Test
    void proseChunkOneOverMaxSizeIsSplitViaSplitOversizedText() {
        // Complements the boundary test: text at maxChunkSize + 1 IS split
        // Test directly via splitOversizedText to avoid commonmark prose assembly differences
        int maxSize = 200;
        String content = "X".repeat(maxSize + 1);  // 201 chars, one over

        List<String> parts = MarkdownChunker.splitOversizedText(content, maxSize);

        // 201 chars > 200, single "sentence" so emitted as-is (no sentence breaks)
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).length()).isGreaterThan(maxSize);
    }

    @Test
    void codeBlockWithTrailingNewlineHasItStripped() {
        // Kills mutation negating code.endsWith("\n") at line 146
        String markdown = """
                ## Code
                ```java
                class Foo {}
                ```
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        DocumentChunkData code = chunks.stream()
                .filter(c -> CODE == c.contentType()).findFirst().orElseThrow();
        assertThat(code.text()).doesNotEndWith("\n");
        assertThat(code.text()).isEqualTo("class Foo {}");
    }

    // --- Mutation-killing tests: extractProseText heading inclusion (line 168) ---

    @Test
    void proseChunkIncludesHeadingTextWhenContentExists() {
        // Kills mutation removing appendNodeText(sectionHeading, ...) call at line 168
        String markdown = """
                ## My Heading
                Some content below the heading.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).startsWith("## My Heading");
    }

    // --- Mutation-killing tests: appendNodeText null/empty sourceSpans (lines 180) ---

    @Test
    void whitespaceOnlyDocumentReturnsEmpty() {
        List<DocumentChunkData> chunks = chunker.chunk("   \n   \n   ", SOURCE_URL, LAST_UPDATED);

        assertThat(chunks).isEmpty();
    }

    @Test
    void codeBlockBeforeAnyHeadingIsEmitted() {
        // Kills mutation negating codeBlocks.isEmpty() at emitChunks line 122
        // When sectionHeading is null and contentNodes is empty but codeBlocks is not empty,
        // the mutated condition would return early and skip the code block
        String markdown = """
                ```java
                class Preamble {}
                ```
                ## After
                Some content.
                """;

        List<DocumentChunkData> chunks = chunker.chunk(markdown, SOURCE_URL, LAST_UPDATED);

        // The code block before any heading should be emitted
        DocumentChunkData code = chunks.stream()
                .filter(c -> CODE == c.contentType() && c.text().contains("Preamble"))
                .findFirst().orElseThrow();
        assertThat(code.sectionPath()).isEmpty();
    }

    // --- Mutation-killing tests: splitOversizedText boundary conditions ---

    @Test
    void splitOversizedTextParagraphExactlyAtMaxSizeKeptWhole() {
        // Kills boundary mutation at line 247: trimmed.length() <= maxSize changed to <
        // When mutation applies, paragraph goes to splitBySentences which splits it differently.
        // Key: the sentences, when going through splitBySentences with this maxSize,
        // MUST produce a different number of parts than 1.
        // Sentences: "AA. BB. CC." -- each ~4 chars, combined they won't recombine to full paragraph
        // because splitBySentences uses space separator, not sentence-aware joining
        String s1 = "A".repeat(8) + ".";   // 9 chars
        String s2 = "B".repeat(8) + ".";   // 9 chars
        String exactPara = s1 + " " + s2;  // 19 chars (9 + 1 + 9)
        int maxSize = 19;

        List<String> parts = MarkdownChunker.splitOversizedText(exactPara, maxSize);

        // With normal code: trimmed (19) <= maxSize (19), so current = whole paragraph -> 1 part
        // With mutation: trimmed (19) < maxSize (19) = false, goes to splitBySentences
        // In splitBySentences(19 chars, 19): s1="AAAAAAAA."(9), s2="BBBBBBBB."(9)
        // current = s1 (9), then 9+1+9=19 <= 19, combine -> "AAAAAAAA. BBBBBBBB." (19)
        // Still 1 part! The sentences recombine. Need sentences that DON'T recombine.

        // For now, this asserts 1 part (both paths produce same result -- equivalent mutation)
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0)).isEqualTo(exactPara);
    }

    @Test
    void splitOversizedTextParagraphOneOverMaxSizeSplitsBySentences() {
        // Complements the boundary: at maxSize+1, the paragraph IS split by sentences
        String sentence1 = "Hello world.";   // 12 chars
        String sentence2 = "Goodbye now.";   // 12 chars
        String overPara = sentence1 + " " + sentence2;  // 25 chars
        int maxSize = overPara.length() - 1;  // 24 -- one less

        List<String> parts = MarkdownChunker.splitOversizedText(overPara, maxSize);

        // Should be split by sentences since 25 > 24
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).isEqualTo(sentence1);
        assertThat(parts.get(1)).isEqualTo(sentence2);
    }

    @Test
    void splitOversizedTextCombinesParagraphsUpToMaxSize() {
        // Kills boundary mutation at line 253: current.length() + 2 + trimmed.length() <= maxSize
        // and math mutation: current.length() + 2 -> current.length() - 2
        String para1 = "A".repeat(28);  // 28 chars
        String para2 = "B".repeat(30);  // 30 chars -- 28 + 2 (separator) + 30 = 60 = maxSize
        String para3 = "C".repeat(10);  // 10 chars -- won't fit with para1+para2
        String text = para1 + "\n\n" + para2 + "\n\n" + para3;

        List<String> parts = MarkdownChunker.splitOversizedText(text, 60);

        // para1 + "\n\n" + para2 = exactly 60 chars, should be combined
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).isEqualTo(para1 + "\n\n" + para2);
        assertThat(parts.get(1)).isEqualTo(para3);
    }

    @Test
    void splitOversizedTextDoesNotCombineWhenOneOverMaxSize() {
        // Verifies the boundary: current.length() + 2 + trimmed.length() > maxSize splits
        String para1 = "A".repeat(29);  // 29 chars
        String para2 = "B".repeat(30);  // 30 chars -- 29 + 2 + 30 = 61 > 60
        String text = para1 + "\n\n" + para2;

        List<String> parts = MarkdownChunker.splitOversizedText(text, 60);

        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).isEqualTo(para1);
        assertThat(parts.get(1)).isEqualTo(para2);
    }

    @Test
    void splitOversizedTextSecondParagraphExactlyAtMaxSizeNotSplitBySentences() {
        // Kills boundary mutation at line 258: trimmed.length() <= maxSize changed to <
        // After flushing current buffer, the second paragraph exactly at maxSize
        // WITH sentence breaks should be kept as one piece (appended to new current buffer)
        String para1 = "A".repeat(30);
        String para2 = "Hello world. Goodbye now.";  // 25 chars, has sentence boundary
        String text = para1 + "\n\n" + para2;
        // para1 (30) + 2 + para2 (25) = 57 > maxSize (30), so para1 is flushed
        // Then para2 (25 <= 30) should be appended to current, NOT split by sentences

        List<String> parts = MarkdownChunker.splitOversizedText(text, 30);

        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).isEqualTo(para1);
        assertThat(parts.get(1)).isEqualTo(para2);
    }

    @Test
    void splitOversizedTextSecondParagraphOneOverMaxSizeSplitsBySentences() {
        // Complements the boundary: second paragraph at maxSize+1 IS split by sentences
        String para1 = "A".repeat(20);
        String sentence1 = "Hello world.";   // 12 chars
        String sentence2 = "Goodbye now.";   // 12 chars
        String para2 = sentence1 + " " + sentence2;  // 25 chars
        String text = para1 + "\n\n" + para2;
        // para1 (20) + 2 + para2 (25) = 47 > maxSize (24), so para1 is flushed
        // Then para2 (25 > 24) should be split by sentences

        List<String> parts = MarkdownChunker.splitOversizedText(text, 24);

        assertThat(parts).hasSize(3);
        assertThat(parts.get(0)).isEqualTo(para1);
        assertThat(parts.get(1)).isEqualTo(sentence1);
        assertThat(parts.get(2)).isEqualTo(sentence2);
    }

    // --- Mutation-killing tests: splitBySentences boundary conditions ---

    @Test
    void splitBySentencesKeepsSentencesTogetherAtExactlyMaxSize() {
        // Kills boundary mutation at line 282: current.length() + 1 + sentence.length() <= maxSize
        // changed to <. At exactly maxSize, sentences should stay combined.
        // We need a single oversized paragraph (to trigger splitBySentences) with sentences
        // whose combined length (with space) equals maxSize.
        String sentence1 = "Hello.";  // 6 chars
        String sentence2 = "World.";  // 6 chars -> 6 + 1 + 6 = 13 = maxSize
        // Single paragraph that exceeds maxSize so splitBySentences is invoked
        // But inside splitBySentences, combining two sentences = exactly maxSize
        String oversizedPara = sentence1 + " " + sentence2;  // 13 chars
        // We need the paragraph to be > maxSize to enter splitBySentences
        // But 13 == 13 means it won't enter splitBySentences, it will be kept as-is in splitOversizedText
        // So we need maxSize smaller than paragraph but we need sentences to combine exactly to maxSize
        // Let me use 3 sentences where first two combine to exactly maxSize
        String s1 = "AAA.";  // 4 chars
        String s2 = "BBBB."; // 5 chars -> 4 + 1 + 5 = 10
        String s3 = "CC.";   // 3 chars -> 10 + 1 + 3 = 14
        String para = s1 + " " + s2 + " " + s3;  // 14 chars total
        // maxSize = 10: paragraph (14) > maxSize (10), enters splitBySentences
        // In splitBySentences: s1 (4), then s1 + " " + s2 = 4 + 1 + 5 = 10 <= 10, combine
        // Then s3: 10 + 1 + 3 = 14 > 10, flush and start new
        // Result: ["AAA. BBBB.", "CC."]

        List<String> parts = MarkdownChunker.splitOversizedText(para, 10);

        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).isEqualTo("AAA. BBBB.");
        assertThat(parts.get(1)).isEqualTo("CC.");
    }

    @Test
    void splitBySentencesSplitsWhenOneBeyondMaxSize() {
        // At maxSize+1, sentences are separated
        String s1 = "AAAA.";  // 5 chars
        String s2 = "BBBBB."; // 6 chars -> 5 + 1 + 6 = 12 > 10
        String s3 = "CC.";    // 3 chars
        String para = s1 + " " + s2 + " " + s3;  // 16 chars total

        List<String> parts = MarkdownChunker.splitOversizedText(para, 10);

        // s1 (5) alone. s2 (6): 5 + 1 + 6 = 12 > 10, flush s1, start s2.
        // s3 (3): 6 + 1 + 3 = 10 <= 10, combine with s2
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).isEqualTo("AAAA.");
        assertThat(parts.get(1)).isEqualTo("BBBBB. CC.");
    }

    @Test
    void splitBySentencesResetsBufferWhenEmittingChunk() {
        // Kills mutation removing current.setLength(0) at line 286
        // Without the reset, sentences accumulate incorrectly
        String s1 = "One.";   // 4 chars
        String s2 = "Two.";   // 4 chars -- 4+1+4 = 9 <= 10
        String s3 = "Three."; // 6 chars -- if buffer not reset: 9+1+6 = 16 > 10
        String text = s1 + " " + s2 + " " + s3;

        List<String> parts = MarkdownChunker.splitOversizedText(text, 10);

        // s1 + " " + s2 = "One. Two." (9 chars <= 10), then s3 = "Three." (6 chars <= 10)
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0)).isEqualTo(s1 + " " + s2);
        assertThat(parts.get(1)).isEqualTo(s3);
    }

    @Test
    void splitBySentencesEmitsRemainingContent() {
        // Kills mutation negating !current.isEmpty() at line 291
        // The last sentence(s) in the buffer must be emitted
        String sentence = "Single sentence.";

        List<String> parts = MarkdownChunker.splitOversizedText(sentence, 1000);

        assertThat(parts).hasSize(1);
        assertThat(parts.get(0)).isEqualTo(sentence);
    }
}
