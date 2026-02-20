package dev.alexandria.crawl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LlmsTxtParserTest {

    @Test
    void parseUrlsExtractsLinksFromStandardFormat() {
        String content = """
                # Spring Boot Documentation

                ## Getting Started

                - [Quick Start](https://docs.spring.io/boot/quick-start)
                - [Installation](https://docs.spring.io/boot/install)
                """;

        var urls = LlmsTxtParser.parseUrls(content);

        assertThat(urls).containsExactly(
                "https://docs.spring.io/boot/quick-start",
                "https://docs.spring.io/boot/install"
        );
    }

    @Test
    void parseUrlsHandlesLinksWithDescriptions() {
        String content = """
                # Docs

                - [Quick Start](https://docs.spring.io/boot/quick-start): How to get started
                - [Config](https://docs.spring.io/boot/config): Configuration reference
                """;

        var urls = LlmsTxtParser.parseUrls(content);

        assertThat(urls).containsExactly(
                "https://docs.spring.io/boot/quick-start",
                "https://docs.spring.io/boot/config"
        );
    }

    @Test
    void parseUrlsIgnoresHeadersAndBlankLines() {
        String content = """
                # Spring Boot Documentation

                ## Getting Started

                - [Quick Start](https://docs.spring.io/boot/quick-start)

                ## Reference

                - [Config](https://docs.spring.io/boot/config)
                """;

        var urls = LlmsTxtParser.parseUrls(content);

        assertThat(urls).containsExactly(
                "https://docs.spring.io/boot/quick-start",
                "https://docs.spring.io/boot/config"
        );
    }

    @Test
    void parseUrlsIgnoresBlockquotes() {
        String content = """
                # Spring Boot Documentation

                > Spring Boot reference documentation

                - [Quick Start](https://docs.spring.io/boot/quick-start)
                """;

        var urls = LlmsTxtParser.parseUrls(content);

        assertThat(urls).containsExactly("https://docs.spring.io/boot/quick-start");
    }

    @Test
    void parseUrlsHandlesLinksWithoutDash() {
        String content = """
                # Docs

                [Quick Start](https://docs.spring.io/boot/quick-start)
                [Installation](https://docs.spring.io/boot/install)
                """;

        var urls = LlmsTxtParser.parseUrls(content);

        assertThat(urls).containsExactly(
                "https://docs.spring.io/boot/quick-start",
                "https://docs.spring.io/boot/install"
        );
    }

    @Test
    void parseUrlsReturnsEmptyForNoLinks() {
        String content = """
                This is just plain text.
                No markdown links here.
                Just some paragraphs.
                """;

        var urls = LlmsTxtParser.parseUrls(content);

        assertThat(urls).isEmpty();
    }

    @Test
    void parseUrlsReturnsEmptyForNullOrEmpty() {
        assertThat(LlmsTxtParser.parseUrls(null)).isEmpty();
        assertThat(LlmsTxtParser.parseUrls("")).isEmpty();
        assertThat(LlmsTxtParser.parseUrls("   ")).isEmpty();
    }

    @Test
    void parseUrlsExtractsMultipleSections() {
        String content = """
                # Spring Boot Documentation

                > Spring Boot reference documentation

                ## Getting Started

                - [Quick Start](https://docs.spring.io/boot/quick-start): How to get started
                - [Installation](https://docs.spring.io/boot/install)

                ## Reference

                - [Configuration](https://docs.spring.io/boot/config)
                - [Actuator](https://docs.spring.io/boot/actuator): Production features
                """;

        var urls = LlmsTxtParser.parseUrls(content);

        assertThat(urls).containsExactly(
                "https://docs.spring.io/boot/quick-start",
                "https://docs.spring.io/boot/install",
                "https://docs.spring.io/boot/config",
                "https://docs.spring.io/boot/actuator"
        );
    }

    @Test
    void isLlmsTxtContentDetectsLinkIndex() {
        String content = """
                # Spring Boot Documentation

                > Summary

                - [Quick Start](https://docs.spring.io/boot/quick-start)
                - [Installation](https://docs.spring.io/boot/install)
                """;

        boolean result = LlmsTxtParser.isLlmsTxtContent(content);

        assertThat(result).isTrue();
    }

    @Test
    void isLlmsTxtContentRejectsPlainMarkdown() {
        String content = """
                # Getting Started with Spring Boot

                Spring Boot makes it easy to create stand-alone, production-grade Spring based
                Applications that you can just run. We take an opinionated view of the Spring
                platform and third-party libraries so you can get started with minimum fuss.
                Most Spring Boot applications need minimal Spring configuration.

                ## Features

                Spring Boot provides a range of features that address common development needs.
                For example, embedded servers, security, metrics, health checks, externalized
                configuration, and more. Here is a detailed description of each feature and
                how to use them effectively in your applications.

                ## System Requirements

                Spring Boot 3.x requires Java 17 or later. It has been tested with the JDK
                distributions from multiple vendors. Make sure you have the correct version
                installed before proceeding.
                """;

        boolean result = LlmsTxtParser.isLlmsTxtContent(content);

        assertThat(result).isFalse();
    }

    @Test
    void parseDistinguishesLlmsTxtFromLlmsFullTxt() {
        // llms.txt: link index
        String llmsTxt = """
                # Spring Boot Documentation

                > Summary

                - [Quick Start](https://docs.spring.io/boot/quick-start)
                - [Installation](https://docs.spring.io/boot/install)
                """;

        LlmsTxtParser.LlmsTxtResult indexResult = LlmsTxtParser.parse(llmsTxt);

        assertThat(indexResult.urls()).containsExactly(
                "https://docs.spring.io/boot/quick-start",
                "https://docs.spring.io/boot/install"
        );
        assertThat(indexResult.rawContent()).isEmpty();
        assertThat(indexResult.isFullContent()).isFalse();

        // llms-full.txt: raw markdown content with some inline links
        String llmsFullTxt = """
                # Spring Boot Documentation

                Spring Boot makes it easy to create stand-alone, production-grade Spring based
                Applications that you can just run. We take an opinionated view of the Spring
                platform and third-party libraries so you can get started with minimum fuss.
                Most Spring Boot applications need minimal Spring configuration.

                ## Getting Started

                To get started with Spring Boot, you need to install Java 17 or later.
                Download the latest version from the [official site](https://adoptium.net).

                ## Features

                Spring Boot provides a range of features that address common development needs.
                For example, embedded servers, security, metrics, health checks, externalized
                configuration, and more. Here is a detailed description of each feature and
                how to use them effectively in your applications. Visit the
                [reference docs](https://docs.spring.io/boot/reference) for details.

                ## System Requirements

                Spring Boot 3.x requires Java 17 or later. It has been tested with the JDK
                distributions from multiple vendors. Make sure you have the correct version
                installed before proceeding.
                """;

        LlmsTxtParser.LlmsTxtResult fullResult = LlmsTxtParser.parse(llmsFullTxt);

        assertThat(fullResult.urls()).containsExactly(
                "https://adoptium.net",
                "https://docs.spring.io/boot/reference"
        );
        assertThat(fullResult.rawContent()).isEqualTo(llmsFullTxt);
        assertThat(fullResult.isFullContent()).isTrue();
    }
}
