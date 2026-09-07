package com.ablsoft.inventory.model;

/**
 * One row exactly as it came out of the file: six cells of text, before any rule is applied.
 *
 * <p>Cells are already plain strings by this point, so nothing downstream touches Apache POI.
 */
public record RawRow(
        int rowNumber,
        String productSku,
        String productName,
        String category,
        String purchaseDate,
        String unitPrice,
        String quantity,
        String readError) {

    public static RawRow of(int rowNumber, String productSku, String productName, String category,
                            String purchaseDate, String unitPrice, String quantity) {
        return new RawRow(rowNumber, productSku, productName, category, purchaseDate, unitPrice, quantity, null);
    }

    /** A row the reader could not turn into text at all, such as a cell holding {@code #DIV/0!}. */
    public static RawRow unreadable(int rowNumber, String readError) {
        return new RawRow(rowNumber, "", "", "", "", "", "", readError);
    }

    public boolean isUnreadable() {
        return readError != null;
    }
}
