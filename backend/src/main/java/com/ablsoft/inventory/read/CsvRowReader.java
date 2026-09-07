package com.ablsoft.inventory.read;

import com.ablsoft.inventory.ImportException;
import com.ablsoft.inventory.model.RawRow;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * Streams a UTF-8 CSV file record by record.
 *
 * <p>Encoding is enforced rather than repaired: a lenient decoder would substitute U+FFFD for bad
 * bytes and turn a mis-encoded file into a file full of corrupted SKUs, which is much harder to
 * notice than an error.
 */
public final class CsvRowReader implements RowReader {

    /**
     * Blank lines are kept rather than skipped, so Commons CSV's record numbers stay equal to the
     * line numbers the user sees in their editor and a reported row number points at the right
     * line.
     */
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setIgnoreEmptyLines(false)
            .setTrim(false)
            .get();

    /** U+FEFF, written as a code point because the character itself is invisible in source. */
    private static final int BYTE_ORDER_MARK = 0xFEFF;

    private final Reader reader;
    private final int maxDataRows;

    CsvRowReader(InputStream inputStream, int maxDataRows) {
        CharsetDecoder strictUtf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        this.reader = new InputStreamReader(inputStream, strictUtf8);
        this.maxDataRows = maxDataRows;
    }

    @Override
    public void forEachRow(Consumer<RawRow> consumer) throws IOException {
        ColumnLayout layout = null;
        List<String> closestCandidate = List.of();
        int scanned = 0;
        int dataRows = 0;

        try (CSVParser parser = FORMAT.parse(reader)) {
            for (CSVRecord record : parser) {
                List<String> cells = toList(record);
                if (isBlank(cells)) {
                    continue;
                }

                if (layout == null) {
                    stripByteOrderMark(cells);
                    Optional<ColumnLayout> resolved = ColumnLayout.tryResolve(cells);
                    if (resolved.isPresent()) {
                        layout = resolved.get();
                        continue;
                    }
                    if (closestCandidate.isEmpty()) {
                        closestCandidate = cells;
                    }
                    if (++scanned >= HEADER_SCAN_LIMIT) {
                        break;
                    }
                    continue;
                }

                if (++dataRows > maxDataRows) {
                    throw ImportException.invalidFile(
                            "File contains more than " + maxDataRows + " data rows.");
                }
                // Record numbers count from 1 and include the header, so they are the row numbers
                // the user sees. Safe to narrow: the row cap bounds this long before int does.
                consumer.accept(layout.toRawRow((int) record.getRecordNumber(), cells));
            }
        } catch (RuntimeException e) {
            // Commons CSV wraps read failures, and the wrapper type has changed across versions.
            // Inspect the cause chain rather than betting on one of them.
            CharacterCodingException encodingFailure = encodingFailure(e);
            if (encodingFailure != null) {
                throw ImportException.invalidFile(
                        "File is not valid UTF-8. Re-save the CSV as UTF-8.", encodingFailure);
            }
            throw e;
        }

        if (layout == null) {
            throw ColumnLayout.headerNotFound(closestCandidate);
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    private static List<String> toList(CSVRecord record) {
        List<String> cells = new ArrayList<>(record.size());
        record.forEach(cells::add);
        return cells;
    }

    private static boolean isBlank(List<String> cells) {
        return cells.stream().allMatch(cell -> cell == null || cell.isBlank());
    }

    private static void stripByteOrderMark(List<String> headerCells) {
        if (headerCells.isEmpty()) {
            return;
        }
        String first = headerCells.get(0);
        if (first != null && !first.isEmpty() && first.charAt(0) == BYTE_ORDER_MARK) {
            headerCells.set(0, first.substring(1));
        }
    }

    private static CharacterCodingException encodingFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof CharacterCodingException failure) {
                return failure;
            }
        }
        return null;
    }
}
