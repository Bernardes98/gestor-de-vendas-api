package com.gestordevendas.api.auth;

import com.gestordevendas.api.auth.dto.AuthResponse;
import com.gestordevendas.api.auth.dto.ForgotPasswordRequest;
import com.gestordevendas.api.auth.dto.ResetPasswordRequest;
import com.gestordevendas.api.auth.dto.LoginRequest;
import com.gestordevendas.api.auth.dto.MeResponse;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.user.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final CurrentUserService currentUserService;
    private final boolean cookieSecure;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthService authService,
                          CurrentUserService currentUserService,
                          AuthRateLimiter rateLimiter,
                          @Value("${app.security.cookie-secure:true}") boolean cookieSecure) {
        this.authService = authService;
        this.currentUserService = currentUserService;
        this.rateLimiter = rateLimiter;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        rateLimiter.checkLogin(clientIp(servletRequest), User.normalizeEmail(request.email()));
        AuthService.AuthSession session = authService.login(request, servletRequest.getHeader("User-Agent"), clientIp(servletRequest));
        return withRefreshCookie(session);
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        AuthService.AuthSession session = authService.refresh(readCookie(request, "refresh_token"),
            request.getHeader("User-Agent"), clientIp(request));
        return withRefreshCookie(session);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(readCookie(request, "refresh_token"));
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, expiredRefreshCookie().toString())
            .build();
    }


    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest servletRequest) {
        rateLimiter.checkPasswordReset(clientIp(servletRequest), User.normalizeEmail(request.email()));
        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    MeResponse me() {
        return authService.me(currentUserService.requireCurrentUser());
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthService.AuthSession session) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", session.refreshToken())
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(Duration.ofDays(30))
            .build();
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookie.toString())
            .body(session.response());
    }

    private ResponseCookie expiredRefreshCookie() {
        return ResponseCookie.from("refresh_token", "")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(Duration.ZERO)
            .build();
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
            .filter(cookie -> name.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst().orElse(null);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
