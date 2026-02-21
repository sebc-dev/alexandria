package dev.alexandria.crawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Per-page result within a {@link Crawl4AiResponse}, containing Markdown and discovered links. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Crawl4AiPageResult(
    String url,
    boolean success,
    @Nullable String status_code,
    @Nullable Crawl4AiMarkdown markdown,
    @Nullable Map<String, List<Crawl4AiLink>> links,
    @Nullable String error_message) {
  public Crawl4AiPageResult {
    links =
        links == null
            ? Map.of()
            : links.entrySet().stream()
                .collect(
                    Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> List.copyOf(e.getValue())));
  }

  /** Extract hrefs from internal links for URL discovery. */
  public List<String> internalLinkHrefs() {
    if (links == null) {
      return List.of();
    }
    return links.getOrDefault("internal", List.of()).stream().map(Crawl4AiLink::href).toList();
  }
}
