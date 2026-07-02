package com.freestone.pettycash.dto;

import java.math.BigDecimal;

public record CashBoxResponse(
        BigDecimal balance,
        BigDecimal lowThreshold
) {}
