package com.freestone.pettycash.model;

/**
 * Transaction type for ledger recording.
 */
public enum TransactionType {
    TOPUP,   // Money added to the cashbox
    EXPENSE  // Money spent from the cashbox
}
