package dev.alexandria.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IngestionStateRepository extends JpaRepository<IngestionState, UUID> {

    Optional<IngestionState> findBySourceIdAndPageUrl(UUID sourceId, String pageUrl);
}
