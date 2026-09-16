package com.gestordevendas.api.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurchaseResponse(UUID id, Instant purchasedAt, String notes, PurchaseStatus status,
                               BigDecimal totalCost, List<PurchaseItemResponse> items) {}
