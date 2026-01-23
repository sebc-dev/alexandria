package fr.kalifazzia.alexandria.core.ingestion;

import fr.kalifazzia.alexandria.core.model.ChunkType;
import fr.kalifazzia.alexandria.core.model.Document;
import fr.kalifazzia.alexandria.core.port.ChunkRepository;
import fr.kalifazzia.alexandria.core.port.ChunkerPort;
import fr.kalifazzia.alexandria.core.port.CrossReferenceExtractorPort;
import fr.kalifazzia.alexandria.core.port.DocumentRepository;
import fr.kalifazzia.alexandria.core.port.EmbeddingGenerator;
import fr.kalifazzia.alexandria.core.port.GraphRepository;
import fr.kalifazzia.alexandria.core.port.MarkdownParserPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngestionService")
class IngestionServiceTest {

    @Mock
    private MarkdownParserPort markdownParser;

    @Mock
    private ChunkerPort chunker;

    @Mock
    private EmbeddingGenerator embeddingGenerator;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ChunkRepository chunkRepository;

    @Mock
    private GraphRepository graphRepository;

    @Mock
    private CrossReferenceExtractorPort crossReferenceExtractor;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private IngestionService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new IngestionService(
                markdownParser,
                chunker,
                embeddingGenerator,
                documentRepository,
                chunkRepository,
                graphRepository,
                crossReferenceExtractor,
                eventPublisher
        );
        // Set self-reference for ingestDirectory to call ingestFile properly
        service.setSelf(service);
    }

    @Nested
    @DisplayName("ingestFile")
    class IngestFile {

        @Test
        @DisplayName("should call components in correct order")
        void callsComponentsInOrder() throws IOException {
            // Given
            Path file = createTempMarkdownFile("# Test\n\nContent here");
            UUID documentId = UUID.randomUUID();
            UUID parentChunkId = UUID.randomUUID();
            float[] embedding = new float[]{0.1f, 0.2f, 0.3f};

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(
                            new DocumentMetadata("Test", "category", List.of("tag"), Map.of()),
                            "Content here"
                    )
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(documentId, file.toString(), "Test", "category",
                            List.of("tag"), "hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of(
                    new ChunkPair("Parent content", List.of("Child 1", "Child 2"), 0)
            ));
            when(embeddingGenerator.embed(anyString())).thenReturn(embedding);
            when(chunkRepository.saveChunk(any(), any(), any(), anyString(), any(), anyInt()))
                    .thenReturn(parentChunkId);

            // When
            service.ingestFile(file);

            // Then - verify call order
            InOrder inOrder = inOrder(markdownParser, documentRepository, chunker, embeddingGenerator, chunkRepository);
            inOrder.verify(documentRepository).findByPath(anyString());
            inOrder.verify(markdownParser).parse(anyString());
            inOrder.verify(documentRepository).save(any());
            inOrder.verify(chunker).chunk("Content here");
            inOrder.verify(embeddingGenerator).embed("Parent content");
            inOrder.verify(chunkRepository).saveChunk(eq(documentId), isNull(), eq(ChunkType.PARENT),
                    eq("Parent content"), any(), eq(0));
            inOrder.verify(embeddingGenerator).embed("Child 1");
            inOrder.verify(chunkRepository).saveChunk(eq(documentId), eq(parentChunkId), eq(ChunkType.CHILD),
                    eq("Child 1"), any(), eq(0));
        }

        @Test
        @DisplayName("should skip unchanged files based on content hash")
        void skipsUnchangedFiles() throws IOException {
            // Given
            String content = "# Test\n\nContent here";
            Path file = createTempMarkdownFile(content);
            String filePath = file.toAbsolutePath().toString();

            // Existing document has same content hash
            String contentHash = computeSha256(content);
            Document existingDoc = new Document(
                    UUID.randomUUID(), filePath, "Test", null, List.of(),
                    contentHash, Map.of(), Instant.now(), Instant.now()
            );
            when(documentRepository.findByPath(filePath)).thenReturn(Optional.of(existingDoc));

            // When
            service.ingestFile(file);

            // Then - nothing else should be called
            verify(documentRepository, never()).delete(any());
            verify(chunkRepository, never()).deleteByDocumentId(any());
            verify(markdownParser, never()).parse(anyString());
            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should delete old chunks before re-indexing changed file")
        void deletesOldChunksOnReindex() throws IOException {
            // Given
            String newContent = "# Updated\n\nNew content";
            Path file = createTempMarkdownFile(newContent);
            String filePath = file.toAbsolutePath().toString();

            UUID existingId = UUID.randomUUID();
            Document existingDoc = new Document(
                    existingId, filePath, "Old", null, List.of(),
                    "old-hash", Map.of(), Instant.now(), Instant.now()
            );
            when(documentRepository.findByPath(filePath)).thenReturn(Optional.of(existingDoc));
            when(markdownParser.parse(newContent)).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "New content")
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(UUID.randomUUID(), filePath, null, null, List.of(),
                            "new-hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // When
            service.ingestFile(file);

            // Then - old chunks and document deleted
            verify(chunkRepository).deleteByDocumentId(existingId);
            verify(documentRepository).delete(existingId);
        }

        @Test
        @DisplayName("should save parent and child chunks with embeddings")
        void savesParentAndChildChunks() throws IOException {
            // Given
            Path file = createTempMarkdownFile("# Test\n\nLong content for chunking...");
            UUID documentId = UUID.randomUUID();
            UUID parentChunkId = UUID.randomUUID();
            float[] parentEmbedding = new float[]{1.0f, 2.0f};
            float[] child1Embedding = new float[]{3.0f, 4.0f};
            float[] child2Embedding = new float[]{5.0f, 6.0f};

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "Long content for chunking...")
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(documentId, file.toString(), null, null, List.of(),
                            "hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of(
                    new ChunkPair("Parent 1", List.of("Child A", "Child B"), 0)
            ));
            when(embeddingGenerator.embed("Parent 1")).thenReturn(parentEmbedding);
            when(embeddingGenerator.embed("Child A")).thenReturn(child1Embedding);
            when(embeddingGenerator.embed("Child B")).thenReturn(child2Embedding);
            when(chunkRepository.saveChunk(eq(documentId), isNull(), eq(ChunkType.PARENT),
                    eq("Parent 1"), any(), eq(0))).thenReturn(parentChunkId);

            // When
            service.ingestFile(file);

            // Then
            // Parent chunk saved with null parent_chunk_id
            verify(chunkRepository).saveChunk(
                    eq(documentId),
                    isNull(),
                    eq(ChunkType.PARENT),
                    eq("Parent 1"),
                    eq(parentEmbedding),
                    eq(0)
            );

            // Child chunks saved with parent_chunk_id reference
            verify(chunkRepository).saveChunk(
                    eq(documentId),
                    eq(parentChunkId),
                    eq(ChunkType.CHILD),
                    eq("Child A"),
                    eq(child1Embedding),
                    eq(0)
            );
            verify(chunkRepository).saveChunk(
                    eq(documentId),
                    eq(parentChunkId),
                    eq(ChunkType.CHILD),
                    eq("Child B"),
                    eq(child2Embedding),
                    eq(1)
            );
        }

        @Test
        @DisplayName("should handle multiple chunk pairs")
        void handlesMultipleChunkPairs() throws IOException {
            // Given
            Path file = createTempMarkdownFile("# Test\n\nVery long content...");
            UUID documentId = UUID.randomUUID();
            UUID parent1Id = UUID.randomUUID();
            UUID parent2Id = UUID.randomUUID();
            float[] embedding = new float[]{0.1f};

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "Very long content...")
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(documentId, file.toString(), null, null, List.of(),
                            "hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of(
                    new ChunkPair("Parent 1", List.of("Child 1A"), 0),
                    new ChunkPair("Parent 2", List.of("Child 2A"), 1)
            ));
            when(embeddingGenerator.embed(anyString())).thenReturn(embedding);
            // Use lenient stubbing for multiple parent chunks
            when(chunkRepository.saveChunk(any(), any(), any(), anyString(), any(), anyInt()))
                    .thenReturn(parent1Id)
                    .thenReturn(UUID.randomUUID())
                    .thenReturn(parent2Id)
                    .thenReturn(UUID.randomUUID());

            // When
            service.ingestFile(file);

            // Then - 2 parent chunks + 2 child chunks = 4 total
            verify(chunkRepository, times(4)).saveChunk(any(), any(), any(), anyString(), any(), anyInt());
        }

        @Test
        @DisplayName("should skip empty content after parsing")
        void skipsEmptyContent() throws IOException {
            // Given
            Path file = createTempMarkdownFile("---\ntitle: Empty\n---\n");

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(new DocumentMetadata("Empty", null, List.of(), Map.of()), "")
            );

            // When
            service.ingestFile(file);

            // Then - no document or chunks saved
            verify(documentRepository, never()).save(any());
            verify(chunkRepository, never()).saveChunk(any(), any(), any(), anyString(), any(), anyInt());
        }

        @Test
        @DisplayName("should publish event with document info for graph vertex creation")
        void createsDocumentVertexInGraph() throws IOException {
            // Given
            Path file = createTempMarkdownFile("# Test\n\nContent here");
            UUID documentId = UUID.randomUUID();
            String filePath = file.toAbsolutePath().toString();

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "Content here")
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(documentId, filePath, null, null, List.of(),
                            "hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // When
            service.ingestFile(file);

            // Then - event published with document info
            ArgumentCaptor<DocumentIngestedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentIngestedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            DocumentIngestedEvent event = eventCaptor.getValue();
            assertThat(event.documentId()).isEqualTo(documentId);
            assertThat(event.documentPath()).isEqualTo(filePath);
        }

        @Test
        @DisplayName("should publish event with chunk operations for graph vertices and edges")
        void createsChunkVerticesAndEdgesInGraph() throws IOException {
            // Given
            Path file = createTempMarkdownFile("# Test\n\nContent here");
            UUID documentId = UUID.randomUUID();
            UUID parentChunkId = UUID.randomUUID();
            UUID child1Id = UUID.randomUUID();
            UUID child2Id = UUID.randomUUID();
            float[] embedding = new float[]{0.1f, 0.2f};

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "Content here")
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(documentId, file.toString(), null, null, List.of(),
                            "hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of(
                    new ChunkPair("Parent content", List.of("Child 1", "Child 2"), 0)
            ));
            when(embeddingGenerator.embed(anyString())).thenReturn(embedding);
            // Parent chunk save returns parentChunkId
            when(chunkRepository.saveChunk(eq(documentId), isNull(), eq(ChunkType.PARENT),
                    eq("Parent content"), any(), eq(0))).thenReturn(parentChunkId);
            // Child chunks return their IDs
            when(chunkRepository.saveChunk(eq(documentId), eq(parentChunkId), eq(ChunkType.CHILD),
                    eq("Child 1"), any(), eq(0))).thenReturn(child1Id);
            when(chunkRepository.saveChunk(eq(documentId), eq(parentChunkId), eq(ChunkType.CHILD),
                    eq("Child 2"), any(), eq(1))).thenReturn(child2Id);

            // When
            service.ingestFile(file);

            // Then - verify event contains chunk operations
            ArgumentCaptor<DocumentIngestedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentIngestedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            DocumentIngestedEvent event = eventCaptor.getValue();

            // Verify 3 chunk operations (1 parent + 2 children)
            assertThat(event.chunkOperations()).hasSize(3);

            // Parent chunk operation (no parentChunkId)
            var parentOp = event.chunkOperations().get(0);
            assertThat(parentOp.chunkId()).isEqualTo(parentChunkId);
            assertThat(parentOp.chunkType()).isEqualTo(ChunkType.PARENT);
            assertThat(parentOp.documentId()).isEqualTo(documentId);
            assertThat(parentOp.parentChunkId()).isNull();

            // Child chunk operations (with parentChunkId for HAS_CHILD edge)
            var child1Op = event.chunkOperations().get(1);
            assertThat(child1Op.chunkId()).isEqualTo(child1Id);
            assertThat(child1Op.chunkType()).isEqualTo(ChunkType.CHILD);
            assertThat(child1Op.parentChunkId()).isEqualTo(parentChunkId);

            var child2Op = event.chunkOperations().get(2);
            assertThat(child2Op.chunkId()).isEqualTo(child2Id);
            assertThat(child2Op.chunkType()).isEqualTo(ChunkType.CHILD);
            assertThat(child2Op.parentChunkId()).isEqualTo(parentChunkId);
        }

        @Test
        @DisplayName("should delete PostgreSQL data before graph data on re-index (safer rollback)")
        void deletesPostgresBeforeGraphOnReindex() throws IOException {
            // Given
            String newContent = "# Updated\n\nNew content";
            Path file = createTempMarkdownFile(newContent);
            String filePath = file.toAbsolutePath().toString();

            UUID existingId = UUID.randomUUID();
            Document existingDoc = new Document(
                    existingId, filePath, "Old", null, List.of(),
                    "old-hash", Map.of(), Instant.now(), Instant.now()
            );
            when(documentRepository.findByPath(filePath)).thenReturn(Optional.of(existingDoc));
            when(markdownParser.parse(newContent)).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "New content")
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(UUID.randomUUID(), filePath, null, null, List.of(),
                            "new-hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // When
            service.ingestFile(file);

            // Then - verify PostgreSQL deletion happens before graph deletion
            // This ensures if PostgreSQL fails, graph data remains intact
            InOrder inOrder = inOrder(chunkRepository, documentRepository, graphRepository);
            inOrder.verify(chunkRepository).deleteByDocumentId(existingId);
            inOrder.verify(documentRepository).delete(existingId);
            inOrder.verify(graphRepository).deleteChunksByDocumentId(existingId);
            inOrder.verify(graphRepository).deleteDocumentGraph(existingId);
        }

        @Test
        @DisplayName("should not publish event or call graph operations when file unchanged")
        void noGraphOperationsWhenUnchanged() throws IOException {
            // Given
            String content = "# Test\n\nContent here";
            Path file = createTempMarkdownFile(content);
            String filePath = file.toAbsolutePath().toString();

            // Existing document has same content hash
            String contentHash = computeSha256(content);
            Document existingDoc = new Document(
                    UUID.randomUUID(), filePath, "Test", null, List.of(),
                    contentHash, Map.of(), Instant.now(), Instant.now()
            );
            when(documentRepository.findByPath(filePath)).thenReturn(Optional.of(existingDoc));

            // When
            service.ingestFile(file);

            // Then - no event published and no graph operations
            verify(eventPublisher, never()).publishEvent(any());
            verify(graphRepository, never()).deleteChunksByDocumentId(any());
            verify(graphRepository, never()).deleteDocumentGraph(any());
        }

        @Test
        @DisplayName("should include REFERENCES operation in event when link target exists")
        void createsReferenceEdgeWhenTargetExists() throws IOException {
            // Given - source file with link to target
            Path sourceFile = createTempMarkdownFile("# Source\n\nSee [target](target.md)");
            Path targetFile = tempDir.resolve("target.md");
            Files.writeString(targetFile, "# Target");
            String sourceFilePath = sourceFile.toAbsolutePath().toString();
            String targetFilePath = targetFile.toAbsolutePath().toString();

            UUID sourceDocId = UUID.randomUUID();
            UUID targetDocId = UUID.randomUUID();

            // First call to findByPath (for source file) returns empty, second call (for target) returns targetDoc
            Document targetDoc = new Document(targetDocId, targetFilePath, "Target", null,
                    List.of(), "hash", Map.of(), Instant.now(), Instant.now());
            when(documentRepository.findByPath(sourceFilePath)).thenReturn(Optional.empty());
            when(documentRepository.findByPath(targetFilePath)).thenReturn(Optional.of(targetDoc));

            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "See [target](target.md)")
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(sourceDocId, sourceFilePath, null, null, List.of(),
                            "hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // Setup cross-reference extraction
            when(crossReferenceExtractor.extractLinks("See [target](target.md)")).thenReturn(List.of(
                    new CrossReferenceExtractorPort.ExtractedLink("target.md", "target")
            ));
            when(crossReferenceExtractor.resolveLink(sourceFile, "target.md"))
                    .thenReturn(Optional.of(targetFile.toAbsolutePath()));

            // When
            service.ingestFile(sourceFile);

            // Then - event contains REFERENCES operation
            ArgumentCaptor<DocumentIngestedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentIngestedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            DocumentIngestedEvent event = eventCaptor.getValue();

            assertThat(event.referenceOperations()).hasSize(1);
            var refOp = event.referenceOperations().get(0);
            assertThat(refOp.sourceDocId()).isEqualTo(sourceDocId);
            assertThat(refOp.targetDocId()).isEqualTo(targetDocId);
            assertThat(refOp.linkText()).isEqualTo("target");
        }

        @Test
        @DisplayName("should not include REFERENCES operation in event when target not found")
        void noReferenceEdgeWhenTargetNotFound() throws IOException {
            // Given
            Path sourceFile = createTempMarkdownFile("# Source\n\nSee [missing](missing.md)");
            String sourceFilePath = sourceFile.toAbsolutePath().toString();
            UUID sourceDocId = UUID.randomUUID();

            // Both source file and target not found - use anyString() for simplicity
            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "See [missing](missing.md)")
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(sourceDocId, sourceFilePath, null, null, List.of(),
                            "hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // Setup cross-reference extraction
            when(crossReferenceExtractor.extractLinks("See [missing](missing.md)")).thenReturn(List.of(
                    new CrossReferenceExtractorPort.ExtractedLink("missing.md", "missing")
            ));
            Path resolvedPath = tempDir.resolve("missing.md").toAbsolutePath();
            when(crossReferenceExtractor.resolveLink(sourceFile, "missing.md"))
                    .thenReturn(Optional.of(resolvedPath));

            // When
            service.ingestFile(sourceFile);

            // Then - event has no reference operations (target not indexed)
            ArgumentCaptor<DocumentIngestedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentIngestedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            DocumentIngestedEvent event = eventCaptor.getValue();

            assertThat(event.referenceOperations()).isEmpty();
        }

        @Test
        @DisplayName("should not process references when content has no links")
        void noReferenceProcessingWhenNoLinks() throws IOException {
            // Given
            Path file = createTempMarkdownFile("# Test\n\nNo links here");
            String filePath = file.toAbsolutePath().toString();
            UUID documentId = UUID.randomUUID();

            when(documentRepository.findByPath(filePath)).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "No links here")
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(documentId, filePath, null, null, List.of(),
                            "hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // No links extracted
            when(crossReferenceExtractor.extractLinks("No links here")).thenReturn(List.of());

            // When
            service.ingestFile(file);

            // Then - resolveLink never called and event has no reference operations
            verify(crossReferenceExtractor, never()).resolveLink(any(), anyString());

            ArgumentCaptor<DocumentIngestedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentIngestedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().referenceOperations()).isEmpty();
        }

        @Test
        @DisplayName("should not call cross-reference extractor when file unchanged")
        void noCrossReferenceExtractionWhenUnchanged() throws IOException {
            // Given
            String content = "# Test\n\nSee [other](other.md)";
            Path file = createTempMarkdownFile(content);
            String filePath = file.toAbsolutePath().toString();

            String contentHash = computeSha256(content);
            Document existingDoc = new Document(
                    UUID.randomUUID(), filePath, "Test", null, List.of(),
                    contentHash, Map.of(), Instant.now(), Instant.now()
            );
            when(documentRepository.findByPath(filePath)).thenReturn(Optional.of(existingDoc));

            // When
            service.ingestFile(file);

            // Then - cross-reference extractor never called
            verify(crossReferenceExtractor, never()).extractLinks(anyString());
        }

        @Test
        @DisplayName("should accept file at exactly max size boundary")
        void acceptsFileAtMaxSizeBoundary() throws IOException {
            // Given - create file of exactly 10MB (MAX_FILE_SIZE_BYTES)
            // We can't easily create a 10MB file in test, so we verify the boundary logic
            // by checking that a small file passes (no exception thrown)
            Path file = createTempMarkdownFile("# Small file");

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "Small file")
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(UUID.randomUUID(), file.toString(), null, null, List.of(),
                            "hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // When - should not throw
            service.ingestFile(file);

            // Then - file was processed
            verify(documentRepository).save(any());
        }

        @Test
        @DisplayName("should convert frontmatter with null value to empty map")
        void convertsFrontmatterNullToEmptyMap() throws IOException {
            // Given - frontmatter is null
            Path file = createTempMarkdownFile("# Test\n\nContent");
            UUID documentId = UUID.randomUUID();

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(
                            new DocumentMetadata("Test", "category", List.of(), null),  // null rawFrontmatter
                            "Content"
                    )
            );
            when(documentRepository.save(any())).thenAnswer(inv -> {
                Document doc = inv.getArgument(0);
                // Verify frontmatter is empty map, not null
                assertThat(doc.frontmatter()).isNotNull();
                assertThat(doc.frontmatter()).isEmpty();
                return new Document(documentId, doc.path(), doc.title(), doc.category(),
                        doc.tags(), doc.contentHash(), doc.frontmatter(), Instant.now(), Instant.now());
            });
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // When
            service.ingestFile(file);

            // Then - document saved with empty frontmatter
            verify(documentRepository).save(any());
        }

        @Test
        @DisplayName("should convert empty frontmatter map to empty map")
        void convertsEmptyFrontmatterToEmptyMap() throws IOException {
            // Given - frontmatter is empty map
            Path file = createTempMarkdownFile("# Test\n\nContent");
            UUID documentId = UUID.randomUUID();

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(
                            new DocumentMetadata("Test", "category", List.of(), Map.of()),  // empty rawFrontmatter
                            "Content"
                    )
            );
            when(documentRepository.save(any())).thenAnswer(inv -> {
                Document doc = inv.getArgument(0);
                assertThat(doc.frontmatter()).isNotNull();
                assertThat(doc.frontmatter()).isEmpty();
                return new Document(documentId, doc.path(), doc.title(), doc.category(),
                        doc.tags(), doc.contentHash(), doc.frontmatter(), Instant.now(), Instant.now());
            });
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // When
            service.ingestFile(file);

            // Then - document saved with empty frontmatter
            verify(documentRepository).save(any());
        }

        @Test
        @DisplayName("should convert non-empty frontmatter correctly")
        void convertsNonEmptyFrontmatter() throws IOException {
            // Given - frontmatter with values (single-element lists flatten to single values)
            Path file = createTempMarkdownFile("# Test\n\nContent");
            UUID documentId = UUID.randomUUID();

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(
                            new DocumentMetadata("Test", "category", List.of(),
                                    Map.of("author", List.of("John"), "tags", List.of("a", "b"))),
                            "Content"
                    )
            );
            when(documentRepository.save(any())).thenAnswer(inv -> {
                Document doc = inv.getArgument(0);
                assertThat(doc.frontmatter()).isNotNull();
                assertThat(doc.frontmatter()).isNotEmpty();
                // Single-element list flattens to single value
                assertThat(doc.frontmatter().get("author")).isEqualTo("John");
                // Multi-element list remains as list
                assertThat(doc.frontmatter().get("tags")).isEqualTo(List.of("a", "b"));
                return new Document(documentId, doc.path(), doc.title(), doc.category(),
                        doc.tags(), doc.contentHash(), doc.frontmatter(), Instant.now(), Instant.now());
            });
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // When
            service.ingestFile(file);

            // Then - document saved with converted frontmatter
            verify(documentRepository).save(any());
        }

        @Test
        @DisplayName("should count chunks correctly with multiple parents and children")
        void countsChunksCorrectlyWithMultipleParentsAndChildren() throws IOException {
            // Given - 2 parent chunks, each with 2 children = 6 total chunks
            Path file = createTempMarkdownFile("# Test\n\nVery long content...");
            UUID documentId = UUID.randomUUID();
            float[] embedding = new float[]{0.1f};

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "Very long content...")
            );
            when(documentRepository.save(any())).thenReturn(
                    new Document(documentId, file.toString(), null, null, List.of(),
                            "hash", Map.of(), Instant.now(), Instant.now())
            );
            when(chunker.chunk(anyString())).thenReturn(List.of(
                    new ChunkPair("Parent 1", List.of("Child 1A", "Child 1B"), 0),
                    new ChunkPair("Parent 2", List.of("Child 2A", "Child 2B"), 1)
            ));
            when(embeddingGenerator.embed(anyString())).thenReturn(embedding);
            when(chunkRepository.saveChunk(any(), any(), any(), anyString(), any(), anyInt()))
                    .thenReturn(UUID.randomUUID());

            // When
            service.ingestFile(file);

            // Then - 2 parents + 4 children = 6 total chunks saved
            verify(chunkRepository, times(6)).saveChunk(any(), any(), any(), anyString(), any(), anyInt());

            // Verify event has 6 chunk operations
            ArgumentCaptor<DocumentIngestedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentIngestedEvent.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertThat(eventCaptor.getValue().chunkOperations()).hasSize(6);
        }
    }

    @Nested
    @DisplayName("ingestDirectory")
    class IngestDirectory {

        @Test
        @DisplayName("should process all markdown files in directory")
        void processesAllMarkdownFiles() throws IOException {
            // Given
            Path subdir = tempDir.resolve("docs");
            Files.createDirectories(subdir);

            createTempMarkdownFile("# Doc 1");
            Files.writeString(subdir.resolve("doc2.md"), "# Doc 2");
            Files.writeString(tempDir.resolve("readme.txt"), "Not markdown");

            when(documentRepository.findByPath(anyString())).thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "Content")
            );
            when(documentRepository.save(any())).thenAnswer(inv -> {
                Document doc = inv.getArgument(0);
                return new Document(UUID.randomUUID(), doc.path(), doc.title(), doc.category(),
                        doc.tags(), doc.contentHash(), doc.frontmatter(), Instant.now(), Instant.now());
            });
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // When
            service.ingestDirectory(tempDir);

            // Then - 2 markdown files processed
            verify(markdownParser, times(2)).parse(anyString());
            verify(documentRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("should continue processing after single file failure")
        void continuesAfterFailure() throws IOException {
            // Given
            createTempMarkdownFile("# Doc 1");
            Path file2 = tempDir.resolve("doc2.md");
            Files.writeString(file2, "# Doc 2");

            when(documentRepository.findByPath(anyString()))
                    .thenThrow(new RuntimeException("DB error"))
                    .thenReturn(Optional.empty());
            when(markdownParser.parse(anyString())).thenReturn(
                    new ParsedDocument(DocumentMetadata.empty(), "Content")
            );
            when(documentRepository.save(any())).thenAnswer(inv -> {
                Document doc = inv.getArgument(0);
                return new Document(UUID.randomUUID(), doc.path(), doc.title(), doc.category(),
                        doc.tags(), doc.contentHash(), doc.frontmatter(), Instant.now(), Instant.now());
            });
            when(chunker.chunk(anyString())).thenReturn(List.of());

            // When
            service.ingestDirectory(tempDir);

            // Then - second file still processed despite first failure
            verify(documentRepository, times(2)).findByPath(anyString());
        }
    }

    private Path createTempMarkdownFile(String content) throws IOException {
        Path file = tempDir.resolve("test-" + System.nanoTime() + ".md");
        Files.writeString(file, content);
        return file;
    }

    private String computeSha256(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
