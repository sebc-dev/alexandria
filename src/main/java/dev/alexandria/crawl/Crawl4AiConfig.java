package dev.alexandria.crawl;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class Crawl4AiConfig {

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
