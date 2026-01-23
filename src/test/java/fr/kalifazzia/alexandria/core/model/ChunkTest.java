package fr.kalifazzia.alexandria.core.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Chunk record factory methods.
 * Tests null handling and type assignment in static factories.
 */
class ChunkTest {

    private static final UUID DOCUMENT_ID = UUID.randomUUID();
    private static final UUID PARENT_CHUNK_ID = UUID.randomUUID();
    private static final String CONTENT = "Test content";
    private static final int POSITION = 0;

    // =====================
    // create() factory tests
    // =====================

    @Test
    void create_setsIdToNull() {
        Chunk chunk = Chunk.create(DOCUMENT_ID, PARENT_CHUNK_ID, ChunkType.CHILD, CONTENT, POSITION);

        assertThat(chunk.id()).isNull();
    }

    @Test
    void create_setsCreatedAtToNull() {
        Chunk chunk = Chunk.create(DOCUMENT_ID, PARENT_CHUNK_ID, ChunkType.CHILD, CONTENT, POSITION);

        assertThat(chunk.createdAt()).isNull();
    }

    @Test
    void create_passesDocumentIdCorrectly() {
        Chunk chunk = Chunk.create(DOCUMENT_ID, PARENT_CHUNK_ID, ChunkType.CHILD, CONTENT, POSITION);

        assertThat(chunk.documentId()).isEqualTo(DOCUMENT_ID);
    }

    @Test
    void create_passesParentChunkIdCorrectly() {
        Chunk chunk = Chunk.create(DOCUMENT_ID, PARENT_CHUNK_ID, ChunkType.CHILD, CONTENT, POSITION);

        assertThat(chunk.parentChunkId()).isEqualTo(PARENT_CHUNK_ID);
    }

    @Test
    void create_passesTypeCorrectly() {
        Chunk chunk = Chunk.create(DOCUMENT_ID, PARENT_CHUNK_ID, ChunkType.CHILD, CONTENT, POSITION);

        assertThat(chunk.type()).isEqualTo(ChunkType.CHILD);
    }

    @Test
    void create_passesContentCorrectly() {
        Chunk chunk = Chunk.create(DOCUMENT_ID, PARENT_CHUNK_ID, ChunkType.CHILD, CONTENT, POSITION);

        assertThat(chunk.content()).isEqualTo(CONTENT);
    }

    @Test
    void create_passesPositionCorrectly() {
        Chunk chunk = Chunk.create(DOCUMENT_ID, PARENT_CHUNK_ID, ChunkType.CHILD, CONTENT, POSITION);

        assertThat(chunk.position()).isEqualTo(POSITION);
    }

    // =====================
    // createParent() factory tests
    // =====================

    @Test
    void createParent_setsTypeToParent() {
        Chunk chunk = Chunk.createParent(DOCUMENT_ID, CONTENT, POSITION);

        assertThat(chunk.type()).isEqualTo(ChunkType.PARENT);
    }

    @Test
    void createParent_setsParentChunkIdToNull() {
        Chunk chunk = Chunk.createParent(DOCUMENT_ID, CONTENT, POSITION);

        assertThat(chunk.parentChunkId()).isNull();
    }

    @Test
    void createParent_setsIdToNull() {
        Chunk chunk = Chunk.createParent(DOCUMENT_ID, CONTENT, POSITION);

        assertThat(chunk.id()).isNull();
    }

    @Test
    void createParent_setsCreatedAtToNull() {
        Chunk chunk = Chunk.createParent(DOCUMENT_ID, CONTENT, POSITION);

        assertThat(chunk.createdAt()).isNull();
    }

    @Test
    void createParent_passesFieldsCorrectly() {
        Chunk chunk = Chunk.createParent(DOCUMENT_ID, CONTENT, 5);

        assertThat(chunk.documentId()).isEqualTo(DOCUMENT_ID);
        assertThat(chunk.content()).isEqualTo(CONTENT);
        assertThat(chunk.position()).isEqualTo(5);
    }

    // =====================
    // createChild() factory tests
    // =====================

    @Test
    void createChild_setsTypeToChild() {
        Chunk chunk = Chunk.createChild(DOCUMENT_ID, PARENT_CHUNK_ID, CONTENT, POSITION);

        assertThat(chunk.type()).isEqualTo(ChunkType.CHILD);
    }

    @Test
    void createChild_setsParentChunkIdToProvidedValue() {
        Chunk chunk = Chunk.createChild(DOCUMENT_ID, PARENT_CHUNK_ID, CONTENT, POSITION);

        assertThat(chunk.parentChunkId()).isEqualTo(PARENT_CHUNK_ID);
    }

    @Test
    void createChild_setsIdToNull() {
        Chunk chunk = Chunk.createChild(DOCUMENT_ID, PARENT_CHUNK_ID, CONTENT, POSITION);

        assertThat(chunk.id()).isNull();
    }

    @Test
    void createChild_setsCreatedAtToNull() {
        Chunk chunk = Chunk.createChild(DOCUMENT_ID, PARENT_CHUNK_ID, CONTENT, POSITION);

        assertThat(chunk.createdAt()).isNull();
    }

    @Test
    void createChild_passesFieldsCorrectly() {
        Chunk chunk = Chunk.createChild(DOCUMENT_ID, PARENT_CHUNK_ID, CONTENT, 3);

        assertThat(chunk.documentId()).isEqualTo(DOCUMENT_ID);
        assertThat(chunk.content()).isEqualTo(CONTENT);
        assertThat(chunk.position()).isEqualTo(3);
    }
}
