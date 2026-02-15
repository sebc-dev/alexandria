package dev.alexandria.crawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Crawl4AiLink(
        String href,
        String text,
        String title
) {}
