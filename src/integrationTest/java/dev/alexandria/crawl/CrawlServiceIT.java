package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import dev.alexandria.BaseIntegrationTest;

class CrawlServiceIT extends BaseIntegrationTest {

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("alexandria.crawl4ai.base-url", SharedCrawl4AiContainer::baseUrl);
    }

    @Autowired
    private CrawlService crawlService;

    @Test
    void crawlSite_crawls_at_least_one_page() {
        List<CrawlResult> results = crawlService.crawlSite("https://example.com", 5);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().success()).isTrue();
        assertThat(results.getFirst().markdown()).isNotBlank();
    }

    @Test
    void crawlSite_respects_maxPages_limit() {
        List<CrawlResult> results = crawlService.crawlSite("https://example.com", 1);

        assertThat(results).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    void crawlSite_normalizes_urls_for_dedup() {
        List<CrawlResult> results = crawlService.crawlSite("https://example.com/", 5);

        List<String> urls = results.stream().map(CrawlResult::url).toList();
        assertThat(new HashSet<>(urls)).hasSameSizeAs(urls);
    }
}
