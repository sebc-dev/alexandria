package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlNormalizerTest {

    @Test
    void normalizeRemovesFragment() {
        String result = UrlNormalizer.normalize("https://docs.example.com/guide#section");
        assertThat(result).isEqualTo("https://docs.example.com/guide");
    }

    @Test
    void normalizeRemovesTrailingSlash() {
        String result = UrlNormalizer.normalize("https://docs.example.com/guide/");
        assertThat(result).isEqualTo("https://docs.example.com/guide");
    }

    @Test
    void normalizeKeepsRootTrailingSlash() {
        String result = UrlNormalizer.normalize("https://docs.example.com/");
        assertThat(result).isEqualTo("https://docs.example.com/");
    }

    @Test
    void normalizeLowercasesHost() {
        String result = UrlNormalizer.normalize("https://Docs.Example.COM/Guide");
        assertThat(result).isEqualTo("https://docs.example.com/Guide");
    }

    @Test
    void normalizeRemovesTrackingParams() {
        String result = UrlNormalizer.normalize("https://docs.example.com/guide?utm_source=x&ref=y");
        assertThat(result).isEqualTo("https://docs.example.com/guide");
    }

    @Test
    void normalizeKeepsMeaningfulParams() {
        String result = UrlNormalizer.normalize("https://docs.example.com/api?version=3");
        assertThat(result).isEqualTo("https://docs.example.com/api?version=3");
    }

    @Test
    void normalizeHandlesMalformedUrl() {
        String result = UrlNormalizer.normalize("not-a-url");
        assertThat(result).isEqualTo("not-a-url");
    }

    @Test
    void isSameSiteMatchesSameHost() {
        boolean result = UrlNormalizer.isSameSite(
                "https://docs.spring.io/guide",
                "https://docs.spring.io/other");
        assertThat(result).isTrue();
    }

    @Test
    void isSameSiteRejectsDifferentHost() {
        boolean result = UrlNormalizer.isSameSite(
                "https://docs.spring.io/guide",
                "https://github.com/spring");
        assertThat(result).isFalse();
    }

    @Test
    void isSameSiteRejectsDifferentScheme() {
        boolean result = UrlNormalizer.isSameSite(
                "https://docs.spring.io",
                "http://docs.spring.io");
        assertThat(result).isFalse();
    }
}
