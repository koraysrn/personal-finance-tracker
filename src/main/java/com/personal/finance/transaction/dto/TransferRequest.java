package com.personal.finance.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferRequest(
        @NotNull(message = "Source account is required")
        Long sourceAccountId,

        @NotNull(message = "Target account is required")
        Long targetAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Amount must be greater than zero")
        @Digits(integer = 16, fraction = 2, message = "Amount must have at most 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "Date is required")
        LocalDate date,

        @Size(max = 1024, message = "Note must not exceed 1024 characters")
        String note,

        @Size(max = 64, message = "Idempotency key must not exceed 64 characters")
        String idempotencyKey) {
}
