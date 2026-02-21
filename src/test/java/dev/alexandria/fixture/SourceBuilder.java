package dev.alexandria.fixture;

import dev.alexandria.source.Source;
import dev.alexandria.source.SourceStatus;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Lightweight test builder for the {@link Source} JPA entity. Provides sensible defaults so tests
 * only override what they care about.
 *
 * <pre>{@code
 * Source source = new SourceBuilder().url("https://docs.spring.io").build();
 * }</pre>
 */
public final class SourceBuilder {

  private @Nullable UUID id = UUID.randomUUID();
  private String url = "https://docs.example.com";
  private String name = "Example Docs";
  private SourceStatus status = SourceStatus.PENDING;
  private @Nullable Instant lastCrawledAt;
  private int chunkCount = 0;
  private @Nullable String allowPatterns;
  private @Nullable String blockPatterns;
  private @Nullable Integer maxDepth;
  private @Nullable Integer maxPages;
  private @Nullable String llmsTxtUrl;

  public SourceBuilder id(@Nullable UUID id) {
    this.id = id;
    return this;
  }

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
    if (id != null) {
      setField(source, "id", id);
    }
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

  private static void setField(Source source, String fieldName, Object value) {
    try {
      Field field = Source.class.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(source, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to set field " + fieldName, e);
    }
  }
}
