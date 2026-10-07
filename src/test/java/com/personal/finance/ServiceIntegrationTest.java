package com.personal.finance;

import com.personal.finance.account.Account;
import com.personal.finance.account.AccountRepository;
import com.personal.finance.account.AccountService;
import com.personal.finance.account.AccountType;
import com.personal.finance.account.BalanceService;
import com.personal.finance.account.dto.AccountRequest;
import com.personal.finance.backup.BackupService;
import com.personal.finance.backup.dto.BackupData;
import com.personal.finance.budget.BudgetRepository;
import com.personal.finance.budget.BudgetService;
import com.personal.finance.budget.dto.BudgetRequest;
import com.personal.finance.category.Category;
import com.personal.finance.category.CategoryRepository;
import com.personal.finance.category.CategoryService;
import com.personal.finance.category.CategoryType;
import com.personal.finance.category.TagRepository;
import com.personal.finance.category.dto.CategoryRequest;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.recurring.RecurringFrequency;
import com.personal.finance.recurring.RecurringTransactionRepository;
import com.personal.finance.recurring.RecurringTransactionService;
import com.personal.finance.recurring.dto.RecurringTransactionRequest;
import com.personal.finance.transaction.TransactionRepository;
import com.personal.finance.transaction.TransactionService;
import com.personal.finance.transaction.TransactionType;
import com.personal.finance.transaction.TransferService;
import com.personal.finance.transaction.dto.TransactionRequest;
import com.personal.finance.transaction.dto.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end service tests against a real (H2, PostgreSQL mode) database with
 * Flyway migrations applied. Deliberately focused on non-happy-path scenarios.
 */
