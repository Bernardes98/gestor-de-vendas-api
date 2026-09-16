package com.gestordevendas.api.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
    String jwtSecretBase64,
    String issuer,
    long accessTokenMinutes,
    long refreshTokenDays,
    String allowedOrigins
) {
}
