package com.gestordevendas.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerOrderResponse(
    UUID id, UUID clientId, String clientName, String status, Instant viewedAt, String notes,
    BigDecimal total, UUID saleId, Instant createdAt, List<CustomerOrderItemResponse> items
) {}
