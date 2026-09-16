package com.gestordevendas.api.pricing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductPriceRequest(
    @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal price
) {}
