package com.personal.finance.report.dto;

import java.math.BigDecimal;

public record AccountBalance(
        Long accountId,
        String accountName,
        String type,
        String currency,
        BigDecimal balance,
        BigDecimal baseBalance) {
}
