package com.freestone.pettycash.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Template entity representing reusable expense blueprints for repetitive claims.
 */
@Entity
@Table(name = "expense_templates")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExpenseTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String category;

    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "receipt_required", nullable = false)
    private boolean receiptRequired = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public ExpenseTemplate(String name, String category, String description, BigDecimal amount, boolean receiptRequired) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.receiptRequired = receiptRequired;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
