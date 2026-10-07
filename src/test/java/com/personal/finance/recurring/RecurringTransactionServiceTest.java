package com.personal.finance.recurring;

import com.personal.finance.account.Account;
import com.personal.finance.account.AccountRepository;
import com.personal.finance.account.AccountType;
import com.personal.finance.category.Category;
import com.personal.finance.category.CategoryRepository;
import com.personal.finance.category.CategoryType;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.recurring.dto.RecurringTransactionRequest;
import com.personal.finance.transaction.Transaction;
import com.personal.finance.transaction.TransactionRepository;
import com.personal.finance.transaction.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Recurring rule day validation, month-end clamping and idempotent generation
 * (REC-01, REC-02, REC-03, REC-05).
 */
@ExtendWith(MockitoExtension.class)
class RecurringTransactionServiceTest {

    @Mock
    private RecurringTransactionRepository recurringRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private Clock clock;

    private RecurringTransactionService service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);
        service = new RecurringTransactionService(
                recurringRepository, transactionRepository, accountRepository, categoryRepository, clock);
    }

    @Test
    void dayOfMonthOutOfRangeIsRejected() {
        Account account = account(1L, "Checking");
        Category category = category(2L, "Rent", CategoryType.EXPENSE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));

        RecurringTransactionRequest request = new RecurringTransactionRequest(
                1L, 2L, TransactionType.EXPENSE, new BigDecimal("100.00"),
                RecurringFrequency.MONTHLY, 32, null, null, true,
                LocalDate.of(2026, 1, 1), null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void monthlyDay31IsClampedToLastDayOfFebruary() {
        Account account = account(1L, "Checking");
        Category category = category(2L, "Rent", CategoryType.EXPENSE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(recurringRepository.save(any(RecurringTransaction.class))).thenAnswer(invocation -> {
            RecurringTransaction rt = invocation.getArgument(0);
            rt.setId(1L);
            return rt;
        });

        RecurringTransactionRequest request = new RecurringTransactionRequest(
                1L, 2L, TransactionType.EXPENSE, new BigDecimal("100.00"),
                RecurringFrequency.MONTHLY, 31, null, null, true,
                LocalDate.of(2026, 2, 1), null);

        var response = service.create(request);

        // February 2026 has 28 days: the 31st is clamped to the last day.
        assertThat(response.nextRun()).isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void inactiveRuleDoesNotGenerateTransactions() {
        RecurringTransaction rule = rule(1L, true);
        when(recurringRepository.findDue(LocalDate.now(clock))).thenReturn(List.of(rule));
        when(transactionRepository.findByIdempotencyKey("REC-1-2026-01-15")).thenReturn(Optional.of(new Transaction()));

        int generated = service.processDue();

        assertThat(generated).isZero();
    }

    @Test
    void generationIsIdempotentWhenTheKeyAlreadyExists() {
        RecurringTransaction rule = rule(1L, true);
        rule.setNextRun(LocalDate.of(2026, 1, 1));
        when(recurringRepository.findDue(LocalDate.now(clock))).thenReturn(List.of(rule));
        when(transactionRepository.findByIdempotencyKey("REC-1-2026-01-01"))
                .thenReturn(Optional.of(new Transaction()));

        int generated = service.processDue();

        assertThat(generated).isZero();
        assertThat(rule.getNextRun()).isAfter(LocalDate.of(2026, 1, 1));
    }

    private RecurringTransaction rule(Long id, boolean active) {
        RecurringTransaction rule = new RecurringTransaction();
        rule.setId(id);
        rule.setAccount(account(1L, "Checking"));
        rule.setCategory(category(2L, "Rent", CategoryType.EXPENSE));
        rule.setType(TransactionType.EXPENSE);
        rule.setAmount(new BigDecimal("100.00"));
        rule.setFrequency(RecurringFrequency.MONTHLY);
        rule.setDayOfMonth(15);
        rule.setActive(active);
        rule.setStartDate(LocalDate.of(2026, 1, 1));
        rule.setNextRun(LocalDate.of(2026, 1, 15));
        return rule;
    }

    private Account account(Long id, String name) {
        Account account = new Account();
        account.setId(id);
        account.setName(name);
        account.setType(AccountType.CHECKING);
        account.setCurrency("USD");
        return account;
    }

    private Category category(Long id, String name, CategoryType type) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setType(type);
        return category;
    }
}
