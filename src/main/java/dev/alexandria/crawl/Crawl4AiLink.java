package dev.alexandria.crawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A hyperlink discovered during crawling, as returned by the Crawl4AI sidecar. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Crawl4AiLink(String href, String text, String title) {}
