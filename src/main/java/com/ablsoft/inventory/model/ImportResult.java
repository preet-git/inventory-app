package com.ablsoft.inventory.model;

import com.ablsoft.inventory.entity.ImportRunEntity;
import com.ablsoft.inventory.entity.ImportStatus;
import java.time.Instant;
import java.util.List;

/**
 * What an import is doing, or did, in the two sections the brief asks for.
 *
 * <p>Returned by both the upload (immediately, still {@code PENDING}) and the status endpoint, so
 * a client polls the same shape it was handed.
 */
public record ImportResult(
        long importId,
        String fileName,
        ImportStatus status,
        Summary summary,
        Rejected rejectedRows) {

    /** Section 1: what the import has read and written so far. */
    public record Summary(
            int rowsRead,
            int importedCount,
            int rejectedCount,
            Instant startedAt,
            Instant finishedAt,
            String failureMessage) {
    }

    /**
     * Section 2: which rows were dropped, by their number in the file.
     *
     * <p>Capped, because a million-row import can reject a great many rows and nobody wants them
     * all in one response. {@code truncated} says whether to page the rest from
     * {@code /api/imports/{id}/rejections}.
     */
    public record Rejected(int count, List<Integer> rowNumbers, boolean truncated) {
    }

    public static ImportResult of(ImportRunEntity run, List<Integer> rowNumbers, boolean truncated) {
        return new ImportResult(
                run.getId(),
                run.getFileName(),
                run.getStatus(),
                new Summary(
                        run.getRowsRead(),
                        run.getRowsImported(),
                        run.getRowsRejected(),
                        run.getStartedAt(),
                        run.getFinishedAt(),
                        run.getFailureMessage()),
                new Rejected(run.getRowsRejected(), rowNumbers, truncated));
    }
}
