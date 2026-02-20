package dev.alexandria.ingestion;

import dev.alexandria.crawl.ContentHasher;
import dev.alexandria.crawl.CrawlResult;
import dev.alexandria.ingestion.chunking.ContentType;
import dev.alexandria.ingestion.chunking.DocumentChunkData;
import dev.alexandria.ingestion.chunking.MarkdownChunker;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    MarkdownChunker chunker;

    @Mock
    EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    EmbeddingModel embeddingModel;

    @Mock
    IngestionStateRepository ingestionStateRepository;

    @InjectMocks
    IngestionService ingestionService;

    @Captor
    ArgumentCaptor<List<TextSegment>> segmentsCaptor;

    @Captor
    ArgumentCaptor<List<Embedding>> embeddingsCaptor;

    // --- Happy path ---

    @Test
    void ingestChunksAndStoresEmbeddingsForAllPages() {
        var page = new CrawlResult("https://docs.example.com/guide", "# Guide\nSome content", List.of(), true, null);
        var chunkData = new DocumentChunkData("Some content", "https://docs.example.com/guide",
                "guide", ContentType.PROSE, "2026-01-01T00:00:00Z", null);
        when(chunker.chunk(eq("# Guide\nSome content"), eq("https://docs.example.com/guide"), anyString()))
                .thenReturn(List.of(chunkData));
        Embedding embedding = Embedding.from(new float[]{0.1f, 0.2f});
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(embedding)));

        int result = ingestionService.ingest(List.of(page));

        assertThat(result).isEqualTo(1);
        verify(embeddingStore).addAll(embeddingsCaptor.capture(), segmentsCaptor.capture());
        assertThat(embeddingsCaptor.getValue()).containsExactly(embedding);
        assertThat(segmentsCaptor.getValue()).hasSize(1);
        assertThat(segmentsCaptor.getValue().getFirst().text()).isEqualTo("Some content");
    }

    // --- Pipeline orchestration ---

    @Test
    void ingestPassesSourceUrlAndMarkdownToChunker() {
        var page = new CrawlResult("https://example.com/api", "# API Reference", List.of(), true, null);
        when(chunker.chunk(anyString(), anyString(), anyString())).thenReturn(List.of());

        ingestionService.ingest(List.of(page));

        verify(chunker).chunk(eq("# API Reference"), eq("https://example.com/api"), anyString());
    }

    @Test
    void ingestCreatesTextSegmentsWithCorrectMetadata() {
        var page = new CrawlResult("https://docs.spring.io/config", "# Config\nSetup instructions", List.of(), true, null);
        var chunkData = new DocumentChunkData("Setup instructions", "https://docs.spring.io/config",
                "config/setup", ContentType.PROSE, "2026-02-15T10:30:00Z", null);
        when(chunker.chunk(anyString(), anyString(), anyString())).thenReturn(List.of(chunkData));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(Embedding.from(new float[]{0.5f}))));

        ingestionService.ingest(List.of(page));

        verify(embeddingStore).addAll(any(), segmentsCaptor.capture());
        TextSegment segment = segmentsCaptor.getValue().getFirst();
        assertThat(segment.metadata().getString("source_url")).isEqualTo("https://docs.spring.io/config");
        assertThat(segment.metadata().getString("section_path")).isEqualTo("config/setup");
        assertThat(segment.metadata().getString("content_type")).isEqualTo("prose");
        assertThat(segment.metadata().getString("last_updated")).isEqualTo("2026-02-15T10:30:00Z");
    }

    @Test
    void ingestCreatesTextSegmentsWithLanguageForCodeChunks() {
        var page = new CrawlResult("https://docs.example.com/code", "```java\nSystem.out.println();\n```", List.of(), true, null);
        var codeChunk = new DocumentChunkData("System.out.println();", "https://docs.example.com/code",
                "code", ContentType.CODE, "2026-01-01T00:00:00Z", "java");
        when(chunker.chunk(anyString(), anyString(), anyString())).thenReturn(List.of(codeChunk));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(Embedding.from(new float[]{0.3f}))));

        ingestionService.ingest(List.of(page));

        verify(embeddingStore).addAll(any(), segmentsCaptor.capture());
        TextSegment segment = segmentsCaptor.getValue().getFirst();
        assertThat(segment.metadata().getString("content_type")).isEqualTo("code");
        assertThat(segment.metadata().getString("language")).isEqualTo("java");
    }

    @Test
    void ingestAccumulatesChunkCountAcrossMultiplePages() {
        var page1 = new CrawlResult("https://example.com/a", "# Page A", List.of(), true, null);
        var page2 = new CrawlResult("https://example.com/b", "# Page B", List.of(), true, null);
        var chunk1 = new DocumentChunkData("text A", "https://example.com/a", "a", ContentType.PROSE, "2026-01-01T00:00:00Z", null);
        var chunk2a = new DocumentChunkData("text B1", "https://example.com/b", "b", ContentType.PROSE, "2026-01-01T00:00:00Z", null);
        var chunk2b = new DocumentChunkData("text B2", "https://example.com/b", "b/sub", ContentType.PROSE, "2026-01-01T00:00:00Z", null);
        when(chunker.chunk(eq("# Page A"), anyString(), anyString())).thenReturn(List.of(chunk1));
        when(chunker.chunk(eq("# Page B"), anyString(), anyString())).thenReturn(List.of(chunk2a, chunk2b));
        when(embeddingModel.embedAll(any())).thenAnswer(invocation -> {
            List<TextSegment> segments = invocation.getArgument(0);
            List<Embedding> embeddings = segments.stream()
                    .map(s -> Embedding.from(new float[]{0.1f}))
                    .toList();
            return Response.from(embeddings);
        });

        int result = ingestionService.ingest(List.of(page1, page2));

        assertThat(result).isEqualTo(3);
    }

    // --- Edge cases ---

    @Test
    void ingestEmptyMarkdownProducesNoChunks() {
        var page = new CrawlResult("https://example.com/empty", "", List.of(), true, null);
        when(chunker.chunk(eq(""), anyString(), anyString())).thenReturn(List.of());

        int result = ingestionService.ingest(List.of(page));

        assertThat(result).isEqualTo(0);
        verify(embeddingStore, never()).addAll(any(), any());
    }

    @Test
    void ingestEmptyPageListReturnsZero() {
        int result = ingestionService.ingest(List.of());

        assertThat(result).isEqualTo(0);
    }

    // --- Single-page convenience method ---

    @Test
    void ingestPageChunksAndStoresWithProvidedTimestamp() {
        var chunkData = new DocumentChunkData("content", "https://example.com/page",
                "section", ContentType.PROSE, "2026-05-01T12:00:00Z", null);
        when(chunker.chunk("# Heading\nContent", "https://example.com/page", "2026-05-01T12:00:00Z"))
                .thenReturn(List.of(chunkData));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(Embedding.from(new float[]{0.4f}))));

        int result = ingestionService.ingestPage("# Heading\nContent", "https://example.com/page", "2026-05-01T12:00:00Z");

        assertThat(result).isEqualTo(1);
        verify(embeddingStore).addAll(any(), any());
    }

    @Test
    void ingestPageReturnsZeroWhenChunkerReturnsEmpty() {
        when(chunker.chunk("", "https://example.com/blank", "2026-01-01T00:00:00Z"))
                .thenReturn(List.of());

        int result = ingestionService.ingestPage("", "https://example.com/blank", "2026-01-01T00:00:00Z");

        assertThat(result).isEqualTo(0);
        verify(embeddingStore, never()).addAll(any(), any());
    }

    // --- Incremental ingestion ---

    @Test
    void ingestPageIncrementalSkipsUnchangedContent() {
        UUID sourceId = UUID.randomUUID();
        String url = "https://example.com/page";
        String markdown = "# Hello";
        String hash = ContentHasher.sha256(markdown);
        var existingState = new IngestionState(sourceId, "https://example.com/page", hash);
        when(ingestionStateRepository.findBySourceIdAndPageUrl(sourceId, "https://example.com/page"))
                .thenReturn(Optional.of(existingState));

        IngestionService.IngestResult result = ingestionService.ingestPageIncremental(sourceId, url, markdown);

        assertThat(result.skipped()).isTrue();
        assertThat(result.changed()).isFalse();
        assertThat(result.chunksStored()).isZero();
        verify(embeddingStore, never()).removeAll(any(Filter.class));
        verify(chunker, never()).chunk(anyString(), anyString(), anyString());
    }

    @Test
    void ingestPageIncrementalReprocessesChangedContent() {
        UUID sourceId = UUID.randomUUID();
        String url = "https://example.com/page";
        String oldHash = ContentHasher.sha256("old content");
        var existingState = new IngestionState(sourceId, "https://example.com/page", oldHash);
        when(ingestionStateRepository.findBySourceIdAndPageUrl(sourceId, "https://example.com/page"))
                .thenReturn(Optional.of(existingState));
        var chunkData = new DocumentChunkData("new content", "https://example.com/page",
                "section", ContentType.PROSE, "2026-01-01T00:00:00Z", null);
        when(chunker.chunk(eq("# New content"), eq("https://example.com/page"), anyString()))
                .thenReturn(List.of(chunkData));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(Embedding.from(new float[]{0.1f}))));

        IngestionService.IngestResult result = ingestionService.ingestPageIncremental(sourceId, url, "# New content");

        assertThat(result.skipped()).isFalse();
        assertThat(result.changed()).isTrue();
        assertThat(result.chunksStored()).isEqualTo(1);
        verify(embeddingStore).removeAll(any(Filter.class));
        verify(ingestionStateRepository).save(existingState);
    }

    @Test
    void ingestPageIncrementalCreatesNewStateForNewPage() {
        UUID sourceId = UUID.randomUUID();
        String url = "https://example.com/new-page";
        when(ingestionStateRepository.findBySourceIdAndPageUrl(sourceId, "https://example.com/new-page"))
                .thenReturn(Optional.empty());
        var chunkData = new DocumentChunkData("content", "https://example.com/new-page",
                "section", ContentType.PROSE, "2026-01-01T00:00:00Z", null);
        when(chunker.chunk(eq("# Brand new"), eq("https://example.com/new-page"), anyString()))
                .thenReturn(List.of(chunkData));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(Embedding.from(new float[]{0.2f}))));

        IngestionService.IngestResult result = ingestionService.ingestPageIncremental(sourceId, url, "# Brand new");

        assertThat(result.skipped()).isFalse();
        assertThat(result.changed()).isTrue();
        assertThat(result.chunksStored()).isEqualTo(1);
        ArgumentCaptor<IngestionState> stateCaptor = ArgumentCaptor.forClass(IngestionState.class);
        verify(ingestionStateRepository).save(stateCaptor.capture());
        assertThat(stateCaptor.getValue().getSourceId()).isEqualTo(sourceId);
        assertThat(stateCaptor.getValue().getPageUrl()).isEqualTo("https://example.com/new-page");
        assertThat(stateCaptor.getValue().getContentHash()).isEqualTo(ContentHasher.sha256("# Brand new"));
    }

    @Test
    void ingestPageIncrementalDeletesOldChunksBeforeReingesting() {
        UUID sourceId = UUID.randomUUID();
        String url = "https://example.com/page";
        when(ingestionStateRepository.findBySourceIdAndPageUrl(sourceId, "https://example.com/page"))
                .thenReturn(Optional.empty());
        var chunkData = new DocumentChunkData("content", "https://example.com/page",
                "section", ContentType.PROSE, "2026-01-01T00:00:00Z", null);
        when(chunker.chunk(eq("# Content"), eq("https://example.com/page"), anyString()))
                .thenReturn(List.of(chunkData));
        when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(Embedding.from(new float[]{0.3f}))));

        ingestionService.ingestPageIncremental(sourceId, url, "# Content");

        InOrder inOrder = inOrder(embeddingStore);
        inOrder.verify(embeddingStore).removeAll(any(Filter.class));
        inOrder.verify(embeddingStore).addAll(any(), any());
    }

    @Test
    void clearIngestionStateDelegatesDeleteToRepository() {
        UUID sourceId = UUID.randomUUID();

        ingestionService.clearIngestionState(sourceId);

        verify(ingestionStateRepository).deleteAllBySourceId(sourceId);
    }
}
