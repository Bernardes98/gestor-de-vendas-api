package com.gestordevendas.api.platform;

import com.gestordevendas.api.audit.AuditRepository;
import com.gestordevendas.api.auth.RefreshTokenRepository;
import com.gestordevendas.api.company.CompanyRepository;
import com.gestordevendas.api.invite.CompanyInviteRepository;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PlatformCompanyIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyMembershipRepository membershipRepository;
    @Autowired CompanyInviteRepository companyInviteRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuditRepository auditRepository;
    @MockBean EmailSender emailSender;

    private String platformToken;
    private String commonToken;

    @BeforeEach
    void setUp() throws Exception {
        auditRepository.deleteAll();
        companyInviteRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        membershipRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        User platformAdmin = User.create("platform@example.com", "Platform", passwordEncoder.encode("Senha123"));
        platformAdmin.setPlatformAdmin(true);
        userRepository.save(platformAdmin);
        User common = User.create("common@example.com", "Common", passwordEncoder.encode("Senha123"));
        common.setPlatformAdmin(false);
        userRepository.save(common);
        var commonCompany = companyRepository.save(com.gestordevendas.api.company.Company.create("common-company", "Common Company", null, null, null, null));
        membershipRepository.save(CompanyMembership.create(commonCompany, common, CompanyRole.OWNER));

        platformToken = login("platform@example.com");
        commonToken = login("common@example.com");
    }

    @Test
    void commonUserCannotProvisionCompanies() throws Exception {
        mvc.perform(post("/api/platform/companies/invites")
                .header("Authorization", "Bearer " + commonToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(inviteJson()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PLATFORM_ADMIN_REQUIRED"));
    }

    @Test
    void platformAdminCreatesCompanyInviteAndSendsEmail() throws Exception {
        mvc.perform(post("/api/platform/companies/invites")
                .header("Authorization", "Bearer " + platformToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(inviteJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.company.name").value("Empresa Nova"))
            .andExpect(jsonPath("$.ownerEmail").value("owner@example.com"));

        verify(emailSender).sendCompanyInvite(anyString(), anyString());
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"Senha123\"}"))
            .andReturn().getResponse().getContentAsString();
        int start = body.indexOf("\"accessToken\":\"") + 15;
        return body.substring(start, body.indexOf('"', start));
    }

    private String inviteJson() {
        return "{\"name\":\"Empresa Nova\",\"legalName\":\"Empresa Nova LTDA\",\"document\":\"12345678000199\",\"ownerEmail\":\"owner@example.com\",\"primaryColor\":\"#800020\",\"secondaryColor\":\"#101827\"}";
    }
}
