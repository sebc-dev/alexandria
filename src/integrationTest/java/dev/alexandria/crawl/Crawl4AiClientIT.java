package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import dev.alexandria.BaseIntegrationTest;

class Crawl4AiClientIT extends BaseIntegrationTest {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("alexandria.crawl4ai.base-url", SharedCrawl4AiContainer::baseUrl);
    }

    @Autowired
    private Crawl4AiClient crawl4AiClient;

    @Test
    void crawl_returns_markdown_for_valid_url() {
        CrawlResult result = crawl4AiClient.crawl("https://example.com");

        assertThat(result.success()).isTrue();
        assertThat(result.markdown()).isNotNull().isNotBlank();
        assertThat(result.markdown()).containsIgnoringCase("Example Domain");
    }

    @Test
    void crawl_returns_internal_links_field() {
        CrawlResult result = crawl4AiClient.crawl("https://example.com");

        assertThat(result.internalLinks()).isNotNull();
    }

    @Test
    void crawl_returns_failure_for_unreachable_url() {
        CrawlResult result = crawl4AiClient.crawl("http://localhost:1");

        assertThat(result.success()).isFalse();
    }
}
