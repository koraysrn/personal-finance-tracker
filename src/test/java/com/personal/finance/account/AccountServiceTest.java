package com.personal.finance.account;

import com.personal.finance.account.dto.AccountRequest;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.transaction.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Account type rules and lifecycle guards (ACC-01, ACC-02, ACC-04, ACC-08).
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void negativeOpeningBalanceIsRejectedForCashAccount() {
        AccountRequest request = new AccountRequest(
                "Cash", AccountType.CASH, "USD", new BigDecimal("-500"), BigDecimal.ZERO, null, null);

        assertThatThrownBy(() -> accountService.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void negativeOpeningBalanceIsAllowedForCreditCard() {
        when(accountRepository.existsByNameIgnoreCase("Card")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(1L);
            return account;
        });

        AccountRequest request = new AccountRequest(
                "Card", AccountType.CREDIT_CARD, "USD", new BigDecimal("-500"), new BigDecimal("1000"), null, null);

        Account created = accountService.create(request);

        assertThat(created.getOpeningBalance()).isEqualByComparingTo("-500");
    }

    @Test
    void duplicateAccountNameIsRejected() {
        when(accountRepository.existsByNameIgnoreCase("Duplicate")).thenReturn(true);
        AccountRequest request = new AccountRequest(
                "Duplicate", AccountType.CHECKING, "USD", BigDecimal.ZERO, BigDecimal.ZERO, null, null);

        assertThatThrownBy(() -> accountService.create(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void accountWithTransactionsCannotBeDeleted() {
        Account account = new Account();
        account.setId(1L);
        account.setName("Checking");
        account.setType(AccountType.CHECKING);
        account.setCurrency("USD");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.existsByAccountId(1L)).thenReturn(true);

        assertThatThrownBy(() -> accountService.delete(1L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.ACCOUNT_IN_USE));
    }
}
