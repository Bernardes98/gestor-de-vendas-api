package com.gestordevendas.api.user;

import com.gestordevendas.api.audit.AuditRepository;
import com.gestordevendas.api.auth.RefreshTokenRepository;
import com.gestordevendas.api.company.Company;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.invite.UserInviteRepository;
import com.gestordevendas.api.mail.EmailSender;
import com.gestordevendas.api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class UserPermissionsIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMembershipRepository membershipRepository;
    @Autowired UserInviteRepository userInviteRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AuditRepository auditRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @MockBean EmailSender emailSender;

    private Company company;
    private CompanyMembership ownerMembership;
    private CompanyMembership adminMembership;
    private CompanyMembership sellerMembership;
    private String ownerToken;
    private String adminToken;
    private String sellerToken;

    @BeforeEach
    void setUp() throws Exception {
        auditRepository.deleteAll();
        userInviteRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        company = companyRepository.save(Company.create("permissions", "Permissions", null, null, null, null));
        User owner = userRepository.save(User.create("owner@company.com", "Owner", passwordEncoder.encode("Senha123")));
        User admin = userRepository.save(User.create("admin@company.com", "Admin", passwordEncoder.encode("Senha123")));
        User seller = userRepository.save(User.create("seller@company.com", "Seller", passwordEncoder.encode("Senha123")));
        ownerMembership = membershipRepository.save(CompanyMembership.create(company, owner, CompanyRole.OWNER));
        adminMembership = membershipRepository.save(CompanyMembership.create(company, admin, CompanyRole.ADMIN));
        sellerMembership = membershipRepository.save(CompanyMembership.create(company, seller, CompanyRole.VENDEDOR));
        ownerToken = login("owner@company.com");
        adminToken = login("admin@company.com");
        sellerToken = login("seller@company.com");
    }

    @Test
    void adminInviteAlwaysBecomesSellerEvenWhenAdminWasRequested() throws Exception {
        mvc.perform(post("/api/users/invites")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new@company.com\",\"role\":\"ADMIN\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.role").value("VENDEDOR"));
    }

    @Test
    void ownerCanChangeRoleButAdminCannot() throws Exception {
        mvc.perform(patch("/api/users/{id}/role", sellerMembership.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isForbidden());

        mvc.perform(patch("/api/users/{id}/role", sellerMembership.getId())
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"ADMIN\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void adminCanBlockSellerButCannotBlockOwner() throws Exception {
        mvc.perform(patch("/api/users/{id}/status", sellerMembership.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
            .andExpect(status().isOk());

        mvc.perform(patch("/api/users/{id}/status", ownerMembership.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void onlyOwnerCanRemoveMembership() throws Exception {
        mvc.perform(delete("/api/users/{id}", sellerMembership.getId())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isForbidden());

        mvc.perform(delete("/api/users/{id}", sellerMembership.getId())
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isNoContent());
    }

    @Test
    void sellerCannotManageUsers() throws Exception {
        mvc.perform(post("/api/users/invites")
                .header("Authorization", "Bearer " + sellerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"blocked@company.com\"}"))
            .andExpect(status().isForbidden());
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
