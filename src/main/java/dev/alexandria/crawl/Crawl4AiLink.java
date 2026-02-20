package dev.alexandria.crawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/** A hyperlink discovered during crawling, as returned by the Crawl4AI sidecar. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Crawl4AiLink(String href, @Nullable String text, @Nullable String title) {}
