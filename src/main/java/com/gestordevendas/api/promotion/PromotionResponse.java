package com.gestordevendas.api.promotion;
import java.math.BigDecimal;
import java.util.UUID;
public record PromotionResponse(UUID productId, BigDecimal promotionalPrice, BigDecimal minimumQuantity) {}
