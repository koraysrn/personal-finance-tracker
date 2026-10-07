package com.personal.finance.recurring;

import com.personal.finance.account.Account;
import com.personal.finance.account.AccountRepository;
import com.personal.finance.category.Category;
import com.personal.finance.category.CategoryRepository;
import com.personal.finance.category.CategoryType;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.common.util.MoneyUtils;
import com.personal.finance.recurring.dto.RecurringTransactionRequest;
import com.personal.finance.recurring.dto.RecurringTransactionResponse;
import com.personal.finance.transaction.Transaction;
import com.personal.finance.transaction.TransactionRepository;
import com.personal.finance.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Manages recurring transaction rules and turns due rules into concrete
 * transactions. Each generation is idempotent thanks to a deterministic key
 * ({@code REC-<id>-<date>}) and the unique idempotency index.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecurringTransactionService {

    private static final int CATCH_UP_GUARD = 366;

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final Clock clock;

    @Transactional
    public RecurringTransactionResponse create(RecurringTransactionRequest request) {
        if (request.type() != TransactionType.INCOME && request.type() != TransactionType.EXPENSE) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Recurring transactions must be either income or expense");
        }
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account not found"));
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Category not found"));
        validateCategoryTypeMatch(request.type(), category);
        validateDayRules(request);

        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE, "End date must not be before start date");
        }

        RecurringTransaction rt = new RecurringTransaction();
        rt.setAccount(account);
        rt.setCategory(category);
        rt.setType(request.type());
        rt.setAmount(MoneyUtils.round(request.amount()));
        rt.setFrequency(request.frequency());
        rt.setDayOfMonth(request.dayOfMonth());
        rt.setDayOfWeek(request.dayOfWeek());
        rt.setNote(request.note());
        rt.setActive(!Boolean.FALSE.equals(request.active()));
        rt.setStartDate(request.startDate());
        rt.setEndDate(request.endDate());
        rt.setNextRun(computeInitialNextRun(request.startDate(), request.frequency(),
                request.dayOfMonth(), request.dayOfWeek()));
        return toResponse(recurringRepository.save(rt));
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> list() {
        return recurringRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long id) {
        RecurringTransaction rt = recurringRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Recurring transaction not found"));
        recurringRepository.delete(rt);
    }

    @Transactional
    public RecurringTransactionResponse setActive(Long id, boolean active) {
        RecurringTransaction rt = recurringRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Recurring transaction not found"));
        rt.setActive(active);
        return toResponse(recurringRepository.save(rt));
    }

    /**
     * Generates transactions for all due recurring rules. Runs inside the
     * scheduled job. A single failing rule is deactivated and logged instead of
     * aborting the whole run.
     *
     * @return the number of transactions generated
     */
    @Transactional
    public int processDue() {
        LocalDate today = LocalDate.now(clock);
        List<RecurringTransaction> due = recurringRepository.findDue(today);
        int generated = 0;
        for (RecurringTransaction rt : due) {
            try {
                generated += processSingleRule(rt, today);
                recurringRepository.save(rt);
            } catch (Exception ex) {
                log.error("Recurring transaction {} failed, deactivating rule", rt.getId(), ex);
                rt.setActive(false);
                recurringRepository.save(rt);
            }
        }
        return generated;
    }

    private int processSingleRule(RecurringTransaction rt, LocalDate today) {
        int generated = 0;
        int guard = 0;
        while (rt.isActive()
                && !rt.getNextRun().isAfter(today)
                && (rt.getEndDate() == null || !rt.getNextRun().isAfter(rt.getEndDate()))
                && guard++ < CATCH_UP_GUARD) {
            if (generateFor(rt, rt.getNextRun())) {
                generated++;
            }
            rt.setLastRun(Instant.now());
            rt.setNextRun(nextOccurrenceAfter(rt.getNextRun(), rt.getFrequency(),
                    rt.getDayOfMonth(), rt.getDayOfWeek()));
        }
        return generated;
    }

    private boolean generateFor(RecurringTransaction rt, LocalDate date) {
        String idempotencyKey = "REC-" + rt.getId() + "-" + date;
        if (transactionRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return false; // already generated by an earlier run (idempotency)
        }

        Account account = accountRepository.findById(rt.getAccount().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Account no longer exists"));
        if (account.isArchived()) {
            throw new ApiException(ErrorCode.ACCOUNT_ARCHIVED, "Account is archived");
        }

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setCategory(rt.getCategory());
        tx.setType(rt.getType());
        tx.setAmount(rt.getAmount());
        tx.setDate(date);
        tx.setNote(rt.getNote());
        tx.setSource(Transaction.SOURCE_RECURRING);
        tx.setIdempotencyKey(idempotencyKey);
        transactionRepository.save(tx);
        return true;
    }

    private void validateCategoryTypeMatch(TransactionType type, Category category) {
        CategoryType expected = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
        if (category.getType() != expected) {
            throw new ApiException(ErrorCode.CATEGORY_TYPE_MISMATCH,
                    "Category type does not match the transaction type");
        }
    }

    private void validateDayRules(RecurringTransactionRequest request) {
        boolean needsDayOfMonth = request.frequency() == RecurringFrequency.MONTHLY
                || request.frequency() == RecurringFrequency.YEARLY;
        if (needsDayOfMonth && request.dayOfMonth() == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Day of month is required for monthly and yearly rules");
        }
        if (request.dayOfMonth() != null
                && (request.dayOfMonth() < 1 || request.dayOfMonth() > 31)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Day of month must be between 1 and 31");
        }
    }

    private LocalDate computeInitialNextRun(LocalDate startDate, RecurringFrequency frequency,
                                            Integer dayOfMonth, Integer dayOfWeek) {
        LocalDate today = LocalDate.now(clock);
        LocalDate candidate = firstOccurrenceAtOrAfter(startDate, frequency, dayOfMonth);
        int guard = 0;
        while (candidate.isBefore(today) && guard++ < CATCH_UP_GUARD) {
            candidate = nextOccurrenceAfter(candidate, frequency, dayOfMonth, dayOfWeek);
        }
        return candidate;
    }

    private LocalDate firstOccurrenceAtOrAfter(LocalDate start, RecurringFrequency frequency,
                                               Integer dayOfMonth) {
        return switch (frequency) {
            case DAILY, WEEKLY -> start;
            case MONTHLY -> {
                int day = dayOfMonth == null ? start.getDayOfMonth() : dayOfMonth;
                YearMonth ym = YearMonth.from(start);
                LocalDate candidate = ym.atDay(Math.min(day, ym.lengthOfMonth()));
                yield candidate.isBefore(start) ? nextMonthly(start, dayOfMonth) : candidate;
            }
            case YEARLY -> start.withDayOfMonth(Math.min(start.getDayOfMonth(), start.lengthOfMonth()));
        };
    }

    private LocalDate nextOccurrenceAfter(LocalDate current, RecurringFrequency frequency,
                                          Integer dayOfMonth, Integer dayOfWeek) {
        return switch (frequency) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case MONTHLY -> nextMonthly(current, dayOfMonth);
            case YEARLY -> {
                LocalDate candidate = current.plusYears(1);
                yield candidate.withDayOfMonth(Math.min(current.getDayOfMonth(), candidate.lengthOfMonth()));
            }
        };
    }

    private LocalDate nextMonthly(LocalDate current, Integer dayOfMonth) {
        YearMonth ym = YearMonth.from(current).plusMonths(1);
        int day = dayOfMonth == null ? current.getDayOfMonth() : dayOfMonth;
        return ym.atDay(Math.min(day, ym.lengthOfMonth()));
    }

    private RecurringTransactionResponse toResponse(RecurringTransaction rt) {
        return new RecurringTransactionResponse(
                rt.getId(),
                rt.getAccount().getId(),
                rt.getAccount().getName(),
                rt.getCategory().getId(),
                rt.getCategory().getName(),
                rt.getType(),
                rt.getAmount(),
                rt.getFrequency(),
                rt.getDayOfMonth(),
                rt.getDayOfWeek(),
                rt.getNote(),
                rt.isActive(),
                rt.getStartDate(),
                rt.getEndDate(),
                rt.getNextRun(),
                rt.getLastRun());
    }
}
