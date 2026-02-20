package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.alexandria.BaseIntegrationTest;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class CrawlServiceIT extends BaseIntegrationTest {

  static GenericContainer<?> crawl4ai =
      new GenericContainer<>(DockerImageName.parse("unclecode/crawl4ai:0.8.0"))
          .withExposedPorts(11235)
          .withCreateContainerCmdModifier(
              cmd -> cmd.getHostConfig().withShmSize(1024L * 1024L * 1024L))
          .waitingFor(
              Wait.forHttp("/health")
                  .forPort(11235)
                  .forStatusCode(200)
                  .withStartupTimeout(Duration.ofSeconds(120)));

  static {
    crawl4ai.start();
  }

  @DynamicPropertySource
  static void overrideProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "alexandria.crawl4ai.base-url",
        () -> "http://" + crawl4ai.getHost() + ":" + crawl4ai.getMappedPort(11235));
  }

  @Autowired private CrawlService crawlService;

  @Test
  void crawlSiteCrawlsAtLeastOnePage() {
    List<CrawlResult> results = crawlService.crawlSite("https://example.com", 5);

    assertThat(results).isNotEmpty();
    assertThat(results.getFirst().success()).isTrue();
    assertThat(results.getFirst().markdown()).isNotBlank();
  }

  @Test
  void crawlSiteRespectsMaxPagesLimit() {
    List<CrawlResult> results = crawlService.crawlSite("https://example.com", 1);

    assertThat(results).hasSizeLessThanOrEqualTo(1);
  }

  @Test
  void crawlSiteNormalizesUrlsForDedup() {
    List<CrawlResult> results = crawlService.crawlSite("https://example.com/", 5);

    List<String> urls = results.stream().map(CrawlResult::url).toList();
    assertThat(new HashSet<>(urls)).hasSameSizeAs(urls);
  }
}
