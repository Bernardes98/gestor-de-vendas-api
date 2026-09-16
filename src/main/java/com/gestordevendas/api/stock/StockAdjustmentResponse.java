package com.gestordevendas.api.stock;

import java.math.BigDecimal;
import java.util.UUID;

public record StockAdjustmentResponse(UUID productId, BigDecimal currentStock) {}
