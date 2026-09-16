package com.gestordevendas.api.receivable;

import java.math.BigDecimal;
import java.util.UUID;

public record ManualReceivableResponse(UUID id, UUID clientId, String clientName, String description,
                                       BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal outstanding,
                                       ManualReceivableStatus status) {}
