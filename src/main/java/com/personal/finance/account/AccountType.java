package com.personal.finance.account;

/**
 * The supported account kinds and their balance sign rules.
 */
public enum AccountType {

    /** Demand deposit account; balance must stay non-negative. */
    CHECKING(false),
    /** Time deposit account; balance must stay non-negative. */
    SAVINGS(false),
    /** Credit card; balance may go negative down to the credit limit. */
    CREDIT_CARD(true),
    /** Physical cash; balance must stay non-negative. */
    CASH(false),
    /** Investment account; balance must stay non-negative. */
    INVESTMENT(false),
    /** Debt / money owed; balance may go negative (no fixed limit). */
    DEBT(true);

    private final boolean allowsNegativeBalance;

    AccountType(boolean allowsNegativeBalance) {
        this.allowsNegativeBalance = allowsNegativeBalance;
    }

    /**
     * Whether a negative opening balance and a negative running balance are
     * permitted for this account type.
     */
    public boolean allowsNegativeBalance() {
        return allowsNegativeBalance;
    }
}
