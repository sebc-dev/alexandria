package dev.alexandria.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global REST error handler that maps application exceptions to RFC 9457 Problem Detail responses.
 *
 * <p>Currently maps {@link IllegalArgumentException} to HTTP 400 Bad Request. Additional exception
 * mappings can be added as the API surface grows.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Maps {@link IllegalArgumentException} to a 400 Bad Request Problem Detail.
   *
   * @param ex the exception thrown by validation logic
   * @return a Problem Detail with HTTP 400 status and the exception message
   */
  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
  }
}
