package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UrlScopeFilterTest {

  private static final String ROOT = "https://docs.example.com/docs";

  @Test
  void externalUrlIsRejected() {
    var scope = CrawlScope.withDefaults(100);

    boolean result = UrlScopeFilter.isAllowed("https://other-site.com/page", ROOT, scope);

    assertThat(result).isFalse();
  }

  @Test
  void sameSiteUrlWithNoScopeIsAllowed() {
    var scope = CrawlScope.withDefaults(100);

    boolean result = UrlScopeFilter.isAllowed("https://docs.example.com/anything", ROOT, scope);

    assertThat(result).isTrue();
  }

  @Test
  void urlMatchingAllowPatternIsAllowed() {
    var scope = new CrawlScope(List.of("/docs/**"), List.of(), null, 100);

    boolean result = UrlScopeFilter.isAllowed("https://docs.example.com/docs/api/v2", ROOT, scope);

    assertThat(result).isTrue();
  }

  @Test
  void urlNotMatchingAllowPatternIsRejected() {
    var scope = new CrawlScope(List.of("/docs/**"), List.of(), null, 100);

    boolean result = UrlScopeFilter.isAllowed("https://docs.example.com/blog/post", ROOT, scope);

    assertThat(result).isFalse();
  }

  @Test
  void blockPatternTakesPriorityOverAllow() {
    var scope = new CrawlScope(List.of("/docs/**"), List.of("/docs/archive/**"), null, 100);

    boolean result =
        UrlScopeFilter.isAllowed("https://docs.example.com/docs/archive/old", ROOT, scope);

    assertThat(result).isFalse();
  }

  @Test
  void rootPathSlashIsAllowed() {
    var scope = CrawlScope.withDefaults(100);

    boolean result = UrlScopeFilter.isAllowed("https://docs.example.com/", ROOT, scope);

    assertThat(result).isTrue();
  }

  @Test
  void multipleAllowPatternsAnyMatch() {
    var scope = new CrawlScope(List.of("/docs/**", "/api/**"), List.of(), null, 100);

    boolean result =
        UrlScopeFilter.isAllowed("https://docs.example.com/api/reference", ROOT, scope);

    assertThat(result).isTrue();
  }

  @Test
  void malformedUrlReturnsFalse() {
    var scope = CrawlScope.withDefaults(100);

    boolean result = UrlScopeFilter.isAllowed("not a valid url ://broken", ROOT, scope);

    assertThat(result).isFalse();
  }
}
