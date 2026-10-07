package com.personal.finance.transaction;

import com.personal.finance.account.Account;
import com.personal.finance.account.AccountRepository;
import com.personal.finance.account.BalanceService;
import com.personal.finance.category.Category;
import com.personal.finance.category.CategoryRepository;
import com.personal.finance.category.CategoryType;
import com.personal.finance.category.Tag;
import com.personal.finance.category.TagRepository;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.common.api.PageResponse;
import com.personal.finance.common.util.MoneyUtils;
import com.personal.finance.transaction.dto.TransactionRequest;
import com.personal.finance.transaction.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Create, update, delete and search income / expense transactions.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final BalanceService balanceService;

    @Value("${app.transaction.allow-future-dates:false}")
    private boolean allowFutureDates;

    @Transactional
    public TransactionResponse create(TransactionRequest request) {
        if (request.type() == TransactionType.TRANSFER_IN || request.type() == TransactionType.TRANSFER_OUT) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Use the transfer endpoint for transfers");
        }

        Optional<Transaction> existing = findExistingByKey(request.idempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Account account = getActiveAccount(request.accountId());
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found"));

        validateCategoryTypeMatch(request.type(), category);
        validateDate(request.date());
        validateAmount(request.amount());
        validateBalanceAfter(account, request.type(), request.amount());

        Transaction transaction = buildTransaction(account, category, request);
        applyTags(transaction, request.tagIds());

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionResponse update(Long id, TransactionRequest request) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Transaction not found"));

        if (transaction.getLinkedTxId() != null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Transfers must be edited through the transfer endpoint or deleted as a whole");
        }

        Account account = getActiveAccount(request.accountId());
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found"));

        validateCategoryTypeMatch(request.type(), category);
        validateDate(request.date());
        validateAmount(request.amount());
        validateBalanceAfter(account, request.type(), request.amount());

        transaction.setAccount(account);
        transaction.setCategory(category);
        transaction.setType(request.type());
        transaction.setAmount(MoneyUtils.round(request.amount()));
        transaction.setDate(request.date());
        transaction.setNote(request.note());
        applyTags(transaction, request.tagIds());

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Transaction not found"));

        if (transaction.getLinkedTxId() != null) {
            // Deleting one leg of a transfer deletes both legs so a one-sided
            // transfer can never remain in the ledger.
            transactionRepository.findById(transaction.getLinkedTxId())
                    .ifPresent(transactionRepository::delete);
        }
        transactionRepository.delete(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long id) {
        return toResponse(transactionRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Transaction not found")));
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(Long accountId, Long categoryId, TransactionType type,
                                                    LocalDate startDate, LocalDate endDate, String search,
                                                    int page, int size) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "Start date must not be after end date");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Page<Transaction> result = transactionRepository.search(accountId, categoryId, type,
                startDate, endDate, search, PageRequest.of(safePage, safeSize));
        return PageResponse.from(result, this::toResponse);
    }

    private Optional<Transaction> findExistingByKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return transactionRepository.findByIdempotencyKey(key);
    }

    private Account getActiveAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found"));
        if (account.isArchived()) {
            throw new ApiException(ErrorCode.ACCOUNT_ARCHIVED, "The account is archived");
        }
        return account;
    }

    private void validateCategoryTypeMatch(TransactionType type, Category category) {
        CategoryType expected = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
        if (category.getType() != expected) {
            throw new ApiException(ErrorCode.CATEGORY_TYPE_MISMATCH,
                    "Category type does not match the transaction type");
        }
    }

    private void validateDate(LocalDate date) {
        if (!allowFutureDates && date.isAfter(LocalDate.now())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Future dates are not allowed");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "Amount must be greater than zero");
        }
        if (MoneyUtils.hasMoreThanTwoDecimals(amount)) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "Amount must have at most 2 decimal places");
        }
    }

    private void validateBalanceAfter(Account account, TransactionType type, BigDecimal amount) {
        if (type != TransactionType.EXPENSE) {
            return;
        }
        BigDecimal resulting = balanceService.balance(account).subtract(MoneyUtils.round(amount));
        if (!account.canGoNegative() && resulting.signum() < 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient balance");
        }
        BigDecimal lowerBound = account.negativeLowerBound();
        if (account.canGoNegative() && lowerBound != null && resulting.compareTo(lowerBound) < 0) {
            throw new ApiException(ErrorCode.CREDIT_LIMIT_EXCEEDED, "Credit limit exceeded");
        }
    }

    private Transaction buildTransaction(Account account, Category category, TransactionRequest request) {
        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        transaction.setCategory(category);
        transaction.setType(request.type());
        transaction.setAmount(MoneyUtils.round(request.amount()));
        transaction.setDate(request.date());
        transaction.setNote(request.note());
        transaction.setIdempotencyKey(request.idempotencyKey());
        return transaction;
    }

    private void applyTags(Transaction transaction, Set<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            transaction.setTags(new HashSet<>());
            return;
        }
        Set<Tag> tags = tagIds.stream()
                .map(tagId -> tagRepository.findById(tagId)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Tag not found: " + tagId)))
                .collect(Collectors.toSet());
        transaction.setTags(tags);
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getAccount().getName(),
                transaction.getCategory() == null ? null : transaction.getCategory().getId(),
                transaction.getCategory() == null ? null : transaction.getCategory().getName(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDate(),
                transaction.getNote(),
                transaction.getLinkedTxId(),
                transaction.getSource(),
                transaction.getTags().stream().map(Tag::getName).collect(Collectors.toSet()));
    }
}
