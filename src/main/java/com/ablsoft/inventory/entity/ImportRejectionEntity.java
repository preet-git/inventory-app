package com.ablsoft.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/**
 * A row that did not make it in, identified by its number in the uploaded file.
 *
 * <p>The reason is stored but the API reports row numbers, per the brief. It is here so a
 * rejection can be explained without re-running the import.
 */
@Entity
@Table(name = "import_rejection")
@IdClass(ImportRejectionEntity.Key.class)
public class ImportRejectionEntity {

    @Id
    @Column(name = "import_id")
    private Long importId;

    @Id
    @Column(name = "row_number")
    private Integer rowNumber;

    @Column
    private String reason;

    protected ImportRejectionEntity() {
        // for JPA
    }

    public Long getImportId() {
        return importId;
    }

    public Integer getRowNumber() {
        return rowNumber;
    }

    public String getReason() {
        return reason;
    }

    /** Composite primary key: one rejection per row per import. */
    public record Key(Long importId, Integer rowNumber) implements Serializable {

        private static final long serialVersionUID = 1L;

        public Key {
            Objects.requireNonNull(importId);
            Objects.requireNonNull(rowNumber);
        }
    }
}
