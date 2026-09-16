package com.gestordevendas.api.purchase;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseItemResponse(UUID productId, String productName, BigDecimal quantity, BigDecimal unitCost) {}
