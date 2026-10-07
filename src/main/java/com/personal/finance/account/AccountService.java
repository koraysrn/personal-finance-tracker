package com.personal.finance.account;

import com.personal.finance.account.dto.AccountRequest;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.common.util.MoneyUtils;
import com.personal.finance.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business rules for account lifecycle: creation, update, archive, deletion.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public Account create(AccountRequest request) {
        if (accountRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "An account with this name already exists");
        }

        Account account = new Account();
        account.setName(request.name().trim());
        account.setType(request.type());
        account.setCurrency(request.currency().toUpperCase());
        account.setOpeningBalance(normalizeOpeningBalance(request));
        account.setCreditLimit(normalizeCreditLimit(request));
        account.setArchived(Boolean.TRUE.equals(request.archived()));
        account.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Account get(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found"));
    }

    @Transactional(readOnly = true)
    public List<Account> list() {
        return accountRepository.findAllByOrderBySortOrderAscNameAsc();
    }

    @Transactional
    public Account update(Long id, AccountRequest request) {
        Account account = get(id);

        if (!account.getName().equalsIgnoreCase(request.name().trim())
                && accountRepository.existsByNameIgnoreCase(request.name().trim())) {
            throw new ApiException(ErrorCode.CONFLICT, "An account with this name already exists");
        }

        account.setName(request.name().trim());
        account.setCurrency(request.currency().toUpperCase());
        account.setCreditLimit(normalizeCreditLimit(request));
        if (Boolean.TRUE.equals(request.archived())) {
            account.setArchived(true);
        }
        if (request.sortOrder() != null) {
            account.setSortOrder(request.sortOrder());
        }
        // Opening balance is immutable once the account has transactions to keep
        // historic balances consistent.
        if (!transactionRepository.existsByAccountId(account.getId())) {
            account.setOpeningBalance(normalizeOpeningBalance(request));
        }
        return accountRepository.save(account);
    }

    @Transactional
    public Account archive(Long id) {
        Account account = get(id);
        account.setArchived(true);
        return accountRepository.save(account);
    }

    @Transactional
    public void delete(Long id) {
        Account account = get(id);
        if (transactionRepository.existsByAccountId(account.getId())) {
            throw new ApiException(ErrorCode.ACCOUNT_IN_USE,
                    "The account has transactions and cannot be deleted; archive it instead");
        }
        accountRepository.delete(account);
    }

    private BigDecimal normalizeOpeningBalance(AccountRequest request) {
        BigDecimal opening = request.openingBalance() == null
                ? BigDecimal.ZERO
                : MoneyUtils.round(request.openingBalance());

        if (opening.signum() < 0 && !request.type().allowsNegativeBalance()) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT,
                    "Opening balance must not be negative for account type " + request.type());
        }
        return opening;
    }

    private BigDecimal normalizeCreditLimit(AccountRequest request) {
        return request.creditLimit() == null
                ? BigDecimal.ZERO
                : MoneyUtils.round(request.creditLimit());
    }
}
