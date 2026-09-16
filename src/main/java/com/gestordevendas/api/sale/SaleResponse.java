package com.gestordevendas.api.sale;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SaleResponse(
    UUID id, long number, UUID clientId, String clientName, Instant soldAt,
    SalePaymentType paymentType, SaleStatus status, BigDecimal total,
    BigDecimal costTotal, BigDecimal profitTotal, BigDecimal paidAmount, BigDecimal outstanding,
    String cancelReason, List<SaleItemResponse> items
) {}
