package dev.alexandria.crawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Markdown output variants from Crawl4AI: raw, filtered ({@code fit_markdown}), and citation
 * formats.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Crawl4AiMarkdown(
    String raw_markdown,
    String fit_markdown,
    String markdown_with_citations,
    String references_markdown) {}
