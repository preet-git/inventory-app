package com.ablsoft.inventory.service;

import com.ablsoft.inventory.ImportException;
import com.ablsoft.inventory.model.ImportResult;
import com.ablsoft.inventory.model.Product;
import com.ablsoft.inventory.model.RawRow;
import com.ablsoft.inventory.read.FileType;
import com.ablsoft.inventory.read.RowReader;
import com.ablsoft.inventory.validate.RowValidator;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Imports one file: read it, validate every row, drop duplicates, replace what the dashboard
 * shows.
 *
 * <p>Rows are processed in file order on the calling thread. That ordering is what makes duplicate
 * handling deterministic — the first row to claim a SKU + date keeps it — and it is fast enough
 * that parallelism would buy nothing: the work is dominated by parsing the file, not by the rules.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    /** Enforced while reading, so an enormous file is refused before it is fully parsed. */
    private static final int MAX_DATA_ROWS = 50_000;

    private final ProductStore store;
    private final RowValidator validator = new RowValidator();

    public ImportService(ProductStore store) {
        this.store = store;
    }

    public ImportResult importFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw ImportException.invalidFile("Uploaded file is empty.");
        }

        Path staged = stage(file);
        try {
            FileType type = FileType.detect(file.getOriginalFilename(), staged);
            return read(staged, type, file.getOriginalFilename());
        } finally {
            deleteQuietly(staged);
        }
    }

    /** Running totals for one file. Single-threaded, so plain collections need no locking. */
    private static final class Tally {
        final Map<String, Product> accepted = new LinkedHashMap<>();
        final List<Integer> rejectedRowNumbers = new ArrayList<>();
        int rowsRead;
    }

    private ImportResult read(Path staged, FileType type, String fileName) throws IOException {
        // One "today" for the whole file, so a midnight rollover cannot age row 50,000 a day
        // differently from row 2.
        LocalDate importedOn = LocalDate.now();
        Tally tally = new Tally();

        try (RowReader reader = RowReader.open(staged, type, MAX_DATA_ROWS)) {
            reader.forEachRow(row -> accept(row, tally));
        }

        List<Product> products = List.copyOf(tally.accepted.values());
        ImportResult result = new ImportResult(
                new ImportResult.Summary(
                        fileName,
                        tally.rowsRead,
                        products.size(),
                        tally.rejectedRowNumbers.size(),
                        totalValue(products),
                        averageStockAge(products, importedOn)),
                new ImportResult.Rejected(
                        tally.rejectedRowNumbers.size(), List.copyOf(tally.rejectedRowNumbers)));

        store.replace(products, result, importedOn);
        log.info("Imported {}: {} rows read, {} imported, {} rejected",
                fileName, tally.rowsRead, products.size(), tally.rejectedRowNumbers.size());
        return result;
    }

    private void accept(RawRow row, Tally tally) {
        tally.rowsRead++;

        RowValidator.Result result = validator.validate(row);
        if (!result.accepted()) {
            tally.rejectedRowNumbers.add(row.rowNumber());
            // The API reports row numbers only; the reasons are one log level away.
            log.debug("Row {} rejected: {}", row.rowNumber(), String.join("; ", result.reasons()));
            return;
        }

        Product product = result.product();
        Product existing = tally.accepted.putIfAbsent(product.uniqueKey(), product);
        if (existing != null) {
            tally.rejectedRowNumbers.add(product.rowNumber());
            log.debug("Row {} rejected: duplicate of row {} ({} on {})",
                    product.rowNumber(), existing.rowNumber(), product.productSku(), product.purchaseDate());
        }
    }

    private static BigDecimal totalValue(List<Product> products) {
        return products.stream()
                .map(Product::lineValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageStockAge(List<Product> products, LocalDate asOf) {
        if (products.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        double average = products.stream()
                .mapToLong(product -> product.stockAgeDays(asOf))
                .average()
                .orElse(0);
        return BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Copies the upload to a temp file, because POI needs a {@code File} and because the
     * magic-number check and the parser both have to read the bytes. With
     * {@code file-size-threshold: 0B} Tomcat has already spooled the part to disk, so this is a
     * disk-to-disk copy rather than a trip through the heap. Only the extension is taken from the
     * client's filename — a name from a request never builds a path.
     */
    private static Path stage(MultipartFile file) throws IOException {
        Path staged = Files.createTempFile("ablsoft-import-", suffixOf(file.getOriginalFilename()));
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(staged);
            throw e;
        }
        return staged;
    }

    private static String suffixOf(String originalFileName) {
        if (originalFileName == null) {
            return ".upload";
        }
        int dot = originalFileName.lastIndexOf('.');
        if (dot < 0 || dot == originalFileName.length() - 1) {
            return ".upload";
        }
        String suffix = originalFileName.substring(dot);
        // The real extension check happens later; this only keeps the temp name harmless.
        return suffix.matches("\\.[A-Za-z0-9]{1,10}") ? suffix : ".upload";
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete staged upload {}", path, e);
        }
    }
}
