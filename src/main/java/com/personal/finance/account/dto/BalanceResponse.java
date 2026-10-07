package com.personal.finance.account.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        Long accountId,
        BigDecimal balance,
        BigDecimal availableLimit) {
}
