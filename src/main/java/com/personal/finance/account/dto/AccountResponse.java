package com.personal.finance.account.dto;

import com.personal.finance.account.AccountType;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        String name,
        AccountType type,
        String currency,
        BigDecimal openingBalance,
        BigDecimal creditLimit,
        boolean archived,
        int sortOrder,
        BigDecimal balance) {
}
