package fr.kalifazzia.alexandria.api.cli;

import fr.kalifazzia.alexandria.core.port.ChunkRepository;
import fr.kalifazzia.alexandria.core.port.DocumentRepository;
import fr.kalifazzia.alexandria.core.port.GraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CLI commands.
 *
 * Tests validation logic and output formatting using mocked port interfaces.
 * Service classes are passed as null where not needed, or tested via integration tests.
 */
class AlexandriaCommandsTest {

    private DocumentRepository documentRepository;
    private ChunkRepository chunkRepository;
    private GraphRepository graphRepository;

    @BeforeEach
    void setUp() {
        // Only mock port interfaces which Mockito can handle on Java 25
        documentRepository = mock(DocumentRepository.class);
        chunkRepository = mock(ChunkRepository.class);
        graphRepository = mock(GraphRepository.class);
    }

    // =====================
    // Index command tests
    // =====================

    @Test
    void index_nonExistentPath_throwsException() {
        // Create commands with null services - we only test validation
        AlexandriaCommands commands = new AlexandriaCommands(
                null, null, documentRepository, chunkRepository, graphRepository);

        assertThatThrownBy(() -> commands.index("/non/existent/path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void index_fileNotDirectory_throwsException(@TempDir Path tempDir) throws Exception {
        AlexandriaCommands commands = new AlexandriaCommands(
                null, null, documentRepository, chunkRepository, graphRepository);

        Path file = Files.createFile(tempDir.resolve("file.txt"));

        assertThatThrownBy(() -> commands.index(file.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a directory");
    }

    // =====================
    // Search command tests
    // =====================

    @Test
    void search_invalidLimit_zero_throwsException() {
        AlexandriaCommands commands = new AlexandriaCommands(
                null, null, documentRepository, chunkRepository, graphRepository);

        assertThatThrownBy(() -> commands.search("query", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 20");
    }

    @Test
    void search_invalidLimit_tooHigh_throwsException() {
        AlexandriaCommands commands = new AlexandriaCommands(
                null, null, documentRepository, chunkRepository, graphRepository);

        assertThatThrownBy(() -> commands.search("query", 21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 20");
    }

    // =====================
    // Status command tests
    // =====================

    @Test
    void status_withDocuments_showsCounts() {
        AlexandriaCommands commands = new AlexandriaCommands(
                null, null, documentRepository, chunkRepository, graphRepository);

        Instant lastUpdated = Instant.parse("2026-01-20T10:00:00Z");
        when(documentRepository.count()).thenReturn(10L);
        when(chunkRepository.count()).thenReturn(50L);
        when(documentRepository.findLastUpdated()).thenReturn(Optional.of(lastUpdated));

        String output = commands.status();

        // Compute expected date using same formatter as AlexandriaCommands
        String expectedDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(lastUpdated);

        assertThat(output).contains("Documents: 10");
        assertThat(output).contains("Chunks: 50");
        assertThat(output).contains(expectedDate);
    }

    @Test
    void status_emptyDatabase_showsNever() {
        AlexandriaCommands commands = new AlexandriaCommands(
                null, null, documentRepository, chunkRepository, graphRepository);

        when(documentRepository.count()).thenReturn(0L);
        when(chunkRepository.count()).thenReturn(0L);
        when(documentRepository.findLastUpdated()).thenReturn(Optional.empty());

        String output = commands.status();

        assertThat(output).contains("Documents: 0");
        assertThat(output).contains("Last indexed: Never");
    }

    // =====================
    // Clear command tests
    // =====================

    @Test
    void clear_withoutForce_showsWarning() {
        AlexandriaCommands commands = new AlexandriaCommands(
                null, null, documentRepository, chunkRepository, graphRepository);

        String output = commands.clear(false);

        assertThat(output).contains("WARNING");
        assertThat(output).contains("--force");
        verifyNoInteractions(graphRepository, chunkRepository, documentRepository);
    }

    @Test
    void clear_withForce_deletesAllData() {
        AlexandriaCommands commands = new AlexandriaCommands(
                null, null, documentRepository, chunkRepository, graphRepository);

        String output = commands.clear(true);

        // Verify order: graph first, then chunks, then documents
        var inOrder = inOrder(graphRepository, chunkRepository, documentRepository);
        inOrder.verify(graphRepository).clearAll();
        inOrder.verify(chunkRepository).deleteAll();
        inOrder.verify(documentRepository).deleteAll();

        assertThat(output).contains("cleared");
    }
}
