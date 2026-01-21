package fr.kalifazzia.alexandria.api.cli;

import org.springframework.shell.command.CommandExceptionResolver;
import org.springframework.shell.command.CommandHandlingResult;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Maps exceptions to appropriate exit codes for CLI commands.
 * Enables proper automation: exit code 0 = success, non-zero = error.
 */
@Component
public class CliExceptionResolver implements CommandExceptionResolver {

    @Override
    public CommandHandlingResult resolve(Exception e) {
        if (e instanceof IllegalArgumentException) {
            // User error (invalid arguments)
            return CommandHandlingResult.of("Error: " + e.getMessage() + "\n", 1);
        }
        if (e instanceof IOException || e instanceof UncheckedIOException) {
            // I/O error (file not found, permission denied, etc.)
            String message = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            if (message == null || message.isBlank()) {
                message = e.getClass().getSimpleName();
            }
            return CommandHandlingResult.of("I/O Error: " + message + "\n", 2);
        }
        // Let other exceptions propagate (Spring Shell handles them)
        return null;
    }
}
