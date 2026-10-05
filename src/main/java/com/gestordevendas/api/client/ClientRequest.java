package com.gestordevendas.api.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

public record ClientRequest(
    @NotBlank @Size(max = 180) String name,
    @Size(max = 20) String document,
    @Size(max = 30) String phone,
    @Email @Size(max = 254) String email,
    @Size(max = 255) String address,
    @Size(max = 120) String city,
    @DecimalMin("0.00") BigDecimal defaultRate,
    @DecimalMin("0.00") BigDecimal deliveryFee,
    @Size(max = 1000) String notes
) {}
