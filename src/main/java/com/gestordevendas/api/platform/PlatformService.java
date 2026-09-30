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
import com.gestordevendas.api.invite.UserInvite;
import com.gestordevendas.api.invite.UserInviteRepository;
import com.gestordevendas.api.mail.EmailSender;
import com.gestordevendas.api.mail.MailProperties;
import com.gestordevendas.api.product.ProductRepository;
import com.gestordevendas.api.sale.SaleRepository;
import com.gestordevendas.api.user.CompanyMembership;
import com.gestordevendas.api.user.CompanyMembershipRepository;
import com.gestordevendas.api.user.CompanyRole;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final UserInviteRepository userInviteRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final MailProperties mailProperties;
    private final JdbcTemplate jdbcTemplate;

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
                           SaleRepository saleRepository,
                           UserInviteRepository userInviteRepository,
                           UserRepository userRepository,
                           EmailSender emailSender,
                           MailProperties mailProperties,
                           JdbcTemplate jdbcTemplate) {
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
        this.userInviteRepository = userInviteRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.mailProperties = mailProperties;
        this.jdbcTemplate = jdbcTemplate;
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

    @Transactional
    public void deleteCompany(UUID companyId) {
        User actor = requirePlatformAdmin();
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa não encontrada."));
        if (company.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPANY_MUST_BE_BLOCKED", "Bloqueie a empresa antes de excluí-la.");
        }

        String companyName = company.getName();
        String companySlug = company.getSlug();

        // Carrega os vínculos uma única vez. Eles ficam gerenciados pelo JPA durante a transação.
        List<CompanyMembership> memberships = membershipRepository.findByCompanyIdOrderByCreatedAtAsc(companyId);

        // Revoga sessões dos membros antes de remover seus vínculos com a empresa.
        memberships.stream()
            .map(membership -> membership.getUser().getId())
            .distinct()
            .forEach(userId -> refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now()));

        // Mantém o histórico de auditoria, sem a FK para a empresa que será removida.
        jdbcTemplate.update("UPDATE api_internal.auditoria SET empresa_id = NULL WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM api_internal.convites_empresa WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM api_internal.convites_usuario WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM api_internal.movimentacoes_estoque WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.cliente_produto_preco WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.cliente_produtos_ocultos WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.compra_itens WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.venda_itens WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.venda_recebimentos WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.recebivel_manual_pagamentos WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.recebiveis_manuais WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.pedidos_cliente WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.vendas WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.compras WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.clientes WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.produto_fotos WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.produtos WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM public.produto_grupos WHERE empresa_id = ?", companyId);
        jdbcTemplate.update("DELETE FROM api_internal.venda_sequencias WHERE empresa_id = ?", companyId);

        // Remove e descarrega os vínculos gerenciados antes de excluir a empresa.
        // Sem isso, o Hibernate tenta sincronizar memberships que ainda apontam para ela.
        membershipRepository.deleteAll(memberships);
        membershipRepository.flush();

        companyRepository.delete(company);
        auditService.record("COMPANY_DELETED", null, actor.getId(), "COMPANY", companyId, null,
            Map.of("name", companyName, "slug", companySlug));
    }

    @Transactional
    public UserInvite inviteCompanyUser(UUID companyId, String email, CompanyRole requestedRole) {
        User actor = requirePlatformAdmin();
        Company company = requireCompany(companyId);
        CompanyRole role = requestedRole == null ? CompanyRole.VENDEDOR : requestedRole;
        if (role == CompanyRole.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE", "Convites podem criar ADMIN ou VENDEDOR.");
        }

        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", "Este e-mail já possui uma conta. Use Vincular existente.");
        }
        if (!company.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPANY_BLOCKED", "Ative a empresa antes de enviar convites.");
        }

        String rawToken = tokenService.generateRawToken();
        UserInvite invite = UserInvite.create(company, normalizedEmail, role, tokenService.hash(rawToken),
            Instant.now().plus(7, ChronoUnit.DAYS), actor);
        userInviteRepository.save(invite);
        String baseUrl = mailProperties.appBaseUrl() == null ? "" : mailProperties.appBaseUrl().replaceAll("/$", "");
        emailSender.sendUserInvite(normalizedEmail, baseUrl + "/primeiro-acesso?token=" + rawToken + "&type=user");
        auditService.record("USER_INVITED", companyId, actor.getId(), "USER_INVITE", invite.getId(), null,
            Map.of("email", normalizedEmail, "role", role.name()));
        return invite;
    }

    @Transactional
    public CompanyUserView linkExistingUser(UUID companyId, String email, CompanyRole role) {
        User actor = requirePlatformAdmin();
        Company company = requireCompany(companyId);
        String normalizedEmail = User.normalizeEmail(email);
        User target = userRepository.findByEmailIgnoreCase(normalizedEmail)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Conta não encontrada. Envie um convite para este e-mail."));
        if (!target.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_BLOCKED", "Esta conta está desativada e não pode ser vinculada.");
        }
        if (!company.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "COMPANY_BLOCKED", "Ative a empresa antes de vincular usuários.");
        }

        CompanyMembership existing = membershipRepository.findByUserId(target.getId()).orElse(null);
        if (existing != null) {
            if (existing.getCompany().getId().equals(companyId)) {
                throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_LINKED", "Este usuário já está vinculado a esta empresa.");
            }
            throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_LINKED_OTHER_COMPANY", "Este usuário já está vinculado a outra empresa.");
        }

        CompanyRole assignedRole = role == null ? CompanyRole.VENDEDOR : role;
        CompanyMembership membership = membershipRepository.save(CompanyMembership.create(company, target, assignedRole));
        auditService.record("USER_LINKED", companyId, actor.getId(), "USER", target.getId(), null,
            Map.of("email", target.getEmail(), "role", assignedRole.name()));
        return toCompanyUserView(membership);
    }

    @Transactional
    public CompanyUserView updateCompanyUser(UUID companyId, UUID membershipId, CompanyRole role, boolean active) {
        User actor = requirePlatformAdmin();
        requireCompany(companyId);
        CompanyMembership target = membershipRepository.findByIdAndCompanyId(membershipId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Vínculo de usuário não encontrado nesta empresa."));
        CompanyRole nextRole = role == null ? target.getRole() : role;
        if (target.isActive() && target.getRole() == CompanyRole.OWNER
            && (!active || nextRole != CompanyRole.OWNER)
            && membershipRepository.countActiveByCompanyIdAndRole(companyId, CompanyRole.OWNER) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_OWNER_REQUIRED", "A empresa precisa manter ao menos um OWNER ativo.");
        }
        target.setRole(nextRole);
        target.setActive(active);
        if (!active) refreshTokenRepository.revokeAllActiveByUserId(target.getUser().getId(), Instant.now());
        auditService.record("PLATFORM_USER_UPDATED", companyId, actor.getId(), "USER", target.getUser().getId(), null,
            Map.of("role", nextRole.name(), "active", active));
        return toCompanyUserView(target);
    }

    @Transactional
    public void removeCompanyUser(UUID companyId, UUID membershipId) {
        User actor = requirePlatformAdmin();
        requireCompany(companyId);
        CompanyMembership target = membershipRepository.findByIdAndCompanyId(membershipId, companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Vínculo de usuário não encontrado nesta empresa."));
        if (target.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_MUST_BE_INACTIVE", "Desative o usuário antes de removê-lo da empresa.");
        }

        UUID userId = target.getUser().getId();
        String email = target.getUser().getEmail();
        CompanyRole role = target.getRole();
        refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now());
        membershipRepository.delete(target);
        membershipRepository.flush();
        auditService.record("PLATFORM_USER_UNLINKED", companyId, actor.getId(), "USER", userId, null,
            Map.of("email", email, "role", role.name()));
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
        requireCompany(companyId);
        return membershipRepository.findByCompanyIdOrderByCreatedAtAsc(companyId).stream()
            .map(this::toCompanyUserView)
            .toList();
    }

    @Transactional(readOnly = true)
    public Company requirePlatformCompany(UUID companyId) {
        requirePlatformAdmin();
        return requireCompany(companyId);
    }

    private Company requireCompany(UUID companyId) {
        return companyRepository.findById(companyId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND", "Empresa não encontrada."));
    }

    private CompanyUserView toCompanyUserView(CompanyMembership membership) {
        return new CompanyUserView(membership.getId(), membership.getUser().getId(), membership.getUser().getName(),
            membership.getUser().getEmail(), membership.getRole(), membership.isActive(), membership.getCreatedAt());
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
