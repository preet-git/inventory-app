package com.ablsoft.inventory.web;

import com.ablsoft.inventory.model.ImportResult;
import com.ablsoft.inventory.model.ProductPage;
import com.ablsoft.inventory.service.ImportService;
import com.ablsoft.inventory.service.ProductStore;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The three endpoints the dashboard needs: upload a file, read the summary, page through the rows.
 *
 * <p>The import runs synchronously and returns its result, rather than handing back a job id to
 * poll. With {@code spring.threads.virtual.enabled} the request sits on a virtual thread while it
 * runs, so a long upload does not consume one of a small pool of platform threads.
 */
@RestController
@RequestMapping("/api")
public class InventoryController {

    private final ImportService importService;
    private final ProductStore store;

    public InventoryController(ImportService importService, ProductStore store) {
        this.importService = importService;
        this.store = store;
    }

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importFile(@RequestParam("file") MultipartFile file) throws IOException {
        return importService.importFile(file);
    }

    /** The same two sections the upload returned, for a dashboard that reloads. */
    @GetMapping("/imports/summary")
    public ImportResult summary() {
        return store.currentResult();
    }

    @GetMapping("/products")
    public ProductPage products(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sortBy", defaultValue = "productSku") String sortBy,
            @RequestParam(value = "direction", defaultValue = "ASC") String direction) {
        return store.page(page, size, sortBy, direction);
    }
}
