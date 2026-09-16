package com.gestordevendas.api.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardOverviewResponse(
    PeriodSummary today,
    PeriodSummary month,
    PeriodSummary year,
    List<LatestSale> latestSales,
    List<ItemSold> itemsSoldToday,
    List<LowStockProduct> lowStock,
    List<TopClient> topClientsMonth
) {
    public record PeriodSummary(long saleCount, BigDecimal revenue, BigDecimal cost, BigDecimal profit, BigDecimal marginPercent) {}
    public record LatestSale(UUID id, long number, String clientName, Instant soldAt, BigDecimal total, BigDecimal profit) {}
    public record ItemSold(UUID productId, String name, BigDecimal quantity, String photoUrl) {}
    public record LowStockProduct(UUID productId, String name, BigDecimal currentStock, BigDecimal minimumStock) {}
    public record TopClient(UUID clientId, String clientName, BigDecimal revenue) {}
}
