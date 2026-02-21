package dev.alexandria.document;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data repository for {@link DocumentChunk} entities. */
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

  /**
   * Batch-updates the {@code version} key in JSONB metadata for all chunks matching the given
   * source URL.
   *
   * @param sourceUrl the source URL to match in metadata
   * @param version the version label to set
   */
  @Modifying
  @Transactional
  @Query(
      value =
          """
            UPDATE document_chunks
            SET metadata = jsonb_set(COALESCE(metadata, '{}'::jsonb), '{version}', to_jsonb(:version))
            WHERE metadata->>'source_url' = :sourceUrl
            """,
      nativeQuery = true)
  void updateVersionMetadata(
      @Param("sourceUrl") String sourceUrl, @Param("version") String version);

  /**
   * Batch-updates the {@code source_name} key in JSONB metadata for all chunks matching the given
   * source URL.
   *
   * @param sourceUrl the source URL to match in metadata
   * @param sourceName the source name to set
   */
  @Modifying
  @Transactional
  @Query(
      value =
          """
            UPDATE document_chunks
            SET metadata = jsonb_set(COALESCE(metadata, '{}'::jsonb), '{source_name}', to_jsonb(:sourceName))
            WHERE metadata->>'source_url' = :sourceUrl
            """,
      nativeQuery = true)
  void updateSourceNameMetadata(
      @Param("sourceUrl") String sourceUrl, @Param("sourceName") String sourceName);

  /**
   * Returns all distinct version labels from chunk metadata.
   *
   * @return list of distinct version strings
   */
  @Query(
      value =
          """
            SELECT DISTINCT metadata->>'version'
            FROM document_chunks
            WHERE metadata->>'version' IS NOT NULL
            """,
      nativeQuery = true)
  List<String> findDistinctVersions();

  /**
   * Returns all distinct source names from chunk metadata.
   *
   * @return list of distinct source name strings
   */
  @Query(
      value =
          """
            SELECT DISTINCT metadata->>'source_name'
            FROM document_chunks
            WHERE metadata->>'source_name' IS NOT NULL
            """,
      nativeQuery = true)
  List<String> findDistinctSourceNames();

  /**
   * Batch-updates the {@code source_id} column for chunks matching the given embedding IDs. Called
   * after {@code embeddingStore.addAll()} to link newly stored chunks to their source.
   *
   * @param sourceId the source UUID to set
   * @param embeddingIds array of embedding ID strings (cast to uuid[] in SQL)
   */
  @Modifying
  @Transactional
  @Query(
      value =
          """
            UPDATE document_chunks SET source_id = :sourceId
            WHERE embedding_id = ANY(CAST(:embeddingIds AS uuid[]))
            """,
      nativeQuery = true)
  void updateSourceIdBatch(
      @Param("sourceId") UUID sourceId, @Param("embeddingIds") String[] embeddingIds);

  /**
   * Counts the total number of chunks for a given source.
   *
   * @param sourceId the source UUID
   * @return total chunk count
   */
  @Query(
      value =
          """
            SELECT COUNT(*) FROM document_chunks WHERE source_id = :sourceId
            """,
      nativeQuery = true)
  long countBySourceId(@Param("sourceId") UUID sourceId);

  /**
   * Counts chunks grouped by content type for a given source.
   *
   * @param sourceId the source UUID
   * @return list of [content_type, count] pairs
   */
  @Query(
      value =
          """
            SELECT COALESCE(metadata->>'content_type', 'unknown') AS content_type, COUNT(*) AS cnt
            FROM document_chunks WHERE source_id = :sourceId
            GROUP BY metadata->>'content_type'
            """,
      nativeQuery = true)
  List<Object[]> countBySourceIdGroupedByContentType(@Param("sourceId") UUID sourceId);

  /**
   * Counts total chunks across all sources.
   *
   * @return total chunk count
   */
  @Query(value = "SELECT COUNT(*) FROM document_chunks", nativeQuery = true)
  long countAllChunks();

  /**
   * Returns the total relation size (data + indexes + TOAST) of the document_chunks table in bytes.
   *
   * @return storage size in bytes
   */
  @Query(value = "SELECT pg_total_relation_size('document_chunks')", nativeQuery = true)
  long getStorageSizeBytes();
}
