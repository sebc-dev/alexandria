package dev.alexandria.crawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Crawl4AiMarkdown(
        String rawMarkdown,
        String fitMarkdown,
        String markdownWithCitations,
        String referencesMarkdown
) {}
