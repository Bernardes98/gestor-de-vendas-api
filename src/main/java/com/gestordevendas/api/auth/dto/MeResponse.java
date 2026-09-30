package com.gestordevendas.api.auth.dto;

import com.gestordevendas.api.user.CompanyRole;

import java.util.UUID;
import java.util.Map;
import com.gestordevendas.api.feature.CompanyFeature;

public record MeResponse(UserView user, CompanyView company, CompanyRole role, Map<CompanyFeature, Boolean> features) {
    public record UserView(UUID id, String email, boolean platformAdmin) {}
    public record CompanyView(UUID id, String name, boolean active) {}
}
