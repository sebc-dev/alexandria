package dev.alexandria;

import dev.alexandria.document.DocumentChunk;
import dev.alexandria.document.DocumentChunkRepository;
import dev.alexandria.ingestion.IngestionState;
import dev.alexandria.ingestion.IngestionStateRepository;
import dev.alexandria.source.Source;
import dev.alexandria.source.SourceRepository;
import dev.alexandria.source.SourceStatus;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compensates for ddl-auto=none by verifying each JPA entity
 * can be persisted and read back against the Flyway schema.
 * Catches entity ↔ migration drift at test time rather than runtime.
 */
@Transactional
class JpaSchemaDriftIT extends BaseIntegrationTest {

    @Autowired
    private SourceRepository sourceRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private IngestionStateRepository ingestionStateRepository;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Test
    void sourceEntityRoundtripsAgainstFlywaySchema() {
        Source source = new Source("https://docs.example.com", "Example Docs");
        source.setStatus(SourceStatus.CRAWLING);
        source.setChunkCount(42);

        Source saved = sourceRepository.saveAndFlush(source);
        Source found = sourceRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getUrl()).isEqualTo("https://docs.example.com");
        assertThat(found.getName()).isEqualTo("Example Docs");
        assertThat(found.getStatus()).isEqualTo(SourceStatus.CRAWLING);
        assertThat(found.getChunkCount()).isEqualTo(42);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void documentChunkReadableViaJpaAfterLangchain4jInsert() {
        // Insert via LangChain4j (the real write path — includes embedding)
        String text = "JPA schema drift test document";
        Embedding embedding = embeddingModel.embed(text).content();
        TextSegment segment = TextSegment.from(text);
        String storedId = embeddingStore.add(embedding, segment);

        // Read back via JPA
        UUID chunkId = UUID.fromString(storedId);
        DocumentChunk found = documentChunkRepository.findById(chunkId).orElseThrow();

        assertThat(found.getId()).isEqualTo(chunkId);
        assertThat(found.getText()).isEqualTo(text);
    }

    @Test
    void ingestionStateEntityRoundtripsAgainstFlywaySchema() {
        Source source = new Source("https://state-test.example.com", "State Test");
        Source savedSource = sourceRepository.saveAndFlush(source);

        IngestionState state = new IngestionState(
                savedSource.getId(),
                "https://state-test.example.com/page1",
                "sha256:abc123"
        );

        IngestionState saved = ingestionStateRepository.saveAndFlush(state);
        IngestionState found = ingestionStateRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.getSourceId()).isEqualTo(savedSource.getId());
        assertThat(found.getPageUrl()).isEqualTo("https://state-test.example.com/page1");
        assertThat(found.getContentHash()).isEqualTo("sha256:abc123");
        assertThat(found.getLastIngestedAt()).isNotNull();
    }
}
