package com.personal.finance.report;

import com.personal.finance.transaction.Transaction;
import com.personal.finance.transaction.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;

/**
 * Exports filtered transactions as CSV (formula-injection safe) and as a
 * printable HTML table.
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private static final String[] CSV_HEADERS =
            {"Date", "Account", "Category", "Type", "Amount", "Currency", "Note"};

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public String transactionsCsv(LocalDate start, LocalDate end) {
        List<Transaction> transactions = transactionRepository.findByDateBetween(start, end);
        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(CSV_HEADERS)
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (Transaction tx : transactions) {
                printer.printRecord(
                        tx.getDate(),
                        neutralizeFormula(tx.getAccount().getName()),
                        tx.getCategory() == null ? "" : neutralizeFormula(tx.getCategory().getName()),
                        tx.getType().name(),
                        tx.getAmount().toPlainString(),
                        tx.getAccount().getCurrency(),
                        neutralizeFormula(tx.getNote()));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build CSV export", ex);
        }
        return writer.toString();
    }

    @Transactional(readOnly = true)
    public String transactionsHtml(LocalDate start, LocalDate end) {
        List<Transaction> transactions = transactionRepository.findByDateBetween(start, end);
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
                .append("<title>Transaction Export</title>")
                .append("<style>body{font-family:sans-serif;}table{border-collapse:collapse;width:100%;}")
                .append("th,td{border:1px solid #ccc;padding:6px;text-align:left;}")
                .append("th{background:#f2f2f2;}</style></head><body>")
                .append("<h1>Transactions</h1><table>")
                .append("<thead><tr><th>Date</th><th>Account</th><th>Category</th><th>Type</th>")
                .append("<th>Amount</th><th>Currency</th><th>Note</th></tr></thead><tbody>");
        for (Transaction tx : transactions) {
            html.append("<tr>")
                    .append("<td>").append(HtmlUtils.htmlEscape(tx.getDate().toString())).append("</td>")
                    .append("<td>").append(HtmlUtils.htmlEscape(tx.getAccount().getName())).append("</td>")
                    .append("<td>").append(tx.getCategory() == null ? ""
                            : HtmlUtils.htmlEscape(tx.getCategory().getName())).append("</td>")
                    .append("<td>").append(tx.getType().name()).append("</td>")
                    .append("<td>").append(tx.getAmount().toPlainString()).append("</td>")
                    .append("<td>").append(HtmlUtils.htmlEscape(tx.getAccount().getCurrency())).append("</td>")
                    .append("<td>").append(tx.getNote() == null ? "" : HtmlUtils.htmlEscape(tx.getNote())).append("</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table></body></html>");
        return html.toString();
    }

    /**
     * Prevents CSV formula injection: values starting with {@code =}, {@code +},
     * {@code -} or {@code @} are prefixed with a single quote so spreadsheet
     * applications treat them as text instead of formulas.
     */
    private String neutralizeFormula(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.startsWith("=") || v.startsWith("+") || v.startsWith("-") || v.startsWith("@")) {
            return "'" + value;
        }
        return value;
    }
}
