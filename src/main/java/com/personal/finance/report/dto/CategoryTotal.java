package com.personal.finance.report.dto;

import java.math.BigDecimal;

public record CategoryTotal(
        Long categoryId,
        String categoryName,
        BigDecimal total) {
}
