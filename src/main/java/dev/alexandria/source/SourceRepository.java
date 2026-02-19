package dev.alexandria.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for {@link Source} entities.
 */
public interface SourceRepository extends JpaRepository<Source, UUID> {
}
