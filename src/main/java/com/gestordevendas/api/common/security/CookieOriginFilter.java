package com.gestordevendas.api.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gestordevendas.api.common.error.ApiError;
import com.gestordevendas.api.common.request.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class CookieOriginFilter extends OncePerRequestFilter {
    private final Set<String> allowedOrigins;
    private final ObjectMapper objectMapper;

    public CookieOriginFilter(String allowedOrigins, ObjectMapper objectMapper) {
        this.allowedOrigins = Arrays.stream(allowedOrigins == null ? new String[0] : allowedOrigins.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toUnmodifiableSet());
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("POST".equalsIgnoreCase(request.getMethod())
            && ("/api/auth/refresh".equals(path) || "/api/auth/logout".equals(path)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.contains(origin)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String requestId = String.valueOf(request.getAttribute(RequestIdFilter.ATTRIBUTE));
            objectMapper.writeValue(response.getOutputStream(), new ApiError(
                "ORIGIN_NOT_ALLOWED", "Origem não permitida.", requestId));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
