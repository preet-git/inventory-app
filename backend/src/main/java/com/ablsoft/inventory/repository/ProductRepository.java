package com.ablsoft.inventory.repository;

import com.ablsoft.inventory.entity.ProductEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Products, written through Spring Data and read through the custom fragment.
 *
 * <p>The dashboard queries stay hand-written: keyset pagination over a sortable column and the
 * summary aggregate are both awkward to express as derived queries and easy to express as SQL.
 */
public interface ProductRepository extends JpaRepository<ProductEntity, Long>, ProductRepositoryCustom {

    /**
     * The rows a chunk might collide with.
     *
     * <p>Matching on the two key columns separately returns a superset — a SKU from one row paired
     * with a date from another — so the caller still has to match on the exact pair. It is a much
     * tighter superset than filtering on SKU alone, and it stays a derived query.
     */
    List<ProductEntity> findByProductSkuInAndPurchaseDateIn(
            Collection<String> productSkus, Collection<LocalDate> purchaseDates);
}
