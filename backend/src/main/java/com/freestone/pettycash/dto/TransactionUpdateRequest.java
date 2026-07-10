package com.freestone.pettycash.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request DTO for updating an existing petty cash transaction.
 * Transaction type is immutable — changing it requires a reversal entry.
 */
public record TransactionUpdateRequest(
        @NotNull(message = "Amount must not be null")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotBlank(message = "Description must not be blank")
        String description,

        @NotNull(message = "Date must not be null")
        LocalDate date,

        String payee,

        Long categoryId,

        Long subcategoryId,

        @NotBlank(message = "Voucher number must not be blank")
        String voucherNumber,

        @NotBlank(message = "Company must not be blank")
        String company
) {}
