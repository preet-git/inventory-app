package com.ablsoft.inventory.repository;

import com.ablsoft.inventory.model.InventorySummary;
import com.ablsoft.inventory.model.ProductPage;
import java.time.LocalDate;

/** The hand-written SQL: the two queries the dashboard needs. */
public interface ProductRepositoryCustom {

    ProductPage page(String cursor, int size, String sortBy, String direction, LocalDate asOf);

    InventorySummary summary(LocalDate asOf);
}
