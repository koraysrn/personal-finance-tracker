package com.personal.finance.budget.dto;

import java.math.BigDecimal;

public record BudgetResponse(
        Long id,
        Long categoryId,
        String categoryName,
        int year,
        int month,
        BigDecimal amount) {
}
