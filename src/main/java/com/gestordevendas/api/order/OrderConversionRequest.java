package com.gestordevendas.api.order;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OrderConversionRequest(@NotNull UUID saleId) {}
