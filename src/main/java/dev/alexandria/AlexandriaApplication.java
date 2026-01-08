package dev.alexandria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Alexandria RAG Server - MCP Server exposing semantic documentation search. */
@SpringBootApplication
public class AlexandriaApplication {

  /**
   * Application entry point.
   *
   * @param args command line arguments
   */
  public static void main(final String[] args) {
    SpringApplication.run(AlexandriaApplication.class, args);
  }
}
