package com.gestordevendas.api.food; import java.math.BigDecimal; public record SnackStatsResponse(long orders, BigDecimal revenue, BigDecimal cost, BigDecimal profit, BigDecimal margin){}
