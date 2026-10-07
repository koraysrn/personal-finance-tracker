package com.personal.finance.report;

import com.personal.finance.category.CategoryType;
import com.personal.finance.report.dto.AccountBalance;
import com.personal.finance.report.dto.CategoryTotal;
import com.personal.finance.report.dto.MonthlySummary;
import com.personal.finance.report.dto.NetWorthResponse;
import com.personal.finance.report.dto.TrendPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/monthly")
    public List<MonthlySummary> monthly(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return reportService.monthly(start, end);
    }

    @GetMapping("/category-breakdown")
    public List<CategoryTotal> categoryBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(defaultValue = "EXPENSE") CategoryType type) {
        return reportService.categoryBreakdown(start, end, type);
    }

    @GetMapping("/account-balances")
    public NetWorthResponse accountBalances() {
        return reportService.accountBalances();
    }

    @GetMapping("/yearly")
    public List<TrendPoint> yearly(@RequestParam int year) {
        return reportService.yearly(year);
    }

    @GetMapping("/net-worth")
    public NetWorthResponse netWorth() {
        return reportService.accountBalances();
    }

    @GetMapping("/account-balance-list")
    public List<AccountBalance> accountBalanceList() {
        return reportService.accountBalances().accounts();
    }
}
