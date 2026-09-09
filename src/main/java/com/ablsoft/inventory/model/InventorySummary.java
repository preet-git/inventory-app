package com.ablsoft.inventory.model;

import java.math.BigDecimal;

/**
 * The dashboard summary card: the whole inventory, not one import.
 *
 * <p>Computed by a single aggregate query. That is a scan, which is fine at these volumes; if the
 * table grows or the card is polled hard, a rollup table or materialised view refreshed after each
 * import is the next step.
 */
public record InventorySummary(
        long totalProducts,
        BigDecimal totalInventoryValue,
        BigDecimal averageStockAgeDays) {
}
