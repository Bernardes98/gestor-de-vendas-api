package com.gestordevendas.api.receivable;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ManualReceivableRequest(
    UUID clientId,
    @NotBlank @Size(max = 500) String description,
    @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal totalAmount
) {}
