package dev.alexandria.ingestion;

import dev.alexandria.BaseIntegrationTest;
import dev.alexandria.crawl.CrawlResult;
import dev.alexandria.search.SearchRequest;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionServiceIT extends BaseIntegrationTest {

    @Autowired
    IngestionService ingestionService;

    @Autowired
    SearchService searchService;

    @Autowired
    EmbeddingStore<TextSegment> embeddingStore;

    @BeforeEach
    void cleanStore() {
        embeddingStore.removeAll();
    }

    @Test
    void ingest_crawl_result_produces_searchable_chunks() {
        String markdown = """
                ## Spring Configuration
                Spring Boot auto-configures beans.
                ```java
                @Configuration
                public class AppConfig {}
                ```""";

        CrawlResult page = new CrawlResult(
                "https://docs.spring.io/config", markdown, List.of(), true, null
        );

        int chunkCount = ingestionService.ingest(List.of(page));
        assertThat(chunkCount).isGreaterThanOrEqualTo(2); // at least 1 prose + 1 code

        List<SearchResult> results = searchService.search(new SearchRequest("Spring auto-configuration"));
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r ->
                "https://docs.spring.io/config".equals(r.sourceUrl()));
    }

    @Test
    void ingested_chunks_carry_correct_metadata() {
        String markdown = "## Getting Started\nSome introductory text about getting started with the framework.";

        ingestionService.ingestPage(markdown, "https://example.com/guide", "2026-02-18T10:00:00Z");

        List<SearchResult> results = searchService.search(new SearchRequest("Getting Started"));
        assertThat(results).isNotEmpty();

        SearchResult top = results.getFirst();
        assertThat(top.sourceUrl()).isEqualTo("https://example.com/guide");
        assertThat(top.sectionPath()).contains("getting-started");
    }

    @Test
    void ingest_multiple_pages() {
        String markdown1 = "## Database Setup\nConfigure PostgreSQL for production use with connection pooling.";
        String markdown2 = "## Authentication\nSecure your API endpoints with JWT authentication tokens.";

        CrawlResult page1 = new CrawlResult(
                "https://docs.example.com/database", markdown1, List.of(), true, null
        );
        CrawlResult page2 = new CrawlResult(
                "https://docs.example.com/auth", markdown2, List.of(), true, null
        );

        int chunkCount = ingestionService.ingest(List.of(page1, page2));
        assertThat(chunkCount).isGreaterThanOrEqualTo(2); // at least 1 chunk per page

        List<SearchResult> dbResults = searchService.search(new SearchRequest("PostgreSQL connection pooling"));
        assertThat(dbResults).anyMatch(r ->
                "https://docs.example.com/database".equals(r.sourceUrl()));

        List<SearchResult> authResults = searchService.search(new SearchRequest("JWT authentication"));
        assertThat(authResults).anyMatch(r ->
                "https://docs.example.com/auth".equals(r.sourceUrl()));
    }

    @Test
    void ingest_page_with_only_code_blocks() {
        String markdown = """
                ## Snippet
                ```bash
                echo hello world
                ```
                """;

        int chunkCount = ingestionService.ingestPage(
                markdown, "https://example.com/snippet", "2026-02-18T10:00:00Z"
        );
        assertThat(chunkCount).isGreaterThanOrEqualTo(1); // code chunk, no empty prose chunk
    }
}
