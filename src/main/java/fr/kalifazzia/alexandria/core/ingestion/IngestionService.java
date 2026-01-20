package fr.kalifazzia.alexandria.core.ingestion;

import fr.kalifazzia.alexandria.core.model.ChunkType;
import fr.kalifazzia.alexandria.core.model.Document;
import fr.kalifazzia.alexandria.core.port.ChunkRepository;
import fr.kalifazzia.alexandria.core.port.ChunkerPort;
import fr.kalifazzia.alexandria.core.port.DocumentRepository;
import fr.kalifazzia.alexandria.core.port.EmbeddingGenerator;
import fr.kalifazzia.alexandria.core.port.GraphRepository;
import fr.kalifazzia.alexandria.core.port.MarkdownParserPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Service orchestrating the full document ingestion pipeline.
 *
 * <p>Pipeline steps:
 * <ol>
 *   <li>Read file content and compute content hash</li>
 *   <li>Check if document already indexed with same content (skip if unchanged)</li>
 *   <li>Delete old version if exists (upsert pattern)</li>
 *   <li>Parse markdown and extract frontmatter</li>
 *   <li>Create document record</li>
 *   <li>Chunk content hierarchically (parent/child)</li>
 *   <li>Generate embeddings for all chunks</li>
 *   <li>Store chunks with embeddings in database</li>
 *   <li>Create graph vertices and HAS_CHILD edges in Apache AGE</li>
 * </ol>
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    private final MarkdownParserPort markdownParser;
    private final ChunkerPort chunker;
    private final EmbeddingGenerator embeddingGenerator;
    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final GraphRepository graphRepository;

    public IngestionService(
            MarkdownParserPort markdownParser,
            ChunkerPort chunker,
            EmbeddingGenerator embeddingGenerator,
            DocumentRepository documentRepository,
            ChunkRepository chunkRepository,
            GraphRepository graphRepository) {
        this.markdownParser = markdownParser;
        this.chunker = chunker;
        this.embeddingGenerator = embeddingGenerator;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.graphRepository = graphRepository;
    }

    /**
     * Ingests all markdown files in a directory recursively.
     *
     * @param directory Path to the directory containing markdown files
     * @throws UncheckedIOException if directory cannot be read
     */
    public void ingestDirectory(Path directory) {
        log.info("Starting ingestion of directory: {}", directory);

        try (Stream<Path> files = Files.walk(directory)) {
            List<Path> markdownFiles = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .toList();

            log.info("Found {} markdown files to process", markdownFiles.size());

            for (Path file : markdownFiles) {
                try {
                    ingestFile(file);
                } catch (Exception e) {
                    log.error("Failed to ingest file: {}", file, e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + directory, e);
        }

        log.info("Completed ingestion of directory: {}", directory);
    }

    /**
     * Ingests a single markdown file.
     *
     * @param file Path to the markdown file
     * @throws UncheckedIOException if file cannot be read
     */
    @Transactional
    public void ingestFile(Path file) {
        log.debug("Processing file: {}", file);

        String content = readFile(file);
        String contentHash = sha256(content);
        String filePath = file.toAbsolutePath().toString();

        // Check if already indexed with same content
        Optional<Document> existing = documentRepository.findByPath(filePath);
        if (existing.isPresent() && existing.get().contentHash().equals(contentHash)) {
            log.debug("File unchanged, skipping: {}", file);
            return;
        }

        // Delete old version if exists (upsert pattern)
        if (existing.isPresent()) {
            log.debug("File changed, re-indexing: {}", file);
            UUID existingId = existing.get().id();
            // Delete graph data first (before PostgreSQL deletion)
            graphRepository.deleteChunksByDocumentId(existingId);
            graphRepository.deleteDocumentGraph(existingId);
            // Then delete from PostgreSQL tables
            chunkRepository.deleteByDocumentId(existingId);
            documentRepository.delete(existingId);
        }

        // Parse markdown and extract frontmatter
        ParsedDocument parsed = markdownParser.parse(content);

        // Skip empty content
        if (parsed.content() == null || parsed.content().isBlank()) {
            log.debug("Empty content after parsing, skipping: {}", file);
            return;
        }

        // Create document record
        Document document = documentRepository.save(Document.create(
                filePath,
                parsed.metadata().title(),
                parsed.metadata().category(),
                parsed.metadata().tags(),
                contentHash,
                convertFrontmatter(parsed.metadata().rawFrontmatter())
        ));

        // Create document vertex in graph
        graphRepository.createDocumentVertex(document.id(), document.path());

        // Chunk content hierarchically
        List<ChunkPair> chunkPairs = chunker.chunk(parsed.content());

        if (chunkPairs.isEmpty()) {
            log.debug("No chunks generated, file may be too short: {}", file);
            return;
        }

        // Process each parent-child group
        int totalChunks = 0;
        for (ChunkPair pair : chunkPairs) {
            // Generate and save parent chunk
            float[] parentEmbedding = embeddingGenerator.embed(pair.parentContent());
            UUID parentId = chunkRepository.saveChunk(
                    document.id(),
                    null,
                    ChunkType.PARENT,
                    pair.parentContent(),
                    parentEmbedding,
                    pair.position()
            );
            totalChunks++;

            // Create parent chunk vertex in graph
            graphRepository.createChunkVertex(parentId, ChunkType.PARENT, document.id());

            // Generate and save child chunks
            for (int i = 0; i < pair.childContents().size(); i++) {
                String childContent = pair.childContents().get(i);
                float[] childEmbedding = embeddingGenerator.embed(childContent);
                UUID childId = chunkRepository.saveChunk(
                        document.id(),
                        parentId,
                        ChunkType.CHILD,
                        childContent,
                        childEmbedding,
                        i
                );
                totalChunks++;

                // Create child chunk vertex and HAS_CHILD edge in graph
                graphRepository.createChunkVertex(childId, ChunkType.CHILD, document.id());
                graphRepository.createParentChildEdge(parentId, childId);
            }
        }

        log.info("Ingested file: {} with {} chunks ({} parents, {} children)",
                file.getFileName(),
                totalChunks,
                chunkPairs.size(),
                totalChunks - chunkPairs.size());
    }

    /**
     * Reads file content as UTF-8 string.
     * @throws IllegalArgumentException if file exceeds MAX_FILE_SIZE_BYTES
     */
    private String readFile(Path file) {
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_SIZE_BYTES) {
                throw new IllegalArgumentException(
                    "File too large: " + size + " bytes (max: " + MAX_FILE_SIZE_BYTES + ")");
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file: " + file, e);
        }
    }

    /**
     * Computes SHA-256 hash of content.
     */
    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts frontmatter from List<String> values to Object values.
     * Flattens single-element lists to single values for cleaner JSON.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertFrontmatter(Map<String, List<String>> rawFrontmatter) {
        if (rawFrontmatter == null || rawFrontmatter.isEmpty()) {
            return Map.of();
        }

        return rawFrontmatter.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().size() == 1 ? e.getValue().getFirst() : e.getValue()
                ));
    }
}
