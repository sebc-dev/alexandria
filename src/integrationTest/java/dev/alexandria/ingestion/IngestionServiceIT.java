package dev.alexandria.ingestion;

import dev.alexandria.BaseIntegrationTest;
import dev.alexandria.search.SearchRequest;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionServiceIT extends BaseIntegrationTest {

    @Autowired
    IngestionService ingestionService;

    @Autowired
    SearchService searchService;

    @Test
    void ingestPageProducesSearchableChunks() {
        String markdown = """
                ## Spring Configuration
                Spring Boot auto-configures beans.
                ```java
                @Configuration
                public class AppConfig {}
                ```""";

        int chunkCount = ingestionService.ingestPage(
                markdown, "https://docs.spring.io/config", Instant.now().toString()
        );
        assertThat(chunkCount).isGreaterThanOrEqualTo(2); // at least 1 prose + 1 code

        List<SearchResult> results = searchService.search(new SearchRequest("Spring auto-configuration"));
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r ->
                "https://docs.spring.io/config".equals(r.sourceUrl()));
    }

    @Test
    void ingestedChunksCarryCorrectMetadata() {
        String markdown = "## Getting Started\nSome introductory text about getting started with the framework.";

        ingestionService.ingestPage(markdown, "https://example.com/guide", "2026-02-18T10:00:00Z");

        List<SearchResult> results = searchService.search(new SearchRequest("Getting Started"));
        assertThat(results).isNotEmpty();

        SearchResult top = results.getFirst();
        assertThat(top.sourceUrl()).isEqualTo("https://example.com/guide");
        assertThat(top.sectionPath()).contains("getting-started");
    }

    @Test
    void ingestMultiplePages() {
        String markdown1 = "## Database Setup\nConfigure PostgreSQL for production use with connection pooling.";
        String markdown2 = "## Authentication\nSecure your API endpoints with JWT authentication tokens.";

        String timestamp = Instant.now().toString();
        int chunkCount1 = ingestionService.ingestPage(
                markdown1, "https://docs.example.com/database", timestamp
        );
        int chunkCount2 = ingestionService.ingestPage(
                markdown2, "https://docs.example.com/auth", timestamp
        );
        assertThat(chunkCount1 + chunkCount2).isGreaterThanOrEqualTo(2); // at least 1 chunk per page

        List<SearchResult> dbResults = searchService.search(new SearchRequest("PostgreSQL connection pooling"));
        assertThat(dbResults).anyMatch(r ->
                "https://docs.example.com/database".equals(r.sourceUrl()));

        List<SearchResult> authResults = searchService.search(new SearchRequest("JWT authentication"));
        assertThat(authResults).anyMatch(r ->
                "https://docs.example.com/auth".equals(r.sourceUrl()));
    }

    @Test
    void ingestPageWithOnlyCodeBlocks() {
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
