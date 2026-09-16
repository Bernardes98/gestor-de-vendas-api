package com.gestordevendas.api.auth;

import com.gestordevendas.api.common.security.SecurityProperties;
import com.gestordevendas.api.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void tokenContainsIdentityButNotTenantAuthorization() {
        byte[] secretBytes = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        SecurityProperties properties = new SecurityProperties(
            Base64.getEncoder().encodeToString(secretBytes),
            "gestor-de-vendas-api",
            15,
            30,
            "http://localhost:5173"
        );
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        JwtService jwtService = new JwtService(encoder, properties);
        User user = new User(UUID.randomUUID(), "owner@example.com", "Owner", true, false, false);

        String token = jwtService.issueAccessToken(user);
        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo(user.getEmail());
        assertThat(jwt.getClaims()).doesNotContainKeys("company_id", "role");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toMinutes()).isLessThanOrEqualTo(15);
    }
}
