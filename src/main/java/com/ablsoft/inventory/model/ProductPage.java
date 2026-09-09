package com.ablsoft.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One page of the dashboard table.
 *
 * <p>Cursor-paged rather than numbered: {@code nextCursor} is fed back as the {@code cursor}
 * parameter to fetch the next page. There is no total count here on purpose — counting the table
 * on every page request is the cost this style of paging exists to avoid. The summary endpoint
 * carries the total.
 */
public record ProductPage(
        List<Row> content,
        int size,
        String sortBy,
        String direction,
        String nextCursor,
        boolean hasMore) {

    /** A table row, including the two columns the dashboard derives rather than stores. */
    public record Row(
            int rowNumber,
            String productSku,
            String productName,
            String category,
            LocalDate purchaseDate,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineValue,
            long stockAgeDays) {
    }
}
