package com.gestordevendas.api.product;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record ProductReorderRequest(@NotEmpty List<UUID> productIds) {}
