package com.gestordevendas.api.promotion;
import java.math.BigDecimal;
import java.util.UUID;
public record PromotionAdminResponse(UUID clientId, String clientName, UUID productId, String productName, BigDecimal normalPrice, BigDecimal promotionalPrice, BigDecimal minimumQuantity) {}
