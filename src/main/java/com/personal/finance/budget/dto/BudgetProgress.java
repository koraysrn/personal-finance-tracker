package com.personal.finance.budget.dto;

import java.math.BigDecimal;

public record BudgetProgress(
        Long budgetId,
        Long categoryId,
        String categoryName,
        int year,
        int month,
        BigDecimal amount,
        BigDecimal spent,
        BigDecimal percentage,
        String status) {
}
