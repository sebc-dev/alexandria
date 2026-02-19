package dev.alexandria.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for {@link DocumentChunk} entities.
 */
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {
}
