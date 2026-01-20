package fr.kalifazzia.alexandria.core.port;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Port interface for cross-reference extraction from markdown content.
 * Follows hexagonal architecture - core defines the contract, infra implements it.
 *
 * <p>Extracts internal markdown links (references to other .md files) from
 * markdown content. Used during document ingestion to build REFERENCES edges
 * in the document graph.
 */
public interface CrossReferenceExtractorPort {

    /**
     * Extracts all internal markdown links from content.
     *
     * <p>Links are considered internal if they:
     * <ul>
     *   <li>Do not start with http:// or https:// (external URLs)</li>
     *   <li>Do not start with mailto: (email links)</li>
     *   <li>Do not start with # (anchor links)</li>
     *   <li>End with .md (markdown files)</li>
     * </ul>
     *
     * @param markdownContent The markdown content to parse
     * @return Immutable list of extracted links (relative paths and link text)
     */
    List<ExtractedLink> extractLinks(String markdownContent);

    /**
     * Resolves a relative link path to an absolute file path.
     *
     * @param sourceFile The path of the file containing the link
     * @param relativePath The relative path from the link destination
     * @return The resolved absolute path, or empty if resolution fails
     */
    Optional<Path> resolveLink(Path sourceFile, String relativePath);

    /**
     * An extracted link from markdown content.
     *
     * @param relativePath The relative path to the target file (e.g., "../other.md")
     * @param linkText The display text of the link (may be empty)
     */
    record ExtractedLink(String relativePath, String linkText) {}
}