@SpringBootTest
@ActiveProfiles("test")
class ServiceIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private RecurringTransactionService recurringTransactionService;

    @Autowired
    private BackupService backupService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private RecurringTransactionRepository recurringRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAllInBatch();
        budgetRepository.deleteAllInBatch();
        recurringRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        tagRepository.deleteAllInBatch();
    }

    @Test
    void creditCardExpenseAboveLimitIsRejected() {
        Account card = account("Card", AccountType.CREDIT_CARD, BigDecimal.ZERO, new BigDecimal("500"));
        Category shopping = category("Shopping", CategoryType.EXPENSE);

        assertThatThrownBy(() -> transactionService.create(transaction(
                card.getId(), shopping.getId(), TransactionType.EXPENSE, new BigDecimal("600"), null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CREDIT_LIMIT_EXCEEDED));
    }

    @Test
    void cashAccountCannotGoNegative() {
        Account cash = account("Cash", AccountType.CASH, new BigDecimal("100"), BigDecimal.ZERO);
        Category food = category("Food", CategoryType.EXPENSE);

        assertThatThrownBy(() -> transactionService.create(transaction(
                cash.getId(), food.getId(), TransactionType.EXPENSE, new BigDecimal("200"), null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
    }

    @Test
    void rejectedTransferLeavesNoPartialRecords() {
        Account source = account("Source", AccountType.CHECKING, new BigDecimal("100"), BigDecimal.ZERO);
        Account target = account("Target", AccountType.CHECKING, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> transferService.transfer(new TransferRequest(
                source.getId(), target.getId(), new BigDecimal("200"), LocalDate.now(), null, "T-1")))
                .isInstanceOf(ApiException.class);

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void repeatedIdempotencyKeyCreatesSingleRecord() {
        Account cash = account("Cash", AccountType.CASH, new BigDecimal("100"), BigDecimal.ZERO);
        Category food = category("Food", CategoryType.EXPENSE);
        String key = "IDEM-1";

        transactionService.create(transaction(cash.getId(), food.getId(), TransactionType.EXPENSE,
                new BigDecimal("10"), key));
        transactionService.create(transaction(cash.getId(), food.getId(), TransactionType.EXPENSE,
                new BigDecimal("10"), key));

        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void deletingTransactionRestoresTheBalance() {
        Account cash = account("Cash", AccountType.CASH, new BigDecimal("100"), BigDecimal.ZERO);
        Category food = category("Food", CategoryType.EXPENSE);

        var response = transactionService.create(transaction(
                cash.getId(), food.getId(), TransactionType.EXPENSE, new BigDecimal("30"), null));

        assertThat(balanceService.balance(accountRepository.findById(cash.getId()).orElseThrow()))
                .isEqualByComparingTo("70.00");

        transactionService.delete(response.id());

        assertThat(balanceService.balance(accountRepository.findById(cash.getId()).orElseThrow()))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void futureTransactionDateIsRejected() {
        Account cash = account("Cash", AccountType.CASH, new BigDecimal("100"), BigDecimal.ZERO);
        Category food = category("Food", CategoryType.EXPENSE);

        TransactionRequest request = new TransactionRequest(
                cash.getId(), food.getId(), TransactionType.EXPENSE, new BigDecimal("10"),
                LocalDate.now().plusDays(1), null, null, null);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void budgetProgressShowsWarningAtEightyPercent() {
        Category food = category("Food", CategoryType.EXPENSE);
        Account cash = account("Cash", AccountType.CASH, new BigDecimal("1000"), BigDecimal.ZERO);
        budgetService.create(new BudgetRequest(food.getId(), LocalDate.now().getYear(),
                LocalDate.now().getMonthValue(), new BigDecimal("100")));
        transactionService.create(transaction(cash.getId(), food.getId(), TransactionType.EXPENSE,
                new BigDecimal("80"), null));

        var progress = budgetService.progress(LocalDate.now().getYear(), LocalDate.now().getMonthValue());

        assertThat(progress).hasSize(1);
        assertThat(progress.get(0).status()).isEqualTo("WARNING");
    }

    @Test
    void recurringGenerationIsIdempotent() {
        Account cash = account("Cash", AccountType.CASH, new BigDecimal("1000"), BigDecimal.ZERO);
        Category rent = category("Rent", CategoryType.EXPENSE);

        var rule = recurringTransactionService.create(new RecurringTransactionRequest(
                cash.getId(), rent.getId(), TransactionType.EXPENSE, new BigDecimal("100"),
                RecurringFrequency.DAILY, null, null, null, true, LocalDate.now(), null));

        assertThat(rule.nextRun()).isEqualTo(LocalDate.now());

        assertThat(recurringTransactionService.processDue()).isEqualTo(1);
        assertThat(recurringTransactionService.processDue()).isZero();
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    void restoreWithoutReplaceIsRejectedWhenDataExists() {
        account("Cash", AccountType.CASH, new BigDecimal("100"), BigDecimal.ZERO);
        BackupData backup = backupService.exportData();

        assertThatThrownBy(() -> backupService.importData(backup, false))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void categoryCycleIsRejectedEndToEnd() {
        Category a = category("A", CategoryType.EXPENSE);
        Category b = category("B", CategoryType.EXPENSE);
        categoryService.update(b.getId(), new CategoryRequest("B", CategoryType.EXPENSE, a.getId(), null, null, null));

        assertThatThrownBy(() -> categoryService.update(a.getId(),
                new CategoryRequest("A", CategoryType.EXPENSE, b.getId(), null, null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CATEGORY_CYCLE));
    }

    private Account account(String name, AccountType type, BigDecimal opening, BigDecimal limit) {
        return accountService.create(new AccountRequest(name, type, "USD", opening, limit, null, null));
    }

    private Category category(String name, CategoryType type) {
        return categoryService.create(new CategoryRequest(name, type, null, null, null, null));
    }

    private TransactionRequest transaction(Long accountId, Long categoryId, TransactionType type,
                                           BigDecimal amount, String idempotencyKey) {
        return new TransactionRequest(accountId, categoryId, type, amount, LocalDate.now(), null, null, idempotencyKey);
    }
}
