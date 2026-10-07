package com.personal.finance.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransferResponse(
        Long sourceTxId,
        Long targetTxId,
        BigDecimal sourceAmount,
        BigDecimal targetAmount,
        String sourceCurrency,
        String targetCurrency,
        LocalDate date) {
}
