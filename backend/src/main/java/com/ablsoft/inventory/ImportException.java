package com.ablsoft.inventory;

import org.springframework.http.HttpStatus;

/**
 * Anything that stops a whole file being imported, carrying the status the API should return.
 *
 * <p>A problem with a single row is never one of these: bad rows are rejected and reported, and
 * the rest of the file still imports.
 */
public class ImportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String code;

    private ImportException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    /** A file we can parse, whose contents we cannot use. */
    public static ImportException invalidFile(String message) {
        return new ImportException(HttpStatus.BAD_REQUEST, "invalid_file", message, null);
    }

    public static ImportException invalidFile(String message, Throwable cause) {
        return new ImportException(HttpStatus.BAD_REQUEST, "invalid_file", message, cause);
    }

    /** Not a .csv, .xls or .xlsx file at all. */
    public static ImportException unsupportedType(String message) {
        return new ImportException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_file_type", message, null);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
