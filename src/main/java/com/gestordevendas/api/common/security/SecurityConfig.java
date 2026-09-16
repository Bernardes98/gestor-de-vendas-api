package com.gestordevendas.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestordevendas.api.common.error.ApiError;
import com.gestordevendas.api.common.request.RequestIdFilter;
import com.gestordevendas.api.mail.MailProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, MailProperties.class})
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecretKey jwtSecretKey(SecurityProperties properties) {
        if (properties.jwtSecretBase64() == null || properties.jwtSecretBase64().isBlank()) {
            throw new IllegalStateException("JWT_SECRET_BASE64 é obrigatório e deve conter pelo menos 32 bytes em Base64.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.jwtSecretBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET_BASE64 não é um Base64 válido.", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET_BASE64 deve conter pelo menos 32 bytes após decodificação.");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey) {
        return NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    CookieOriginFilter cookieOriginFilter(SecurityProperties properties, ObjectMapper objectMapper) {
        return new CookieOriginFilter(properties.allowedOrigins(), objectMapper);
    }


    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(properties.allowedOrigins() == null ? new String[0] : properties.allowedOrigins().split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuration.setExposedHeaders(List.of("X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        CookieOriginFilter cookieOriginFilter,
        ObjectMapper objectMapper
    ) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/orders/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/public/orders/**").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                    "/api/invites/company/accept",
                    "/api/invites/user/accept"
                ).permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})
                .authenticationEntryPoint((request, response, exception) -> writeSecurityError(
                    response, objectMapper, request.getAttribute(RequestIdFilter.ATTRIBUTE),
                    HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Autenticação necessária."))
                .accessDeniedHandler((request, response, exception) -> writeSecurityError(
                    response, objectMapper, request.getAttribute(RequestIdFilter.ATTRIBUTE),
                    HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Acesso negado."))
            )
            .addFilterBefore(cookieOriginFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeSecurityError(HttpServletResponse response, ObjectMapper objectMapper, Object requestId,
                                           int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
            new ApiError(code, message, requestId == null ? "unknown" : requestId.toString()));
    }
}
