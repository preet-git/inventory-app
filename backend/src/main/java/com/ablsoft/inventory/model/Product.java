package com.ablsoft.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A row that passed every rule. */
public record Product(
        int rowNumber,
        String productSku,
        String productName,
        String category,
        LocalDate purchaseDate,
        BigDecimal unitPrice,
        int quantity) {

    /**
     * The combination the brief requires to be unique.
     *
     * <p>The SKU is already trimmed and upper-cased and the date is already a real date, so
     * "sp-100053" and " SP-100053 " collide as they should, and so do "2024-03-16" and
     * "03/16/2024".
     */
    public String uniqueKey() {
        return uniqueKey(productSku, purchaseDate);
    }

    /** The same key for a row that is already stored, so both sides agree on what collides. */
    public static String uniqueKey(String productSku, LocalDate purchaseDate) {
        return productSku + "|" + purchaseDate;
    }

    public BigDecimal lineValue() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** Negative for a future purchase date, which is the honest answer rather than zero. */
    public long stockAgeDays(LocalDate asOf) {
        return ChronoUnit.DAYS.between(purchaseDate, asOf);
    }
}
