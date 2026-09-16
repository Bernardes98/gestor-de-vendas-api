package com.gestordevendas.api.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PublicOrderRequest(
    @Size(max = 1000) String notes,
    @NotEmpty List<@Valid Item> items
) {
    public record Item(
        @NotNull UUID productId,
        @NotNull @DecimalMin("0.001") @Digits(integer = 11, fraction = 3) BigDecimal quantity
    ) {}
}
