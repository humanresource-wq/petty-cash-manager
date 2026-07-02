package com.freestone.pettycash.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * CashBox entity storing current running balance.
 * Implements Optimistic Locking using @Version for thread-safe balance ledger operations.
 */
@Entity
@Table(name = "cash_boxes")
@Getter
@Setter
public class CashBox {

    @Id
    private Long id = 1L;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal lowThreshold = BigDecimal.valueOf(2000.00);

    @Version
    private Long version;
}
