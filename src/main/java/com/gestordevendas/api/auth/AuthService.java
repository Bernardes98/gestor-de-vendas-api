package com.gestordevendas.api.auth;

import com.gestordevendas.api.auth.dto.AuthResponse;
import com.gestordevendas.api.auth.dto.LoginRequest;
import com.gestordevendas.api.auth.dto.MeResponse;
import com.gestordevendas.api.audit.AuditService;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.SecurityProperties;
import com.gestordevendas.api.common.token.SecureTokenService;
import com.gestordevendas.api.mail.EmailSender;
import com.gestordevendas.api.mail.MailProperties;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.user.CompanyMembership;
import com.gestordevendas.api.user.CompanyMembershipRepository;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {
    private static final String DUMMY_BCRYPT = "$2a$12$0QDjK0MqvNjKQuO6bIqL0uJbZLX2sPSV4P/epqYHnuYTe1nBBDnc6";

    private final UserRepository userRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final TenantContextService tenantContextService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureTokenService secureTokenService;
    private final SecurityProperties securityProperties;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordPolicy passwordPolicy;
    private final EmailSender emailSender;
    private final MailProperties mailProperties;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       CompanyMembershipRepository membershipRepository,
                       TenantContextService tenantContextService,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenRepository refreshTokenRepository,
                       SecureTokenService secureTokenService,
                       SecurityProperties securityProperties,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordPolicy passwordPolicy,
                       EmailSender emailSender,
                       MailProperties mailProperties,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.tenantContextService = tenantContextService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.secureTokenService = secureTokenService;
        this.securityProperties = securityProperties;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordPolicy = passwordPolicy;
        this.emailSender = emailSender;
        this.mailProperties = mailProperties;
        this.auditService = auditService;
    }

    @Transactional
    public AuthSession login(LoginRequest request, String userAgent, String ipAddress) {
        String email = User.normalizeEmail(request.email());
        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT);
            throw invalidCredentials();
        }
        if (user.isMustResetPassword()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PASSWORD_RESET_REQUIRED", "Defina uma nova senha para continuar.");
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_BLOCKED", "Usuário bloqueado.");
        }
        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        validateTenantWhenRequired(user);
        return createSession(user, userAgent, ipAddress);
    }

    @Transactional
    public AuthSession refresh(String rawToken, String userAgent, String ipAddress) {
        if (rawToken == null || rawToken.isBlank()) {
            throw sessionInvalid();
        }
        String hash = secureTokenService.hash(rawToken);
        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(hash)
            .orElseThrow(this::sessionInvalid);
        Instant now = Instant.now();
        if (current.isRevoked()) {
            refreshTokenRepository.revokeAllActiveByUserId(current.getUser().getId(), now);
            throw sessionInvalid();
        }
        if (current.isExpired(now)) {
            current.revoke(now);
            throw sessionInvalid();
        }
        User user = current.getUser();
        if (!user.isActive()) {
            refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_BLOCKED", "Usuário bloqueado.");
        }
        validateTenantWhenRequired(user);

        String newRawToken = secureTokenService.generateRawToken();
        RefreshToken replacement = RefreshToken.create(user, secureTokenService.hash(newRawToken),
            now.plus(securityProperties.refreshTokenDays(), ChronoUnit.DAYS), userAgent, ipAddress);
        refreshTokenRepository.save(replacement);
        current.replaceWith(replacement, now);
        return new AuthSession(new AuthResponse(jwtService.issueAccessToken(user), securityProperties.accessTokenMinutes() * 60), newRawToken);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokenRepository.findByTokenHashForUpdate(secureTokenService.hash(rawToken))
            .filter(token -> !token.isRevoked())
            .ifPresent(token -> token.revoke(Instant.now()));
    }

    @Transactional(readOnly = true)
    public MeResponse me(User user) {
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_BLOCKED", "Usuário bloqueado.");
        }
        if (user.isPlatformAdmin()) {
            return new MeResponse(
                new MeResponse.UserView(user.getId(), user.getEmail(), true), null, null);
        }
        TenantContext context = tenantContextService.requireForUser(user.getId());
        CompanyMembership membership = membershipRepository.findByUserId(user.getId())
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NO_COMPANY_MEMBERSHIP", "Usuário sem empresa vinculada."));
        return new MeResponse(
            new MeResponse.UserView(user.getId(), user.getEmail(), false),
            new MeResponse.CompanyView(context.companyId(), membership.getCompany().getName(), true),
            context.role());
    }


    @Transactional
    public void requestPasswordReset(String email) {
        String normalized = User.normalizeEmail(email);
        userRepository.findByEmailIgnoreCase(normalized)
            .filter(User::isActive)
            .ifPresent(user -> {
                String rawToken = secureTokenService.generateRawToken();
                PasswordResetToken token = PasswordResetToken.create(user, secureTokenService.hash(rawToken),
                    Instant.now().plus(30, ChronoUnit.MINUTES));
                passwordResetTokenRepository.save(token);
                String baseUrl = mailProperties.appBaseUrl() == null ? "" : mailProperties.appBaseUrl().replaceAll("/$", "");
                emailSender.sendPasswordReset(user.getEmail(), baseUrl + "/redefinir-senha?token=" + rawToken);
            });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        passwordPolicy.validate(newPassword);
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHashForUpdate(secureTokenService.hash(rawToken))
            .orElseThrow(this::resetTokenInvalid);
        Instant now = Instant.now();
        if (token.isUsed() || token.isExpired(now)) {
            throw resetTokenInvalid();
        }
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustResetPassword(false);
        token.markUsed(now);
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
        membershipRepository.findByUserId(user.getId()).ifPresentOrElse(
            membership -> auditService.record("PASSWORD_RESET", membership.getCompany().getId(), user.getId(), "USER", user.getId(), null, java.util.Map.of()),
            () -> auditService.record("PASSWORD_RESET", null, user.getId(), "USER", user.getId(), null, java.util.Map.of())
        );
    }

    @Transactional
    public void revokeAllSessions(User user) {
        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
    }

    private AuthSession createSession(User user, String userAgent, String ipAddress) {
        String rawRefreshToken = secureTokenService.generateRawToken();
        RefreshToken refreshToken = RefreshToken.create(user, secureTokenService.hash(rawRefreshToken),
            Instant.now().plus(securityProperties.refreshTokenDays(), ChronoUnit.DAYS), userAgent, ipAddress);
        refreshTokenRepository.save(refreshToken);
        return new AuthSession(new AuthResponse(jwtService.issueAccessToken(user), securityProperties.accessTokenMinutes() * 60), rawRefreshToken);
    }

    private void validateTenantWhenRequired(User user) {
        if (!user.isPlatformAdmin()) {
            tenantContextService.requireForUser(user.getId());
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "E-mail ou senha inválidos.");
    }

    private ApiException sessionInvalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID", "Sessão inválida ou expirada.");
    }

    private ApiException resetTokenInvalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "RESET_TOKEN_INVALID", "Link de redefinição inválido ou expirado.");
    }

    public record AuthSession(AuthResponse response, String refreshToken) {}
}
