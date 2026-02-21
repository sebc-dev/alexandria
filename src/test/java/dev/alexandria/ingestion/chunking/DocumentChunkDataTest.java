package dev.alexandria.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

class DocumentChunkDataTest {

  private static final String TEXT = "Some content";
  private static final String SOURCE_URL = "https://docs.example.com/guide";
  private static final String SECTION_PATH = "guide/setup";
  private static final String LAST_UPDATED = "2026-02-18T10:00:00Z";

  @Test
  void toMetadataIncludesVersionWhenPresent() {
    var chunk =
        new DocumentChunkData(
            TEXT, SOURCE_URL, SECTION_PATH, ContentType.PROSE, LAST_UPDATED, null, "3.5", null);

    Metadata metadata = chunk.toMetadata();

    assertThat(metadata.getString("version")).isEqualTo("3.5");
  }

  @Test
  void toMetadataExcludesVersionWhenNull() {
    var chunk =
        new DocumentChunkData(
            TEXT, SOURCE_URL, SECTION_PATH, ContentType.PROSE, LAST_UPDATED, null, null, null);

    Metadata metadata = chunk.toMetadata();

    assertThat(metadata.containsKey("version")).isFalse();
  }

  @Test
  void toMetadataIncludesSourceNameWhenPresent() {
    var chunk =
        new DocumentChunkData(
            TEXT,
            SOURCE_URL,
            SECTION_PATH,
            ContentType.PROSE,
            LAST_UPDATED,
            null,
            null,
            "Spring Boot Docs");

    Metadata metadata = chunk.toMetadata();

    assertThat(metadata.getString("source_name")).isEqualTo("Spring Boot Docs");
  }

  @Test
  void toMetadataExcludesSourceNameWhenNull() {
    var chunk =
        new DocumentChunkData(
            TEXT, SOURCE_URL, SECTION_PATH, ContentType.PROSE, LAST_UPDATED, null, null, null);

    Metadata metadata = chunk.toMetadata();

    assertThat(metadata.containsKey("source_name")).isFalse();
  }

  @Test
  void toMetadataIncludesBothVersionAndSourceNameWhenPresent() {
    var chunk =
        new DocumentChunkData(
            TEXT,
            SOURCE_URL,
            SECTION_PATH,
            ContentType.CODE,
            LAST_UPDATED,
            "java",
            "4.0",
            "Spring Framework");

    Metadata metadata = chunk.toMetadata();

    assertThat(metadata.getString("version")).isEqualTo("4.0");
    assertThat(metadata.getString("source_name")).isEqualTo("Spring Framework");
    assertThat(metadata.getString("language")).isEqualTo("java");
    assertThat(metadata.getString("content_type")).isEqualTo("code");
  }

  @Test
  void toMetadataAlwaysIncludesBaseFields() {
    var chunk =
        new DocumentChunkData(
            TEXT, SOURCE_URL, SECTION_PATH, ContentType.PROSE, LAST_UPDATED, null, null, null);

    Metadata metadata = chunk.toMetadata();

    assertThat(metadata.getString("source_url")).isEqualTo(SOURCE_URL);
    assertThat(metadata.getString("section_path")).isEqualTo(SECTION_PATH);
    assertThat(metadata.getString("content_type")).isEqualTo("prose");
    assertThat(metadata.getString("last_updated")).isEqualTo(LAST_UPDATED);
  }
}
