package com.ablsoft.inventory.read;

import com.ablsoft.inventory.model.RawRow;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Streams a file's data rows as text, one at a time, in file order. */
public interface RowReader extends Closeable {

    /**
     * How far down a sheet we look for the header row. Exports commonly put a company name and an
     * export timestamp above it, so insisting on row 1 would reject perfectly good files.
     */
    int HEADER_SCAN_LIMIT = 20;

    void forEachRow(Consumer<RawRow> consumer) throws IOException;

    /**
     * There is no row limit. Both readers stream, so a file's size bounds how long an import
     * takes rather than how much memory it needs.
     */
    static RowReader open(Path path, FileType type) throws IOException {
        return type.isExcel()
                ? new ExcelRowReader(path, type)
                : new CsvRowReader(Files.newInputStream(path));
    }
}
