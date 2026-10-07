package com.personal.finance.backup.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Full snapshot of the application data used for export / restore.
 */
public record BackupData(
        int version,
        Instant exportedAt,
        List<AccountItem> accounts,
        List<CategoryItem> categories,
        List<TagItem> tags,
        List<TransactionItem> transactions,
        List<BudgetItem> budgets,
        List<RecurringItem> recurring) {

    public record AccountItem(
            String name,
            String type,
            String currency,
            BigDecimal openingBalance,
            BigDecimal creditLimit,
            boolean archived,
            int sortOrder) {
    }

    public record CategoryItem(
            String name,
            String type,
            String parentName,
            String icon,
            String color,
            boolean archived) {
    }

    public record TagItem(String name) {
    }

    public record TransactionItem(
            Long oldId,
            String accountName,
            String categoryName,
            String type,
            BigDecimal amount,
            LocalDate date,
            String note,
            Long linkedOldId,
            String source,
            List<String> tags) {
    }

    public record BudgetItem(
            String categoryName,
            int year,
            int month,
            BigDecimal amount) {
    }

    public record RecurringItem(
            String accountName,
            String categoryName,
            String type,
            BigDecimal amount,
            String frequency,
            Integer dayOfMonth,
            Integer dayOfWeek,
            String note,
            boolean active,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate nextRun) {
    }
}
