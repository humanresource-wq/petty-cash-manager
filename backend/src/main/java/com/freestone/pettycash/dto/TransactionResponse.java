package com.freestone.pettycash.dto;

import com.freestone.pettycash.model.ReceiptStatus;
import com.freestone.pettycash.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String transactionNo,
        TransactionType type,
        BigDecimal amount,
        String description,
        LocalDate date,
        LocalDateTime timestamp,
        String payer,
        String payee,
        Long categoryId,
        String categoryName,
        Long subcategoryId,
        String subcategoryName,
        ReceiptStatus receiptStatus,
        String receiptFileId,
        String receiptName,
        String voucherFileId,
        String voucherNumber,
        String company
) {}
