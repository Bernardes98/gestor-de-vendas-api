package com.gestordevendas.api.auth.dto;

import com.gestordevendas.api.user.CompanyRole;

import java.util.UUID;

public record MeResponse(UserView user, CompanyView company, CompanyRole role) {
    public record UserView(UUID id, String email, boolean platformAdmin) {}
    public record CompanyView(UUID id, String name, boolean active) {}
}
