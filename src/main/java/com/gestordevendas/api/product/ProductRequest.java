package com.gestordevendas.api.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
    @NotBlank @Size(max = 180) String name,
    @Size(max = 80) String code,
    @Size(max = 120) String brand,
    @Size(max = 1000) String description,
    @NotNull UUID categoryId,
    @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal costPrice,
    @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal salePrice,
    boolean stockControlled,
    @DecimalMin("0.000") @Digits(integer = 11, fraction = 3) BigDecimal minimumStock
) {}
