package fr.kalifazzia.alexandria.core.ingestion;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Extracts internal markdown links from markdown content using CommonMark visitor pattern.
 *
 * <p>This extractor detects links to other .md files (cross-references) while
 * ignoring external URLs, anchors, and non-markdown links. It is used during
 * document ingestion to build REFERENCES edges in the document graph.
 *
 * <p>Example links detected:
 * <ul>
 *   <li>[Other doc](other.md) - same directory</li>
 *   <li>[Subdoc](sub/doc.md) - subdirectory</li>
 *   <li>[Parent](../parent.md) - parent directory</li>
 * </ul>
 *
 * <p>Example links ignored:
 * <ul>
 *   <li>[External](https://example.com) - http/https URLs</li>
 *   <li>[Email](mailto:user@example.com) - mailto links</li>
 *   <li>[Section](#section) - anchor links</li>
 *   <li>[File](image.png) - non-.md files</li>
 * </ul>
 */
@Component
public class CrossReferenceExtractor {

    private final Parser parser;

    public CrossReferenceExtractor() {
        this.parser = Parser.builder().build();
    }

    /**
     * Extracts all internal markdown links from content.
     *
     * @param markdownContent The markdown content to parse
     * @return Immutable list of extracted links (relative paths and link text)
     */
    public List<ExtractedLink> extractLinks(String markdownContent) {
        if (markdownContent == null || markdownContent.isBlank()) {
            return List.of();
        }

        Node document = parser.parse(markdownContent);
        LinkVisitor visitor = new LinkVisitor();
        document.accept(visitor);
        return visitor.getLinks();
    }

    /**
     * Resolves a relative link path to an absolute file path.
     *
     * @param sourceFile The path of the file containing the link
     * @param relativePath The relative path from the link destination
     * @return The resolved absolute path, or empty if resolution fails
     */
    public Optional<Path> resolveLink(Path sourceFile, String relativePath) {
        if (sourceFile == null || relativePath == null || relativePath.isBlank()) {
            return Optional.empty();
        }

        try {
            Path sourceDir = sourceFile.getParent();
            if (sourceDir == null) {
                return Optional.empty();
            }

            Path resolved = sourceDir.resolve(relativePath).normalize();
            return Optional.of(resolved.toAbsolutePath());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * An extracted link from markdown content.
     *
     * @param relativePath The relative path to the target file (e.g., "../other.md")
     * @param linkText The display text of the link (may be empty)
     */
    public record ExtractedLink(String relativePath, String linkText) {}

    /**
     * CommonMark visitor that collects internal markdown links.
     */
    private static class LinkVisitor extends AbstractVisitor {

        private final List<ExtractedLink> links = new ArrayList<>();

        @Override
        public void visit(Link link) {
            String destination = link.getDestination();

            if (isInternalMarkdownLink(destination)) {
                String linkText = extractLinkText(link);
                links.add(new ExtractedLink(destination, linkText));
            }

            // Continue visiting children to handle nested links
            visitChildren(link);
        }

        /**
         * Checks if a link destination is an internal markdown link.
         *
         * @param destination The link destination to check
         * @return true if it's an internal .md link, false otherwise
         */
        private boolean isInternalMarkdownLink(String destination) {
            if (destination == null || destination.isEmpty()) {
                return false;
            }
            if (destination.startsWith("http://") || destination.startsWith("https://")) {
                return false;
            }
            if (destination.startsWith("mailto:")) {
                return false;
            }
            if (destination.startsWith("#")) {
                return false;
            }
            return destination.endsWith(".md");
        }

        /**
         * Extracts the display text from a link node.
         * Concatenates all Text children of the link.
         *
         * @param link The link node
         * @return The extracted text, or empty string if no text
         */
        private String extractLinkText(Link link) {
            StringBuilder sb = new StringBuilder();
            Node child = link.getFirstChild();
            while (child != null) {
                if (child instanceof Text text) {
                    sb.append(text.getLiteral());
                }
                child = child.getNext();
            }
            return sb.toString();
        }

        /**
         * Returns an immutable copy of the collected links.
         *
         * @return List of extracted links
         */
        public List<ExtractedLink> getLinks() {
            return List.copyOf(links);
        }
    }
}
