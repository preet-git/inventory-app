package com.ablsoft.inventory.model;

import java.math.BigDecimal;
import java.util.List;

/** What an import produced, in the two sections the brief asks for. */
public record ImportResult(Summary summary, Rejected rejectedRows) {

    /** Section 1: the totals, and the figures the dashboard summary card shows. */
    public record Summary(
            String fileName,
            int rowsRead,
            int importedCount,
            int rejectedCount,
            BigDecimal totalInventoryValue,
            BigDecimal averageStockAgeDays) {
    }

    /** Section 2: which rows were dropped, by the row number shown in the file. */
    public record Rejected(int count, List<Integer> rowNumbers) {
    }

    public static ImportResult empty() {
        return new ImportResult(
                new Summary("", 0, 0, 0, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2)),
                new Rejected(0, List.of()));
    }
}
