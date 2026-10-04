package com.gestordevendas.api.company;

import java.util.UUID;

public record CompanySettingsResponse(
    UUID id,
    String slug,
    String name,
    String legalName,
    String document,
    String phone,
    String email,
    String address,
    String city,
    String primaryColor,
    String secondaryColor,
    String settleButtonColor,
    boolean active
) {
    public static CompanySettingsResponse from(Company company) {
        return new CompanySettingsResponse(
            company.getId(), company.getSlug(), company.getName(), company.getLegalName(),
            company.getDocument(), company.getPhone(), company.getEmail(), company.getAddress(),
            company.getCity(), company.getPrimaryColor(), company.getSecondaryColor(), company.getSettleButtonColor(), company.isActive()
        );
    }
}
