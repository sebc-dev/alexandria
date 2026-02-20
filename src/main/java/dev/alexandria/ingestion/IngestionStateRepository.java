package dev.alexandria.ingestion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link IngestionState} entities.
 *
 * <p>Provides query methods for incremental crawling: lookup by source and page URL, bulk retrieval
 * by source, and orphaned page cleanup.
 */
public interface IngestionStateRepository extends JpaRepository<IngestionState, UUID> {

  Optional<IngestionState> findBySourceIdAndPageUrl(UUID sourceId, String pageUrl);

  /** Find all ingestion states for a given source, used for deleted page detection. */
  List<IngestionState> findAllBySourceId(UUID sourceId);

  /** Delete all ingestion states for a source (full recrawl reset). */
  void deleteAllBySourceId(UUID sourceId);

  /**
   * Delete orphaned ingestion states: pages that were previously indexed but are no longer present
   * in the current crawl results.
   */
  void deleteAllBySourceIdAndPageUrlNotIn(UUID sourceId, Collection<String> pageUrls);
}
