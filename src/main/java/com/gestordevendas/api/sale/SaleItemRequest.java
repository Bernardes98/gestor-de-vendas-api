package com.gestordevendas.api.sale;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record SaleItemRequest(
    @NotNull UUID productId,
    @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity,
    @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice
) {}
