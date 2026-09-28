package com.gestordevendas.api.promotion;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record PromotionRequest(
    @NotNull @DecimalMin("0.00") @Digits(integer=12, fraction=2) BigDecimal promotionalPrice,
    @NotNull @DecimalMin("1.00") @Digits(integer=12, fraction=3) BigDecimal minimumQuantity
) {}
