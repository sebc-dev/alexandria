package fr.kalifazzia.alexandria.core.port;

import fr.kalifazzia.alexandria.core.ingestion.ParsedDocument;

/**
 * Port interface for markdown parsing with frontmatter extraction.
 * Follows hexagonal architecture - core defines the contract, infra implements it.
 */
public interface MarkdownParserPort {

    /**
     * Parses markdown content and extracts YAML frontmatter.
     *
     * @param content Raw markdown content (may include frontmatter)
     * @return ParsedDocument with extracted metadata and clean content
     */
    ParsedDocument parse(String content);
}
