package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UrlNormalizerTest {

    @Nested
    class Normalize {

        @Test
        void removes_fragment() {
            String result = UrlNormalizer.normalize("https://docs.example.com/guide#section");
            assertThat(result).isEqualTo("https://docs.example.com/guide");
        }

        @Test
        void removes_trailing_slash() {
            String result = UrlNormalizer.normalize("https://docs.example.com/guide/");
            assertThat(result).isEqualTo("https://docs.example.com/guide");
        }

        @Test
        void keeps_root_trailing_slash() {
            String result = UrlNormalizer.normalize("https://docs.example.com/");
            assertThat(result).isEqualTo("https://docs.example.com/");
        }

        @Test
        void lowercases_host() {
            String result = UrlNormalizer.normalize("https://Docs.Example.COM/Guide");
            assertThat(result).isEqualTo("https://docs.example.com/Guide");
        }

        @Test
        void removes_tracking_params() {
            String result = UrlNormalizer.normalize("https://docs.example.com/guide?utm_source=x&ref=y");
            assertThat(result).isEqualTo("https://docs.example.com/guide");
        }

        @Test
        void keeps_meaningful_params() {
            String result = UrlNormalizer.normalize("https://docs.example.com/api?version=3");
            assertThat(result).isEqualTo("https://docs.example.com/api?version=3");
        }

        @Test
        void keeps_meaningful_and_removes_tracking_params() {
            String result = UrlNormalizer.normalize(
                    "https://docs.example.com/api?utm_source=twitter&version=3&ref=home");
            assertThat(result).isEqualTo("https://docs.example.com/api?version=3");
        }

        @Test
        void sorts_query_params_alphabetically() {
            String result = UrlNormalizer.normalize("https://example.com/search?z=1&a=2&m=3");
            assertThat(result).isEqualTo("https://example.com/search?a=2&m=3&z=1");
        }

        @Test
        void handles_malformed_url() {
            String result = UrlNormalizer.normalize("not-a-url");
            assertThat(result).isEqualTo("not-a-url");
        }

        @Test
        void returns_null_for_null() {
            assertThat(UrlNormalizer.normalize(null)).isNull();
        }

        @Test
        void returns_blank_for_blank() {
            assertThat(UrlNormalizer.normalize("  ")).isEqualTo("  ");
        }

        @Test
        void omits_default_port_443_for_https() {
            String result = UrlNormalizer.normalize("https://example.com:443/path");
            assertThat(result).isEqualTo("https://example.com/path");
        }

        @Test
        void omits_default_port_80_for_http() {
            String result = UrlNormalizer.normalize("http://example.com:80/path");
            assertThat(result).isEqualTo("http://example.com/path");
        }

        @Test
        void keeps_non_default_port() {
            String result = UrlNormalizer.normalize("https://example.com:8443/path");
            assertThat(result).isEqualTo("https://example.com:8443/path");
        }

        @Test
        void returns_url_without_scheme_unchanged() {
            String result = UrlNormalizer.normalize("//example.com/path");
            assertThat(result).isEqualTo("//example.com/path");
        }
    }

    @Nested
    class NormalizeToBase {

        @Test
        void extracts_scheme_and_host() {
            assertThat(UrlNormalizer.normalizeToBase("https://docs.spring.io/guide"))
                    .isEqualTo("https://docs.spring.io");
        }

        @Test
        void omits_default_https_port() {
            assertThat(UrlNormalizer.normalizeToBase("https://example.com:443/path"))
                    .isEqualTo("https://example.com");
        }

        @Test
        void omits_default_http_port() {
            assertThat(UrlNormalizer.normalizeToBase("http://example.com:80/path"))
                    .isEqualTo("http://example.com");
        }

        @Test
        void keeps_non_default_port() {
            assertThat(UrlNormalizer.normalizeToBase("https://example.com:9090/path"))
                    .isEqualTo("https://example.com:9090");
        }

        @Test
        void returns_malformed_url_unchanged() {
            assertThat(UrlNormalizer.normalizeToBase("not a url"))
                    .isEqualTo("not a url");
        }
    }

    @Nested
    class IsSameSite {

        @Test
        void matches_same_host() {
            assertThat(UrlNormalizer.isSameSite(
                    "https://docs.spring.io/guide",
                    "https://docs.spring.io/other")).isTrue();
        }

        @Test
        void rejects_different_host() {
            assertThat(UrlNormalizer.isSameSite(
                    "https://docs.spring.io/guide",
                    "https://github.com/spring")).isFalse();
        }

        @Test
        void rejects_different_scheme() {
            assertThat(UrlNormalizer.isSameSite(
                    "https://docs.spring.io",
                    "http://docs.spring.io")).isFalse();
        }

        @Test
        void rejects_different_ports() {
            assertThat(UrlNormalizer.isSameSite(
                    "https://example.com:8080/a",
                    "https://example.com:9090/b")).isFalse();
        }

        @Test
        void matches_same_non_default_port() {
            assertThat(UrlNormalizer.isSameSite(
                    "https://example.com:8080/a",
                    "https://example.com:8080/b")).isTrue();
        }

        @Test
        void returns_false_for_malformed_root() {
            assertThat(UrlNormalizer.isSameSite("not-valid", "https://example.com")).isFalse();
        }

        @Test
        void returns_false_for_malformed_candidate() {
            assertThat(UrlNormalizer.isSameSite("https://example.com", "://bad")).isFalse();
        }
    }
}
