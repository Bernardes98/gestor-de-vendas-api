package com.gestordevendas.api.tenant;

import com.gestordevendas.api.common.error.ApiException;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.user.CompanyMembership;
import com.gestordevendas.api.user.CompanyMembershipRepository;
import com.gestordevendas.api.user.CompanyRole;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantContextServiceTest {
    private final UUID userId = UUID.randomUUID();
    private UserRepository userRepository;
    private CompanyMembershipRepository membershipRepository;
    private TenantContextService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        membershipRepository = mock(CompanyMembershipRepository.class);
        service = new TenantContextService(userRepository, membershipRepository);
    }

    @Test
    void blocksUserWhenUserIsInactive() {
        User user = new User(userId, "user@example.com", "User", false, false, false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.requireForUser(userId))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).code())
            .isEqualTo("USER_BLOCKED");
    }

    @Test
    void blocksUserWhenMembershipIsInactive() {
        User user = new User(userId, "user@example.com", "User", true, false, false);
        Company company = new Company(UUID.randomUUID(), "empresa", "Empresa", true);
        CompanyMembership membership = new CompanyMembership(UUID.randomUUID(), company, user, CompanyRole.ADMIN, false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(userId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.requireForUser(userId))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).code())
            .isEqualTo("USER_BLOCKED");
    }

    @Test
    void blocksCompanyWhenCompanyIsInactive() {
        User user = new User(userId, "user@example.com", "User", true, false, false);
        Company company = new Company(UUID.randomUUID(), "empresa", "Empresa", false);
        CompanyMembership membership = new CompanyMembership(UUID.randomUUID(), company, user, CompanyRole.ADMIN, true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(userId)).thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> service.requireForUser(userId))
            .isInstanceOf(ApiException.class)
            .extracting(ex -> ((ApiException) ex).code())
            .isEqualTo("COMPANY_BLOCKED");
    }

    @Test
    void returnsServerResolvedTenantContext() {
        User user = new User(userId, "user@example.com", "User", true, false, false);
        Company company = new Company(UUID.randomUUID(), "empresa", "Empresa", true);
        CompanyMembership membership = new CompanyMembership(UUID.randomUUID(), company, user, CompanyRole.ADMIN, true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUserId(userId)).thenReturn(Optional.of(membership));

        TenantContext context = service.requireForUser(userId);

        assertThat(context.companyId()).isEqualTo(company.getId());
        assertThat(context.userId()).isEqualTo(userId);
        assertThat(context.role()).isEqualTo(CompanyRole.ADMIN);
    }
}
