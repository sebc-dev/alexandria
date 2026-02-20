package dev.alexandria.ingestion.prechunked;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.alexandria.ingestion.chunking.ContentType;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreChunkedImporterTest {

  @Mock EmbeddingStore<TextSegment> embeddingStore;

  @Mock EmbeddingModel embeddingModel;

  @Mock Validator validator;

  @InjectMocks PreChunkedImporter importer;

  @Captor ArgumentCaptor<List<TextSegment>> segmentsCaptor;

  @Captor ArgumentCaptor<List<Embedding>> embeddingsCaptor;

  @Captor ArgumentCaptor<Filter> filterCaptor;

  // --- Happy path ---

  @Test
  void importValidChunksCreatesTextSegmentsAndStoresEmbeddings() {
    var chunk =
        new PreChunkedChunk(
            "Some documentation text",
            "https://docs.example.com/guide",
            "guide/intro",
            ContentType.PROSE,
            "2026-01-15T10:00:00Z",
            null);
    var request = new PreChunkedRequest("https://docs.example.com/guide", List.of(chunk));
    when(validator.validate(request)).thenReturn(Set.of());
    Embedding embedding = Embedding.from(new float[] {0.1f, 0.2f});
    when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(embedding)));

    int result = importer.importChunks(request);

    assertThat(result).isEqualTo(1);
    verify(embeddingStore).addAll(embeddingsCaptor.capture(), segmentsCaptor.capture());
    assertThat(embeddingsCaptor.getValue()).containsExactly(embedding);
    assertThat(segmentsCaptor.getValue()).hasSize(1);
    assertThat(segmentsCaptor.getValue().getFirst().text()).isEqualTo("Some documentation text");
  }

  // --- Validation ---

  @SuppressWarnings("unchecked")
  @Test
  void importInvalidRequestThrowsExceptionBeforeAnyStorage() {
    var request =
        new PreChunkedRequest(
            "https://example.com",
            List.of(
                new PreChunkedChunk(
                    "text",
                    "https://example.com",
                    "path",
                    ContentType.PROSE,
                    "2026-01-01T00:00:00Z",
                    null)));
    ConstraintViolation<PreChunkedRequest> violation = mock(ConstraintViolation.class);
    Path propertyPath = mock(Path.class);
    when(propertyPath.toString()).thenReturn("sourceUrl");
    when(violation.getPropertyPath()).thenReturn(propertyPath);
    when(violation.getMessage()).thenReturn("must not be blank");
    when(validator.validate(request)).thenReturn(Set.of(violation));

    assertThatThrownBy(() -> importer.importChunks(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Validation failed")
        .hasMessageContaining("sourceUrl: must not be blank");

    verify(embeddingModel, never()).embedAll(any());
    verify(embeddingStore, never()).addAll(any(), any());
    verify(embeddingStore, never()).removeAll(any(Filter.class));
  }

  // --- Replacement semantics ---

  @Test
  void importComputesEmbeddingsBeforeDeletingExistingChunks() {
    var chunk =
        new PreChunkedChunk(
            "New content",
            "https://docs.example.com/api",
            "api",
            ContentType.PROSE,
            "2026-02-01T08:00:00Z",
            null);
    var request = new PreChunkedRequest("https://docs.example.com/api", List.of(chunk));
    when(validator.validate(request)).thenReturn(Set.of());
    Embedding embedding = Embedding.from(new float[] {0.5f});
    when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(embedding)));

    importer.importChunks(request);

    InOrder inOrder = inOrder(embeddingModel, embeddingStore);
    inOrder.verify(embeddingModel).embedAll(any());
    inOrder.verify(embeddingStore).removeAll(any(Filter.class));
    inOrder.verify(embeddingStore).addAll(any(), any());
  }

  @Test
  void importRemovesExistingChunksForSourceUrlBeforeAddingNew() {
    var chunk =
        new PreChunkedChunk(
            "Updated content",
            "https://docs.example.com/ref",
            "ref",
            ContentType.PROSE,
            "2026-03-01T12:00:00Z",
            null);
    var request = new PreChunkedRequest("https://docs.example.com/ref", List.of(chunk));
    when(validator.validate(request)).thenReturn(Set.of());
    when(embeddingModel.embedAll(any()))
        .thenReturn(Response.from(List.of(Embedding.from(new float[] {0.3f}))));

    importer.importChunks(request);

    verify(embeddingStore).removeAll(any(Filter.class));
    verify(embeddingStore).addAll(any(), any());
  }

  // --- Edge cases ---

  @Test
  void importMultipleChunksStoresAllEmbeddings() {
    var chunk1 =
        new PreChunkedChunk(
            "First chunk",
            "https://example.com/multi",
            "intro",
            ContentType.PROSE,
            "2026-01-01T00:00:00Z",
            null);
    var chunk2 =
        new PreChunkedChunk(
            "System.out.println();",
            "https://example.com/multi",
            "intro/code",
            ContentType.CODE,
            "2026-01-01T00:00:00Z",
            "java");
    var request = new PreChunkedRequest("https://example.com/multi", List.of(chunk1, chunk2));
    when(validator.validate(request)).thenReturn(Set.of());
    var emb1 = Embedding.from(new float[] {0.1f});
    var emb2 = Embedding.from(new float[] {0.2f});
    when(embeddingModel.embedAll(any())).thenReturn(Response.from(List.of(emb1, emb2)));

    int result = importer.importChunks(request);

    assertThat(result).isEqualTo(2);
    verify(embeddingStore).addAll(embeddingsCaptor.capture(), segmentsCaptor.capture());
    assertThat(embeddingsCaptor.getValue()).containsExactly(emb1, emb2);
    assertThat(segmentsCaptor.getValue()).hasSize(2);
  }

  // --- Metadata mapping ---

  @Test
  void importMapsChunkFieldsToTextSegmentMetadata() {
    var chunk =
        new PreChunkedChunk(
            "Code snippet content",
            "https://docs.spring.io/boot",
            "boot/config",
            ContentType.CODE,
            "2026-02-10T14:30:00Z",
            "yaml");
    var request = new PreChunkedRequest("https://docs.spring.io/boot", List.of(chunk));
    when(validator.validate(request)).thenReturn(Set.of());
    when(embeddingModel.embedAll(any()))
        .thenReturn(Response.from(List.of(Embedding.from(new float[] {0.7f}))));

    importer.importChunks(request);

    verify(embeddingStore).addAll(any(), segmentsCaptor.capture());
    TextSegment segment = segmentsCaptor.getValue().getFirst();
    assertThat(segment.text()).isEqualTo("Code snippet content");
    assertThat(segment.metadata().getString("source_url")).isEqualTo("https://docs.spring.io/boot");
    assertThat(segment.metadata().getString("section_path")).isEqualTo("boot/config");
    assertThat(segment.metadata().getString("content_type")).isEqualTo("code");
    assertThat(segment.metadata().getString("last_updated")).isEqualTo("2026-02-10T14:30:00Z");
    assertThat(segment.metadata().getString("language")).isEqualTo("yaml");
  }

  @Test
  void importProseChunkHasNoLanguageMetadata() {
    var chunk =
        new PreChunkedChunk(
            "Plain prose text",
            "https://example.com/doc",
            "doc",
            ContentType.PROSE,
            "2026-01-01T00:00:00Z",
            null);
    var request = new PreChunkedRequest("https://example.com/doc", List.of(chunk));
    when(validator.validate(request)).thenReturn(Set.of());
    when(embeddingModel.embedAll(any()))
        .thenReturn(Response.from(List.of(Embedding.from(new float[] {0.4f}))));

    importer.importChunks(request);

    verify(embeddingStore).addAll(any(), segmentsCaptor.capture());
    TextSegment segment = segmentsCaptor.getValue().getFirst();
    assertThat(segment.metadata().getString("language")).isNull();
  }
}
