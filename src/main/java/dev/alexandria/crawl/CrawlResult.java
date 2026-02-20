package dev.alexandria.crawl;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Result of crawling a single URL: Markdown content, discovered links, and success status. */
public record CrawlResult(
    String url,
    @Nullable String markdown,
    List<String> internalLinks,
    boolean success,
    @Nullable String errorMessage) {
  public CrawlResult {
    internalLinks = internalLinks == null ? List.of() : List.copyOf(internalLinks);
  }
}
