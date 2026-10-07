package com.personal.finance.transaction.dto;

import com.personal.finance.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

public record TransactionResponse(
        Long id,
        Long accountId,
        String accountName,
        Long categoryId,
        String categoryName,
        TransactionType type,
        BigDecimal amount,
        LocalDate date,
        String note,
        Long linkedTxId,
        String source,
        Set<String> tags) {
}
