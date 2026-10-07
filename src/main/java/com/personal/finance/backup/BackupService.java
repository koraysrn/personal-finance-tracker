package com.personal.finance.backup;

import com.personal.finance.account.Account;
import com.personal.finance.account.AccountRepository;
import com.personal.finance.account.AccountType;
import com.personal.finance.backup.dto.BackupData;
import com.personal.finance.backup.dto.ImportResult;
import com.personal.finance.budget.Budget;
import com.personal.finance.budget.BudgetRepository;
import com.personal.finance.category.Category;
import com.personal.finance.category.CategoryRepository;
import com.personal.finance.category.CategoryType;
import com.personal.finance.category.Tag;
import com.personal.finance.category.TagRepository;
import com.personal.finance.common.api.ApiException;
import com.personal.finance.common.api.ErrorCode;
import com.personal.finance.recurring.RecurringFrequency;
import com.personal.finance.recurring.RecurringTransaction;
import com.personal.finance.recurring.RecurringTransactionRepository;
import com.personal.finance.transaction.Transaction;
import com.personal.finance.transaction.TransactionRepository;
import com.personal.finance.transaction.TransactionType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full snapshot export and restore, plus CSV transaction import.
 * A restore is transactional: either everything is loaded or nothing is.
 */
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final int CURRENT_VERSION = 1;

    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final RecurringTransactionRepository recurringRepository;

    @Transactional(readOnly = true)
    public BackupData exportData() {
        List<BackupData.AccountItem> accounts = accountRepository.findAll().stream()
                .map(a -> new BackupData.AccountItem(
                        a.getName(), a.getType().name(), a.getCurrency(),
                        a.getOpeningBalance(), a.getCreditLimit(), a.isArchived(), a.getSortOrder()))
                .toList();

        List<BackupData.CategoryItem> categories = categoryRepository.findAll().stream()
                .map(c -> new BackupData.CategoryItem(
                        c.getName(), c.getType().name(),
                        c.getParent() == null ? null : c.getParent().getName(),
                        c.getIcon(), c.getColor(), c.isArchived()))
                .toList();

        List<BackupData.TagItem> tags = tagRepository.findAll().stream()
                .map(t -> new BackupData.TagItem(t.getName()))
                .toList();

        List<BackupData.TransactionItem> transactions = transactionRepository
                .findAll(Sort.by("id")).stream()
                .map(t -> new BackupData.TransactionItem(
                        t.getId(),
                        t.getAccount().getName(),
                        t.getCategory() == null ? null : t.getCategory().getName(),
                        t.getType().name(),
                        t.getAmount(),
                        t.getDate(),
                        t.getNote(),
                        t.getLinkedTxId(),
                        t.getSource(),
                        t.getTags().stream().map(Tag::getName).toList()))
                .toList();

        List<BackupData.BudgetItem> budgets = budgetRepository.findAll().stream()
                .map(b -> new BackupData.BudgetItem(
                        b.getCategory().getName(), b.getYear(), b.getMonth(), b.getAmount()))
                .toList();

        List<BackupData.RecurringItem> recurring = recurringRepository.findAll().stream()
                .map(r -> new BackupData.RecurringItem(
                        r.getAccount().getName(), r.getCategory().getName(), r.getType().name(),
                        r.getAmount(), r.getFrequency().name(), r.getDayOfMonth(), r.getDayOfWeek(),
                        r.getNote(), r.isActive(), r.getStartDate(), r.getEndDate(), r.getNextRun()))
                .toList();

        return new BackupData(CURRENT_VERSION, Instant.now(),
                accounts, categories, tags, transactions, budgets, recurring);
    }

    /**
     * Restores a backup snapshot atomically. When the database already contains
     * data, {@code replace} must be explicitly requested to avoid accidental
     * data loss.
     */
    @Transactional
    public Map<String, Object> importData(BackupData data, boolean replace) {
        if (data.version() > CURRENT_VERSION) {
            throw new ApiException(ErrorCode.IMPORT_FAILED, "Backup version is not supported");
        }
        if (!replace && hasAnyData()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Existing data found; pass replace=true to overwrite everything");
        }

        clearAll();

        Map<String, Category> categoryByName = insertCategories(data.categories());
        Map<String, Account> accountByName = insertAccounts(data.accounts());
        insertTags(data.tags());
        int importedTx = insertTransactions(data.transactions(), accountByName, categoryByName);
        int importedBudgets = insertBudgets(data.budgets(), categoryByName);
        int importedRecurring = insertRecurring(data.recurring(), accountByName, categoryByName);

        return Map.of(
                "imported", true,
                "accounts", accountByName.size(),
                "categories", categoryByName.size(),
                "transactions", importedTx,
                "budgets", importedBudgets,
                "recurring", importedRecurring);
    }

    /**
     * Imports transactions from a CSV stream. Valid rows are imported, invalid
     * rows are skipped and reported with their row number.
     */
    @Transactional
    public ImportResult importCsv(String csv) {
        List<ImportResult.RowError> errors = new ArrayList<>();
        int imported = 0;
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(new StringReader(csv))) {

            for (CSVRecord record : parser) {
                int row = (int) record.getRecordNumber();
                try {
                    importRow(record);
                    imported++;
                } catch (Exception ex) {
                    errors.add(new ImportResult.RowError(row, ex.getMessage()));
                }
            }
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.IMPORT_FAILED, "CSV file could not be parsed: " + ex.getMessage());
        }
        return new ImportResult(imported, errors);
    }

    private void importRow(CSVRecord record) {
        LocalDate date = LocalDate.parse(record.get("Date"));
        String accountName = record.get("Account");
        String categoryName = record.get("Category");
        TransactionType type = TransactionType.valueOf(record.get("Type"));
        BigDecimal amount = new BigDecimal(record.get("Amount"));
        String note = record.isSet("Note") ? record.get("Note") : null;

        Account account = accountRepository.findAll().stream()
                .filter(a -> a.getName().equalsIgnoreCase(accountName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown account: " + accountName));
        Category category = categoryRepository.findAll().stream()
                .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + categoryName));

        if (type != TransactionType.INCOME && type != TransactionType.EXPENSE) {
            throw new IllegalArgumentException("Only INCOME and EXPENSE rows can be imported");
        }
        CategoryType expected = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
        if (category.getType() != expected) {
            throw new IllegalArgumentException("Category type does not match the transaction type");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setCategory(category);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setDate(date);
        tx.setNote(note);
        tx.setSource(Transaction.SOURCE_IMPORT);
        transactionRepository.save(tx);
    }

    private boolean hasAnyData() {
        return accountRepository.count() > 0 || transactionRepository.count() > 0;
    }

    private void clearAll() {
        transactionRepository.deleteAllInBatch();
        budgetRepository.deleteAllInBatch();
        recurringRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        tagRepository.deleteAllInBatch();
    }

    private Map<String, Category> insertCategories(List<BackupData.CategoryItem> items) {
        Map<String, Category> byName = new HashMap<>();
        for (BackupData.CategoryItem item : items) {
            Category category = new Category();
            category.setName(item.name());
            category.setType(CategoryType.valueOf(item.type()));
            category.setIcon(item.icon());
            category.setColor(item.color());
            category.setArchived(item.archived());
            category = categoryRepository.saveAndFlush(category);
            byName.put(item.name(), category);
        }
        for (BackupData.CategoryItem item : items) {
            if (item.parentName() != null) {
                Category child = byName.get(item.name());
                Category parent = byName.get(item.parentName());
                if (parent != null) {
                    child.setParent(parent);
                    categoryRepository.save(child);
                }
            }
        }
        return byName;
    }

    private Map<String, Account> insertAccounts(List<BackupData.AccountItem> items) {
        Map<String, Account> byName = new HashMap<>();
        for (BackupData.AccountItem item : items) {
            Account account = new Account();
            account.setName(item.name());
            account.setType(AccountType.valueOf(item.type()));
            account.setCurrency(item.currency());
            account.setOpeningBalance(item.openingBalance() == null ? BigDecimal.ZERO : item.openingBalance());
            account.setCreditLimit(item.creditLimit() == null ? BigDecimal.ZERO : item.creditLimit());
            account.setArchived(item.archived());
            account.setSortOrder(item.sortOrder());
            account = accountRepository.save(account);
            byName.put(item.name(), account);
        }
        return byName;
    }

    private void insertTags(List<BackupData.TagItem> items) {
        for (BackupData.TagItem item : items) {
            Tag tag = new Tag();
            tag.setName(item.name());
            tagRepository.save(tag);
        }
    }

    private int insertTransactions(List<BackupData.TransactionItem> items,
                                   Map<String, Account> accountByName,
                                   Map<String, Category> categoryByName) {
        Map<Long, Long> oldToNew = new HashMap<>();
        Map<Long, Long> linkedOldIds = new HashMap<>();

        for (BackupData.TransactionItem item : items) {
            Account account = accountByName.get(item.accountName());
            if (account == null) {
                throw new ApiException(ErrorCode.IMPORT_FAILED, "Unknown account in backup: " + item.accountName());
            }
            Category category = item.categoryName() == null ? null : categoryByName.get(item.categoryName());

            Transaction tx = new Transaction();
            tx.setAccount(account);
            tx.setCategory(category);
            tx.setType(TransactionType.valueOf(item.type()));
            tx.setAmount(item.amount());
            tx.setDate(item.date());
            tx.setNote(item.note());
            tx.setSource(item.source() == null ? Transaction.SOURCE_MANUAL : item.source());
            tx.setTags(resolveTags(item.tags()));
            tx = transactionRepository.saveAndFlush(tx);
            oldToNew.put(item.oldId(), tx.getId());
            if (item.linkedOldId() != null) {
                linkedOldIds.put(tx.getId(), item.linkedOldId());
            }
        }

        for (Map.Entry<Long, Long> entry : linkedOldIds.entrySet()) {
            Long newLinkedId = oldToNew.get(entry.getValue());
            if (newLinkedId != null) {
                Transaction tx = transactionRepository.findById(entry.getKey()).orElse(null);
                if (tx != null) {
                    tx.setLinkedTxId(newLinkedId);
                    transactionRepository.save(tx);
                }
            }
        }
        return items.size();
    }

    private Set<Tag> resolveTags(List<String> names) {
        Set<Tag> result = new HashSet<>();
        if (names == null) {
            return result;
        }
        for (String name : names) {
            tagRepository.findAll().stream()
                    .filter(t -> t.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }

    private int insertBudgets(List<BackupData.BudgetItem> items, Map<String, Category> categoryByName) {
        for (BackupData.BudgetItem item : items) {
            Category category = categoryByName.get(item.categoryName());
            if (category == null) {
                throw new ApiException(ErrorCode.IMPORT_FAILED, "Unknown category in budget: " + item.categoryName());
            }
            Budget budget = new Budget();
            budget.setCategory(category);
            budget.setYear(item.year());
            budget.setMonth(item.month());
            budget.setAmount(item.amount());
            budgetRepository.save(budget);
        }
        return items.size();
    }

    private int insertRecurring(List<BackupData.RecurringItem> items,
                                Map<String, Account> accountByName,
                                Map<String, Category> categoryByName) {
        for (BackupData.RecurringItem item : items) {
            Account account = accountByName.get(item.accountName());
            Category category = categoryByName.get(item.categoryName());
            if (account == null || category == null) {
                throw new ApiException(ErrorCode.IMPORT_FAILED,
                        "Unknown account or category in recurring item: " + item.accountName());
            }
            RecurringTransaction rt = new RecurringTransaction();
            rt.setAccount(account);
            rt.setCategory(category);
            rt.setType(TransactionType.valueOf(item.type()));
            rt.setAmount(item.amount());
            rt.setFrequency(RecurringFrequency.valueOf(item.frequency()));
            rt.setDayOfMonth(item.dayOfMonth());
            rt.setDayOfWeek(item.dayOfWeek());
            rt.setNote(item.note());
            rt.setActive(item.active());
            rt.setStartDate(item.startDate());
            rt.setEndDate(item.endDate());
            rt.setNextRun(item.nextRun());
            recurringRepository.save(rt);
        }
        return items.size();
    }
}
