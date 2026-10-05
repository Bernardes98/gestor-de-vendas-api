package com.gestordevendas.api.pricing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

public record ProductPriceRequest(
    @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal price,
    String mode,
    @Digits(integer = 6, fraction = 4) BigDecimal rate,
    @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal fixedPrice
) {}
