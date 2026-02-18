package dev.alexandria.ingestion.prechunked;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * Imports pre-chunked JSON content into the EmbeddingStore with validation and replacement semantics.
 *
 * <p>Validation is all-or-nothing: if any chunk fails validation, the entire request is rejected
 * and no chunks are stored. Import uses replacement mode: existing chunks for the same source_url
 * are deleted before new chunks are inserted.
 *
 * <p><strong>Ordering for safety:</strong> embeddings are computed <em>before</em> any store
 * mutation (delete + insert). This ensures that if the embedding model call fails, the existing
 * chunks remain untouched. Note that {@code @Transactional} is <em>not</em> used because
 * {@code PgVectorEmbeddingStore} manages its own JDBC connections and does not participate
 * in Spring's transaction synchronization.
 */
@Service
public class PreChunkedImporter {

    static final int EMBED_BATCH_SIZE = 256;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final Validator validator;

    public PreChunkedImporter(EmbeddingStore<TextSegment> embeddingStore,
                              EmbeddingModel embeddingModel,
                              Validator validator) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.validator = validator;
    }

    /**
     * Validates and imports pre-chunked content, replacing any existing chunks for the same source URL.
     *
     * @param request the import request containing source URL and chunks
     * @return the number of chunks imported
     * @throws IllegalArgumentException if any chunk fails validation
     */
    public int importChunks(PreChunkedRequest request) {
        // 1. Validate entire request (all-or-nothing)
        Set<ConstraintViolation<PreChunkedRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String messages = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Validation failed: " + messages);
        }

        // 2. Convert chunks to TextSegments
        List<TextSegment> segments = request.chunks().stream()
                .map(chunk -> chunk.toDocumentChunkData().toTextSegment())
                .toList();

        // 3. Compute ALL embeddings before mutating the store.
        //    If this fails, existing chunks remain untouched.
        List<Embedding> embeddings = embedAll(segments);

        // 4. Delete existing chunks for this source_url (replacement mode)
        embeddingStore.removeAll(metadataKey("source_url").isEqualTo(request.sourceUrl()));

        // 5. Store new embeddings
        embeddingStore.addAll(embeddings, segments);

        return segments.size();
    }

    private List<Embedding> embedAll(List<TextSegment> segments) {
        if (segments.size() <= EMBED_BATCH_SIZE) {
            return embeddingModel.embedAll(segments).content();
        }
        List<Embedding> all = new ArrayList<>();
        for (int i = 0; i < segments.size(); i += EMBED_BATCH_SIZE) {
            List<TextSegment> batch = segments.subList(i, Math.min(i + EMBED_BATCH_SIZE, segments.size()));
            all.addAll(embeddingModel.embedAll(batch).content());
        }
        return all;
    }
}
