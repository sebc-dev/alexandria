package dev.alexandria.crawl;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Crawl4AiPageResult(
        String url,
        boolean success,
        String status_code,
        Crawl4AiMarkdown markdown,
        Map<String, List<Crawl4AiLink>> links,
        String error_message
) {

    /**
     * Extract hrefs from internal links for URL discovery.
     */
    public List<String> internalLinkHrefs() {
        if (links == null) {
            return List.of();
        }
        return links.getOrDefault("internal", List.of())
                .stream()
                .map(Crawl4AiLink::href)
                .toList();
    }
}
