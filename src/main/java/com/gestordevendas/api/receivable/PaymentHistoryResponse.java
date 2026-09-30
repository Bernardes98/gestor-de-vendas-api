package com.gestordevendas.api.receivable;
import java.math.BigDecimal; import java.time.*; import java.util.UUID;
public record PaymentHistoryResponse(UUID id, BigDecimal amount, LocalDate date, String notes, Instant createdAt) {}
