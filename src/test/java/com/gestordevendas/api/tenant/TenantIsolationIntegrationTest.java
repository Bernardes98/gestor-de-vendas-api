package com.gestordevendas.api.tenant;

import com.gestordevendas.api.audit.AuditRepository;
import com.gestordevendas.api.auth.RefreshTokenRepository;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.invite.CompanyInviteRepository;
import com.gestordevendas.api.invite.UserInviteRepository;
import com.gestordevendas.api.mail.EmailSender;
import com.gestordevendas.api.support.PostgresIntegrationTest;
import com.gestordevendas.api.user.CompanyMembership;
import com.gestordevendas.api.user.CompanyMembershipRepository;
import com.gestordevendas.api.user.CompanyRole;
import com.gestordevendas.api.user.User;
import com.gestordevendas.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TenantIsolationIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMembershipRepository membershipRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserInviteRepository userInviteRepository;
    @Autowired CompanyInviteRepository companyInviteRepository;
    @Autowired AuditRepository auditRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean EmailSender emailSender;

    private Company companyA;
    private Company companyB;
    private CompanyMembership adminA;
    private CompanyMembership sellerB;
    private String tokenAdminA;
    private String tokenOwnerA;
    private String tokenOwnerB;
    private String platformToken;

    @BeforeEach
    void setUp() throws Exception {
        auditRepository.deleteAll();
        userInviteRepository.deleteAll();
        companyInviteRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        companyA = companyRepository.save(Company.create("empresa-a", "Empresa A", null, null, null, null));
        companyB = companyRepository.save(Company.create("empresa-b", "Empresa B", null, null, null, null));

        User ownerAUser = userRepository.save(User.create("owner-a@example.com", "Owner A", passwordEncoder.encode("Senha123")));
        User adminAUser = userRepository.save(User.create("admin-a@example.com", "Admin A", passwordEncoder.encode("Senha123")));
        User ownerBUser = userRepository.save(User.create("owner-b@example.com", "Owner B", passwordEncoder.encode("Senha123")));
        User sellerBUser = userRepository.save(User.create("seller-b@example.com", "Seller B", passwordEncoder.encode("Senha123")));
        User platform = User.create("platform-isolation@example.com", "Platform", passwordEncoder.encode("Senha123"));
        platform.setPlatformAdmin(true);
        userRepository.save(platform);

        membershipRepository.save(CompanyMembership.create(companyA, ownerAUser, CompanyRole.OWNER));
        adminA = membershipRepository.save(CompanyMembership.create(companyA, adminAUser, CompanyRole.ADMIN));
        membershipRepository.save(CompanyMembership.create(companyB, ownerBUser, CompanyRole.OWNER));
        sellerB = membershipRepository.save(CompanyMembership.create(companyB, sellerBUser, CompanyRole.VENDEDOR));

        tokenOwnerA = login("owner-a@example.com");
        tokenAdminA = login("admin-a@example.com");
        tokenOwnerB = login("owner-b@example.com");
        platformToken = login("platform-isolation@example.com");
    }

    @Test
    void adminFromCompanyACannotBlockMembershipFromCompanyB() throws Exception {
        mvc.perform(patch("/api/users/{id}/status", sellerB.getId())
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void manipulatedRoleChangeAcrossCompaniesIsBlocked() throws Exception {
        mvc.perform(patch("/api/users/{id}/role", sellerB.getId())
                .header("Authorization", "Bearer " + tokenOwnerA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void platformAdminCanBlockCompanyThroughPlatformEndpointAndExistingJwtStopsWorking() throws Exception {
        mvc.perform(post("/api/platform/companies/{id}/block", companyB.getId())
                .header("Authorization", "Bearer " + platformToken))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + tokenOwnerB))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("COMPANY_BLOCKED"));
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Senha123\"}"))
            .andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"accessToken\":\"") + 15;
        return body.substring(start, body.indexOf('"', start));
    }
}
