package com.gestordevendas.api.supplier;
import jakarta.validation.constraints.*;
public record SupplierRequest(@NotBlank @Size(max=150) String name) {}
