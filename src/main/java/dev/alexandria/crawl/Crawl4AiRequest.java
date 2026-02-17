package dev.alexandria.crawl;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Crawl4AiRequest(
        List<String> urls,
        Map<String, Object> browserConfig,
        Map<String, Object> crawlerConfig
) {}
