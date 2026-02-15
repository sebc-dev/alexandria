package dev.alexandria.crawl;

import java.net.URI;
import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import crawlercommons.sitemaps.AbstractSiteMap;
import crawlercommons.sitemaps.SiteMap;
import crawlercommons.sitemaps.SiteMapIndex;
import crawlercommons.sitemaps.SiteMapURL;

/**
 * Parses sitemap.xml from well-known locations using crawler-commons.
 * Handles both single sitemaps and sitemap index files.
 */
@Component
public class SitemapParser {

    private static final Logger log = LoggerFactory.getLogger(SitemapParser.class);

    private final RestClient.Builder restClientBuilder;

    public SitemapParser(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    /**
     * Try to discover URLs from sitemap.xml at well-known locations.
     * Returns empty list if no sitemap found or unparseable.
     */
    public List<String> discoverFromSitemap(String rootUrl) {
        String baseUrl = normalizeToBase(rootUrl);
        List<String> candidates = List.of(
                baseUrl + "/sitemap.xml",
                baseUrl + "/sitemap_index.xml"
        );

        RestClient httpClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, "*/*")
                .build();

        for (String sitemapUrl : candidates) {
            try {
                byte[] content = httpClient.get()
                        .uri(sitemapUrl)
                        .retrieve()
                        .body(byte[].class);
                if (content == null || content.length == 0) {
                    continue;
                }

                crawlercommons.sitemaps.SiteMapParser parser =
                        new crawlercommons.sitemaps.SiteMapParser(false);
                AbstractSiteMap result = parser.parseSiteMap(content, URI.create(sitemapUrl).toURL());

                if (result instanceof SiteMapIndex index) {
                    return index.getSitemaps().stream()
                            .map(AbstractSiteMap::getUrl)
                            .map(URL::toString)
                            .flatMap(url -> parseSingleSitemap(httpClient, url).stream())
                            .toList();
                } else if (result instanceof SiteMap siteMap) {
                    return extractUrls(siteMap);
                }
            } catch (Exception e) {
                log.debug("Sitemap not available at {}: {}", sitemapUrl, e.getMessage());
            }
        }
        return List.of();
    }

    /**
     * Extract scheme + host (+ port if non-default) from a URL.
     * Example: "https://docs.spring.io/boot/reference/" -> "https://docs.spring.io"
     */
    String normalizeToBase(String rootUrl) {
        try {
            URI uri = URI.create(rootUrl);
            URL url = uri.toURL();
            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();

            if (port == -1 || (protocol.equals("http") && port == 80)
                    || (protocol.equals("https") && port == 443)) {
                return protocol + "://" + host;
            }
            return protocol + "://" + host + ":" + port;
        } catch (Exception e) {
            log.warn("Could not parse base URL from {}: {}", rootUrl, e.getMessage());
            return rootUrl;
        }
    }

    private List<String> parseSingleSitemap(RestClient httpClient, String sitemapUrl) {
        try {
            byte[] content = httpClient.get()
                    .uri(sitemapUrl)
                    .retrieve()
                    .body(byte[].class);
            if (content == null || content.length == 0) {
                return List.of();
            }

            crawlercommons.sitemaps.SiteMapParser parser =
                    new crawlercommons.sitemaps.SiteMapParser(false);
            AbstractSiteMap result = parser.parseSiteMap(content, URI.create(sitemapUrl).toURL());

            if (result instanceof SiteMap siteMap) {
                return extractUrls(siteMap);
            }
        } catch (Exception e) {
            log.debug("Could not parse sub-sitemap {}: {}", sitemapUrl, e.getMessage());
        }
        return List.of();
    }

    private List<String> extractUrls(SiteMap siteMap) {
        return siteMap.getSiteMapUrls().stream()
                .map(SiteMapURL::getUrl)
                .map(URL::toString)
                .toList();
    }
}
