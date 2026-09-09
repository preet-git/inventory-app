package com.ablsoft.inventory.read;

import com.ablsoft.inventory.ImportException;
import com.ablsoft.inventory.model.RawRow;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Maps the six required headers to their column positions.
 *
 * <p>Resolved once per file, so reading a row is an array index rather than a name lookup, and a
 * file with the wrong columns fails immediately instead of producing one rejection per row.
 * Matching ignores case and collapses runs of whitespace, so {@code "  product   sku "} matches
 * {@code "Product SKU"}. Extra columns are ignored: spreadsheets routinely carry notes columns,
 * and refusing them would fail files that contain everything we need.
 */
public final class ColumnLayout {

    public enum Column {

        PRODUCT_SKU("Product SKU"),
        PRODUCT_NAME("Product Name"),
        CATEGORY("Category"),
        PURCHASE_DATE("Purchase Date"),
        UNIT_PRICE("Unit Price"),
        QUANTITY("Quantity");

        private final String header;

        Column(String header) {
            this.header = header;
        }

        public String header() {
            return header;
        }
    }

    private final Map<Column, Integer> positions;
    private final int width;

    private ColumnLayout(Map<Column, Integer> positions) {
        this.positions = new EnumMap<>(positions);
        this.width = positions.values().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
    }

    /**
     * Resolves a candidate row, or empty when it does not carry all six headers.
     *
     * <p>Returning empty rather than throwing is what lets a reader walk down a sheet looking for
     * the header, instead of assuming it is the first row.
     */
    public static Optional<ColumnLayout> tryResolve(List<String> cells) {
        Map<Column, Integer> found = new EnumMap<>(Column.class);
        for (int i = 0; i < cells.size(); i++) {
            String normalised = normalise(cells.get(i));
            if (normalised.isEmpty()) {
                continue;
            }
            for (Column column : Column.values()) {
                if (!found.containsKey(column) && normalise(column.header()).equals(normalised)) {
                    found.put(column, i);
                }
            }
        }
        return found.size() == Column.values().length ? Optional.of(new ColumnLayout(found)) : Optional.empty();
    }

    /**
     * The failure for a file with no usable header row anywhere.
     *
     * @param closestCandidate the first row that looked like a header, used to name what it was
     *                         missing; empty when the file had no rows to judge
     */
    public static ImportException headerNotFound(List<String> closestCandidate) {
        List<String> present = closestCandidate.stream().map(ColumnLayout::normalise).toList();
        List<String> missing = new ArrayList<>();
        for (Column column : Column.values()) {
            if (!present.contains(normalise(column.header()))) {
                missing.add(column.header());
            }
        }
        return ImportException.invalidFile(
                "No header row found. Missing required columns: " + String.join(", ", missing) + ".");
    }

    /** Number of cells a reader must materialise per row to cover every required column. */
    public int width() {
        return width;
    }

    /** Which required column sits at this index, if any. Used to name a column in an error. */
    public Optional<Column> columnAt(int index) {
        return positions.entrySet().stream()
                .filter(entry -> entry.getValue() == index)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** Maps one row's cells onto the six fields, tolerating rows shorter than the header. */
    public RawRow toRawRow(int rowNumber, List<String> cells) {
        return RawRow.of(rowNumber,
                valueAt(cells, Column.PRODUCT_SKU),
                valueAt(cells, Column.PRODUCT_NAME),
                valueAt(cells, Column.CATEGORY),
                valueAt(cells, Column.PURCHASE_DATE),
                valueAt(cells, Column.UNIT_PRICE),
                valueAt(cells, Column.QUANTITY));
    }

    private String valueAt(List<String> cells, Column column) {
        int index = positions.get(column);
        return index < cells.size() ? cells.get(index) : "";
    }

    private static String normalise(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
