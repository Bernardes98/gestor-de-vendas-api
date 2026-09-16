package com.gestordevendas.api.sale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaleCancelRequest(@NotBlank @Size(max = 500) String reason) {}
