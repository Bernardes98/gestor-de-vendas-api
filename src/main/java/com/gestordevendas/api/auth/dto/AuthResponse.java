package com.gestordevendas.api.auth.dto;

public record AuthResponse(String accessToken, long expiresInSeconds) {
}
