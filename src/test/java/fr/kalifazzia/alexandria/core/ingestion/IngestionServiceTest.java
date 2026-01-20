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
                crossReferenceExtractor
        );
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
        @DisplayName("should create document vertex in graph after saving document")
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

            // Then - document vertex created
            verify(graphRepository).createDocumentVertex(documentId, filePath);
        }

        @Test
        @DisplayName("should create chunk vertices and HAS_CHILD edges in graph")
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

            // Then - verify graph vertices created
            verify(graphRepository).createChunkVertex(parentChunkId, ChunkType.PARENT, documentId);
            verify(graphRepository).createChunkVertex(child1Id, ChunkType.CHILD, documentId);
            verify(graphRepository).createChunkVertex(child2Id, ChunkType.CHILD, documentId);

            // Then - verify HAS_CHILD edges created
            verify(graphRepository).createParentChildEdge(parentChunkId, child1Id);
            verify(graphRepository).createParentChildEdge(parentChunkId, child2Id);
        }

        @Test
        @DisplayName("should delete graph data before PostgreSQL data on re-index")
        void deletesGraphDataBeforePostgresOnReindex() throws IOException {
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

            // Then - verify graph deletion happens before PostgreSQL deletion
            InOrder inOrder = inOrder(graphRepository, chunkRepository, documentRepository);
            inOrder.verify(graphRepository).deleteChunksByDocumentId(existingId);
            inOrder.verify(graphRepository).deleteDocumentGraph(existingId);
            inOrder.verify(chunkRepository).deleteByDocumentId(existingId);
            inOrder.verify(documentRepository).delete(existingId);
        }

        @Test
        @DisplayName("should not call graph operations when file unchanged")
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

            // Then - no graph operations
            verify(graphRepository, never()).createDocumentVertex(any(), anyString());
            verify(graphRepository, never()).createChunkVertex(any(), any(), any());
            verify(graphRepository, never()).createParentChildEdge(any(), any());
            verify(graphRepository, never()).deleteChunksByDocumentId(any());
            verify(graphRepository, never()).deleteDocumentGraph(any());
        }

        @Test
        @DisplayName("should create REFERENCES edge when link target exists")
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

            // Then - REFERENCES edge created
            verify(graphRepository).createReferenceEdge(sourceDocId, targetDocId, "target");
        }

        @Test
        @DisplayName("should not create REFERENCES edge when target not found")
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

            // Then - no REFERENCES edge created (target not indexed)
            verify(graphRepository, never()).createReferenceEdge(any(), any(), anyString());
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

            // Then - resolveLink and createReferenceEdge never called
            verify(crossReferenceExtractor, never()).resolveLink(any(), anyString());
            verify(graphRepository, never()).createReferenceEdge(any(), any(), anyString());
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
