package com.gestordevendas.api.report;

import java.math.BigDecimal;
import java.util.List;

public record ChartResponse(
    ChartPeriod period,
    BigDecimal currentRevenue,
    BigDecimal currentProfit,
    BigDecimal previousRevenue,
    BigDecimal previousProfit,
    BigDecimal revenueGrowthPercent,
    BigDecimal profitGrowthPercent,
    List<Point> points
) {
    public record Point(String key, String label, String shortLabel, BigDecimal revenue, BigDecimal profit) {}
}
