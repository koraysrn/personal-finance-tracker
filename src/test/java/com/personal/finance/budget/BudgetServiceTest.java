package com.personal.finance.budget;

import com.personal.finance.budget.dto.BudgetRequest;
import com.personal.finance.category.Category;
import com.personal.finance.category.CategoryRepository;
import com.personal.finance.category.CategoryType;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Budget duplicate guard, zero-division safety and threshold status (BUD-01, BUD-03, BUD-04, BUD-06).
 */
@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BudgetService budgetService;

    @Test
    void duplicateBudgetForSameCategoryAndMonthIsRejected() {
        when(budgetRepository.existsByCategoryIdAndYearAndMonth(2L, 2026, 1)).thenReturn(true);

        BudgetRequest request = new BudgetRequest(2L, 2026, 1, new BigDecimal("100.00"));

        assertThatThrownBy(() -> budgetService.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.DUPLICATE_BUDGET));
    }

    @Test
    void zeroSpendingDoesNotDivideByZero() {
        Budget budget = budget(1L, 2L, "Food");
        when(budgetRepository.findByYearAndMonth(2026, 1)).thenReturn(List.of(budget));
        when(categoryRepository.findByParentId(2L)).thenReturn(List.of());
        when(transactionRepository.sumExpenseByCategoryBetween(2L,
                java.time.LocalDate.of(2026, 1, 1),
                java.time.LocalDate.of(2026, 1, 31))).thenReturn(BigDecimal.ZERO);

        var progress = budgetService.progress(2026, 1);

        assertThat(progress).hasSize(1);
        assertThat(progress.get(0).percentage()).isEqualByComparingTo("0.00");
        assertThat(progress.get(0).status()).isEqualTo("OK");
    }

    @Test
    void eightyPercentSpendingTriggersWarning() {
        Budget budget = budget(1L, 2L, "Food");
        when(budgetRepository.findByYearAndMonth(2026, 1)).thenReturn(List.of(budget));
        when(categoryRepository.findByParentId(2L)).thenReturn(List.of());
        when(transactionRepository.sumExpenseByCategoryBetween(2L,
                java.time.LocalDate.of(2026, 1, 1),
                java.time.LocalDate.of(2026, 1, 31))).thenReturn(new BigDecimal("80.00"));

        var progress = budgetService.progress(2026, 1);

        assertThat(progress.get(0).status()).isEqualTo("WARNING");
        assertThat(progress.get(0).percentage()).isEqualByComparingTo("80.00");
    }

    @Test
    void overspendingTriggersCriticalStatus() {
        Budget budget = budget(1L, 2L, "Food");
        when(budgetRepository.findByYearAndMonth(2026, 1)).thenReturn(List.of(budget));
        when(categoryRepository.findByParentId(2L)).thenReturn(List.of());
        when(transactionRepository.sumExpenseByCategoryBetween(2L,
                java.time.LocalDate.of(2026, 1, 1),
                java.time.LocalDate.of(2026, 1, 31))).thenReturn(new BigDecimal("110.00"));

        var progress = budgetService.progress(2026, 1);

        assertThat(progress.get(0).status()).isEqualTo("CRITICAL");
    }

    private Budget budget(Long id, Long categoryId, String categoryName) {
        Category category = new Category();
        category.setId(categoryId);
        category.setName(categoryName);
        category.setType(CategoryType.EXPENSE);

        Budget budget = new Budget();
        budget.setId(id);
        budget.setCategory(category);
        budget.setYear(2026);
        budget.setMonth(1);
        budget.setAmount(new BigDecimal("100.00"));
        return budget;
    }
}
