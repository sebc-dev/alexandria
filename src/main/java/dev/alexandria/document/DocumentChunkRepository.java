package dev.alexandria.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data repository for {@link DocumentChunk} entities.
 */
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    /**
     * Batch-updates the {@code version} key in JSONB metadata for all chunks
     * matching the given source URL.
     *
     * @param sourceUrl the source URL to match in metadata
     * @param version   the version label to set
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE document_chunks
            SET metadata = jsonb_set(COALESCE(metadata, '{}'::jsonb), '{version}', to_jsonb(:version))
            WHERE metadata->>'source_url' = :sourceUrl
            """, nativeQuery = true)
    void updateVersionMetadata(@Param("sourceUrl") String sourceUrl, @Param("version") String version);

    /**
     * Batch-updates the {@code source_name} key in JSONB metadata for all chunks
     * matching the given source URL.
     *
     * @param sourceUrl  the source URL to match in metadata
     * @param sourceName the source name to set
     */
    @Modifying
    @Transactional
    @Query(value = """
            UPDATE document_chunks
            SET metadata = jsonb_set(COALESCE(metadata, '{}'::jsonb), '{source_name}', to_jsonb(:sourceName))
            WHERE metadata->>'source_url' = :sourceUrl
            """, nativeQuery = true)
    void updateSourceNameMetadata(@Param("sourceUrl") String sourceUrl, @Param("sourceName") String sourceName);

    /**
     * Returns all distinct version labels from chunk metadata.
     *
     * @return list of distinct version strings
     */
    @Query(value = """
            SELECT DISTINCT metadata->>'version'
            FROM document_chunks
            WHERE metadata->>'version' IS NOT NULL
            """, nativeQuery = true)
    List<String> findDistinctVersions();

    /**
     * Returns all distinct source names from chunk metadata.
     *
     * @return list of distinct source name strings
     */
    @Query(value = """
            SELECT DISTINCT metadata->>'source_name'
            FROM document_chunks
            WHERE metadata->>'source_name' IS NOT NULL
            """, nativeQuery = true)
    List<String> findDistinctSourceNames();
}
