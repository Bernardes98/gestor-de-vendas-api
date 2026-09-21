package com.gestordevendas.api.platform;

import com.gestordevendas.api.audit.AuditService;
import com.gestordevendas.api.auth.RefreshTokenRepository;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.security.CurrentUserService;
import com.gestordevendas.api.common.token.SecureTokenService;
import com.gestordevendas.api.client.ClientRepository;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.invite.CompanyInvite;
import com.gestordevendas.api.invite.CompanyInviteRepository;
import com.gestordevendas.api.invite.InviteService;
import com.gestordevendas.api.product.ProductRepository;
import com.gestordevendas.api.sale.SaleRepository;
import com.gestordevendas.api.user.CompanyMembership;
import com.gestordevendas.api.user.CompanyMembershipRepository;
import com.gestordevendas.api.user.CompanyRole;
import com.gestordevendas.api.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PlatformService {
    private final CurrentUserService currentUserService;
    private final CompanyRepository companyRepository;
    private final CompanyInviteRepository companyInviteRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureTokenService tokenService;
    private final InviteService inviteService;
    private final AuditService auditService;
    private final ClientRepository clientRepository;
    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;

    public PlatformService(CurrentUserService currentUserService,
                           CompanyRepository companyRepository,
                           CompanyInviteRepository companyInviteRepository,
                           CompanyMembershipRepository membershipRepository,
                           RefreshTokenRepository refreshTokenRepository,
                           SecureTokenService tokenService,
                           InviteService inviteService,
                           AuditService auditService,
                           ClientRepository clientRepository,
                           ProductRepository productRepository,
                           SaleRepository saleRepository) {
        this.currentUserService = currentUserService;
        this.companyRepository = companyRepository;
        this.companyInviteRepository = companyInviteRepository;
        this.membershipRepository = membershipRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenService = tokenService;
        this.inviteService = inviteService;
        this.auditService = auditService;
        this.clientRepository = clientRepository;
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
    }

    @Transactional
    public CompanyInvite createCompanyInvite(CreateCompanyInviteCommand command) {
        User actor = requirePlatformAdmin();
        String slug = uniqueSlug(command.name());
        Company company = Company.create(slug, command.name(), command.legalName(), command.document(),
            command.primaryColor(), command.secondaryColor());
        companyRepository.save(company);
        String rawToken = tokenService.generateRawToken();
        CompanyInvite invite = CompanyInvite.create(company, User.normalizeEmail(command.ownerEmail()),
            tokenService.hash(rawToken), Instant.now().plus(7, ChronoUnit.DAYS), actor);
        companyInviteRepository.save(invite);
        auditService.record("COMPANY_CREATED", company.getId(), actor.getId(), "COMPANY", company.getId(), null,
            Map.of("ownerEmail", invite.getOwnerEmail()));
        inviteService.sendCompanyInvite(invite, rawToken);
        return invite;
    }

    @Transactional
    public void resendInvite(UUID inviteId) {
        User actor = requirePlatformAdmin();
        CompanyInvite invite = companyInviteRepository.findDetailedById(inviteId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND", "Convite não encontrado."));
        if (invite.getUsedAt() != null) throw new ApiException(HttpStatus.BAD_REQUEST, "INVITE_INVALID", "Convite já utilizado.");
        String rawToken = tokenService.generateRawToken();
        invite.reissue(tokenService.hash(rawToken), Instant.now().plus(7, ChronoUnit.DAYS));
        inviteService.sendCompanyInvite(invite, rawToken);
        auditService.record("COMPANY_INVITE_RESENT", invite.getCompany().getId(), actor.getId(), "COMPANY_INVITE", invite.getId(), null, Map.of());
    }

    @Transactional
    public void cancelInvite(UUID inviteId) {
        User actor = requirePlatformAdmin();
        CompanyInvite invite = companyInviteRepository.findDetailedById(inviteId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND", "Convite não encontrado."));
        if (invite.getUsedAt() != null) throw new ApiException(HttpStatus.BAD_REQUEST, "INVITE_INVALID", "Convite já utilizado.");
        invite.cancel(Instant.now());
        auditService.record("COMPANY_INVITE_CANCELLED", invite.getCompany().getId(), actor.getId(), "COMPANY_INVITE", invite.getId(), null, Map.of());
    }

    @Transactional
    public void setCompanyActive(UUID companyId, boolean active) {
        User actor = requirePlatformAdmin();
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa não encontrada."));
        company.setActive(active);
        if (!active) {
            membershipRepository.findActiveByCompanyId(companyId).forEach(membership ->
                refreshTokenRepository.revokeAllActiveByUserId(membership.getUser().getId(), Instant.now()));
        }
        auditService.record(active ? "COMPANY_ACTIVATED" : "COMPANY_BLOCKED", companyId, actor.getId(),
            "COMPANY", companyId, null, Map.of());
    }

    @Transactional(readOnly = true)
    public List<CompanySummaryView> listCompanySummaries() {
        requirePlatformAdmin();
        return companyRepository.findAll().stream()
            .map(company -> {
                List<CompanyMembership> activeMemberships = membershipRepository.findActiveByCompanyId(company.getId());
                String ownerEmail = activeMemberships.stream()
                    .filter(membership -> membership.getRole() == CompanyRole.OWNER)
                    .map(membership -> membership.getUser().getEmail())
                    .findFirst()
                    .orElse("");
                return new CompanySummaryView(
                    company.getId(),
                    company.getName(),
                    company.getSlug(),
                    company.isActive(),
                    ownerEmail,
                    activeMemberships.size(),
                    clientRepository.countByCompanyId(company.getId()),
                    productRepository.countByCompanyId(company.getId()),
                    saleRepository.countByCompanyId(company.getId())
                );
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public List<CompanyUserView> listCompanyUsers(UUID companyId) {
        requirePlatformAdmin();
        if (!companyRepository.existsById(companyId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa não encontrada.");
        }
        return membershipRepository.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
            .map(membership -> new CompanyUserView(
                membership.getId(),
                membership.getUser().getId(),
                membership.getUser().getName(),
                membership.getUser().getEmail(),
                membership.getRole(),
                membership.isActive(),
                membership.getCreatedAt()))
            .toList();
    }

    private User requirePlatformAdmin() {
        User current = currentUserService.requireCurrentUser();
        if (!current.isPlatformAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PLATFORM_ADMIN_REQUIRED", "Acesso de administrador da plataforma necessário.");
        }
        return current;
    }

    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
        if (base.isBlank()) base = "empresa";
        String candidate = base;
        int suffix = 2;
        while (companyRepository.existsBySlug(candidate)) candidate = base + "-" + suffix++;
        return candidate;
    }

    public record CreateCompanyInviteCommand(String name, String legalName, String document, String ownerEmail,
                                             String primaryColor, String secondaryColor) {}

    public record CompanySummaryView(UUID id, String name, String slug, boolean active, String ownerEmail,
                                     long userCount, long clientCount, long productCount, long saleCount) {}

    public record CompanyUserView(UUID membershipId, UUID userId, String name, String email, CompanyRole role,
                                  boolean active, Instant createdAt) {}
}
