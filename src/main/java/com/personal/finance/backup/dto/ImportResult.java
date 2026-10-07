package com.personal.finance.backup.dto;

import java.util.List;

/**
 * Result of a CSV import: number of successfully imported rows plus a list of
 * per-row errors for rows that were rejected.
 */
public record ImportResult(int imported, List<RowError> errors) {

    public record RowError(int row, String message) {
    }
}
