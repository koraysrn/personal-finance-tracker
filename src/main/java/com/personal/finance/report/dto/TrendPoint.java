package com.personal.finance.report.dto;

import java.math.BigDecimal;

public record TrendPoint(
        int month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net) {
}
