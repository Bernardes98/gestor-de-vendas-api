package com.gestordevendas.api.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record PurchaseRequest(
    Instant purchasedAt,
    @Size(max = 1000) String notes,
    @NotEmpty List<@Valid PurchaseItemRequest> items
) {}
