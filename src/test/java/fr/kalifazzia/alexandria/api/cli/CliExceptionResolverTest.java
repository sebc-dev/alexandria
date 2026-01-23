package fr.kalifazzia.alexandria.api.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.shell.command.CommandHandlingResult;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CliExceptionResolver.
 * Tests exception to exit code mapping for CLI commands.
 */
class CliExceptionResolverTest {

    private CliExceptionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CliExceptionResolver();
    }

    // =====================
    // IllegalArgumentException tests
    // =====================

    @Test
    void resolve_illegalArgumentException_withMessage_returnsCode1() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid parameter");

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.message()).isEqualTo("Error: Invalid parameter\n");
    }

    @Test
    void resolve_illegalArgumentException_withNullMessage_returnsClassNameFallback() {
        IllegalArgumentException ex = new IllegalArgumentException((String) null);

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.message()).isEqualTo("Error: IllegalArgumentException\n");
    }

    @Test
    void resolve_illegalArgumentException_withBlankMessage_returnsClassNameFallback() {
        IllegalArgumentException ex = new IllegalArgumentException("   ");

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.message()).isEqualTo("Error: IllegalArgumentException\n");
    }

    @Test
    void resolve_illegalArgumentException_withEmptyMessage_returnsClassNameFallback() {
        IllegalArgumentException ex = new IllegalArgumentException("");

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.message()).isEqualTo("Error: IllegalArgumentException\n");
    }

    // =====================
    // IOException tests
    // =====================

    @Test
    void resolve_ioException_withMessage_returnsCode2() {
        IOException ex = new IOException("File not found");

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.message()).isEqualTo("I/O Error: File not found\n");
    }

    @Test
    void resolve_ioException_withCause_returnsCauseMessage() {
        IOException cause = new IOException("Root cause message");
        IOException ex = new IOException("Wrapper message", cause);

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.message()).isEqualTo("I/O Error: Root cause message\n");
    }

    @Test
    void resolve_ioException_withNullMessage_andNoCause_returnsClassNameFallback() {
        IOException ex = new IOException((String) null);

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.message()).isEqualTo("I/O Error: IOException\n");
    }

    @Test
    void resolve_ioException_withBlankMessage_andNoCause_returnsClassNameFallback() {
        IOException ex = new IOException("   ");

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.message()).isEqualTo("I/O Error: IOException\n");
    }

    // =====================
    // UncheckedIOException tests
    // =====================

    @Test
    void resolve_uncheckedIOException_returnsCode2() {
        IOException cause = new IOException("Wrapped IO error");
        UncheckedIOException ex = new UncheckedIOException(cause);

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.message()).isEqualTo("I/O Error: Wrapped IO error\n");
    }

    @Test
    void resolve_uncheckedIOException_withNullCauseMessage_returnsClassNameFallback() {
        IOException cause = new IOException((String) null);
        UncheckedIOException ex = new UncheckedIOException(cause);

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNotNull();
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.message()).isEqualTo("I/O Error: UncheckedIOException\n");
    }

    // =====================
    // Other exception tests
    // =====================

    @Test
    void resolve_runtimeException_returnsNull() {
        RuntimeException ex = new RuntimeException("Some error");

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNull();
    }

    @Test
    void resolve_nullPointerException_returnsNull() {
        NullPointerException ex = new NullPointerException("Null reference");

        CommandHandlingResult result = resolver.resolve(ex);

        assertThat(result).isNull();
    }
}
