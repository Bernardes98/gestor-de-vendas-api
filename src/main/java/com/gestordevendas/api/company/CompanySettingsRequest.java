package com.gestordevendas.api.company;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanySettingsRequest(
    @NotBlank @Size(max = 180) String name,
    @Size(max = 180) String legalName,
    @Size(max = 20) String document,
    @Size(max = 30) String phone,
    @Email @Size(max = 254) String email,
    @Size(max = 255) String address,
    @Size(max = 120) String city,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor principal inválida.") String primaryColor,
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor do menu inválida.") String secondaryColor
) {}
