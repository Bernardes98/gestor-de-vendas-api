package com.gestordevendas.api.supplier;
import jakarta.validation.constraints.NotEmpty; import java.util.*;
public record SupplierReorderRequest(@NotEmpty List<UUID> supplierIds) {}
