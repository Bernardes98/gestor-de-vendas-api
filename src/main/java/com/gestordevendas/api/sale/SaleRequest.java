package com.gestordevendas.api.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SaleRequest(
    UUID clientId,
    Instant soldAt,
    @NotNull SalePaymentType paymentType,
    @NotEmpty List<@Valid SaleItemRequest> items
) {}
