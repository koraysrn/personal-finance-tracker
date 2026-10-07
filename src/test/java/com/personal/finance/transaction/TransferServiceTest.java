package com.personal.finance.transaction;

import com.personal.finance.account.Account;
import com.personal.finance.account.AccountRepository;
import com.personal.finance.account.AccountType;
import com.personal.finance.account.BalanceService;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.transaction.dto.TransferRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Transfer guards and two-legged atomic record creation (TRF-01, TRF-02, TRF-04, TRF-05).
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private BalanceService balanceService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private TransferService transferService;

    @Test
    void transferToTheSameAccountIsRejected() {
        TransferRequest request = new TransferRequest(
                1L, 1L, new BigDecimal("10.00"), LocalDate.now(), null, null);

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.SAME_ACCOUNT_TRANSFER));
    }

    @Test
    void zeroAmountIsRejected() {
        TransferRequest request = new TransferRequest(
                1L, 2L, BigDecimal.ZERO, LocalDate.now(), null, null);

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.INVALID_AMOUNT));
    }

    @Test
    void insufficientSourceBalanceIsRejected() {
        Account source = account(1L, "Checking", AccountType.CHECKING, "USD");
        Account target = account(2L, "Savings", AccountType.CHECKING, "USD");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(target));
        when(balanceService.balance(source)).thenReturn(new BigDecimal("100.00"));

        TransferRequest request = new TransferRequest(
                1L, 2L, new BigDecimal("200.00"), LocalDate.now(), null, null);

        assertThatThrownBy(() -> transferService.transfer(request))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
    }

    @Test
    void transferCreatesTwoLinkedRecords() {
        Account source = account(1L, "Checking", AccountType.CHECKING, "USD");
        Account target = account(2L, "Savings", AccountType.CHECKING, "USD");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(source));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(target));
        when(balanceService.balance(source)).thenReturn(new BigDecimal("100.00"));

        AtomicLong id = new AtomicLong();
        when(transactionRepository.saveAndFlush(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(id.incrementAndGet());
            return tx;
        });

        TransferRequest request = new TransferRequest(
                1L, 2L, new BigDecimal("50.00"), LocalDate.now(), null, "KEY");

        var response = transferService.transfer(request);

        assertThat(response.sourceTxId()).isNotNull();
        assertThat(response.targetTxId()).isNotNull();
        assertThat(response.sourceAmount()).isEqualByComparingTo("50.00");
        assertThat(response.targetAmount()).isEqualByComparingTo("50.00");
    }

    private Account account(Long id, String name, AccountType type, String currency) {
        Account account = new Account();
        account.setId(id);
        account.setName(name);
        account.setType(type);
        account.setCurrency(currency);
        return account;
    }
}
