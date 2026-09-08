package com.ablsoft.inventory.repository;

import com.ablsoft.inventory.entity.ImportRejectionEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRejectionRepository
        extends JpaRepository<ImportRejectionEntity, ImportRejectionEntity.Key>,
                ImportRejectionRepositoryCustom {

    /** Always ascending: a rejection list is read alongside the spreadsheet, top to bottom. */
    List<ImportRejectionEntity> findByImportIdOrderByRowNumberAsc(Long importId, Pageable pageable);
}
