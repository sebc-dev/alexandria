package fr.kalifazzia.alexandria.core.ingestion;

import fr.kalifazzia.alexandria.core.port.CrossReferenceExtractorPort.ExtractedLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CrossReferenceExtractor")
class CrossReferenceExtractorTest {

    private CrossReferenceExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new CrossReferenceExtractor();
    }

    @Nested
    @DisplayName("extractLinks")
    class ExtractLinks {

        @Test
        @DisplayName("should extract single markdown link")
        void extractsSingleLink() {
            String content = "Check out [other doc](other.md) for more info.";

            List<ExtractedLink> links = extractor.extractLinks(content);

            assertThat(links).hasSize(1);
            assertThat(links.getFirst().relativePath()).isEqualTo("other.md");
            assertThat(links.getFirst().linkText()).isEqualTo("other doc");
        }

        @Test
        @DisplayName("should extract multiple links from same content")
        void extractsMultipleLinks() {
            String content = """
                See [doc one](one.md) and [doc two](sub/two.md) for details.
                Also check [parent](../parent.md).
                """;

            List<ExtractedLink> links = extractor.extractLinks(content);

            assertThat(links).hasSize(3);
            assertThat(links).extracting(ExtractedLink::relativePath)
                    .containsExactly("one.md", "sub/two.md", "../parent.md");
            assertThat(links).extracting(ExtractedLink::linkText)
                    .containsExactly("doc one", "doc two", "parent");
        }

        @Test
        @DisplayName("should ignore external http links")
        void ignoresHttpLinks() {
            String content = """
                See [Google](https://google.com) and [local](local.md).
                Also [HTTP](http://example.com).
                """;

            List<ExtractedLink> links = extractor.extractLinks(content);

            assertThat(links).hasSize(1);
            assertThat(links.getFirst().relativePath()).isEqualTo("local.md");
        }

        @Test
        @DisplayName("should ignore mailto links")
        void ignoresMailtoLinks() {
            String content = """
                Contact [admin](mailto:admin@example.com) or see [docs](docs.md).
                """;

            List<ExtractedLink> links = extractor.extractLinks(content);

            assertThat(links).hasSize(1);
            assertThat(links.getFirst().relativePath()).isEqualTo("docs.md");
        }

        @Test
        @DisplayName("should ignore anchor links")
        void ignoresAnchorLinks() {
            String content = """
                Jump to [section](#section) or see [other](other.md).
                """;

            List<ExtractedLink> links = extractor.extractLinks(content);

            assertThat(links).hasSize(1);
            assertThat(links.getFirst().relativePath()).isEqualTo("other.md");
        }

        @Test
        @DisplayName("should ignore links without .md extension")
        void ignoresNonMarkdownLinks() {
            String content = """
                See [image](image.png) and [pdf](doc.pdf) and [markdown](doc.md).
                """;

            List<ExtractedLink> links = extractor.extractLinks(content);

            assertThat(links).hasSize(1);
            assertThat(links.getFirst().relativePath()).isEqualTo("doc.md");
        }

        @Test
        @DisplayName("should handle link with empty text")
        void handlesEmptyLinkText() {
            String content = "See [](other.md) for more.";

            List<ExtractedLink> links = extractor.extractLinks(content);

            assertThat(links).hasSize(1);
            assertThat(links.getFirst().relativePath()).isEqualTo("other.md");
            assertThat(links.getFirst().linkText()).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for null content")
        void returnsEmptyForNull() {
            List<ExtractedLink> links = extractor.extractLinks(null);

            assertThat(links).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for blank content")
        void returnsEmptyForBlank() {
            List<ExtractedLink> links = extractor.extractLinks("   ");

            assertThat(links).isEmpty();
        }

        @Test
        @DisplayName("should return empty list for content without links")
        void returnsEmptyForNoLinks() {
            String content = "This is plain text without any links.";

            List<ExtractedLink> links = extractor.extractLinks(content);

            assertThat(links).isEmpty();
        }

        @Test
        @DisplayName("should extract link with complex relative path")
        void extractsComplexPath() {
            String content = "See [sibling](../sibling/nested/doc.md) for info.";

            List<ExtractedLink> links = extractor.extractLinks(content);

            assertThat(links).hasSize(1);
            assertThat(links.getFirst().relativePath()).isEqualTo("../sibling/nested/doc.md");
        }

        @Test
        @DisplayName("should extract links from code blocks (CommonMark behavior)")
        void extractsLinksInCodeBlocks() {
            // Note: CommonMark parses inline code, but not fenced code blocks
            // Links in fenced code blocks are treated as plain text
            String content = """
                ```
                [code link](code.md)
                ```

                [real link](real.md)
                """;

            List<ExtractedLink> links = extractor.extractLinks(content);

            // Only the real link outside code block is extracted
            assertThat(links).hasSize(1);
            assertThat(links.getFirst().relativePath()).isEqualTo("real.md");
        }

        @Test
        @DisplayName("should handle mixed valid and invalid links")
        void handlesMixedLinks() {
            String content = """
                # Documentation

                See [internal](internal.md) for details.
                External resource: [Google](https://google.com)
                Jump to [section](#installation)
                Download [file](document.pdf)
                Contact [support](mailto:support@example.com)
                Related: [related doc](../related/doc.md)
                """;

            List<ExtractedLink> links = extractor.extractLinks(content);

            assertThat(links).hasSize(2);
            assertThat(links).extracting(ExtractedLink::relativePath)
                    .containsExactly("internal.md", "../related/doc.md");
        }

        @Test
        @DisplayName("should extract links from nested structures like lists and blockquotes")
        void extractsLinksFromNestedStructures() {
            String content = """
                # Nested Links Test

                Regular paragraph with [link1](link1.md).

                - List item with [link2](link2.md)
                - Another item
                  - Nested list with [link3](link3.md)

                > Blockquote with [link4](link4.md)
                >
                > > Nested blockquote with [link5](link5.md)

                1. Ordered list with [link6](link6.md)
                """;

            List<ExtractedLink> links = extractor.extractLinks(content);

            assertThat(links).hasSize(6);
            assertThat(links).extracting(ExtractedLink::relativePath)
                    .containsExactly("link1.md", "link2.md", "link3.md", "link4.md", "link5.md", "link6.md");
        }

    }

    @Nested
    @DisplayName("resolveLink")
    class ResolveLink {

        @TempDir
        Path tempDir;

        @Test
        @DisplayName("should resolve same directory link")
        void resolvesSameDirectory() throws IOException {
            Path sourceFile = tempDir.resolve("source.md");
            Files.createFile(sourceFile);

            Optional<Path> resolved = extractor.resolveLink(sourceFile, "other.md");

            assertThat(resolved).isPresent();
            assertThat(resolved.get()).isEqualTo(tempDir.resolve("other.md").toAbsolutePath());
        }

        @Test
        @DisplayName("should resolve subdirectory link")
        void resolvesSubdirectory() throws IOException {
            Path sourceFile = tempDir.resolve("source.md");
            Files.createFile(sourceFile);

            Optional<Path> resolved = extractor.resolveLink(sourceFile, "sub/doc.md");

            assertThat(resolved).isPresent();
            assertThat(resolved.get()).isEqualTo(tempDir.resolve("sub/doc.md").toAbsolutePath());
        }

        @Test
        @DisplayName("should resolve parent directory link")
        void resolvesParentDirectory() throws IOException {
            Path subDir = tempDir.resolve("subdir");
            Files.createDirectories(subDir);
            Path sourceFile = subDir.resolve("source.md");
            Files.createFile(sourceFile);

            Optional<Path> resolved = extractor.resolveLink(sourceFile, "../other.md");

            assertThat(resolved).isPresent();
            assertThat(resolved.get()).isEqualTo(tempDir.resolve("other.md").toAbsolutePath());
        }

        @Test
        @DisplayName("should resolve complex relative path")
        void resolvesComplexPath() throws IOException {
            Path subDir = tempDir.resolve("docs/api");
            Files.createDirectories(subDir);
            Path sourceFile = subDir.resolve("source.md");
            Files.createFile(sourceFile);

            Optional<Path> resolved = extractor.resolveLink(sourceFile, "../../guides/setup.md");

            assertThat(resolved).isPresent();
            assertThat(resolved.get()).isEqualTo(tempDir.resolve("guides/setup.md").toAbsolutePath());
        }

        @Test
        @DisplayName("should normalize path with redundant segments")
        void normalizesPath() throws IOException {
            Path sourceFile = tempDir.resolve("source.md");
            Files.createFile(sourceFile);

            Optional<Path> resolved = extractor.resolveLink(sourceFile, "./sub/../other.md");

            assertThat(resolved).isPresent();
            assertThat(resolved.get()).isEqualTo(tempDir.resolve("other.md").toAbsolutePath());
        }

        @Test
        @DisplayName("should return empty for null source file")
        void returnsEmptyForNullSource() {
            Optional<Path> resolved = extractor.resolveLink(null, "other.md");

            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("should return empty for null relative path")
        void returnsEmptyForNullPath() throws IOException {
            Path sourceFile = tempDir.resolve("source.md");
            Files.createFile(sourceFile);

            Optional<Path> resolved = extractor.resolveLink(sourceFile, null);

            assertThat(resolved).isEmpty();
        }

        @Test
        @DisplayName("should return empty for blank relative path")
        void returnsEmptyForBlankPath() throws IOException {
            Path sourceFile = tempDir.resolve("source.md");
            Files.createFile(sourceFile);

            Optional<Path> resolved = extractor.resolveLink(sourceFile, "   ");

            assertThat(resolved).isEmpty();
        }
    }
}
