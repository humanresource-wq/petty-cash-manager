package com.freestone.pettycash.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TemplateRequest(
        @NotBlank(message = "Template name must not be blank")
        String name,

        @NotBlank(message = "Category name must not be blank")
        String category,

        String description,

        @NotNull(message = "Amount must not be null")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        BigDecimal amount,

        boolean receiptRequired
) {}
