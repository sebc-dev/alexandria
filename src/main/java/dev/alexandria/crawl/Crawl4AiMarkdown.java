package dev.alexandria.crawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Crawl4AiMarkdown(
        String raw_markdown,
        String fit_markdown,
        String markdown_with_citations,
        String references_markdown
) {}
