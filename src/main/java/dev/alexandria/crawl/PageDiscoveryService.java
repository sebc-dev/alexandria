package dev.alexandria.crawl;

import java.net.URI;
import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Coordinates URL discovery for a site using a priority cascade:
 * <ol>
 *   <li>llms.txt / llms-full.txt (machine-readable documentation index)</li>
 *   <li>sitemap.xml (via {@link SitemapParser})</li>
 *   <li>BFS link crawling (caller seeds with root URL)</li>
 * </ol>
 */
@Service
public class PageDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(PageDiscoveryService.class);

    private final SitemapParser sitemapParser;
    private final RestClient restClient;

    public PageDiscoveryService(SitemapParser sitemapParser, RestClient.Builder restClientBuilder) {
        this.sitemapParser = sitemapParser;
        this.restClient = restClientBuilder.build();
    }

    /**
     * Discover URLs for a site using the priority cascade:
     * llms.txt/llms-full.txt first, then sitemap.xml, then link crawl fallback.
     *
     * @param rootUrl the root URL of the site to discover
     * @return discovered URLs with discovery method and optional full content
     */
    public DiscoveryResult discoverUrls(String rootUrl) {
        String baseUrl = normalizeToBase(rootUrl);

        // 1. Try llms-full.txt first (provides content for direct ingestion)
        DiscoveryResult llmsFullResult = tryLlmsFullTxt(baseUrl);
        if (llmsFullResult != null) {
            return llmsFullResult;
        }

        // 2. Try llms.txt (link index)
        DiscoveryResult llmsTxtResult = tryLlmsTxt(baseUrl);
        if (llmsTxtResult != null) {
            return llmsTxtResult;
        }

        // 3. Try sitemap.xml
        List<String> sitemapUrls = sitemapParser.discoverFromSitemap(rootUrl);
        if (!sitemapUrls.isEmpty()) {
            List<String> filtered = sitemapUrls.stream()
                    .filter(url -> UrlNormalizer.isSameSite(rootUrl, url))
                    .map(UrlNormalizer::normalize)
                    .distinct()
                    .toList();
            return new DiscoveryResult(filtered, DiscoveryMethod.SITEMAP, null);
        }

        // 4. Fall back to link crawl
        return new DiscoveryResult(List.of(), DiscoveryMethod.LINK_CRAWL, null);
    }

    private DiscoveryResult tryLlmsFullTxt(String baseUrl) {
        String llmsFullUrl = baseUrl + "/llms-full.txt";
        String content = fetchText(llmsFullUrl);
        if (content == null) {
            return null;
        }

        LlmsTxtParser.LlmsTxtResult parsed = LlmsTxtParser.parse(content);
        if (parsed.isFullContent()) {
            log.info("Discovered llms-full.txt at {} ({} inline URLs)", llmsFullUrl, parsed.urls().size());
            return new DiscoveryResult(parsed.urls(), DiscoveryMethod.LLMS_FULL_TXT, parsed.rawContent());
        }

        // Content at llms-full.txt path but looks like a link index -- treat as llms.txt
        if (!parsed.urls().isEmpty()) {
            log.info("llms-full.txt at {} is a link index ({} URLs)", llmsFullUrl, parsed.urls().size());
            return new DiscoveryResult(parsed.urls(), DiscoveryMethod.LLMS_TXT, null);
        }

        return null;
    }

    private DiscoveryResult tryLlmsTxt(String baseUrl) {
        String llmsTxtUrl = baseUrl + "/llms.txt";
        String content = fetchText(llmsTxtUrl);
        if (content == null) {
            return null;
        }

        List<String> urls = LlmsTxtParser.parseUrls(content);
        if (!urls.isEmpty()) {
            log.info("Discovered llms.txt at {} ({} URLs)", llmsTxtUrl, urls.size());
            return new DiscoveryResult(urls, DiscoveryMethod.LLMS_TXT, null);
        }

        return null;
    }

    /**
     * Fetch text content from a URL, returning null on any error (404, connection error, etc.).
     */
    private String fetchText(String url) {
        try {
            return restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.debug("Could not fetch {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Extract scheme + host (+ port if non-default) from a URL.
     */
    private String normalizeToBase(String rootUrl) {
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

    /**
     * Method used for URL discovery.
     */
    public enum DiscoveryMethod {
        /** URLs discovered from llms.txt link index */
        LLMS_TXT,
        /** Full content discovered from llms-full.txt */
        LLMS_FULL_TXT,
        /** URLs discovered from sitemap.xml */
        SITEMAP,
        /** No discovery source found; caller should use BFS link crawling */
        LINK_CRAWL
    }

    /**
     * Result of URL discovery for a site.
     *
     * @param urls            discovered URLs to crawl
     * @param method          how URLs were discovered
     * @param llmsFullContent raw llms-full.txt content for direct ingestion (null unless method is LLMS_FULL_TXT)
     */
    public record DiscoveryResult(List<String> urls, DiscoveryMethod method, String llmsFullContent) {
        public DiscoveryResult {
            urls = urls == null ? List.of() : List.copyOf(urls);
        }
    }
}
