package com.personal.finance.account;

import com.personal.finance.account.dto.BalanceResponse;
import com.personal.finance.transaction.TransactionRepository;
import com.personal.finance.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Computes account balances from the opening balance plus the signed sum of
 * all transactions. Balances are never stored, only derived, so deleting or
 * editing a transaction can never leave a stale balance behind.
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public BigDecimal balance(Account account) {
        BigDecimal signed = BigDecimal.ZERO;
        List<Object[]> rows = transactionRepository.sumAmountsGroupedByType(account.getId());
        for (Object[] row : rows) {
            TransactionType type = (TransactionType) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            signed = signed.add(signedAmount(type, sum));
        }
        return account.getOpeningBalance().add(signed);
    }

    @Transactional(readOnly = true)
    public BalanceResponse balanceResponse(Account account) {
        BigDecimal balance = balance(account);
        BigDecimal availableLimit = null;
        if (account.getType() == AccountType.CREDIT_CARD) {
            // Available limit = credit limit + balance (balance is negative when used).
            availableLimit = account.getCreditLimit().add(balance);
        }
        return new BalanceResponse(account.getId(), balance, availableLimit);
    }

    private BigDecimal signedAmount(TransactionType type, BigDecimal amount) {
        return switch (type) {
            case INCOME, TRANSFER_IN -> amount;
            case EXPENSE, TRANSFER_OUT -> amount.negate();
        };
    }
}
