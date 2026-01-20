package fr.kalifazzia.alexandria.core.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MarkdownParser")
class MarkdownParserTest {

    private MarkdownParser parser;

    @BeforeEach
    void setUp() {
        parser = new MarkdownParser();
    }

    @Nested
    @DisplayName("with full frontmatter")
    class WithFullFrontmatter {

        @Test
        @DisplayName("extracts title, category, and tags")
        void extractsAllMetadata() {
            String markdown = """
                    ---
                    title: Spring Boot Guide
                    category: java
                    tags:
                      - spring
                      - boot
                      - tutorial
                    ---
                    # Introduction

                    This is the content.
                    """;

            ParsedDocument result = parser.parse(markdown);

            assertEquals("Spring Boot Guide", result.metadata().title());
            assertEquals("java", result.metadata().category());
            assertEquals(3, result.metadata().tags().size());
            assertTrue(result.metadata().tags().contains("spring"));
            assertTrue(result.metadata().tags().contains("boot"));
            assertTrue(result.metadata().tags().contains("tutorial"));
        }

        @Test
        @DisplayName("returns content without frontmatter")
        void returnsCleanContent() {
            String markdown = """
                    ---
                    title: Test Document
                    category: testing
                    tags:
                      - test
                    ---
                    # Main Content

                    Some text here.
                    """;

            ParsedDocument result = parser.parse(markdown);

            assertTrue(result.content().startsWith("# Main Content"));
            assertFalse(result.content().contains("---"));
            assertFalse(result.content().contains("title:"));
        }
    }

    @Nested
    @DisplayName("with partial frontmatter")
    class WithPartialFrontmatter {

        @Test
        @DisplayName("handles frontmatter with only title")
        void handlesOnlyTitle() {
            String markdown = """
                    ---
                    title: Just a Title
                    ---
                    Content goes here.
                    """;

            ParsedDocument result = parser.parse(markdown);

            assertEquals("Just a Title", result.metadata().title());
            assertNull(result.metadata().category());
            assertTrue(result.metadata().tags().isEmpty());
        }

        @Test
        @DisplayName("handles frontmatter with only category")
        void handlesOnlyCategory() {
            String markdown = """
                    ---
                    category: documentation
                    ---
                    Content here.
                    """;

            ParsedDocument result = parser.parse(markdown);

            assertNull(result.metadata().title());
            assertEquals("documentation", result.metadata().category());
            assertTrue(result.metadata().tags().isEmpty());
        }

        @Test
        @DisplayName("handles frontmatter with only tags")
        void handlesOnlyTags() {
            String markdown = """
                    ---
                    tags:
                      - one
                      - two
                    ---
                    Tagged content.
                    """;

            ParsedDocument result = parser.parse(markdown);

            assertNull(result.metadata().title());
            assertNull(result.metadata().category());
            assertEquals(2, result.metadata().tags().size());
        }
    }

    @Nested
    @DisplayName("without frontmatter")
    class WithoutFrontmatter {

        @Test
        @DisplayName("parses plain markdown")
        void parsesPlainMarkdown() {
            String markdown = """
                    # Hello World

                    This is plain markdown without frontmatter.
                    """;

            ParsedDocument result = parser.parse(markdown);

            assertNull(result.metadata().title());
            assertNull(result.metadata().category());
            assertTrue(result.metadata().tags().isEmpty());
            assertTrue(result.content().contains("# Hello World"));
        }

        @Test
        @DisplayName("returns original content as-is")
        void returnsOriginalContent() {
            String markdown = "Simple single line";

            ParsedDocument result = parser.parse(markdown);

            assertEquals("Simple single line", result.content());
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("handles empty content")
        void handlesEmptyContent() {
            ParsedDocument result = parser.parse("");

            assertNotNull(result);
            assertNotNull(result.metadata());
            assertEquals("", result.content());
        }

        @Test
        @DisplayName("handles null content")
        void handlesNullContent() {
            ParsedDocument result = parser.parse(null);

            assertNotNull(result);
            assertNotNull(result.metadata());
            assertEquals("", result.content());
        }

        @Test
        @DisplayName("handles blank content")
        void handlesBlankContent() {
            ParsedDocument result = parser.parse("   \n\t  ");

            assertNotNull(result);
            assertEquals("", result.content());
        }

        @Test
        @DisplayName("handles malformed frontmatter (missing closing delimiter)")
        void handlesMalformedFrontmatter() {
            String markdown = """
                    ---
                    title: Unclosed
                    Some content without closing delimiter.
                    """;

            ParsedDocument result = parser.parse(markdown);

            // Should treat as no frontmatter due to missing closing ---
            assertNull(result.metadata().title());
            assertTrue(result.content().contains("---"));
        }

        @Test
        @DisplayName("handles content starting with dash but not frontmatter")
        void handlesContentWithDashes() {
            String markdown = "- First item\n- Second item";

            ParsedDocument result = parser.parse(markdown);

            assertTrue(result.content().contains("- First item"));
            assertNull(result.metadata().title());
        }
    }

    @Nested
    @DisplayName("preserves raw frontmatter")
    class RawFrontmatter {

        @Test
        @DisplayName("includes custom fields in raw frontmatter")
        void includesCustomFields() {
            String markdown = """
                    ---
                    title: Custom Fields
                    author: John Doe
                    version: 1.0.0
                    ---
                    Content.
                    """;

            ParsedDocument result = parser.parse(markdown);

            assertTrue(result.metadata().rawFrontmatter().containsKey("author"));
            assertTrue(result.metadata().rawFrontmatter().containsKey("version"));
            assertEquals("John Doe", result.metadata().rawFrontmatter().get("author").getFirst());
        }
    }
}
