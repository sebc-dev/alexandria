package dev.alexandria.crawl;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Crawl4AiRequest(
        List<String> urls,
        Map<String, Object> browser_config,
        Map<String, Object> crawler_config
) {}
