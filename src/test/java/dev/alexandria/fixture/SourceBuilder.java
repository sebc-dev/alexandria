package dev.alexandria.fixture;

import dev.alexandria.source.Source;
import dev.alexandria.source.SourceStatus;

import java.time.Instant;

/**
 * Lightweight test builder for the {@link Source} JPA entity.
 * Provides sensible defaults so tests only override what they care about.
 *
 * <pre>{@code
 * Source source = new SourceBuilder().url("https://docs.spring.io").build();
 * }</pre>
 */
public final class SourceBuilder {

    private String url = "https://docs.example.com";
    private String name = "Example Docs";
    private SourceStatus status = SourceStatus.PENDING;
    private Instant lastCrawledAt;
    private int chunkCount = 0;
    private String allowPatterns;
    private String blockPatterns;
    private Integer maxDepth;
    private Integer maxPages;
    private String llmsTxtUrl;

    public SourceBuilder url(String url) {
        this.url = url;
        return this;
    }

    public SourceBuilder name(String name) {
        this.name = name;
        return this;
    }

    public SourceBuilder status(SourceStatus status) {
        this.status = status;
        return this;
    }

    public SourceBuilder lastCrawledAt(Instant lastCrawledAt) {
        this.lastCrawledAt = lastCrawledAt;
        return this;
    }

    public SourceBuilder chunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
        return this;
    }

    public SourceBuilder allowPatterns(String allowPatterns) {
        this.allowPatterns = allowPatterns;
        return this;
    }

    public SourceBuilder blockPatterns(String blockPatterns) {
        this.blockPatterns = blockPatterns;
        return this;
    }

    public SourceBuilder maxDepth(Integer maxDepth) {
        this.maxDepth = maxDepth;
        return this;
    }

    public SourceBuilder maxPages(Integer maxPages) {
        this.maxPages = maxPages;
        return this;
    }

    public SourceBuilder llmsTxtUrl(String llmsTxtUrl) {
        this.llmsTxtUrl = llmsTxtUrl;
        return this;
    }

    public Source build() {
        Source source = new Source(url, name);
        source.setStatus(status);
        if (lastCrawledAt != null) {
            source.setLastCrawledAt(lastCrawledAt);
        }
        source.setChunkCount(chunkCount);
        if (allowPatterns != null) {
            source.setAllowPatterns(allowPatterns);
        }
        if (blockPatterns != null) {
            source.setBlockPatterns(blockPatterns);
        }
        if (maxDepth != null) {
            source.setMaxDepth(maxDepth);
        }
        if (maxPages != null) {
            source.setMaxPages(maxPages);
        }
        if (llmsTxtUrl != null) {
            source.setLlmsTxtUrl(llmsTxtUrl);
        }
        return source;
    }
}
