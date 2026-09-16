package com.gestordevendas.api.sale;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SaleItemResponse(UUID productId, String productName, BigDecimal quantity,
                               BigDecimal unitPrice, BigDecimal unitCost, BigDecimal lineTotal, BigDecimal lineCost) {}
