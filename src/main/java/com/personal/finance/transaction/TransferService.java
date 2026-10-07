package com.personal.finance.transaction;

import com.personal.finance.account.Account;
import com.personal.finance.account.AccountRepository;
import com.personal.finance.account.BalanceService;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.common.util.MoneyUtils;
import com.personal.finance.transaction.dto.TransferRequest;
import com.personal.finance.transaction.dto.TransferResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Atomic money movement between two accounts. A transfer is always recorded as
 * two linked transactions inside a single database transaction, so a partial
 * (one-sided) transfer can never be committed.
 */
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BalanceService balanceService;
    private final ExchangeRateService exchangeRateService;

    @Value("${app.transaction.allow-future-dates:false}")
    private boolean allowFutureDates;

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new ApiException(ErrorCode.SAME_ACCOUNT_TRANSFER, "Source and target accounts must differ");
        }
        validateAmount(request.amount());
        validateDate(request.date());

        Optional<Transaction> existing = findExistingByKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return toTransferResponse(existing.get());
        }

        Account source = getActiveAccount(request.sourceAccountId());
        Account target = getActiveAccount(request.targetAccountId());

        BigDecimal sourceAmount = MoneyUtils.round(request.amount());
        BigDecimal targetAmount = source.getCurrency().equalsIgnoreCase(target.getCurrency())
                ? sourceAmount
                : exchangeRateService.convert(sourceAmount, source.getCurrency(), target.getCurrency());

        validateSourceBalance(source, sourceAmount);

        Transaction sourceTx = new Transaction();
        sourceTx.setAccount(source);
        sourceTx.setCategory(null);
        sourceTx.setType(TransactionType.TRANSFER_OUT);
        sourceTx.setAmount(sourceAmount);
        sourceTx.setDate(request.date());
        sourceTx.setNote(request.note());
        sourceTx.setIdempotencyKey(request.idempotencyKey());
        Transaction savedSource = transactionRepository.saveAndFlush(sourceTx);

        Transaction targetTx = new Transaction();
        targetTx.setAccount(target);
        targetTx.setCategory(null);
        targetTx.setType(TransactionType.TRANSFER_IN);
        targetTx.setAmount(targetAmount);
        targetTx.setDate(request.date());
        targetTx.setNote(request.note());
        targetTx.setLinkedTxId(savedSource.getId());
        Transaction savedTarget = transactionRepository.saveAndFlush(targetTx);

        savedSource.setLinkedTxId(savedTarget.getId());
        transactionRepository.save(savedSource);

        return new TransferResponse(
                savedSource.getId(),
                savedTarget.getId(),
                savedSource.getAmount(),
                savedTarget.getAmount(),
                source.getCurrency(),
                target.getCurrency(),
                savedSource.getDate());
    }

    private Optional<Transaction> findExistingByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return transactionRepository.findByIdempotencyKey(key);
    }

    private TransferResponse toTransferResponse(Transaction sourceTx) {
        Transaction targetTx = sourceTx.getLinkedTxId() == null
                ? null
                : transactionRepository.findById(sourceTx.getLinkedTxId()).orElse(null);
        if (targetTx == null) {
            return new TransferResponse(
                    sourceTx.getId(), null,
                    sourceTx.getAmount(), sourceTx.getAmount(),
                    sourceTx.getAccount().getCurrency(), sourceTx.getAccount().getCurrency(),
                    sourceTx.getDate());
        }
        return new TransferResponse(
                sourceTx.getId(), targetTx.getId(),
                sourceTx.getAmount(), targetTx.getAmount(),
                sourceTx.getAccount().getCurrency(), targetTx.getAccount().getCurrency(),
                sourceTx.getDate());
    }

    private Account getActiveAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found"));
        if (account.isArchived()) {
            throw new ApiException(ErrorCode.ACCOUNT_ARCHIVED, "The account is archived");
        }
        return account;
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "Amount must be greater than zero");
        }
        if (MoneyUtils.hasMoreThanTwoDecimals(amount)) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "Amount must have at most 2 decimal places");
        }
    }

    private void validateDate(LocalDate date) {
        if (!allowFutureDates && date.isAfter(LocalDate.now())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Future dates are not allowed");
        }
    }

    private void validateSourceBalance(Account source, BigDecimal amount) {
        BigDecimal resulting = balanceService.balance(source).subtract(amount);
        if (!source.canGoNegative() && resulting.signum() < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance");
        }
        BigDecimal lowerBound = source.negativeLowerBound();
        if (source.canGoNegative() && lowerBound != null && resulting.compareTo(lowerBound) < 0) {
            throw new ApiException(ErrorCode.CREDIT_LIMIT_EXCEEDED, "Credit limit exceeded");
        }
    }
}
