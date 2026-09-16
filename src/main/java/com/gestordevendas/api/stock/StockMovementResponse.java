package com.gestordevendas.api.stock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
    UUID id, UUID productId, String productName, StockMovementType type,
    BigDecimal quantityDelta, BigDecimal balanceAfter,
    String referenceType, UUID referenceId, String reason, Instant createdAt
) {}
