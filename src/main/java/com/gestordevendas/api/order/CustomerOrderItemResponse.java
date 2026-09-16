package com.gestordevendas.api.order;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerOrderItemResponse(UUID id, UUID productId, String productName, BigDecimal quantity,
                                        BigDecimal unitPrice, BigDecimal total) {}
