package com.gestordevendas.api.fixedexpense;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record FixedExpenseRequest(
    @NotBlank @Size(max=120) String name,
    @NotNull @DecimalMin(value="0.00", inclusive=true) BigDecimal amount,
    @Min(1) @Max(31) Integer dueDay,
    @Size(max=500) String notes
) {}
