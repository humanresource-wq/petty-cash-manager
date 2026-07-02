package com.freestone.pettycash.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Transaction entity recording ledger movements in the cashbox.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PettyCashTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_no", nullable = false, unique = true)
    private String transactionNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String payer; // Payer email

    private String payee; // Payee/Recipient (if expense)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategory_id")
    private Subcategory subcategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "receipt_status", nullable = false)
    private ReceiptStatus receiptStatus = ReceiptStatus.NA;

    @Column(name = "receipt_file_id")
    private String receiptFileId; // Google Drive file ID of the uploaded receipt/invoice

    @Column(name = "receipt_name")
    private String receiptName; // Receipt filename

    @Column(name = "voucher_file_id")
    private String voucherFileId; // Google Drive file ID of the dynamically generated PDF voucher

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public PettyCashTransaction(String transactionNo, TransactionType type, BigDecimal amount, String description,
                                LocalDate date, String payer, String payee, Category category, Subcategory subcategory) {
        this.transactionNo = transactionNo;
        this.type = type;
        this.amount = amount;
        this.description = description;
        this.date = date;
        this.timestamp = LocalDateTime.now();
        this.payer = payer;
        this.payee = payee;
        this.category = category;
        this.subcategory = subcategory;
        this.receiptStatus = (type == TransactionType.TOPUP) ? ReceiptStatus.NA : ReceiptStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.timestamp == null) {
            this.timestamp = this.createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
