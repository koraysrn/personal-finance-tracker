package com.personal.finance.transaction.dto;

import com.personal.finance.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record TransactionRequest(
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

        @NotNull(message = "Date is required")
        LocalDate date,

        @Size(max = 1024, message = "Note must not exceed 1024 characters")
        String note,

        Set<Long> tagIds,

        @Size(max = 64, message = "Idempotency key must not exceed 64 characters")
        String idempotencyKey) {
}
