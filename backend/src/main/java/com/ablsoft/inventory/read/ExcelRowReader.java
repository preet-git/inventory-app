package com.ablsoft.inventory.read;

import com.ablsoft.inventory.ImportException;
import com.ablsoft.inventory.model.RawRow;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Reads an .xls or .xlsx workbook in file order, evaluating formulas as it goes.
 *
 * <p><b>Finding the data.</b> The header row is located by looking for the six required columns
 * rather than assuming row 1 of sheet 1. Real exports put a title and a timestamp above the
 * header, and workbooks routinely carry a lookup sheet that the data sheet's formulas reference,
 * so the first sheet whose header row we recognise is the one we read. Getting this wrong does not
 * produce a subtly wrong import — it produces a file that will not import at all.
 *
 * <p><b>Threading.</b> POI's {@code Workbook}, {@code Sheet}, {@code Row}, {@code Cell} and
 * {@code FormulaEvaluator} are not thread safe. One workbook and one evaluator are created here
 * and confined to the thread that drives {@link #forEachRow}; cells become plain strings before
 * they leave, so no POI object escapes. Sharing an evaluator corrupts its dependency cache and
 * yields wrong values rather than an exception, which is the kind of bug that survives testing.
 */
public final class ExcelRowReader implements RowReader {

    private final Workbook workbook;
    private final FormulaEvaluator evaluator;
    private final int maxDataRows;

    ExcelRowReader(Path path, int maxDataRows) throws IOException {
        // Read-only: POI skips the structures it would only need in order to write the file back.
        this.workbook = WorkbookFactory.create(path.toFile(), null, true);
        this.evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        // A formula pointing at another workbook cannot be resolved here. Treat it as an
        // unevaluated cell rather than failing the whole import.
        this.evaluator.setIgnoreMissingWorkbooks(true);
        this.maxDataRows = maxDataRows;
    }

    /** Where the data starts, once we have found it. */
    private record Header(ColumnLayout layout, int rowIndex, List<String> closestCandidate) {
        boolean found() {
            return layout != null;
        }
    }

    @Override
    public void forEachRow(Consumer<RawRow> consumer) {
        List<String> closestCandidate = List.of();

        for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            Header header = findHeader(sheet);
            if (header.found()) {
                readDataRows(sheet, header, consumer);
                return;
            }
            if (closestCandidate.isEmpty()) {
                closestCandidate = header.closestCandidate();
            }
        }
        throw ColumnLayout.headerNotFound(closestCandidate);
    }

    @Override
    public void close() throws IOException {
        workbook.close();
    }

    /** Walks down a sheet looking for the six required headers, skipping title and note rows. */
    private Header findHeader(Sheet sheet) {
        List<String> closestCandidate = List.of();
        int scanned = 0;

        for (Row row : sheet) {
            if (isBlank(row)) {
                continue;
            }
            List<String> cells = headerCells(row);
            Optional<ColumnLayout> layout = ColumnLayout.tryResolve(cells);
            if (layout.isPresent()) {
                return new Header(layout.get(), row.getRowNum(), cells);
            }
            if (closestCandidate.isEmpty()) {
                closestCandidate = cells;
            }
            if (++scanned >= HEADER_SCAN_LIMIT) {
                break;
            }
        }
        return new Header(null, -1, closestCandidate);
    }

    private void readDataRows(Sheet sheet, Header header, Consumer<RawRow> consumer) {
        // Cheap upper bound from the sheet's own row count, before a single cell is touched.
        // Opening a workbook is what makes a file resident in memory, so this is the last chance
        // to refuse an enormous one without paying to parse it.
        if (sheet.getLastRowNum() - header.rowIndex() > maxDataRows) {
            throw ImportException.invalidFile("File contains more than " + maxDataRows + " data rows.");
        }

        int dataRows = 0;
        for (Row row : sheet) {
            if (row.getRowNum() <= header.rowIndex() || isBlank(row)) {
                continue;
            }
            if (++dataRows > maxDataRows) {
                throw ImportException.invalidFile("File contains more than " + maxDataRows + " data rows.");
            }

            int rowNumber = row.getRowNum() + 1;   // POI counts from 0; users count from 1
            try {
                consumer.accept(header.layout().toRawRow(rowNumber, readCells(row, header.layout())));
            } catch (FormulaFailure failure) {
                // Reject this row and carry on, rather than failing the whole import.
                consumer.accept(RawRow.unreadable(rowNumber, failure.getMessage()));
            }
        }
    }

    /**
     * Reads a candidate header row without evaluating anything. A header is text; refusing to
     * evaluate here means a sheet full of broken formulas cannot throw while we are still working
     * out whether it is even the sheet we want.
     */
    private static List<String> headerCells(Row row) {
        int width = Math.max(row.getLastCellNum(), 0);
        List<String> cells = new ArrayList<>(width);
        for (int i = 0; i < width; i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            cells.add(cell != null && cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : "");
        }
        return cells;
    }

    private List<String> readCells(Row row, ColumnLayout layout) {
        List<String> cells = new ArrayList<>(layout.width());
        for (int i = 0; i < layout.width(); i++) {
            cells.add(cellText(row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL), i, layout));
        }
        return cells;
    }

    /** Converts one cell to text, evaluating formulas on this thread and this thread only. */
    private String cellText(Cell cell, int index, ColumnLayout layout) {
        if (cell == null) {
            return "";
        }
        if (cell.getCellType() != CellType.FORMULA) {
            return literalText(cell);
        }

        CellValue evaluated;
        try {
            evaluated = evaluator.evaluate(cell);
        } catch (RuntimeException e) {
            // POI throws several unrelated runtime types for unsupported or malformed formulas.
            throw new FormulaFailure(columnName(index, layout), cell, e.getMessage());
        }
        if (evaluated == null) {
            throw new FormulaFailure(columnName(index, layout), cell, "evaluation produced no value");
        }

        return switch (evaluated.getCellType()) {
            case STRING -> evaluated.getStringValue();
            case NUMERIC -> numericText(cell, evaluated.getNumberValue());
            case BOOLEAN -> Boolean.toString(evaluated.getBooleanValue());
            case ERROR -> throw new FormulaFailure(
                    columnName(index, layout), cell, errorName(evaluated.getErrorValue()));
            default -> "";
        };
    }

    private static String literalText(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> numericText(cell, cell.getNumericCellValue());
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /**
     * Excel stores dates as numbers, so a date cell is recognised by its format and emitted as ISO
     * text, which the date normaliser then reads back. Everything else becomes a plain decimal
     * string with no exponent notation, because {@code BigDecimal} has to parse it later.
     */
    private static String numericText(Cell cell, double value) {
        if (isDateFormatted(cell)) {
            return DateTimeFormatter.ISO_LOCAL_DATE.format(DateUtil.getLocalDateTime(value).toLocalDate());
        }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static boolean isDateFormatted(Cell cell) {
        try {
            return DateUtil.isCellDateFormatted(cell);
        } catch (IllegalStateException e) {
            return false;   // a cached formula result that is not numeric
        }
    }

    private static String errorName(byte errorCode) {
        try {
            return FormulaError.forInt(errorCode).getString();   // "#DIV/0!", "#REF!", ...
        } catch (IllegalArgumentException e) {
            return "unknown error";
        }
    }

    private static String columnName(int index, ColumnLayout layout) {
        return layout.columnAt(index)
                .map(ColumnLayout.Column::header)
                .orElse("Column " + (index + 1));
    }

    private static boolean isBlank(Row row) {
        if (row == null || row.getLastCellNum() < 0) {
            return true;
        }
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    /** Internal signal, caught one row later. Never escapes this class. */
    private static final class FormulaFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        FormulaFailure(String columnName, Cell cell, String detail) {
            super(columnName + " formula '" + safeFormula(cell) + "' could not be evaluated: " + detail,
                    null, false, false);
        }

        private static String safeFormula(Cell cell) {
            try {
                return "=" + cell.getCellFormula();
            } catch (RuntimeException e) {
                return "unknown";
            }
        }
    }
}
