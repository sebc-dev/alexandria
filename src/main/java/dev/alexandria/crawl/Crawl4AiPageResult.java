package dev.alexandria.crawl;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Crawl4AiPageResult(
        String url,
        boolean success,
        String statusCode,
        Crawl4AiMarkdown markdown,
        Map<String, List<Crawl4AiLink>> links,
        String errorMessage
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
