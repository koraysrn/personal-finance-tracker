package com.personal.finance.report;

import com.personal.finance.account.Account;
import com.personal.finance.account.AccountType;
import com.personal.finance.category.Category;
import com.personal.finance.category.CategoryType;
import com.personal.finance.transaction.Transaction;
import com.personal.finance.transaction.TransactionRepository;
import com.personal.finance.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * CSV export is formula-injection safe (RPT-04).
 */
@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private ExportService exportService;

    @Test
    void formulaStartingNotesAreNeutralizedInCsv() {
        Account account = new Account();
        account.setName("Bank");
        account.setType(AccountType.CHECKING);
        account.setCurrency("USD");

        Category category = new Category();
        category.setName("Food");
        category.setType(CategoryType.EXPENSE);

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setCategory(category);
        tx.setType(TransactionType.EXPENSE);
        tx.setAmount(new BigDecimal("10.00"));
        tx.setDate(LocalDate.of(2026, 1, 15));
        tx.setNote("=HYPERLINK(\"http://evil.example\")");

        when(transactionRepository.findByDateBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of(tx));

        String csv = exportService.transactionsCsv(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(csv).contains("'=HYPERLINK");
        assertThat(csv).doesNotContain("=HYPERLINK(\"http://evil.example\")");
    }

    @Test
    void csvContainsHeaderRow() {
        when(transactionRepository.findByDateBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of());

        String csv = exportService.transactionsCsv(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(csv).contains("Date");
        assertThat(csv).contains("Note");
    }
}
