package com.ablsoft.inventory.repository;

import com.ablsoft.inventory.model.RejectedRow;
import java.util.List;

public interface ImportRejectionRepositoryCustom {

    /** Batch-inserts a chunk's rejections. */
    void insertAll(long importId, List<RejectedRow> rejections);
}
