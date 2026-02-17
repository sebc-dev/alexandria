package dev.alexandria.crawl;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(Crawl4AiProperties.class)
@EnableRetry
public class Crawl4AiConfig {

    @Bean
    public RestClient crawl4AiRestClient(RestClient.Builder builder, Crawl4AiProperties props) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(props.connectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(props.readTimeoutMs()));

        return builder
                .baseUrl(props.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
