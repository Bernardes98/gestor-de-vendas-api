package com.gestordevendas.api.tenant;

import com.gestordevendas.api.user.CompanyRole;

import java.util.UUID;

public record TenantContext(UUID companyId, UUID userId, CompanyRole role) {
}
