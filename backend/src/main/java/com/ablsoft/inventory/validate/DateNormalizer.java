package com.ablsoft.inventory.validate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.DateUtil;

/**
 * Turns whatever a spreadsheet calls a date into a {@link LocalDate}.
 *
 * <p>This runs before the uniqueness check, and that order is the point: as text, "2024-03-16" and
 * "03/16/2024" are two different keys for the same purchase, so a duplicate would slip through.
 * Normalising first means SKU + date is compared as a real date.
 *
 * <p>Patterns are tried in order, and the order is the policy. A genuinely ambiguous value like
 * 03/04/2024 is read month-first, as 4 March 2024. Patterns that cannot be misread — a four-digit
 * year first, a month spelled out, a day above twelve — resolve on their own regardless of
 * position. Parsing is strict, so 2024-02-31 is rejected rather than rolled forward to 2 March.
 *
 * <p>{@code uuuu} rather than {@code yyyy} throughout: under {@link ResolverStyle#STRICT},
 * {@code yyyy} means year-of-era and needs an era field to resolve, so a strict {@code yyyy}
 * parser rejects every date it is given.
 */
public final class DateNormalizer {

    /**
     * Single-letter field patterns ({@code M}, {@code d}) accept one or two digits, so "3/5/2024"
     * and "03/05/2024" are both covered without listing width variants.
     */
    private static final List<DateTimeFormatter> PATTERNS = Stream.of(
                    "uuuu-M-d",        // ISO, and what the Excel reader emits for real date cells
                    "M/d/uuuu",        // month first: the preferred reading of an ambiguous value
                    "d/M/uuuu",
                    "uuuu/M/d",
                    "M-d-uuuu",
                    "d-M-uuuu",
                    "d MMMM uuuu",
                    "MMMM d, uuuu",
                    "MMMM d uuuu",
                    "d MMM uuuu",
                    "MMM d, uuuu",
                    "MMM d uuuu")
            .map(pattern -> DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                    .withResolverStyle(ResolverStyle.STRICT))
            .toList();

    /** Excel's own range: serial 1 is 1900-01-01 and 2958465 is 9999-12-31. */
    private static final long MIN_EXCEL_SERIAL = 1;
    private static final long MAX_EXCEL_SERIAL = 2958465;

    private static final int ISO_DATE_LENGTH = 10;

    private DateNormalizer() {
    }

    /** Empty means "present but not a date we recognise" — the caller checks for blank first. */
    public static Optional<LocalDate> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim();

        // An ISO timestamp is a date with baggage; keep the date and drop the time.
        if (value.length() > ISO_DATE_LENGTH && value.charAt(ISO_DATE_LENGTH) == 'T') {
            value = value.substring(0, ISO_DATE_LENGTH);
        }

        for (DateTimeFormatter formatter : PATTERNS) {
            try {
                // LocalDate.parse rejects trailing characters, so "15/03/2024 (approx)" fails
                // rather than being silently truncated to a date.
                return Optional.of(LocalDate.parse(value, formatter));
            } catch (DateTimeParseException e) {
                // Not this pattern. A failure here is the normal path, not an error.
            }
        }
        return excelSerial(value);
    }

    /**
     * A bare number in the date column is Excel's own storage leaking through, which happens when
     * a date cell is copied into a general-formatted one. POI's conversion carries the 1900
     * leap-year bug that Excel itself has, so the answer matches what the user sees in Excel.
     */
    private static Optional<LocalDate> excelSerial(String value) {
        if (!value.matches("\\d{1,7}")) {
            return Optional.empty();
        }
        long serial = Long.parseLong(value);
        if (serial < MIN_EXCEL_SERIAL || serial > MAX_EXCEL_SERIAL) {
            return Optional.empty();
        }
        return Optional.of(DateUtil.getLocalDateTime(serial).toLocalDate());
    }
}
