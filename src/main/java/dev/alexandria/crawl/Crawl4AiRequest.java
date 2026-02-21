package dev.alexandria.crawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

/**
 * JSON request body for the Crawl4AI {@code /crawl} endpoint, including browser and crawler
 * configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Crawl4AiRequest(
    List<String> urls, Map<String, Object> browser_config, Map<String, Object> crawler_config) {
  public Crawl4AiRequest {
    urls = urls == null ? List.of() : List.copyOf(urls);
    browser_config = browser_config == null ? Map.of() : Map.copyOf(browser_config);
    crawler_config = crawler_config == null ? Map.of() : Map.copyOf(crawler_config);
  }
}
