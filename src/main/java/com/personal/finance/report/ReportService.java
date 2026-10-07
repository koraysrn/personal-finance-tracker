package com.personal.finance.report;

import com.personal.finance.account.Account;
import com.personal.finance.account.AccountRepository;
import com.personal.finance.account.BalanceService;
import com.personal.finance.category.Category;
import com.personal.finance.category.CategoryType;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.report.dto.AccountBalance;
import com.personal.finance.report.dto.CategoryTotal;
import com.personal.finance.report.dto.MonthlySummary;
import com.personal.finance.report.dto.NetWorthResponse;
import com.personal.finance.report.dto.TrendPoint;
import com.personal.finance.transaction.ExchangeRateService;
import com.personal.finance.transaction.Transaction;
import com.personal.finance.transaction.TransactionRepository;
import com.personal.finance.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregates transactions into reports: monthly summaries, category breakdowns,
 * account balances / net worth and yearly trends. The base currency for net
 * worth is user configurable through the exchange rate service.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BalanceService balanceService;
    private final ExchangeRateService exchangeRateService;

    @Transactional(readOnly = true)
    public List<MonthlySummary> monthly(LocalDate start, LocalDate end) {
        validateRange(start, end);
        Map<YearMonth, BigDecimal[]> sums = new TreeMap<>();
        for (Transaction tx : transactionRepository.findByDateBetween(start, end)) {
            YearMonth ym = YearMonth.from(tx.getDate());
            BigDecimal[] acc = sums.computeIfAbsent(ym, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            switch (tx.getType()) {
                case INCOME -> acc[0] = acc[0].add(tx.getAmount());
                case EXPENSE -> acc[1] = acc[1].add(tx.getAmount());
                default -> {
                    // transfers do not count as income or expense
                }
            }
        }

        List<MonthlySummary> result = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        YearMonth last = YearMonth.from(end);
        while (!cursor.isAfter(last)) {
            BigDecimal[] acc = sums.getOrDefault(cursor, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            result.add(new MonthlySummary(
                    cursor.getYear(),
                    cursor.getMonthValue(),
                    acc[0],
                    acc[1],
                    acc[0].subtract(acc[1])));
            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<CategoryTotal> categoryBreakdown(LocalDate start, LocalDate end, CategoryType type) {
        validateRange(start, end);
        TransactionType wantedType = type == CategoryType.INCOME
                ? TransactionType.INCOME
                : TransactionType.EXPENSE;

        Map<Long, MutableTotal> totals = new LinkedHashMap<>();
        for (Transaction tx : transactionRepository.findByDateBetween(start, end)) {
            if (tx.getType() != wantedType || tx.getCategory() == null) {
                continue;
            }
            Category category = tx.getCategory();
            totals.computeIfAbsent(category.getId(), id -> new MutableTotal(id, category.getName()))
                    .total = totals.get(category.getId()).total.add(tx.getAmount());
        }

        return totals.values().stream()
                .sorted(Comparator.comparing((MutableTotal t) -> t.total).reversed())
                .map(t -> new CategoryTotal(t.categoryId, t.categoryName, t.total))
                .toList();
    }

    @Transactional(readOnly = true)
    public NetWorthResponse accountBalances() {
        String baseCurrency = exchangeRateService.getBaseCurrency();
        BigDecimal total = BigDecimal.ZERO;
        List<AccountBalance> accounts = new ArrayList<>();
        for (Account account : accountRepository.findAll()) {
            if (account.isArchived()) {
                continue;
            }
            BigDecimal balance = balanceService.balance(account);
            BigDecimal baseBalance = exchangeRateService.convert(balance, account.getCurrency(), baseCurrency);
            accounts.add(new AccountBalance(
                    account.getId(),
                    account.getName(),
                    account.getType().name(),
                    account.getCurrency(),
                    balance,
                    baseBalance));
            total = total.add(baseBalance);
        }
        return new NetWorthResponse(baseCurrency, total, accounts);
    }

    @Transactional(readOnly = true)
    public List<TrendPoint> yearly(int year) {
        List<MonthlySummary> monthly = monthly(
                LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31));
        return monthly.stream()
                .map(m -> new TrendPoint(m.month(), m.income(), m.expense(), m.net()))
                .toList();
    }

    private void validateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "Start and end dates are required");
        }
        if (start.isAfter(end)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "Start date must not be after end date");
        }
    }

    private static final class MutableTotal {
        private final Long categoryId;
        private final String categoryName;
        private BigDecimal total = BigDecimal.ZERO;

        private MutableTotal(Long categoryId, String categoryName) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
        }
    }
}
