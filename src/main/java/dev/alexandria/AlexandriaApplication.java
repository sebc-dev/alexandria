package dev.alexandria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Alexandria RAG application.
 *
 * <p>Supports two Spring profiles: {@code web} (REST + MCP SSE on port 8080)
 * and {@code stdio} (MCP stdio transport, no web server).
 */
@SpringBootApplication
public class AlexandriaApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlexandriaApplication.class, args);
    }
}
