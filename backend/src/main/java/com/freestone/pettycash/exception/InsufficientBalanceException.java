package com.freestone.pettycash.exception;

import java.math.BigDecimal;

/**
 * Thrown when trying to record an expense that exceeds the current cash box balance.
 */
public class InsufficientBalanceException extends IllegalArgumentException {

    public InsufficientBalanceException(BigDecimal requested, BigDecimal available) {
        super("Insufficient balance in Cash Box. Requested: Rs. %s, Available: Rs. %s"
                .formatted(requested.setScale(2).toString(), available.setScale(2).toString()));
    }
}
