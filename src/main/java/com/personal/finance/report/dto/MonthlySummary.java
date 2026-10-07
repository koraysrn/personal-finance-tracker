package com.personal.finance.report.dto;

import java.math.BigDecimal;

public record MonthlySummary(
        int year,
        int month,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net) {
}
