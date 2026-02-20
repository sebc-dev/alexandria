package dev.alexandria.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.alexandria.document.DocumentChunkRepository;
import dev.alexandria.ingestion.chunking.ContentType;
import dev.alexandria.ingestion.chunking.DocumentChunkData;
import dev.alexandria.ingestion.chunking.MarkdownChunker;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

  @Mock MarkdownChunker chunker;

  @Mock EmbeddingStore<TextSegment> embeddingStore;

  @Mock EmbeddingModel embeddingModel;

  @Mock IngestionStateRepository ingestionStateRepository;

  @Mock DocumentChunkRepository documentChunkRepository;

  @InjectMocks IngestionService ingestionService;

  @Captor ArgumentCaptor<List<TextSegment>> segmentsCaptor;

  @Captor ArgumentCaptor<List<Embedding>> embeddingsCaptor;

  // --- Single-page ingestion ---

  @Test
  void ingestPageChunksAndStoresWithProvidedTimestamp() {
    var chunkData =
        new DocumentChunkData(
            "content",
            "https://example.com/page",
            "section",
            ContentType.PROSE,
            "2026-05-01T12:00:00Z",
            null,
            null,
            null);
    when(chunker.chunk("# Heading\nContent", "https://example.com/page", "2026-05-01T12:00:00Z"))
        .thenReturn(List.of(chunkData));
    when(embeddingModel.embedAll(any()))
        .thenReturn(Response.from(List.of(Embedding.from(new float[] {0.4f}))));

    int result =
        ingestionService.ingestPage(
            "# Heading\nContent", "https://example.com/page", "2026-05-01T12:00:00Z");

    assertThat(result).isEqualTo(1);
    verify(embeddingStore).addAll(any(), any());
  }

  @Test
  void ingestPageReturnsZeroWhenChunkerReturnsEmpty() {
    when(chunker.chunk("", "https://example.com/blank", "2026-01-01T00:00:00Z"))
        .thenReturn(List.of());

    int result =
        ingestionService.ingestPage("", "https://example.com/blank", "2026-01-01T00:00:00Z");

    assertThat(result).isEqualTo(0);
    verify(embeddingStore, never()).addAll(any(), any());
  }

  @Test
  void ingestPageCreatesTextSegmentsWithCorrectMetadata() {
    var chunkData =
        new DocumentChunkData(
            "Setup instructions",
            "https://docs.spring.io/config",
            "config/setup",
            ContentType.PROSE,
            "2026-02-15T10:30:00Z",
            null,
            null,
            null);
    when(chunker.chunk(anyString(), anyString(), anyString())).thenReturn(List.of(chunkData));
    when(embeddingModel.embedAll(any()))
        .thenReturn(Response.from(List.of(Embedding.from(new float[] {0.5f}))));

    ingestionService.ingestPage(
        "# Config\nSetup instructions", "https://docs.spring.io/config", "2026-02-15T10:30:00Z");

    verify(embeddingStore).addAll(any(), segmentsCaptor.capture());
    TextSegment segment = segmentsCaptor.getValue().getFirst();
    assertThat(segment.metadata().getString("source_url"))
        .isEqualTo("https://docs.spring.io/config");
    assertThat(segment.metadata().getString("section_path")).isEqualTo("config/setup");
    assertThat(segment.metadata().getString("content_type")).isEqualTo("prose");
    assertThat(segment.metadata().getString("last_updated")).isEqualTo("2026-02-15T10:30:00Z");
  }

  @Test
  void ingestPageCreatesTextSegmentsWithLanguageForCodeChunks() {
    var codeChunk =
        new DocumentChunkData(
            "System.out.println();",
            "https://docs.example.com/code",
            "code",
            ContentType.CODE,
            "2026-01-01T00:00:00Z",
            "java",
            null,
            null);
    when(chunker.chunk(anyString(), anyString(), anyString())).thenReturn(List.of(codeChunk));
    when(embeddingModel.embedAll(any()))
        .thenReturn(Response.from(List.of(Embedding.from(new float[] {0.3f}))));

    ingestionService.ingestPage(
        "```java\nSystem.out.println();\n```",
        "https://docs.example.com/code",
        "2026-01-01T00:00:00Z");

    verify(embeddingStore).addAll(any(), segmentsCaptor.capture());
    TextSegment segment = segmentsCaptor.getValue().getFirst();
    assertThat(segment.metadata().getString("content_type")).isEqualTo("code");
    assertThat(segment.metadata().getString("language")).isEqualTo("java");
  }

  @Test
  void ingestPageEmbedsBatchesOfChunks() {
    var chunk1 =
        new DocumentChunkData(
            "text A",
            "https://example.com/a",
            "a",
            ContentType.PROSE,
            "2026-01-01T00:00:00Z",
            null,
            null,
            null);
    var chunk2 =
        new DocumentChunkData(
            "text B",
            "https://example.com/a",
            "b",
            ContentType.PROSE,
            "2026-01-01T00:00:00Z",
            null,
            null,
            null);
    when(chunker.chunk(anyString(), anyString(), anyString())).thenReturn(List.of(chunk1, chunk2));
    when(embeddingModel.embedAll(any()))
        .thenAnswer(
            invocation -> {
              List<TextSegment> segments = invocation.getArgument(0);
              List<Embedding> embeddings =
                  segments.stream().map(s -> Embedding.from(new float[] {0.1f})).toList();
              return Response.from(embeddings);
            });

    int result =
        ingestionService.ingestPage(
            "# Page A\ntext A\n## Section B\ntext B",
            "https://example.com/a",
            "2026-01-01T00:00:00Z");

    assertThat(result).isEqualTo(2);
    verify(embeddingStore).addAll(embeddingsCaptor.capture(), segmentsCaptor.capture());
    assertThat(embeddingsCaptor.getValue()).hasSize(2);
    assertThat(segmentsCaptor.getValue()).hasSize(2);
  }

  // --- Delete chunks for URL ---

  @Test
  void deleteChunksForUrlDelegatesToEmbeddingStore() {
    ingestionService.deleteChunksForUrl("https://example.com/page");

    verify(embeddingStore).removeAll(any(Filter.class));
  }

  // --- Version/sourceName passthrough ---

  @Test
  void ingestPageWithVersionAndSourceNameEnrichesChunkMetadata() {
    var chunkData =
        new DocumentChunkData(
            "content",
            "https://example.com/page",
            "section",
            ContentType.PROSE,
            "2026-01-01T00:00:00Z",
            null,
            null,
            null);
    when(chunker.chunk(anyString(), anyString(), anyString())).thenReturn(List.of(chunkData));
    when(embeddingModel.embedAll(any()))
        .thenReturn(Response.from(List.of(Embedding.from(new float[] {0.5f}))));

    ingestionService.ingestPage(
        "# Page\nContent",
        "https://example.com/page",
        "2026-01-01T00:00:00Z",
        "3.5",
        "Spring Docs");

    verify(embeddingStore).addAll(any(), segmentsCaptor.capture());
    TextSegment segment = segmentsCaptor.getValue().getFirst();
    assertThat(segment.metadata().getString("version")).isEqualTo("3.5");
    assertThat(segment.metadata().getString("source_name")).isEqualTo("Spring Docs");
  }

  @Test
  void ingestPageThreeParamDelegatesWithNullVersionAndSourceName() {
    var chunkData =
        new DocumentChunkData(
            "content",
            "https://example.com/page",
            "section",
            ContentType.PROSE,
            "2026-01-01T00:00:00Z",
            null,
            null,
            null);
    when(chunker.chunk(anyString(), anyString(), anyString())).thenReturn(List.of(chunkData));
    when(embeddingModel.embedAll(any()))
        .thenReturn(Response.from(List.of(Embedding.from(new float[] {0.5f}))));

    ingestionService.ingestPage(
        "# Page\nContent", "https://example.com/page", "2026-01-01T00:00:00Z");

    verify(embeddingStore).addAll(any(), segmentsCaptor.capture());
    TextSegment segment = segmentsCaptor.getValue().getFirst();
    assertThat(segment.metadata().containsKey("version")).isFalse();
    assertThat(segment.metadata().containsKey("source_name")).isFalse();
  }

  // --- Clear ingestion state ---

  @Test
  void clearIngestionStateDelegatesDeleteToRepository() {
    UUID sourceId = UUID.randomUUID();

    ingestionService.clearIngestionState(sourceId);

    verify(ingestionStateRepository).deleteAllBySourceId(sourceId);
  }

  // --- source_id FK population ---

  @Test
  void storeChunksCallsUpdateSourceIdBatchWhenSourceIdProvided() {
    UUID sourceId = UUID.randomUUID();
    var chunkData =
        new DocumentChunkData(
            "content",
            "https://example.com/page",
            "section",
            ContentType.PROSE,
            "2026-01-01T00:00:00Z",
            null,
            null,
            null);
    when(chunker.chunk(anyString(), anyString(), anyString())).thenReturn(List.of(chunkData));
    when(embeddingModel.embedAll(any()))
        .thenReturn(Response.from(List.of(Embedding.from(new float[] {0.5f}))));
    when(embeddingStore.addAll(any(), any())).thenReturn(List.of("id1", "id2"));

    ingestionService.ingestPage(
        sourceId,
        "# Page\nContent",
        "https://example.com/page",
        "2026-01-01T00:00:00Z",
        null,
        null);

    verify(documentChunkRepository)
        .updateSourceIdBatch(eq(sourceId), eq(new String[] {"id1", "id2"}));
  }

  @Test
  void storeChunksSkipsSourceIdUpdateWhenSourceIdNull() {
    var chunkData =
        new DocumentChunkData(
            "content",
            "https://example.com/page",
            "section",
            ContentType.PROSE,
            "2026-01-01T00:00:00Z",
            null,
            null,
            null);
    when(chunker.chunk(anyString(), anyString(), anyString())).thenReturn(List.of(chunkData));
    when(embeddingModel.embedAll(any()))
        .thenReturn(Response.from(List.of(Embedding.from(new float[] {0.5f}))));

    ingestionService.ingestPage(
        "# Page\nContent", "https://example.com/page", "2026-01-01T00:00:00Z");

    verify(documentChunkRepository, never()).updateSourceIdBatch(any(), any());
  }
}
