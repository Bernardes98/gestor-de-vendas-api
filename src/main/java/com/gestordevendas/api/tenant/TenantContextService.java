package com.gestordevendas.api.tenant;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.user.CompanyMembership;
import com.gestordevendas.api.user.CompanyMembershipRepository;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TenantContextService {
    private final UserRepository userRepository;
    private final CompanyMembershipRepository membershipRepository;

    public TenantContextService(UserRepository userRepository, CompanyMembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
    }

    @Transactional(readOnly = true)
    public TenantContext requireForUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "USER_BLOCKED", "Usuário sem acesso."));
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_BLOCKED", "Usuário bloqueado.");
        }

        CompanyMembership membership = membershipRepository.findByUserId(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NO_COMPANY_MEMBERSHIP", "Usuário sem empresa vinculada."));
        if (!membership.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_BLOCKED", "Usuário bloqueado nesta empresa.");
        }
        if (!membership.getCompany().isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "COMPANY_BLOCKED", "Empresa bloqueada.");
        }

        return new TenantContext(membership.getCompany().getId(), userId, membership.getRole());
    }
}
