package com.gestordevendas.api.fixedexpense;
import java.math.BigDecimal; import java.util.UUID;
public record FixedExpenseResponse(UUID id, String name, BigDecimal amount, Integer dueDay, String notes) {
    static FixedExpenseResponse from(FixedExpense e){ return new FixedExpenseResponse(e.getId(), e.getName(), e.getAmount(), e.getDueDay(), e.getNotes()); }
}
