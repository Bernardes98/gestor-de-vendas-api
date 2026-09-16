package com.gestordevendas.api.invite;

import com.gestordevendas.api.audit.AuditService;
import com.gestordevendas.api.auth.PasswordPolicy;
import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.common.token.SecureTokenService;
import com.gestordevendas.api.mail.EmailSender;
import com.gestordevendas.api.mail.MailProperties;
import com.gestordevendas.api.user.CompanyMembership;
import com.gestordevendas.api.user.CompanyMembershipRepository;
import com.gestordevendas.api.user.CompanyRole;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class InviteService {
    private final CompanyInviteRepository companyInviteRepository;
    private final UserInviteRepository userInviteRepository;
    private final UserRepository userRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final SecureTokenService tokenService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final MailProperties mailProperties;
    private final AuditService auditService;

    public InviteService(CompanyInviteRepository companyInviteRepository,
                         UserInviteRepository userInviteRepository,
                         UserRepository userRepository,
                         CompanyMembershipRepository membershipRepository,
                         SecureTokenService tokenService,
                         PasswordPolicy passwordPolicy,
                         PasswordEncoder passwordEncoder,
                         EmailSender emailSender,
                         MailProperties mailProperties,
                         AuditService auditService) {
        this.companyInviteRepository = companyInviteRepository;
        this.userInviteRepository = userInviteRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.tokenService = tokenService;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.mailProperties = mailProperties;
        this.auditService = auditService;
    }

    @Transactional
    public void acceptCompanyInvite(String rawToken, String name, String password) {
        passwordPolicy.validate(password);
        CompanyInvite invite = companyInviteRepository.findByTokenHashForUpdate(tokenService.hash(rawToken))
            .orElseThrow(this::invalidInvite);
        Instant now = Instant.now();
        if (!invite.isPending(now)) throw invalidInvite();
        if (!invite.getCompany().isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPANY_BLOCKED", "Empresa bloqueada.");
        }

        User user = userRepository.findByEmailIgnoreCase(invite.getOwnerEmail()).orElse(null);
        if (user != null && membershipRepository.findByUserId(user.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_LINKED", "Usuário já vinculado a uma empresa.");
        }
        if (user == null) {
            user = User.create(invite.getOwnerEmail(), name, passwordEncoder.encode(password));
        } else {
            user.setName(name);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setActive(true);
            user.setMustResetPassword(false);
        }
        userRepository.save(user);
        membershipRepository.save(CompanyMembership.create(invite.getCompany(), user, CompanyRole.OWNER));
        invite.markUsed(now);
        auditService.record("COMPANY_INVITE_ACCEPTED", invite.getCompany().getId(), user.getId(),
            "COMPANY", invite.getCompany().getId(), null, java.util.Map.of());
    }


    @Transactional
    public void acceptUserInvite(String rawToken, String name, String password) {
        passwordPolicy.validate(password);
        UserInvite invite = userInviteRepository.findByTokenHashForUpdate(tokenService.hash(rawToken))
            .orElseThrow(this::invalidInvite);
        Instant now = Instant.now();
        if (!invite.isPending(now)) throw invalidInvite();
        if (!invite.getCompany().isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPANY_BLOCKED", "Empresa bloqueada.");
        }
        User user = userRepository.findByEmailIgnoreCase(invite.getEmail()).orElse(null);
        if (user != null && membershipRepository.findByUserId(user.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "USER_ALREADY_LINKED", "Usuário já vinculado a uma empresa.");
        }
        if (user == null) {
            user = User.create(invite.getEmail(), name, passwordEncoder.encode(password));
        } else {
            user.setName(name);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setActive(true);
            user.setMustResetPassword(false);
        }
        userRepository.save(user);
        membershipRepository.save(CompanyMembership.create(invite.getCompany(), user, invite.getRole()));
        invite.markUsed(now);
        auditService.record("USER_INVITE_ACCEPTED", invite.getCompany().getId(), user.getId(), "USER", user.getId(), null,
            java.util.Map.of("role", invite.getRole().name()));
    }

    public void sendCompanyInvite(CompanyInvite invite, String rawToken) {
        String url = baseUrl() + "/primeiro-acesso?token=" + rawToken + "&type=company";
        emailSender.sendCompanyInvite(invite.getOwnerEmail(), url);
    }

    private String baseUrl() {
        return mailProperties.appBaseUrl() == null ? "" : mailProperties.appBaseUrl().replaceAll("/$", "");
    }

    private ApiException invalidInvite() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVITE_INVALID", "Convite inválido ou expirado.");
    }
}
