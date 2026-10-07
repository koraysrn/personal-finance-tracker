package com.personal.finance.budget;

import com.personal.finance.budget.dto.BudgetProgress;
import com.personal.finance.budget.dto.BudgetRequest;
import com.personal.finance.budget.dto.BudgetResponse;
import com.personal.finance.category.Category;
import com.personal.finance.category.CategoryRepository;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.common.util.MoneyUtils;
import com.personal.finance.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Budget lifecycle and progress calculation. A budget on a parent category
 * also covers all of its subcategories.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final BigDecimal WARNING_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal CRITICAL_THRESHOLD = new BigDecimal("1.10");

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public BudgetResponse create(BudgetRequest request) {
        if (budgetRepository.existsByCategoryIdAndYearAndMonth(
                request.categoryId(), request.year(), request.month())) {
            throw new ApiException(ErrorCode.DUPLICATE_BUDGET,
                    "A budget for this category and month already exists");
        }
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found"));

        Budget budget = new Budget();
        budget.setCategory(category);
        budget.setYear(request.year());
        budget.setMonth(request.month());
        budget.setAmount(MoneyUtils.round(request.amount()));
        return toResponse(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> list(int year, int month) {
        return budgetRepository.findByYearAndMonth(year, month).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BudgetResponse update(Long id, BudgetRequest request) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Budget not found"));
        if (budgetRepository.existsByCategoryIdAndYearAndMonthAndIdNot(
                request.categoryId(), request.year(), request.month(), id)) {
            throw new ApiException(ErrorCode.DUPLICATE_BUDGET,
                    "A budget for this category and month already exists");
        }
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found"));

        budget.setCategory(category);
        budget.setYear(request.year());
        budget.setMonth(request.month());
        budget.setAmount(MoneyUtils.round(request.amount()));
        return toResponse(budgetRepository.save(budget));
    }

    @Transactional
    public void delete(Long id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Budget not found"));
        budgetRepository.delete(budget);
    }

    @Transactional(readOnly = true)
    public List<BudgetProgress> progress(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        List<BudgetProgress> result = new ArrayList<>();
        for (Budget budget : budgetRepository.findByYearAndMonth(year, month)) {
            Set<Long> categoryIds = categoryAndDescendants(budget.getCategory().getId());
            BigDecimal spent = BigDecimal.ZERO;
            for (Long categoryId : categoryIds) {
                spent = spent.add(transactionRepository.sumExpenseByCategoryBetween(categoryId, start, end));
            }

            BigDecimal percentage = budget.getAmount().signum() > 0
                    ? spent.multiply(new BigDecimal("100"))
                            .divide(budget.getAmount(), 2, RoundingMode.HALF_EVEN)
                    : BigDecimal.ZERO;

            result.add(new BudgetProgress(
                    budget.getId(),
                    budget.getCategory().getId(),
                    budget.getCategory().getName(),
                    year,
                    month,
                    budget.getAmount(),
                    spent,
                    percentage,
                    statusOf(spent, budget.getAmount())));
        }
        return result;
    }

    private String statusOf(BigDecimal spent, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return "OK";
        }
        if (spent.compareTo(amount.multiply(CRITICAL_THRESHOLD)) >= 0) {
            return "CRITICAL";
        }
        if (spent.compareTo(amount) >= 0) {
            return "OVER";
        }
        if (spent.compareTo(amount.multiply(WARNING_THRESHOLD)) >= 0) {
            return "WARNING";
        }
        return "OK";
    }

    private Set<Long> categoryAndDescendants(Long rootId) {
        Set<Long> result = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            Long id = queue.poll();
            if (!result.add(id)) {
                continue;
            }
            for (Category child : categoryRepository.findByParentId(id)) {
                queue.add(child.getId());
            }
        }
        return result;
    }

    private BudgetResponse toResponse(Budget budget) {
        return new BudgetResponse(
                budget.getId(),
                budget.getCategory().getId(),
                budget.getCategory().getName(),
                budget.getYear(),
                budget.getMonth(),
                budget.getAmount());
    }
}
