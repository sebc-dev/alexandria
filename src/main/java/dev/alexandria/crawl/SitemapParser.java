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
    private final long maxSitemapSizeBytes;

    public SitemapParser(RestClient.Builder restClientBuilder, Crawl4AiProperties props) {
        this.restClientBuilder = restClientBuilder;
        this.maxSitemapSizeBytes = props.maxSitemapSizeBytes();
    }

    /**
     * Try to discover URLs from sitemap.xml at well-known locations.
     * Returns empty list if no sitemap found or unparseable.
     */
    public List<String> discoverFromSitemap(String rootUrl) {
        String baseUrl = UrlNormalizer.normalizeToBase(rootUrl);
        List<String> candidates = List.of(
                baseUrl + "/sitemap.xml",
                baseUrl + "/sitemap_index.xml"
        );

        RestClient httpClient = restClientBuilder
                .defaultHeader(HttpHeaders.ACCEPT, "*/*")
                .build();

        for (String sitemapUrl : candidates) {
            try {
                byte[] content = fetchSitemap(httpClient, sitemapUrl);
                if (content == null) {
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
     * Fetch sitemap content with size limit to prevent OOM on giant sitemaps.
     */
    private byte[] fetchSitemap(RestClient httpClient, String sitemapUrl) {
        byte[] content = httpClient.get()
                .uri(sitemapUrl)
                .retrieve()
                .body(byte[].class);
        if (content == null || content.length == 0) {
            return null;
        }
        if (content.length > maxSitemapSizeBytes) {
            log.warn("Sitemap at {} exceeds size limit ({} bytes > {} bytes), skipping",
                    sitemapUrl, content.length, maxSitemapSizeBytes);
            return null;
        }
        return content;
    }

    private List<String> parseSingleSitemap(RestClient httpClient, String sitemapUrl) {
        try {
            byte[] content = fetchSitemap(httpClient, sitemapUrl);
            if (content == null) {
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
