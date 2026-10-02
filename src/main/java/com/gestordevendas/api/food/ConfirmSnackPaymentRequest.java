package com.gestordevendas.api.food; import java.math.BigDecimal; public record ConfirmSnackPaymentRequest(PaymentMethod paymentMethod, BigDecimal cashReceived) {}
