package com.freestone.pettycash.dto;

import com.freestone.pettycash.model.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionRequest(
        @NotNull(message = "Transaction type must not be null")
        TransactionType type,

        @NotNull(message = "Amount must not be null")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Description must not be blank")
        String description,

        @NotNull(message = "Date must not be null")
        LocalDate date,

        String payee, // Optional (recipient)

        Long categoryId, // Optional (mandatory for expense)

        Long subcategoryId, // Optional

        @NotBlank(message = "Voucher number must not be blank")
        String voucherNumber,

        @NotBlank(message = "Company must not be blank")
        String company
) {}
