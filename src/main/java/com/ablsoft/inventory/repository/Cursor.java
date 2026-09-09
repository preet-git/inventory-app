package com.ablsoft.inventory.repository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Where the previous page stopped: the sort column's value, plus the row id that breaks its ties.
 *
 * <p>Encoded opaquely so callers treat it as a token rather than something to assemble by hand --
 * the id in particular is an internal detail. This is encoding, not security: the value is
 * re-validated on the way in and only ever reaches SQL as a bound parameter.
 *
 * <p>Split on the LAST separator, so a sort value containing the separator character is still
 * decoded correctly. Only the id is guaranteed not to contain one.
 */
public record Cursor(String sortValue, long id) {

    private static final char SEPARATOR = '|';

    public String encode() {
        String raw = sortValue + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static Cursor decode(String encoded) {
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cursor is not a valid token", e);
        }
        int separator = decoded.lastIndexOf(SEPARATOR);
        if (separator < 0) {
            throw new IllegalArgumentException("Cursor is not a valid token");
        }
        try {
            return new Cursor(decoded.substring(0, separator),
                    Long.parseLong(decoded.substring(separator + 1)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cursor is not a valid token", e);
        }
    }
}
