package com.personal.finance.account.dto;

import com.personal.finance.account.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AccountRequest(
        @NotBlank(message = "Account name is required")
        @Size(max = 128, message = "Account name must not exceed 128 characters")
        String name,

        @NotNull(message = "Account type is required")
        AccountType type,

        @NotBlank(message = "Currency is required")
        @Size(min = 3, max = 3, message = "Currency must be a 3 letter code")
        String currency,

        @Digits(integer = 16, fraction = 2, message = "Opening balance must have at most 2 decimal places")
        BigDecimal openingBalance,

        @DecimalMin(value = "0.0", message = "Credit limit must not be negative")
        @Digits(integer = 16, fraction = 2, message = "Credit limit must have at most 2 decimal places")
        BigDecimal creditLimit,

        Boolean archived,

        Integer sortOrder) {
}
