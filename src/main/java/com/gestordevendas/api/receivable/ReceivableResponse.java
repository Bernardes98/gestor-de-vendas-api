package com.gestordevendas.api.receivable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReceivableResponse(String type, UUID id, Long saleNumber, UUID clientId, String clientName,
                                 String description, BigDecimal totalAmount, BigDecimal paidAmount,
                                 BigDecimal outstanding, Instant createdAt) {}
