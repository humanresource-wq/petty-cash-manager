package com.freestone.pettycash.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardStatsResponse(
    BigDecimal balance,
    BigDecimal filteredBalance,
    BigDecimal openingBalance,
    BigDecimal lowThreshold,
    BigDecimal currentMonthSpent,
    long currentMonthSpentCount,
    BigDecimal currentMonthAdded,
    BigDecimal totalAmountReceived,
    long pendingReceiptsCount,
    BigDecimal pendingReceiptsValue,
    List<MonthlyFlow> monthlyFlows,
    List<CategorySpend> categorySpends
) {
    public record MonthlyFlow(
        String month, // e.g. "Jul", "Aug"
        BigDecimal spent,
        BigDecimal added
    ) {}

    public record CategorySpend(
        String name,
        BigDecimal value
    ) {}
}
