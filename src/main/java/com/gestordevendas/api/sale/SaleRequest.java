package com.gestordevendas.api.sale;
import com.gestordevendas.api.food.PaymentMethod; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.time.Instant; import java.util.*;
public record SaleRequest(UUID clientId,Instant soldAt,@NotNull SalePaymentType paymentType,PaymentMethod paymentMethod,@DecimalMin("0.00") BigDecimal cashReceived,@NotEmpty List<@Valid SaleItemRequest> items) {}
