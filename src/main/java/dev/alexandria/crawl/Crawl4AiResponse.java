package dev.alexandria.crawl;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Crawl4AiResponse(
        boolean success,
        List<Crawl4AiPageResult> results
) {
    public Crawl4AiResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
