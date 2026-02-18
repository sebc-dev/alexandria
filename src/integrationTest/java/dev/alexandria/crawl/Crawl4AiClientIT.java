package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import dev.alexandria.BaseIntegrationTest;

class Crawl4AiClientIT extends BaseIntegrationTest {

    static GenericContainer<?> crawl4ai = new GenericContainer<>(
            DockerImageName.parse("unclecode/crawl4ai:0.8.0"))
            .withExposedPorts(11235)
            .withCreateContainerCmdModifier(cmd ->
                    cmd.getHostConfig().withShmSize(1024L * 1024L * 1024L))
            .waitingFor(Wait.forHttp("/health")
                    .forPort(11235)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofSeconds(120)));

    static {
        crawl4ai.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("alexandria.crawl4ai.base-url", () ->
                "http://" + crawl4ai.getHost() + ":" + crawl4ai.getMappedPort(11235));
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
