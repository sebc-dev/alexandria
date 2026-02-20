package dev.alexandria.crawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Markdown output variants from Crawl4AI: raw, filtered ({@code fit_markdown}), and citation
 * formats.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Crawl4AiMarkdown(
    @Nullable String raw_markdown,
    @Nullable String fit_markdown,
    @Nullable String markdown_with_citations,
    @Nullable String references_markdown) {}
