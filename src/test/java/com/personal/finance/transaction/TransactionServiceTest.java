package com.personal.finance.transaction;

import com.personal.finance.account.Account;
import com.personal.finance.account.AccountRepository;
import com.personal.finance.account.AccountType;
import com.personal.finance.account.BalanceService;
import com.personal.finance.category.Category;
import com.personal.finance.category.CategoryRepository;
import com.personal.finance.category.CategoryType;
import com.personal.finance.category.TagRepository;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.transaction.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Non-happy-path transaction rules (TX-01, TX-02, TX-04, TX-05, TX-06, TX-10).
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private BalanceService balanceService;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void transferTypeIsRejectedThroughTheTransactionEndpoint() {
        TransactionRequest request = new TransactionRequest(
                1L, 2L, TransactionType.TRANSFER_IN, new BigDecimal("10.00"),
                LocalDate.now(), null, null, null);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void expenseWithIncomeCategoryIsRejected() {
        stubAccountAndCategory(AccountType.CHECKING, CategoryType.INCOME);
        TransactionRequest request = new TransactionRequest(
                1L, 2L, TransactionType.EXPENSE, new BigDecimal("10.00"),
                LocalDate.now(), null, null, null);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CATEGORY_TYPE_MISMATCH));
    }

    @Test
    void expenseAboveBalanceIsRejected() {
        Account account = stubAccountAndCategory(AccountType.CHECKING, CategoryType.EXPENSE);
        when(balanceService.balance(account)).thenReturn(new BigDecimal("100.00"));

        TransactionRequest request = new TransactionRequest(
                1L, 2L, TransactionType.EXPENSE, new BigDecimal("200.00"),
                LocalDate.now(), null, null, null);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
    }

    @Test
    void zeroAmountIsRejected() {
        stubAccountAndCategory(AccountType.CHECKING, CategoryType.EXPENSE);
        TransactionRequest request = new TransactionRequest(
                1L, 2L, TransactionType.EXPENSE, BigDecimal.ZERO,
                LocalDate.now(), null, null, null);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void futureDateIsRejected() {
        stubAccountAndCategory(AccountType.CHECKING, CategoryType.EXPENSE);
        TransactionRequest request = new TransactionRequest(
                1L, 2L, TransactionType.EXPENSE, new BigDecimal("10.00"),
                LocalDate.now().plusDays(1), null, null, null);

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void repeatedIdempotencyKeyReturnsTheExistingTransaction() {
        Account account = new Account();
        account.setId(1L);
        account.setName("Checking");
        Category category = new Category();
        category.setId(2L);
        category.setName("Food");
        Transaction existing = new Transaction();
        existing.setId(99L);
        existing.setAccount(account);
        existing.setCategory(category);
        existing.setType(TransactionType.EXPENSE);
        existing.setAmount(new BigDecimal("10.00"));
        existing.setDate(LocalDate.now());

        when(transactionRepository.findByIdempotencyKey("KEY-1")).thenReturn(Optional.of(existing));

        TransactionRequest request = new TransactionRequest(
                1L, 2L, TransactionType.EXPENSE, new BigDecimal("10.00"),
                LocalDate.now(), null, null, "KEY-1");

        var response = transactionService.create(request);

        assertThat(response.id()).isEqualTo(99L);
        verify(transactionRepository, never()).save(any());
    }

    private Account stubAccountAndCategory(AccountType accountType, CategoryType categoryType) {
        Account account = new Account();
        account.setId(1L);
        account.setName("Account");
        account.setType(accountType);
        account.setCurrency("USD");

        Category category = new Category();
        category.setId(2L);
        category.setName("Category");
        category.setType(categoryType);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        return account;
    }
}
