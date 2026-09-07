package com.ablsoft.inventory.read;

import com.ablsoft.inventory.ImportException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.apache.poi.poifs.filesystem.FileMagic;

/**
 * The three file types we accept, and the check that a file really is what its name claims.
 *
 * <p>The extension chooses the parser; the leading bytes decide whether to believe it. That second
 * check is what refuses an .exe renamed to .xlsx, or a workbook saved as .csv, before any parser
 * opens the file. The browser's declared MIME type is deliberately not consulted — it is wrong
 * often enough (Windows reports {@code application/vnd.ms-excel} for .csv) to be worse than
 * useless next to the actual bytes.
 */
public enum FileType {

    CSV(".csv"),
    XLS(".xls"),
    XLSX(".xlsx");

    private final String extension;

    FileType(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public boolean isExcel() {
        return this != CSV;
    }

    /**
     * @throws ImportException 415 when the extension is not one we handle, 400 when the file's
     *                         contents contradict its extension
     */
    public static FileType detect(String fileName, Path path) throws IOException {
        String name = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        FileType type = null;
        for (FileType candidate : values()) {
            if (name.endsWith(candidate.extension)) {
                type = candidate;
                break;
            }
        }
        if (type == null) {
            throw ImportException.unsupportedType("'" + describe(fileName)
                    + "' is not a supported file. Upload a .csv, .xls or .xlsx file.");
        }

        FileMagic magic;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            magic = FileMagic.valueOf(FileMagic.prepareToCheckMagic(in));
        }
        // CSV has no magic number, so the test is inverted: it must not be a workbook container.
        boolean contentMatches = switch (type) {
            case XLSX -> magic == FileMagic.OOXML;
            case XLS -> magic == FileMagic.OLE2;
            case CSV -> magic != FileMagic.OOXML && magic != FileMagic.OLE2;
        };
        if (!contentMatches) {
            throw ImportException.invalidFile("'" + describe(fileName) + "' is not a real "
                    + type.extension + " file; its contents do not match its extension.");
        }
        return type;
    }

    private static String describe(String fileName) {
        return fileName == null || fileName.isBlank() ? "(unnamed file)" : fileName;
    }
}
