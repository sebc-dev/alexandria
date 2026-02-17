package dev.alexandria.crawl;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alexandria.crawl4ai")
public record Crawl4AiProperties(
        String baseUrl,
        int connectTimeoutMs,
        int readTimeoutMs,
        long maxSitemapSizeBytes,
        Retry retry
) {
    public record Retry(int maxAttempts, long delayMs, double multiplier) {}
}
