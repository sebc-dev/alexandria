package dev.alexandria.mcp;

import dev.alexandria.fixture.SourceBuilder;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import dev.alexandria.source.Source;
import dev.alexandria.source.SourceRepository;
import dev.alexandria.source.SourceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class McpToolServiceTest {

    @Mock
    SearchService searchService;

    @Mock
    SourceRepository sourceRepository;

    @Mock
    TokenBudgetTruncator truncator;

    @InjectMocks
    McpToolService mcpToolService;

    @Captor
    ArgumentCaptor<dev.alexandria.search.SearchRequest> searchRequestCaptor;

    // --- searchDocs ---

    @Test
    void searchDocsReturnsFormattedResults() {
        var results = List.of(new SearchResult("content", 0.9, "https://docs.example.com", "Section"));
        given(searchService.search(any())).willReturn(results);
        given(truncator.truncate(results)).willReturn("formatted output");

        String output = mcpToolService.searchDocs("spring boot", null);

        assertThat(output).isEqualTo("formatted output");
    }

    @Test
    void searchDocsWithNullQueryReturnsError() {
        String output = mcpToolService.searchDocs(null, null);

        assertThat(output).startsWith("Error:");
    }

    @Test
    void searchDocsWithBlankQueryReturnsError() {
        String output = mcpToolService.searchDocs("   ", null);

        assertThat(output).startsWith("Error:");
    }

    @Test
    void searchDocsWithNoResultsReturnsNotFoundMessage() {
        given(searchService.search(any())).willReturn(Collections.emptyList());

        String output = mcpToolService.searchDocs("nonexistent topic", null);

        assertThat(output).contains("No results found");
    }

    @Test
    void searchDocsDefaultsMaxResultsTo10() {
        given(searchService.search(searchRequestCaptor.capture())).willReturn(Collections.emptyList());

        mcpToolService.searchDocs("test query", null);

        assertThat(searchRequestCaptor.getValue().maxResults()).isEqualTo(10);
    }

    @Test
    void searchDocsClampsMaxResultsTo50() {
        given(searchService.search(searchRequestCaptor.capture())).willReturn(Collections.emptyList());

        mcpToolService.searchDocs("test query", 100);

        assertThat(searchRequestCaptor.getValue().maxResults()).isEqualTo(50);
    }

    @Test
    void searchDocsHandlesExceptionGracefully() {
        given(searchService.search(any())).willThrow(new RuntimeException("connection failed"));

        String output = mcpToolService.searchDocs("test query", null);

        assertThat(output).startsWith("Error");
        assertThat(output).contains("connection failed");
    }

    // --- listSources ---

    @Test
    void listSourcesFormatsAllSources() {
        Source source1 = new SourceBuilder()
                .url("https://docs.spring.io")
                .name("Spring Docs")
                .status(SourceStatus.INDEXED)
                .chunkCount(150)
                .lastCrawledAt(Instant.parse("2026-01-15T10:00:00Z"))
                .build();
        Source source2 = new SourceBuilder()
                .url("https://docs.langchain4j.dev")
                .name("LangChain4j Docs")
                .status(SourceStatus.PENDING)
                .chunkCount(0)
                .build();
        given(sourceRepository.findAll()).willReturn(List.of(source1, source2));

        String output = mcpToolService.listSources();

        assertThat(output).contains("Spring Docs");
        assertThat(output).contains("https://docs.spring.io");
        assertThat(output).contains("INDEXED");
        assertThat(output).contains("LangChain4j Docs");
        assertThat(output).contains("https://docs.langchain4j.dev");
        assertThat(output).contains("PENDING");
    }

    @Test
    void listSourcesWithNoSourcesReturnsEmptyMessage() {
        given(sourceRepository.findAll()).willReturn(Collections.emptyList());

        String output = mcpToolService.listSources();

        assertThat(output).contains("No documentation sources");
    }

    @Test
    void listSourcesHandlesExceptionGracefully() {
        given(sourceRepository.findAll()).willThrow(new RuntimeException("db error"));

        String output = mcpToolService.listSources();

        assertThat(output).startsWith("Error");
        assertThat(output).contains("db error");
    }

    // --- addSource ---

    @Test
    void addSourceCreatesPendingSource() {
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        String output = mcpToolService.addSource("https://docs.spring.io", "Spring Docs");

        assertThat(output).contains("Spring Docs");
        assertThat(output).contains("PENDING");
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).save(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getStatus()).isEqualTo(SourceStatus.PENDING);
        assertThat(sourceCaptor.getValue().getUrl()).isEqualTo("https://docs.spring.io");
    }

    @Test
    void addSourceWithBlankUrlReturnsError() {
        String output = mcpToolService.addSource("", "Some Name");

        assertThat(output).startsWith("Error:");
    }

    // --- removeSource ---

    @Test
    void removeSourceDeletesById() {
        UUID uuid = UUID.randomUUID();

        String output = mcpToolService.removeSource(uuid.toString());

        verify(sourceRepository).deleteById(uuid);
        assertThat(output).contains("removed");
    }

    @Test
    void removeSourceWithInvalidUuidReturnsError() {
        String output = mcpToolService.removeSource("not-a-uuid");

        assertThat(output).startsWith("Error:");
        assertThat(output).contains("Invalid source ID");
    }

    // --- crawlStatus ---

    @Test
    void crawlStatusReturnsSourceInfo() {
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("Spring Docs")
                .status(SourceStatus.INDEXED)
                .chunkCount(42)
                .lastCrawledAt(Instant.parse("2026-02-01T12:00:00Z"))
                .build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));

        String output = mcpToolService.crawlStatus(uuid.toString());

        assertThat(output).contains("Spring Docs");
        assertThat(output).contains("INDEXED");
        assertThat(output).contains("42");
    }

    @Test
    void crawlStatusWithUnknownSourceReturnsError() {
        UUID uuid = UUID.randomUUID();
        given(sourceRepository.findById(uuid)).willReturn(Optional.empty());

        String output = mcpToolService.crawlStatus(uuid.toString());

        assertThat(output).startsWith("Error:");
        assertThat(output).contains("not found");
    }

    // --- recrawlSource ---

    @Test
    void recrawlSourceReturnsStubMessage() {
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder().name("Spring Docs").build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));

        String output = mcpToolService.recrawlSource(uuid.toString());

        assertThat(output).contains("Spring Docs");
        assertThat(output).contains("will be available");
    }

    @Test
    void recrawlSourceWithUnknownSourceReturnsError() {
        UUID uuid = UUID.randomUUID();
        given(sourceRepository.findById(uuid)).willReturn(Optional.empty());

        String output = mcpToolService.recrawlSource(uuid.toString());

        assertThat(output).startsWith("Error:");
        assertThat(output).contains("not found");
    }
}
