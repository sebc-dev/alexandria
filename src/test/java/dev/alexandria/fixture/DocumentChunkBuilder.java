package dev.alexandria.fixture;

import dev.alexandria.document.DocumentChunk;

import java.util.UUID;

/**
 * Lightweight test builder for the {@link DocumentChunk} JPA entity.
 * Provides sensible defaults so tests only override what they care about.
 *
 * <pre>{@code
 * DocumentChunk chunk = new DocumentChunkBuilder().text("Some content").build();
 * }</pre>
 */
public final class DocumentChunkBuilder {

    private String text = "Sample documentation text for testing.";
    private String metadata = "{\"source_url\":\"https://docs.example.com\",\"section_path\":\"getting-started\"}";
    private UUID sourceId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public DocumentChunkBuilder text(String text) {
        this.text = text;
        return this;
    }

    public DocumentChunkBuilder metadata(String metadata) {
        this.metadata = metadata;
        return this;
    }

    public DocumentChunkBuilder sourceId(UUID sourceId) {
        this.sourceId = sourceId;
        return this;
    }

    public DocumentChunk build() {
        return new DocumentChunk(text, metadata, sourceId);
    }
}
