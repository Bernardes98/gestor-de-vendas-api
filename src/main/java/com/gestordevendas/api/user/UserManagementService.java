package com.gestordevendas.api.user;

import com.gestordevendas.api.audit.AuditService;
import com.gestordevendas.api.auth.RefreshTokenRepository;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.common.token.SecureTokenService;
import com.gestordevendas.api.invite.UserInvite;
import com.gestordevendas.api.invite.UserInviteRepository;
import com.gestordevendas.api.mail.EmailSender;
import com.gestordevendas.api.mail.MailProperties;
import com.gestordevendas.api.tenant.TenantContext;
import com.gestordevendas.api.tenant.TenantContextService;
import com.gestordevendas.api.tenant.TenantGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserManagementService {
    private final CurrentUserService currentUserService;
    private final TenantContextService tenantContextService;
    private final TenantGuard tenantGuard;
    private final CompanyMembershipRepository membershipRepository;
    private final UserInviteRepository userInviteRepository;
    private final SecureTokenService tokenService;
    private final EmailSender emailSender;
    private final MailProperties mailProperties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;

    public UserManagementService(CurrentUserService currentUserService,
                                 TenantContextService tenantContextService,
                                 TenantGuard tenantGuard,
                                 CompanyMembershipRepository membershipRepository,
                                 UserInviteRepository userInviteRepository,
                                 SecureTokenService tokenService,
                                 EmailSender emailSender,
                                 MailProperties mailProperties,
                                 RefreshTokenRepository refreshTokenRepository,
                                 AuditService auditService) {
        this.currentUserService = currentUserService;
        this.tenantContextService = tenantContextService;
        this.tenantGuard = tenantGuard;
        this.membershipRepository = membershipRepository;
        this.userInviteRepository = userInviteRepository;
        this.tokenService = tokenService;
        this.emailSender = emailSender;
        this.mailProperties = mailProperties;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<CompanyMembership> listUsers() {
        TenantContext context = currentTenant();
        tenantGuard.requireOwnerOrAdmin(context);
        return membershipRepository.findActiveByCompanyId(context.companyId());
    }

    @Transactional
    public UserInvite invite(String email, CompanyRole requestedRole) {
        User actor = currentUserService.requireCurrentUser();
        TenantContext context = tenantContextService.requireForUser(actor.getId());
        tenantGuard.requireOwnerOrAdmin(context);

        CompanyRole role;
        if (context.role() == CompanyRole.ADMIN) {
            role = CompanyRole.VENDEDOR;
        } else {
            role = requestedRole == null ? CompanyRole.VENDEDOR : requestedRole;
            if (role == CompanyRole.OWNER) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Convites podem criar ADMIN ou VENDEDOR.");
            }
        }

        String normalizedEmail = User.normalizeEmail(email);
        String rawToken = tokenService.generateRawToken();
        CompanyMembership actorMembership = membershipRepository.findByUserId(actor.getId()).orElseThrow();
        UserInvite invite = UserInvite.create(actorMembership.getCompany(), normalizedEmail, role,
            tokenService.hash(rawToken), Instant.now().plus(7, ChronoUnit.DAYS), actor);
        userInviteRepository.save(invite);
        String baseUrl = mailProperties.appBaseUrl() == null ? "" : mailProperties.appBaseUrl().replaceAll("/$", "");
        emailSender.sendUserInvite(normalizedEmail, baseUrl + "/primeiro-acesso?token=" + rawToken + "&type=user");
        auditService.record("USER_INVITED", context.companyId(), actor.getId(), "USER_INVITE", invite.getId(), null,
            Map.of("email", normalizedEmail, "role", role.name()));
        return invite;
    }

    @Transactional
    public CompanyMembership setStatus(UUID membershipId, boolean active) {
        User actor = currentUserService.requireCurrentUser();
        TenantContext context = tenantContextService.requireForUser(actor.getId());
        tenantGuard.requireOwnerOrAdmin(context);
        CompanyMembership target = targetMembership(context, membershipId);

        if (context.role() == CompanyRole.ADMIN && target.getRole() == CompanyRole.OWNER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "OWNER_REQUIRED", "ADMIN não pode bloquear OWNER.");
        }
        if (!active && target.getRole() == CompanyRole.OWNER) {
            ensureAnotherActiveOwner(context.companyId(), target);
        }
        target.setActive(active);
        if (!active) {
            refreshTokenRepository.revokeAllActiveByUserId(target.getUser().getId(), Instant.now());
        }
        auditService.record(active ? "USER_UNBLOCKED" : "USER_BLOCKED", context.companyId(), actor.getId(),
            "USER", target.getUser().getId(), null, Map.of());
        return target;
    }

    @Transactional
    public CompanyMembership setRole(UUID membershipId, CompanyRole role) {
        User actor = currentUserService.requireCurrentUser();
        TenantContext context = tenantContextService.requireForUser(actor.getId());
        tenantGuard.requireOwner(context);
        CompanyMembership target = targetMembership(context, membershipId);
        if (target.getRole() == CompanyRole.OWNER && role != CompanyRole.OWNER) {
            ensureAnotherActiveOwner(context.companyId(), target);
        }
        target.setRole(role);
        auditService.record("USER_ROLE_CHANGED", context.companyId(), actor.getId(), "USER", target.getUser().getId(), null,
            Map.of("role", role.name()));
        return target;
    }

    @Transactional
    public void remove(UUID membershipId) {
        User actor = currentUserService.requireCurrentUser();
        TenantContext context = tenantContextService.requireForUser(actor.getId());
        tenantGuard.requireOwner(context);
        CompanyMembership target = targetMembership(context, membershipId);
        if (target.getRole() == CompanyRole.OWNER) {
            ensureAnotherActiveOwner(context.companyId(), target);
        }
        target.setActive(false);
        refreshTokenRepository.revokeAllActiveByUserId(target.getUser().getId(), Instant.now());
        auditService.record("USER_REMOVED", context.companyId(), actor.getId(), "USER", target.getUser().getId(), null, Map.of());
    }

    private TenantContext currentTenant() {
        return tenantContextService.requireForUser(currentUserService.requireUserId());
    }

    private CompanyMembership targetMembership(TenantContext context, UUID membershipId) {
        CompanyMembership target = membershipRepository.findById(membershipId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Usuário não encontrado."));
        tenantGuard.requireSameCompany(context, target);
        return target;
    }

    private void ensureAnotherActiveOwner(UUID companyId, CompanyMembership target) {
        long owners = membershipRepository.countActiveByCompanyIdAndRole(companyId, CompanyRole.OWNER);
        if (owners <= 1 && target.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_OWNER_REQUIRED", "A empresa precisa manter ao menos um OWNER ativo.");
        }
    }
}
