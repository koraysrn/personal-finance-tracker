package com.personal.finance.recurring.dto;

import com.personal.finance.recurring.RecurringFrequency;
import com.personal.finance.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record RecurringTransactionResponse(
        Long id,
        Long accountId,
        String accountName,
        Long categoryId,
        String categoryName,
        TransactionType type,
        BigDecimal amount,
        RecurringFrequency frequency,
        Integer dayOfMonth,
        Integer dayOfWeek,
        String note,
        boolean active,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextRun,
        Instant lastRun) {
}
