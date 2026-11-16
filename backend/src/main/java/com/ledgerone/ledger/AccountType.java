package com.ledgerone.ledger;

public enum AccountType {
    ASSET, LIABILITY, EQUITY, INCOME, EXPENSE;

    /** Debit-normal accounts grow with debits; the rest grow with credits. */
    public boolean debitNormal() {
        return this == ASSET || this == EXPENSE;
    }
}
