package com.gestordevendas.api.order;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record HiddenProductsRequest(@NotNull List<@NotNull UUID> productIds) {}
