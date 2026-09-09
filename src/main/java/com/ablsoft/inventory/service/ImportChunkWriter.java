package com.ablsoft.inventory.service;

import com.ablsoft.inventory.entity.ImportRunEntity;
import com.ablsoft.inventory.entity.ImportStatus;
import com.ablsoft.inventory.entity.ProductEntity;
import com.ablsoft.inventory.model.Product;
import com.ablsoft.inventory.model.RejectedRow;
import com.ablsoft.inventory.repository.ImportRejectionRepository;
import com.ablsoft.inventory.repository.ImportRunRepository;
import com.ablsoft.inventory.repository.ProductRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything an import writes, one transaction at a time.
 *
 * <p>A separate component because Spring's transaction support works through a proxy: were these
 * methods on {@code ImportService} and called from its own worker loop, the calls would bypass the
 * proxy and none of them would be transactional.
 *
 * <p>Each chunk is its own transaction. That is what keeps memory flat over a file of any size,
 * and what lets the status endpoint see progress — an uncommitted counter is invisible to the
 * connection serving the poll. The trade is that an import is not atomic: if it fails half way, the
 * chunks already committed stay. Since a re-import refreshes rows rather than duplicating them,
 * that is the normal batch-processing model — running the file again simply brings it up to date.
 */
@Component
public class ImportChunkWriter {

    private final ProductRepository products;
    private final ImportRejectionRepository rejections;
    private final ImportRunRepository runs;

    public ImportChunkWriter(ProductRepository products,
                             ImportRejectionRepository rejections,
                             ImportRunRepository runs) {
        this.products = products;
        this.rejections = rejections;
        this.runs = runs;
    }

    @Transactional
    public void writeChunk(long importId,
                           List<Product> accepted,
                           List<RejectedRow> rejected,
                           int rowsRead,
                           int rowsImported,
                           int rowsRejected) {
        saveProducts(importId, accepted);
        rejections.insertAll(importId, rejected);
        runs.updateProgress(importId, rowsRead, rowsImported, rowsRejected);
    }

    /**
     * Inserts the rows that are new and refreshes the ones already stored.
     *
     * <p>JPA has no upsert, so the collisions have to be found first: one query loads whatever the
     * chunk might overwrite, and every row is then either a loaded entity being updated or a fresh
     * one being inserted. Both go through {@code saveAll} together, which Hibernate batches because
     * the id comes from a sequence rather than from the insert itself.
     */
    private void saveProducts(long importId, List<Product> accepted) {
        if (accepted.isEmpty()) {
            return;
        }

        Map<String, ProductEntity> existing = loadCollisions(accepted);
        Instant now = Instant.now();
        List<ProductEntity> toSave = new ArrayList<>(accepted.size());

        for (Product product : accepted) {
            ProductEntity entity = existing.get(product.uniqueKey());
            if (entity == null) {
                entity = new ProductEntity(product.productSku(), product.purchaseDate());
            }
            // Set on both paths, so a row that already existed ends up identical to the row an
            // empty table would have produced from the same file.
            entity.setImportId(importId);
            entity.setSourceRowNumber(product.rowNumber());
            entity.setProductName(product.productName());
            entity.setCategory(product.category());
            entity.setUnitPrice(product.unitPrice());
            entity.setQuantity(product.quantity());
            entity.setUpdatedAt(now);
            toSave.add(entity);
        }

        products.saveAll(toSave);
    }

    /**
     * The stored rows this chunk would collide with, by unique key.
     *
     * <p>The query matches the two key columns independently, so it can return pairings that no
     * row in the chunk actually asks for; keying the result on the exact pair drops those.
     */
    private Map<String, ProductEntity> loadCollisions(List<Product> accepted) {
        Set<String> skus = new HashSet<>();
        Set<LocalDate> dates = new HashSet<>();
        for (Product product : accepted) {
            skus.add(product.productSku());
            dates.add(product.purchaseDate());
        }

        List<ProductEntity> found = products.findByProductSkuInAndPurchaseDateIn(skus, dates);
        Map<String, ProductEntity> byKey = new HashMap<>(found.size() * 2);
        for (ProductEntity entity : found) {
            byKey.put(Product.uniqueKey(entity.getProductSku(), entity.getPurchaseDate()), entity);
        }
        return byKey;
    }

    @Transactional
    public void markRunning(long importId) {
        runs.findById(importId).ifPresent(run -> run.setStatus(ImportStatus.RUNNING));
    }

    @Transactional
    public void markCompleted(long importId, int rowsRead, int rowsImported, int rowsRejected) {
        runs.findById(importId).ifPresent(run -> {
            run.setRowsRead(rowsRead);
            run.setRowsImported(rowsImported);
            run.setRowsRejected(rowsRejected);
            run.setStatus(ImportStatus.COMPLETED);
            run.setFinishedAt(Instant.now());
        });
    }

    /**
     * Recorded in its own transaction so it survives whatever rolled back, and so a crashed
     * import still leaves an explanation behind rather than a row stuck at RUNNING.
     */
    @Transactional
    public void markFailed(long importId, String message) {
        runs.findById(importId).ifPresent(run -> {
            run.setStatus(ImportStatus.FAILED);
            run.setFailureMessage(message);
            run.setFinishedAt(Instant.now());
        });
    }

    @Transactional(readOnly = true)
    public ImportRunEntity require(long importId) {
        return runs.findById(importId).orElse(null);
    }
}
