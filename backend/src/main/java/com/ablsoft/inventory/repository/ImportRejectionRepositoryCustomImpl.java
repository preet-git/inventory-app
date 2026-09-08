package com.ablsoft.inventory.repository;

import com.ablsoft.inventory.model.RejectedRow;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ImportRejectionRepositoryCustomImpl implements ImportRejectionRepositoryCustom {

    /**
     * {@code DO NOTHING} guards the one case that can repeat a row number: a chunk retried after a
     * partial failure. A rejection is a fact about a row, so recording it twice is meaningless
     * rather than wrong.
     */
    private static final String INSERT = """
            INSERT INTO import_rejection (import_id, row_number, reason)
            VALUES (?, ?, ?)
            ON CONFLICT (import_id, row_number) DO NOTHING
            """;

    /** Rejection reasons are diagnostic; the column is text but there is no point storing an essay. */
    private static final int MAX_REASON_LENGTH = 500;

    private final JdbcTemplate jdbcTemplate;

    public ImportRejectionRepositoryCustomImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insertAll(long importId, List<RejectedRow> rejections) {
        if (rejections.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT, rejections, rejections.size(), (statement, rejection) -> {
            statement.setLong(1, importId);
            statement.setInt(2, rejection.rowNumber());
            statement.setString(3, truncate(rejection.reason()));
        });
    }

    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }
}
