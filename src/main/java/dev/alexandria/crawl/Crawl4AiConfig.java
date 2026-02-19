package dev.alexandria.crawl;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Configures the {@link RestClient} used to communicate with the Crawl4AI Python sidecar.
 *
 * <p>Timeouts are externalized via {@code alexandria.crawl4ai.*} properties.
 * The client defaults to JSON content type and is qualified as {@code "crawl4AiRestClient"}.
 */
@Configuration
public class Crawl4AiConfig {

    /**
     * Creates a pre-configured {@link RestClient} targeting the Crawl4AI sidecar.
     *
     * @param builder          Spring-provided builder with common defaults
     * @param baseUrl          sidecar base URL (e.g. {@code http://localhost:11235})
     * @param connectTimeoutMs TCP connection timeout in milliseconds
     * @param readTimeoutMs    response read timeout in milliseconds
     * @return a named REST client bean for injection into {@link Crawl4AiClient}
     */
    @Bean
    public RestClient crawl4AiRestClient(
            RestClient.Builder builder,
            @Value("${alexandria.crawl4ai.base-url}") String baseUrl,
            @Value("${alexandria.crawl4ai.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${alexandria.crawl4ai.read-timeout-ms}") int readTimeoutMs) {

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        return builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
