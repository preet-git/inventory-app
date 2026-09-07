package com.ablsoft.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** One page of the dashboard table. */
public record ProductPage(
        List<Row> content,
        int page,
        int size,
        int totalElements,
        int totalPages,
        String sortBy,
        String direction) {

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

        public static Row from(Product product, LocalDate asOf) {
            return new Row(
                    product.rowNumber(),
                    product.productSku(),
                    product.productName(),
                    product.category(),
                    product.purchaseDate(),
                    product.unitPrice(),
                    product.quantity(),
                    product.lineValue(),
                    product.stockAgeDays(asOf));
        }
    }
}
