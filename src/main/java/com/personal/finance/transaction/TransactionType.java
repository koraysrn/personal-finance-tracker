package com.personal.finance.transaction;

/**
 * The direction of a transaction. Transfers are modelled as two linked
 * records: {@link #TRANSFER_OUT} on the source account and {@link #TRANSFER_IN}
 * on the target account.
 */
public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER_IN,
    TRANSFER_OUT
}
