package dev.alexandria.ingestion.prechunked;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
public class PreChunkedImporter {

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
    @Transactional
    public int importChunks(PreChunkedRequest request) {
        // 1. Validate entire request (all-or-nothing)
        Set<ConstraintViolation<PreChunkedRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String messages = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Validation failed: " + messages);
        }

        // 2. Delete existing chunks for this source_url (replacement mode)
        embeddingStore.removeAll(metadataKey("source_url").isEqualTo(request.sourceUrl()));

        // 3. Convert chunks to TextSegments
        List<TextSegment> segments = request.chunks().stream()
                .map(this::toTextSegment)
                .toList();

        // 4. Batch embed
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

        // 5. Batch store
        embeddingStore.addAll(embeddings, segments);

        return segments.size();
    }

    private TextSegment toTextSegment(PreChunkedChunk chunk) {
        Metadata metadata = Metadata.from("source_url", chunk.sourceUrl())
                .put("section_path", chunk.sectionPath())
                .put("content_type", chunk.contentType().value())
                .put("last_updated", chunk.lastUpdated());
        if (chunk.language() != null) {
            metadata.put("language", chunk.language());
        }
        return TextSegment.from(chunk.text(), metadata);
    }
}
