package dev.alexandria.source;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.UUID;

/**
 * Spring Data repository for {@link Source} entities.
 */
public interface SourceRepository extends JpaRepository<Source, UUID> {

    /**
     * Returns the most recent last_crawled_at timestamp across all sources.
     *
     * @return the most recent crawl timestamp, or null if no source has been crawled
     */
    @Query("SELECT MAX(s.lastCrawledAt) FROM Source s")
    Instant findMaxLastCrawledAt();
}
