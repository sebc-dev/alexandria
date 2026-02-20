package dev.alexandria.mcp;

import dev.alexandria.crawl.CrawlProgress;
import dev.alexandria.crawl.CrawlProgressTracker;
import dev.alexandria.crawl.CrawlScope;
import dev.alexandria.crawl.CrawlService;
import dev.alexandria.document.DocumentChunkRepository;
import dev.alexandria.fixture.SourceBuilder;
import dev.alexandria.ingestion.IngestionService;
import dev.alexandria.search.SearchResult;
import dev.alexandria.search.SearchService;
import dev.alexandria.source.Source;
import dev.alexandria.source.SourceRepository;
import dev.alexandria.source.SourceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class McpToolServiceTest {

    @Mock
    SearchService searchService;

    @Mock
    SourceRepository sourceRepository;

    @Mock
    TokenBudgetTruncator truncator;

    @Mock
    CrawlService crawlService;

    @Mock
    CrawlProgressTracker progressTracker;

    @Mock
    IngestionService ingestionService;

    @Mock
    DocumentChunkRepository documentChunkRepository;

    McpToolService mcpToolService;

    @Captor
    ArgumentCaptor<dev.alexandria.search.SearchRequest> searchRequestCaptor;

    @BeforeEach
    void setUp() {
        mcpToolService = spy(new McpToolService(
                searchService, sourceRepository, truncator,
                crawlService, progressTracker, ingestionService,
                documentChunkRepository));
    }

    /**
     * Suppress async dispatch for tests that call addSource or recrawlSource.
     * Must be called before invoking the method under test.
     */
    private void suppressAsyncDispatch() {
        doNothing().when(mcpToolService).dispatchCrawl(any(), anyString(), any(CrawlScope.class), anyBoolean());
    }

    // --- searchDocs ---

    @Test
    void searchDocsReturnsFormattedResults() {
        var results = List.of(new SearchResult("content", 0.9, "https://docs.example.com", "Section"));
        given(searchService.search(any())).willReturn(results);
        given(truncator.truncate(results)).willReturn("formatted output");

        String output = mcpToolService.searchDocs("spring boot", null, null, null, null, null, null, null);

        assertThat(output).isEqualTo("formatted output");
    }

    @Test
    void searchDocsWithNullQueryReturnsError() {
        String output = mcpToolService.searchDocs(null, null, null, null, null, null, null, null);

        assertThat(output).startsWith("Error:");
    }

    @Test
    void searchDocsWithBlankQueryReturnsError() {
        String output = mcpToolService.searchDocs("   ", null, null, null, null, null, null, null);

        assertThat(output).startsWith("Error:");
    }

    @Test
    void searchDocsWithNoResultsReturnsNotFoundMessage() {
        given(searchService.search(any())).willReturn(Collections.emptyList());

        String output = mcpToolService.searchDocs("nonexistent topic", null, null, null, null, null, null, null);

        assertThat(output).contains("No results found");
    }

    @Test
    void searchDocsDefaultsMaxResultsTo10() {
        given(searchService.search(searchRequestCaptor.capture())).willReturn(Collections.emptyList());

        mcpToolService.searchDocs("test query", null, null, null, null, null, null, null);

        assertThat(searchRequestCaptor.getValue().maxResults()).isEqualTo(10);
    }

    @Test
    void searchDocsClampsMaxResultsTo50() {
        given(searchService.search(searchRequestCaptor.capture())).willReturn(Collections.emptyList());

        mcpToolService.searchDocs("test query", 100, null, null, null, null, null, null);

        assertThat(searchRequestCaptor.getValue().maxResults()).isEqualTo(50);
    }

    @Test
    void searchDocsHandlesExceptionGracefully() {
        given(searchService.search(any())).willThrow(new RuntimeException("connection failed"));

        String output = mcpToolService.searchDocs("test query", null, null, null, null, null, null, null);

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
        List<Object[]> proseChunks = List.<Object[]>of(new Object[]{"prose", 150L});
        List<Object[]> noChunks = Collections.emptyList();
        given(documentChunkRepository.countBySourceIdGroupedByContentType(any()))
                .willReturn(proseChunks)
                .willReturn(noChunks);

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
    void addSourceCreatesSourceWithScopeAndTriggersCrawl() {
        suppressAsyncDispatch();
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        String output = mcpToolService.addSource(
                "https://docs.spring.io", "Spring Docs",
                "/docs/**,/api/**", "/archive/**", 3, 200, null, null);

        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).save(sourceCaptor.capture());
        Source saved = sourceCaptor.getValue();
        assertThat(saved.getUrl()).isEqualTo("https://docs.spring.io");
        assertThat(saved.getName()).isEqualTo("Spring Docs");
        assertThat(saved.getAllowPatterns()).isEqualTo("/docs/**,/api/**");
        assertThat(saved.getBlockPatterns()).isEqualTo("/archive/**");
        assertThat(saved.getMaxDepth()).isEqualTo(3);
        assertThat(saved.getMaxPages()).isEqualTo(200);
        assertThat(saved.getStatus()).isEqualTo(SourceStatus.CRAWLING);
        assertThat(output).contains("Crawl started");
        assertThat(output).contains("Spring Docs");
        verify(mcpToolService).dispatchCrawl(any(), eq("https://docs.spring.io"), any(CrawlScope.class), eq(false));
    }

    @Test
    void addSourceWithNullScopeUsesDefaults() {
        suppressAsyncDispatch();
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        String output = mcpToolService.addSource(
                "https://docs.spring.io", "Spring Docs",
                null, null, null, null, null, null);

        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).save(sourceCaptor.capture());
        Source saved = sourceCaptor.getValue();
        assertThat(saved.getAllowPatterns()).isNull();
        assertThat(saved.getBlockPatterns()).isNull();
        assertThat(saved.getMaxDepth()).isNull();
        assertThat(saved.getMaxPages()).isEqualTo(500);
        assertThat(output).contains("Crawl started");
    }

    @Test
    void addSourceReturnsImmediatelyWithId() {
        suppressAsyncDispatch();
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        String output = mcpToolService.addSource(
                "https://docs.spring.io", "Spring Docs",
                null, null, null, null, null, null);

        assertThat(output).contains("Spring Docs");
        assertThat(output).contains("Crawl started");
        assertThat(output).contains("crawl_status");
    }

    @Test
    void addSourceWithBlankUrlReturnsError() {
        String output = mcpToolService.addSource("", "Some Name",
                null, null, null, null, null, null);

        assertThat(output).startsWith("Error:");
    }

    @Test
    void addSourceWithLlmsTxtUrlSavesIt() {
        suppressAsyncDispatch();
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        mcpToolService.addSource(
                "https://docs.spring.io", "Spring Docs",
                null, null, null, null, "https://docs.spring.io/llms.txt", null);

        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).save(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getLlmsTxtUrl()).isEqualTo("https://docs.spring.io/llms.txt");
    }

    // --- removeSource ---

    @Test
    void removeSourceDeletesById() {
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("Spring Docs")
                .status(SourceStatus.INDEXED)
                .build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));
        given(documentChunkRepository.countBySourceId(uuid)).willReturn(42L);

        String output = mcpToolService.removeSource(uuid.toString());

        verify(sourceRepository).deleteById(uuid);
        assertThat(output).contains("removed");
        assertThat(output).contains("42 chunks deleted");
    }

    @Test
    void removeSourceWithInvalidUuidReturnsError() {
        String output = mcpToolService.removeSource("not-a-uuid");

        assertThat(output).startsWith("Error:");
        assertThat(output).contains("Invalid source ID");
    }

    // --- crawlStatus ---

    @Test
    void crawlStatusShowsActiveProgress() {
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("Spring Docs")
                .url("https://docs.spring.io")
                .status(SourceStatus.CRAWLING)
                .build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));
        CrawlProgress progress = new CrawlProgress(
                uuid, CrawlProgress.Status.CRAWLING, 15, 3, 50, 2,
                List.of("https://docs.spring.io/broken"),
                List.of("https://docs.spring.io/archive/old"),
                Instant.now().minusSeconds(120));
        given(progressTracker.getProgress(uuid)).willReturn(Optional.of(progress));

        String output = mcpToolService.crawlStatus(uuid.toString());

        assertThat(output).contains("Spring Docs");
        assertThat(output).contains("CRAWLING");
        assertThat(output).contains("15/50");
        assertThat(output).contains("3 skipped");
        assertThat(output).contains("2 errors");
    }

    @Test
    void crawlStatusShowsCompletedSummary() {
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("Spring Docs")
                .url("https://docs.spring.io")
                .status(SourceStatus.INDEXED)
                .chunkCount(42)
                .lastCrawledAt(Instant.parse("2026-02-01T12:00:00Z"))
                .build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));
        given(progressTracker.getProgress(uuid)).willReturn(Optional.empty());
        List<Object[]> proseChunks = List.<Object[]>of(new Object[]{"prose", 42L});
        given(documentChunkRepository.countBySourceIdGroupedByContentType(any()))
                .willReturn(proseChunks);

        String output = mcpToolService.crawlStatus(uuid.toString());

        assertThat(output).contains("Spring Docs");
        assertThat(output).contains("INDEXED");
        assertThat(output).contains("42");
        assertThat(output).contains("2026-02-01");
    }

    @Test
    void crawlStatusShowsFilteredUrls() {
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("Spring Docs")
                .url("https://docs.spring.io")
                .status(SourceStatus.CRAWLING)
                .build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));
        CrawlProgress progress = new CrawlProgress(
                uuid, CrawlProgress.Status.CRAWLING, 5, 0, 20, 0,
                List.of(),
                List.of("https://docs.spring.io/archive/v1", "https://docs.spring.io/archive/v2"),
                Instant.now().minusSeconds(60));
        given(progressTracker.getProgress(uuid)).willReturn(Optional.of(progress));

        String output = mcpToolService.crawlStatus(uuid.toString());

        assertThat(output).contains("Filtered URLs");
        assertThat(output).contains("https://docs.spring.io/archive/v1");
        assertThat(output).contains("https://docs.spring.io/archive/v2");
    }

    @Test
    void crawlStatusShowsErrorUrls() {
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("Spring Docs")
                .url("https://docs.spring.io")
                .status(SourceStatus.CRAWLING)
                .build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));
        CrawlProgress progress = new CrawlProgress(
                uuid, CrawlProgress.Status.CRAWLING, 10, 0, 20, 2,
                List.of("https://docs.spring.io/broken", "https://docs.spring.io/404"),
                List.of(),
                Instant.now().minusSeconds(60));
        given(progressTracker.getProgress(uuid)).willReturn(Optional.of(progress));

        String output = mcpToolService.crawlStatus(uuid.toString());

        assertThat(output).contains("Error URLs");
        assertThat(output).contains("https://docs.spring.io/broken");
        assertThat(output).contains("https://docs.spring.io/404");
    }

    @Test
    void crawlStatusWithUnknownSourceReturnsError() {
        UUID uuid = UUID.randomUUID();
        given(sourceRepository.findById(uuid)).willReturn(Optional.empty());

        String output = mcpToolService.crawlStatus(uuid.toString());

        assertThat(output).startsWith("Error:");
        assertThat(output).contains("not found");
    }

    @Test
    void crawlStatusWithInvalidIdReturnsError() {
        String output = mcpToolService.crawlStatus("not-a-uuid");

        assertThat(output).startsWith("Error:");
        assertThat(output).contains("Invalid source ID");
    }

    // --- recrawlSource ---

    @Test
    void recrawlSourceTriggersIncrementalByDefault() {
        suppressAsyncDispatch();
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("Spring Docs")
                .url("https://docs.spring.io")
                .status(SourceStatus.INDEXED)
                .build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        String output = mcpToolService.recrawlSource(uuid.toString(), null, null, null, null, null, null, null);

        assertThat(output).contains("incremental");
        assertThat(output).contains("Spring Docs");
        verify(ingestionService, never()).clearIngestionState(any());
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).save(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getStatus()).isEqualTo(SourceStatus.UPDATING);
        verify(mcpToolService).dispatchCrawl(eq(uuid), eq("https://docs.spring.io"), any(CrawlScope.class), eq(false));
    }

    @Test
    void recrawlSourceWithFullFlagClearsStateFirst() {
        suppressAsyncDispatch();
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("Spring Docs")
                .url("https://docs.spring.io")
                .status(SourceStatus.INDEXED)
                .build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        String output = mcpToolService.recrawlSource(uuid.toString(), true, null, null, null, null, null, null);

        assertThat(output).contains("full");
        verify(ingestionService).clearIngestionState(uuid);
        verify(mcpToolService).dispatchCrawl(eq(uuid), eq("https://docs.spring.io"), any(CrawlScope.class), eq(true));
    }

    @Test
    void recrawlSourceWithScopeOverridesUsesOverrides() {
        suppressAsyncDispatch();
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("Spring Docs")
                .url("https://docs.spring.io")
                .status(SourceStatus.INDEXED)
                .allowPatterns("/docs/**")
                .maxPages(500)
                .build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        String output = mcpToolService.recrawlSource(
                uuid.toString(), null, "/api/**", "/old/**", 5, 100, null, null);

        assertThat(output).contains("incremental");
        assertThat(output).contains("Spring Docs");
        // Source entity scope should NOT be modified (overrides are one-time)
        assertThat(source.getAllowPatterns()).isEqualTo("/docs/**");
        assertThat(source.getMaxPages()).isEqualTo(500);
    }

    @Test
    void recrawlSourceRejectsActivelyRunningCrawl() {
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("Spring Docs")
                .status(SourceStatus.CRAWLING)
                .build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));

        String output = mcpToolService.recrawlSource(uuid.toString(), null, null, null, null, null, null, null);

        assertThat(output).startsWith("Error:");
        assertThat(output).contains("already being crawled");
    }

    @Test
    void recrawlSourceRejectsUpdatingSource() {
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("Spring Docs")
                .status(SourceStatus.UPDATING)
                .build();
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));

        String output = mcpToolService.recrawlSource(uuid.toString(), null, null, null, null, null, null, null);

        assertThat(output).startsWith("Error:");
        assertThat(output).contains("already being crawled");
    }

    @Test
    void recrawlSourceWithUnknownSourceReturnsError() {
        UUID uuid = UUID.randomUUID();
        given(sourceRepository.findById(uuid)).willReturn(Optional.empty());

        String output = mcpToolService.recrawlSource(uuid.toString(), null, null, null, null, null, null, null);

        assertThat(output).startsWith("Error:");
        assertThat(output).contains("not found");
    }

    @Test
    void recrawlSourceWithInvalidIdReturnsError() {
        String output = mcpToolService.recrawlSource("not-a-uuid", null, null, null, null, null, null, null);

        assertThat(output).startsWith("Error:");
        assertThat(output).contains("Invalid source ID");
    }

    // --- searchDocs filter parameters ---

    @Test
    void searchDocsPassesFilterParamsToSearchRequest() {
        given(searchService.search(searchRequestCaptor.capture())).willReturn(
                List.of(new SearchResult("content", 0.9, "https://docs.example.com", "Section")));
        given(truncator.truncate(any())).willReturn("output");

        mcpToolService.searchDocs("query", 5, "Spring Docs", "API Reference", "React 19", "PROSE", null, null);

        var captured = searchRequestCaptor.getValue();
        assertThat(captured.source()).isEqualTo("Spring Docs");
        assertThat(captured.sectionPath()).isEqualTo("API Reference");
        assertThat(captured.version()).isEqualTo("React 19");
        assertThat(captured.contentType()).isEqualTo("PROSE");
    }

    @Test
    void searchDocsPassesMinScoreToSearchRequest() {
        given(searchService.search(searchRequestCaptor.capture())).willReturn(
                List.of(new SearchResult("content", 0.9, "https://docs.example.com", "Section")));
        given(truncator.truncate(any())).willReturn("output");

        mcpToolService.searchDocs("query", null, null, null, null, null, 0.75, null);

        assertThat(searchRequestCaptor.getValue().minScore()).isEqualTo(0.75);
    }

    @Test
    void searchDocsEmptyResultWithFiltersShowsAvailableValues() {
        given(searchService.search(any())).willReturn(Collections.emptyList());
        given(documentChunkRepository.findDistinctVersions()).willReturn(List.of("3.5", "React 19"));
        given(documentChunkRepository.findDistinctSourceNames()).willReturn(List.of("Spring Docs", "React Docs"));

        String output = mcpToolService.searchDocs("query", null, null, null, "React 19", null, null, null);

        assertThat(output).contains("No results for query");
        assertThat(output).contains("version='React 19'");
        assertThat(output).contains("3.5");
        assertThat(output).contains("React 19");
        assertThat(output).contains("Spring Docs");
        assertThat(output).contains("React Docs");
    }

    @Test
    void searchDocsEmptyResultWithoutFiltersShowsPlainMessage() {
        given(searchService.search(any())).willReturn(Collections.emptyList());

        String output = mcpToolService.searchDocs("query", null, null, null, null, null, null, null);

        assertThat(output).isEqualTo("No results found for query: query");
    }

    // --- addSource version ---

    @Test
    void addSourceSetsVersionOnEntity() {
        suppressAsyncDispatch();
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        mcpToolService.addSource("https://docs.spring.io", "Spring Docs",
                null, null, null, null, null, "React 19");

        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).save(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getVersion()).isEqualTo("React 19");
    }

    @Test
    void addSourceWithNullVersionLeavesVersionNull() {
        suppressAsyncDispatch();
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        mcpToolService.addSource("https://docs.spring.io", "Spring Docs",
                null, null, null, null, null, null);

        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(sourceRepository).save(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().getVersion()).isNull();
    }

    // --- recrawlSource version ---

    @Test
    void recrawlSourceUpdatesVersionAndBatchUpdatesMetadata() {
        suppressAsyncDispatch();
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("React Docs")
                .url("https://react.dev")
                .status(SourceStatus.INDEXED)
                .build();
        source.setVersion("React 18");
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        mcpToolService.recrawlSource(uuid.toString(), null, null, null, null, null, "React 19", null);

        assertThat(source.getVersion()).isEqualTo("React 19");
        verify(documentChunkRepository).updateVersionMetadata("https://react.dev", "React 19");
    }

    @Test
    void recrawlSourceSameVersionSkipsBatchUpdate() {
        suppressAsyncDispatch();
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("React Docs")
                .url("https://react.dev")
                .status(SourceStatus.INDEXED)
                .build();
        source.setVersion("React 19");
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        mcpToolService.recrawlSource(uuid.toString(), null, null, null, null, null, "React 19", null);

        verify(documentChunkRepository, never()).updateVersionMetadata(anyString(), anyString());
    }

    @Test
    void recrawlSourceNullVersionSkipsBatchUpdate() {
        suppressAsyncDispatch();
        UUID uuid = UUID.randomUUID();
        Source source = new SourceBuilder()
                .name("React Docs")
                .url("https://react.dev")
                .status(SourceStatus.INDEXED)
                .build();
        source.setVersion("React 18");
        given(sourceRepository.findById(uuid)).willReturn(Optional.of(source));
        given(sourceRepository.save(any(Source.class))).willAnswer(inv -> inv.getArgument(0));

        mcpToolService.recrawlSource(uuid.toString(), null, null, null, null, null, null, null);

        verify(documentChunkRepository, never()).updateVersionMetadata(anyString(), anyString());
    }
}
