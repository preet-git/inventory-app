package com.ablsoft.inventory.service;

import com.ablsoft.inventory.model.ImportResult;
import com.ablsoft.inventory.model.Product;
import com.ablsoft.inventory.model.ProductPage;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * Holds the current import in memory and serves it to the dashboard, sorted and paged.
 *
 * <p>A new import replaces the previous one wholesale, as one immutable snapshot. A request that
 * is reading while an import finishes therefore sees one file's data in full, rather than half of
 * each, without any locking.
 *
 * <p>Stock ages are measured from the date the file was imported, not from today, so the average
 * on the summary card stays the mean of the ages in the table even if the application is left
 * running past midnight.
 */
@Component
public class ProductStore {

    private static final int MAX_PAGE_SIZE = 200;

    /** Sorting is restricted to these, so a client cannot ask to sort by an arbitrary string. */
    private static final Map<String, Comparator<Product>> SORTS = new TreeMap<>(Map.of(
            "rowNumber", Comparator.comparingInt(Product::rowNumber),
            "productSku", Comparator.comparing(Product::productSku),
            "productName", Comparator.comparing(Product::productName, String.CASE_INSENSITIVE_ORDER),
            "category", Comparator.comparing(Product::category, String.CASE_INSENSITIVE_ORDER),
            "purchaseDate", Comparator.comparing(Product::purchaseDate),
            "unitPrice", Comparator.comparing(Product::unitPrice),
            "quantity", Comparator.comparingInt(Product::quantity),
            "lineValue", Comparator.comparing(Product::lineValue),
            // Older stock has an earlier purchase date, so ascending age is descending date.
            "stockAgeDays", Comparator.comparing(Product::purchaseDate, Comparator.reverseOrder())));

    private record Snapshot(List<Product> products, ImportResult result, LocalDate importedOn) {

        static Snapshot empty() {
            return new Snapshot(List.of(), ImportResult.empty(), LocalDate.now());
        }
    }

    private volatile Snapshot snapshot = Snapshot.empty();

    public void replace(List<Product> products, ImportResult result, LocalDate importedOn) {
        this.snapshot = new Snapshot(List.copyOf(products), result, importedOn);
    }

    public ImportResult currentResult() {
        return snapshot.result();
    }

    public ProductPage page(int page, int size, String sortBy, String direction) {
        Comparator<Product> comparator = SORTS.get(sortBy);
        if (comparator == null) {
            throw new IllegalArgumentException(
                    "Unknown sortBy '" + sortBy + "'. Valid values: " + String.join(", ", SORTS.keySet()));
        }
        boolean descending = "DESC".equalsIgnoreCase(direction);
        if (!descending && !"ASC".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException(
                    "Unknown direction '" + direction + "'. Valid values: ASC, DESC");
        }

        // Read the snapshot once: an import may replace it while this response is being built.
        Snapshot current = snapshot;
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int pageIndex = Math.max(page, 0);

        List<Product> sorted = new ArrayList<>(current.products());
        sorted.sort(descending ? comparator.reversed() : comparator);

        int total = sorted.size();
        int from = (int) Math.min((long) pageIndex * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<ProductPage.Row> content = sorted.subList(from, to).stream()
                .map(product -> ProductPage.Row.from(product, current.importedOn()))
                .toList();

        return new ProductPage(content, pageIndex, pageSize, total,
                (int) Math.ceil((double) total / pageSize), sortBy, descending ? "DESC" : "ASC");
    }
}
