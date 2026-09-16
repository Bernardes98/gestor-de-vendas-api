package com.gestordevendas.api.report;

import com.gestordevendas.api.sale.SalePaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SalesReportResponse(Summary summary, List<Row> sales) {
    public record Summary(long saleCount, BigDecimal revenue, BigDecimal cost, BigDecimal profit,
                          BigDecimal marginPercent, BigDecimal markupPercent) {}
    public record Row(UUID id, long number, Instant soldAt, UUID clientId, String clientName,
                      SalePaymentType paymentType, BigDecimal total, BigDecimal cost,
                      BigDecimal profit, BigDecimal marginPercent) {}
}
