package com.ablsoft.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A stored inventory row.
 *
 * <p>The unique constraint on (product_sku, purchase_date) is the brief's rule, enforced by the
 * database rather than by application code — which is what makes it hold even against a concurrent
 * import or a direct SQL insert.
 *
 * <p>Rows are read and written through this entity: an import loads the rows a chunk would
 * collide with, updates those, and leaves the rest to be inserted.
 */
@Entity
@Table(name = "product", uniqueConstraints =
        @UniqueConstraint(name = "product_unique_sku_date", columnNames = {"product_sku", "purchase_date"}))
public class ProductEntity {

    /**
     * A sequence rather than IDENTITY so that saveAll can batch: Hibernate claims a block of ids
     * up front instead of round-tripping each insert to read its generated key. allocationSize is
     * the block size, and V2__product_id_sequence.sql sets the sequence to step by the same
     * amount -- the two must match or chunks get overlapping ids.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_id_generator")
    @SequenceGenerator(name = "product_id_generator", sequenceName = "product_id_seq", allocationSize = 1000)
    private Long id;

    /** The run that last wrote this row; a re-import overwrites it. */
    @Column(name = "import_id", nullable = false)
    private Long importId;

    /** The row number in the uploaded file, so a stored row can be traced back to its source. */
    @Column(name = "source_row_number", nullable = false)
    private int sourceRowNumber;

    @Column(name = "product_sku", nullable = false, length = 64)
    private String productSku;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private String category;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductEntity() {
        // for JPA
    }

    /**
     * A row that is not in the table yet. Only the natural key is taken here -- everything else is
     * set through the setters, by the same code that refreshes an existing row, so a new row and
     * an updated one cannot drift apart.
     */
    public ProductEntity(String productSku, LocalDate purchaseDate) {
        this.productSku = productSku;
        this.purchaseDate = purchaseDate;
    }

    public Long getId() {
        return id;
    }

    public Long getImportId() {
        return importId;
    }

    public int getSourceRowNumber() {
        return sourceRowNumber;
    }

    public String getProductSku() {
        return productSku;
    }

    public String getProductName() {
        return productName;
    }

    public String getCategory() {
        return category;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setImportId(Long importId) {
        this.importId = importId;
    }

    public void setSourceRowNumber(int sourceRowNumber) {
        this.sourceRowNumber = sourceRowNumber;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
