package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlNormalizerTest {

    @Test
    void normalize_removes_fragment() {
        String result = UrlNormalizer.normalize("https://docs.example.com/guide#section");
        assertThat(result).isEqualTo("https://docs.example.com/guide");
    }

    @Test
    void normalize_removes_trailing_slash() {
        String result = UrlNormalizer.normalize("https://docs.example.com/guide/");
        assertThat(result).isEqualTo("https://docs.example.com/guide");
    }

    @Test
    void normalize_keeps_root_trailing_slash() {
        String result = UrlNormalizer.normalize("https://docs.example.com/");
        assertThat(result).isEqualTo("https://docs.example.com/");
    }

    @Test
    void normalize_lowercases_host() {
        String result = UrlNormalizer.normalize("https://Docs.Example.COM/Guide");
        assertThat(result).isEqualTo("https://docs.example.com/Guide");
    }

    @Test
    void normalize_removes_tracking_params() {
        String result = UrlNormalizer.normalize("https://docs.example.com/guide?utm_source=x&ref=y");
        assertThat(result).isEqualTo("https://docs.example.com/guide");
    }

    @Test
    void normalize_keeps_meaningful_params() {
        String result = UrlNormalizer.normalize("https://docs.example.com/api?version=3");
        assertThat(result).isEqualTo("https://docs.example.com/api?version=3");
    }

    @Test
    void normalize_handles_malformed_url() {
        String result = UrlNormalizer.normalize("not-a-url");
        assertThat(result).isEqualTo("not-a-url");
    }

    @Test
    void isSameSite_matches_same_host() {
        boolean result = UrlNormalizer.isSameSite(
                "https://docs.spring.io/guide",
                "https://docs.spring.io/other");
        assertThat(result).isTrue();
    }

    @Test
    void isSameSite_rejects_different_host() {
        boolean result = UrlNormalizer.isSameSite(
                "https://docs.spring.io/guide",
                "https://github.com/spring");
        assertThat(result).isFalse();
    }

    @Test
    void isSameSite_rejects_different_scheme() {
        boolean result = UrlNormalizer.isSameSite(
                "https://docs.spring.io",
                "http://docs.spring.io");
        assertThat(result).isFalse();
    }
}
