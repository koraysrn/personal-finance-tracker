package com.personal.finance.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record NetWorthResponse(
        String baseCurrency,
        BigDecimal total,
        List<AccountBalance> accounts) {
}
