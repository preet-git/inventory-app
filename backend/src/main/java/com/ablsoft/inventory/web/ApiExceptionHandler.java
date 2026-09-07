package com.ablsoft.inventory.web;

import com.ablsoft.inventory.ImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Turns failures into HTTP responses. The only place that decides a status code, and every error
 * body has the same two fields, so a client parses one shape regardless of what went wrong.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ApiError(String code, String message) {
    }

    @ExceptionHandler(ImportException.class)
    public ResponseEntity<ApiError> handleImportFailure(ImportException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage()));
    }

    /** A bad sortBy or direction; the message already names the valid values. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadParameter(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ApiError("invalid_request", e.getMessage()));
    }

    /** Tomcat rejected the body before our own size check could run. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_file", "Uploaded file exceeds the maximum permitted size."));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleMissingFile(Exception e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("missing_parameter", "Required request part 'file' is missing."));
    }

    /** Last resort: the caller gets a generic message, the log gets the stack trace. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Unexpected failure handling request", e);
        return ResponseEntity.internalServerError()
                .body(new ApiError("internal_error", "An unexpected error occurred."));
    }
}
