package dev.alexandria.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ChunkMetadataTest {

  @Test
  void shouldCreateWithAllFields() {
    var now = Instant.now();
    var fileModified = Instant.now().minusSeconds(3600);

    var metadata =
        new ChunkMetadata(
            "file:///docs/readme.md",
            "abc123hash",
            0,
            "Docs > Introduction",
            "README",
            "contenthash456",
            now,
            "markdown",
            1024L,
            fileModified);

    assertThat(metadata.sourceUri()).isEqualTo("file:///docs/readme.md");
    assertThat(metadata.documentHash()).isEqualTo("abc123hash");
    assertThat(metadata.chunkIndex()).isZero();
    assertThat(metadata.breadcrumbs()).isEqualTo("Docs > Introduction");
    assertThat(metadata.documentTitle()).isEqualTo("README");
    assertThat(metadata.contentHash()).isEqualTo("contenthash456");
    assertThat(metadata.createdAt()).isEqualTo(now);
    assertThat(metadata.documentType()).isEqualTo("markdown");
    assertThat(metadata.fileSize()).isEqualTo(1024L);
    assertThat(metadata.fileModifiedAt()).isEqualTo(fileModified);
  }

  @Test
  void toLogicalUriShouldCombineSourceAndIndex() {
    var metadata =
        new ChunkMetadata(
            "file:///docs/guide.md",
            "hash",
            5,
            "breadcrumbs",
            "Guide",
            "content",
            Instant.now(),
            "markdown",
            512L,
            Instant.now());

    assertThat(metadata.toLogicalUri()).isEqualTo("file:///docs/guide.md#chunk-5");
  }

  @Test
  void computeHashShouldBeConsistentForSameContent() {
    String content = "This is test content for hashing";

    String hash1 = ChunkMetadata.computeHash(content);
    String hash2 = ChunkMetadata.computeHash(content);

    assertThat(hash1).isEqualTo(hash2);
    assertThat(hash1).hasSize(64); // SHA-256 produces 64 hex characters
  }

  @Test
  void computeHashShouldNormalizeUnicode() {
    // U+00E9 (é as single code point) vs U+0065 U+0301 (e + combining acute accent)
    String eAccentSingle = "\u00e9"; // é
    String eAccentCombined = "e\u0301"; // e + ́

    String hash1 = ChunkMetadata.computeHash(eAccentSingle);
    String hash2 = ChunkMetadata.computeHash(eAccentCombined);

    assertThat(hash1)
        .as("NFKC normalization should make equivalent Unicode sequences produce same hash")
        .isEqualTo(hash2);
  }

  @Test
  void computeHashShouldDifferForDifferentContent() {
    String content1 = "First content";
    String content2 = "Second content";

    String hash1 = ChunkMetadata.computeHash(content1);
    String hash2 = ChunkMetadata.computeHash(content2);

    assertThat(hash1).isNotEqualTo(hash2);
  }

  @Test
  void computeHashShouldRejectNullContent() {
    assertThatThrownBy(() -> ChunkMetadata.computeHash(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Content cannot be null");
  }

  @Test
  void shouldRejectNullSourceUri() {
    assertThatThrownBy(
            () ->
                new ChunkMetadata(
                    null,
                    "hash",
                    0,
                    "breadcrumbs",
                    "title",
                    "content",
                    Instant.now(),
                    "md",
                    100L,
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("sourceUri cannot be null or blank");
  }

  @Test
  void shouldRejectNegativeChunkIndex() {
    assertThatThrownBy(
            () ->
                new ChunkMetadata(
                    "file:///test.md",
                    "hash",
                    -1,
                    "breadcrumbs",
                    "title",
                    "content",
                    Instant.now(),
                    "md",
                    100L,
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("chunkIndex must be >= 0");
  }

  @Test
  void shouldRejectNegativeFileSize() {
    assertThatThrownBy(
            () ->
                new ChunkMetadata(
                    "file:///test.md",
                    "hash",
                    0,
                    "breadcrumbs",
                    "title",
                    "content",
                    Instant.now(),
                    "md",
                    -1L,
                    Instant.now()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("fileSize must be >= 0");
  }
}
