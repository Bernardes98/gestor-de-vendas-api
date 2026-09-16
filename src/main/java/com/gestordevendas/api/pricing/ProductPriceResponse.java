package com.gestordevendas.api.pricing;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductPriceResponse(UUID clientId, UUID productId, BigDecimal price, boolean custom) {}
