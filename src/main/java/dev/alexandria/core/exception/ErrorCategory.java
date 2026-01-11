package dev.alexandria.core.exception;

/** Categorizes errors for consistent error handling and user messaging. */
public enum ErrorCategory {
  VALIDATION("Validation Error", "Check your query and try again"),
  NOT_FOUND("Not Found", "The requested resource doesn't exist"),
  SERVICE_UNAVAILABLE("Service Unavailable", "Retry in a few seconds"),
  INGESTION_FAILED("Ingestion Failed", "Check the document format and try again"),
  DATABASE_ERROR("Database Error", "Contact support if the problem persists"),
  TIMEOUT("Timeout", "Try with a simpler query");

  private final String title;
  private final String suggestedAction;

  ErrorCategory(String title, String suggestedAction) {
    this.title = title;
    this.suggestedAction = suggestedAction;
  }

  public String title() {
    return title;
  }

  public String suggestedAction() {
    return suggestedAction;
  }
}
