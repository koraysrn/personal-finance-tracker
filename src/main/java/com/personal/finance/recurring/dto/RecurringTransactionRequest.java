package com.personal.finance.recurring.dto;

import com.personal.finance.recurring.RecurringFrequency;
import com.personal.finance.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringTransactionRequest(
        @NotNull(message = "Account is required")
        Long accountId,

        @NotNull(message = "Category is required")
        Long categoryId,

        @NotNull(message = "Transaction type is required")
        TransactionType type,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than zero")
        @Digits(integer = 16, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "Frequency is required")
        RecurringFrequency frequency,

        @Min(value = 1, message = "Day of month must be between 1 and 31")
        @Max(value = 31, message = "Day of month must be between 1 and 31")
        Integer dayOfMonth,

        @Min(value = 1, message = "Day of week must be between 1 and 7")
        @Max(value = 7, message = "Day of week must be between 1 and 7")
        Integer dayOfWeek,

        @Size(max = 1024, message = "Note must not exceed 1024 characters")
        String note,

        Boolean active,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        LocalDate endDate) {
}
