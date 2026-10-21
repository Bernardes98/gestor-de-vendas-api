package com.gestordevendas.api.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PublicPurchaseHistoryResponse(UUID id, long number, Instant soldAt, BigDecimal total, List<Item> items) {
    public record Item(String productName, BigDecimal quantity, BigDecimal total) {}
}
